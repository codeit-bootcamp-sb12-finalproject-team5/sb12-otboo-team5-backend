"""분리된 좋아요·댓글·팔로우 payload → worker → DB → HTTP SSE를 검증한다.

로컬 API/worker/Kafka/Postgres 실행 후 python3 scripts/verify-notification-sources.py.
업무 API가 아직 없는 세 유형은 전용 원본 행과 Kafka 요청으로 검증한다.
테스트가 만든 업무 행은 삭제하고 테스트 계정은 잠근다. 비밀값은 출력하지 않는다.
"""
import importlib.util
import http.client
import json
from pathlib import Path
import queue
import secrets
import subprocess
import threading
import time
import urllib.parse
import uuid
from datetime import datetime, timezone

spec = importlib.util.spec_from_file_location("verify", Path(__file__).with_name("verify-notification.py"))
api = importlib.util.module_from_spec(spec)
spec.loader.exec_module(api)


def sql(statement):
    result = subprocess.run(["docker", "exec", "-i", "otboo-postgres", "sh", "-c",
        'psql -X -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -At'],
        input=statement, text=True, capture_output=True, timeout=20)
    if result.returncode:
        raise RuntimeError("검증용 SQL 실패: " + result.stderr)
    return result.stdout.strip()


def publish(messages):
    result = subprocess.run(["docker", "exec", "-i", "otboo-kafka",
        "/opt/kafka/bin/kafka-console-producer.sh", "--bootstrap-server", "localhost:9092",
        "--topic", "notification-create", "--property", "parse.key=true", "--property", "key.separator=|"],
        input="".join(m["eventId"] + "|" + json.dumps(m, ensure_ascii=False) + "\n" for m in messages),
        text=True, capture_output=True, timeout=30)
    if result.returncode:
        raise RuntimeError("Kafka 검증 메시지 발행 실패")


def main():
    admin = api.login(*api.admin_credentials())
    accounts = []
    connection = None
    feed, like, comment, follow = (str(uuid.uuid4()) for _ in range(4))
    try:
        for name in ("알림 원본 작성자", "알림 원본 수신자"):
            email = "notification-source-" + uuid.uuid4().hex[:12] + "@example.invalid"
            password = secrets.token_urlsafe(24)
            user = api.request("POST", "/api/users", {"name": name, "email": email, "password": password})
            accounts.append((user["id"], api.login(email, password)))
        (actor, _), (receiver, token) = accounts
        sql(f"""BEGIN;
INSERT INTO outfit(id,user_id,name,category,created_at,updated_at)
VALUES ('{feed}','{receiver}','알림 검증','DAILY',now(),now());
INSERT INTO feed(id,user_id,content,is_visible,created_at,updated_at)
VALUES ('{feed}','{receiver}','SSE 좋아요 피드 원문',true,now(),now());
INSERT INTO feed_like(id,feed_id,user_id,created_at) VALUES ('{like}','{feed}','{actor}',now());
INSERT INTO feed_comment(id,feed_id,user_id,content,created_at,updated_at)
VALUES ('{comment}','{feed}','{actor}','SSE 댓글 원문',now(),now());
INSERT INTO follow(id,follower_id,followee_id,created_at) VALUES ('{follow}','{actor}','{receiver}',now());
COMMIT;""")
        url = urllib.parse.urlsplit(api.BASE_URL)
        client = http.client.HTTPSConnection if url.scheme == "https" else http.client.HTTPConnection
        connection = client(url.hostname, url.port, timeout=45)
        connection.request("GET", url.path + "/api/sse", headers={
            "Authorization": "Bearer " + token, "Accept": "text/event-stream"})
        response = connection.getresponse()
        assert response.status == 200, f"SSE HTTP {response.status}"
        assert "text/event-stream" in response.getheader("Content-Type", "")
        received = queue.Queue()
        def read_stream():
            event, data = "", []
            try:
                while True:
                    line = response.readline()
                    if not line:
                        return
                    line = line.decode().rstrip("\r\n")
                    if line.startswith("event:"):
                        event = line[6:].strip()
                    elif line.startswith("data:"):
                        data.append(line[5:].lstrip())
                    elif not line:
                        if event == "notifications" and data:
                            received.put(json.loads("\n".join(data)))
                        event, data = "", []
            except Exception as error:
                received.put(error)
        threading.Thread(target=read_stream, daemon=True).start()
        messages = [{"eventId": source, "schemaVersion": 2, "type": kind,
            "occurredAt": datetime.now(timezone.utc).isoformat(), "deduplicationKey": kind + ":" + source,
            "payload": {field: source}} for kind, field, source in (
                ("FEED_LIKED", "likeId", like), ("FEED_COMMENTED", "commentId", comment),
                ("FOLLOWED", "followId", follow))]
        publish(messages)
        notifications = []
        try:
            for _ in messages:
                notifications.append(received.get(timeout=30))
        except queue.Empty:
            raise RuntimeError(f"SSE 수신 시간 초과: {len(notifications)}/3개 수신") from None
        for item in notifications:
            if isinstance(item, Exception):
                raise item
            assert item["receiverId"] == receiver
        assert {item["content"] for item in notifications} == {"SSE 좋아요 피드 원문", "SSE 댓글 원문", ""}
        listing = api.request("GET", "/api/notifications?limit=20", token=token)
        assert {item["id"] for item in listing["data"]} == {item["id"] for item in notifications}
        publish(messages)
        time.sleep(3)
        assert received.empty(), "중복 요청이 SSE로 재발송됨"
        assert api.request("GET", "/api/notifications?limit=20", token=token)["totalCount"] == 3
        print(json.dumps({"result": "PASS", "types": [m["type"] for m in messages],
            "sseReceived": len(notifications), "storedInList": 3,
            "originalContentMatched": True, "emptyFollowContentAllowed": True,
            "duplicateSuppressed": True}, ensure_ascii=False))
    finally:
        if connection:
            connection.close()
        # 이 실행에서 생성한 UUID로만 정리한다. 사용자 원본 데이터에는 접근하지 않는다.
        sql(f"""BEGIN;
DELETE FROM notification WHERE deduplication_key IN ('FEED_LIKED:{like}','FEED_COMMENTED:{comment}','FOLLOWED:{follow}');
DELETE FROM feed_comment WHERE id='{comment}';
DELETE FROM feed_like WHERE id='{like}';
DELETE FROM follow WHERE id='{follow}';
DELETE FROM feed WHERE id='{feed}';
DELETE FROM outfit WHERE id='{feed}';
COMMIT;""")
        admin = api.login(*api.admin_credentials())
        for user_id, _ in accounts:
            api.request("PATCH", f"/api/users/{user_id}/lock", {"locked": True}, admin)


if __name__ == "__main__":
    main()
