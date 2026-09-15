"""실행 중인 로컬 API에서 테스트 계정의 권한 변경 → SSE → 목록을 검증한다.

사용: python3 scripts/verify-notification.py
기존 관리자 설정으로 로그인하며 비밀번호와 JWT는 출력하거나 저장하지 않는다.
매 실행마다 테스트 계정을 만들고, 검증 후 USER 권한/잠금 상태로 정리한다.
"""

import http.client
import json
import os
from pathlib import Path
import queue
import re
import secrets
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid


ROOT = Path(__file__).resolve().parents[1]
BASE_URL = os.environ.get("NOTIFICATION_TEST_API_URL", "http://localhost:8080").rstrip("/")


def admin_credentials():
    values = {}
    env_file = ROOT / ".env"
    if env_file.exists():
        for line in env_file.read_text().splitlines():
            key, sep, value = line.strip().removeprefix("export ").partition("=")
            if sep and key in ("ADMIN_EMAIL", "ADMIN_PASSWORD"):
                values[key] = value.strip().strip("\"'")
    values.update({key: os.environ[key] for key in ("ADMIN_EMAIL", "ADMIN_PASSWORD") if key in os.environ})
    config = (ROOT / "api/src/main/resources/application.yaml").read_text()
    for key in ("ADMIN_EMAIL", "ADMIN_PASSWORD"):
        match = re.search(r"\$\{" + key + r":([^}]+)\}", config)
        if key not in values and match:
            values[key] = match.group(1)
    if not all(values.get(key) for key in ("ADMIN_EMAIL", "ADMIN_PASSWORD")):
        raise RuntimeError("관리자 로그인 설정이 필요합니다.")
    return values["ADMIN_EMAIL"], values["ADMIN_PASSWORD"]


def request(method, path, body=None, token=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(BASE_URL + path,
            data=None if body is None else json.dumps(body).encode(), headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=20) as response:
            data = response.read()
            return json.loads(data) if data else None
    except urllib.error.HTTPError as error:
        # 로그인/토큰 응답 본문은 로그에 남기지 않는다.
        raise RuntimeError(f"{method} {path}: HTTP {error.code}") from None


def login(email, password):
    return request("POST", "/api/auth/sign-in", {"email": email, "password": password})["accessToken"]


def main():
    admin_email, admin_password = admin_credentials()
    admin = login(admin_email, admin_password)
    email = "notification-check-" + uuid.uuid4().hex[:12] + "@example.invalid"
    password = secrets.token_urlsafe(24)
    target = request("POST", "/api/users", {"name": "알림 수신 검증", "email": email, "password": password})
    target_id = target["id"]
    connection = None
    try:
        recipient = login(email, password)
        url = urllib.parse.urlsplit(BASE_URL)
        client = http.client.HTTPSConnection if url.scheme == "https" else http.client.HTTPConnection
        connection = client(url.hostname, url.port, timeout=35)
        connection.request("GET", url.path + "/api/sse", headers={
                "Authorization": "Bearer " + recipient, "Accept": "text/event-stream"})
        response = connection.getresponse()
        if response.status != 200 or "text/event-stream" not in response.getheader("Content-Type", ""):
            raise RuntimeError(f"SSE 연결 실패: HTTP {response.status}")

        received = queue.Queue()

        def read_stream():
            event, data = "", []
            try:
                while True:
                    line = response.readline()
                    if not line:
                        raise RuntimeError("알림 수신 전 SSE 연결이 종료되었습니다.")
                    line = line.decode().rstrip("\r\n")
                    if line.startswith("event:"):
                        event = line[6:].strip()
                    elif line.startswith("data:"):
                        data.append(line[5:].lstrip())
                    elif not line:
                        if event == "notifications" and data:
                            received.put(json.loads("\n".join(data)))
                            return
                        event, data = "", []
            except Exception as error:
                received.put(error)

        threading.Thread(target=read_stream, daemon=True).start()
        request("PATCH", f"/api/users/{target_id}/role", {"role": "ADMIN"}, admin)
        try:
            notification = received.get(timeout=30)
        except queue.Empty:
            raise RuntimeError("30초 내 SSE 알림을 받지 못했습니다.") from None
        if isinstance(notification, Exception):
            raise notification
        assert notification["receiverId"] == target_id
        assert notification["title"] and notification["content"]

        recipient = login(email, password)
        listing = request("GET", "/api/notifications?limit=20", token=recipient)
        assert any(item["id"] == notification["id"] for item in listing["data"])
        assert listing["totalCount"] == 1

        # 같은 권한 재지정은 추가 알림을 만들지 않아야 한다.
        request("PATCH", f"/api/users/{target_id}/role", {"role": "ADMIN"}, admin)
        recipient = login(email, password)
        time.sleep(2)
        assert request("GET", "/api/notifications?limit=20", token=recipient)["totalCount"] == 1
        print(json.dumps({"result": "PASS", "receiverId": target_id,
                "notificationId": notification["id"], "event": "notifications",
                "title": notification["title"], "sseReceived": True,
                "foundInListAfterRelogin": True, "sameRoleDidNotDuplicate": True}, ensure_ascii=False))
    finally:
        if connection:
            connection.close()
        # 테스트 계정의 관리자 권한을 남기지 않는다. 알림/계정 행은 검증 기록으로 보존한다.
        try:
            request("PATCH", f"/api/users/{target_id}/role", {"role": "USER"}, admin)
        finally:
            request("PATCH", f"/api/users/{target_id}/lock", {"locked": True}, admin)
        print("테스트 계정을 USER 권한으로 복구하고 잠갔습니다: " + email)


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print("알림 검증 실패: " + str(error))
        raise SystemExit(1)
