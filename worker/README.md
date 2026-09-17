# 권한 변경 알림

## 패키지 구조

기존 API·batch처럼 notification 도메인 아래에 역할별 패키지를 둔다.

```text
com.codeit.otboo.worker
├── WorkerApplication
└── notification
    ├── config      # Kafka consumer 설정
    ├── listener    # 생성 요청 수신과 저장 후 전달
    ├── handler     # 유형별 수신자·payload 처리
    ├── service     # 알림 저장 트랜잭션
    └── repository  # worker 전용 JDBC 삽입
```

JPA Repository와 엔티티는 기존 domain 모듈에 유지한다.
단위 테스트는 대상 코드의 패키지에 맞추며 파이프라인 통합 테스트는 notification 아래에 둔다.

## 처리 흐름

AdminService.updateRole에서 권한이 실제로 바뀐 경우 NotificationEvents.roleChanged를 발행한다.
업무 트랜잭션의 AFTER_COMMIT 리스너가 notification-create로 보낸다. 롤백 및 트랜잭션 밖 발행은 전달하지 않는다.

worker는 NotificationRequestHandler 목록을 유형별로 등록하고 SingleNotificationHandler로
ROLE_CHANGED를 처리한다. 존재하며 탈퇴하지 않은 사용자를 대상으로 알림을 저장한다.
잠긴 사용자도 DB 알림은 보존한다. 기존 권한 변경의 세션 무효화 동작은 유지한다.

NotificationSaveService의 트랜잭션이 커밋된 다음 notification-broadcasting으로 전송한다.
API마다 서로 다른 consumer group으로 받아 NotificationSseService의 제한된 전송 큐에 넣는다.
이벤트 이름은 notifications이며 데이터는 기존 NotificationDto다.

## 실행 설정

기존 버전, 비밀값, Docker 구성은 변경하지 않았다. API와 worker 모두
NOTIFICATION_KAFKA_ENABLED=true를 명시해야 Kafka 기능이 활성화된다.
비활성 상태에서는 권한 변경과 기존 알림 조회/SSE 연결만 동작하며 새 알림을 생성하지 않는다.

필요한 설정:

- KAFKA_BOOTSTRAP_SERVERS: 사용할 브로커 주소. 기본 localhost:9092.
- NOTIFICATION_INSTANCE_ID: API별 고유하고 안정된 이름. 예: api-a, api-b.
  활성화 시 비어 있으면 API 시작이 실패한다. 동시 API에 같은 이름을 사용하지 않는다.
- POSTGRES_DB, POSTGRES_USER, POSTGRES_PASSWORD: 기존 DB 설정.
- NOTIFICATION_DB_URL: worker DB URL을 별도로 지정할 때 사용한다.
  기본은 API와 같은 localhost:5432/POSTGRES_DB다.

API는 기존 Flyway로 V3까지 적용한 뒤 worker를 시작한다.
worker는 JDBC를 사용하고 Flyway/JPA 스캔은 실행하지 않는다.
UUIDv7과 UTC 생성 시각을 INSERT에서 명시하고 실제 저장된 값을 RETURNING으로 받는다.
support 전체를 스캔하지 않고 Kafka 설정만 import하며 worker의 OpenAI 모델 자동 설정은 껐다.

브로커에 notification-create와 notification-broadcasting 토픽을 미리 준비해야 한다.
본 프로젝트 docker-compose.yml에 로컬 Kafka와 Kafka UI가 포함되어 있다.
Kafka는 localhost:9092, UI는 http://localhost:8084이며 테스트 프로젝트의 9094/8085와 분리된다.
컨테이너 내부 클라이언트는 kafka:9092를 사용한다. 데이터는 본 프로젝트 kafka-data 볼륨에 저장한다.

로컬에서 Kafka만 시작하고 토픽을 준비하는 명령:

```bash
docker compose up -d kafka kafka-ui
# Kafka가 healthy 상태가 된 후 실행한다.
docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:9092 --create --if-not-exists --topic notification-create --partitions 1 --replication-factor 1
docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:9092 --create --if-not-exists --topic notification-broadcasting --partitions 1 --replication-factor 1
```

위 명령은 실행 안내이며 이번 구성 추가에서 서버 시작·토픽 생성은 수행하지 않았다.
이 변경에는 토픽 프로비저닝 코드를 넣지 않았다. 브로커 자체의 자동 생성 정책은 별도다.
worker는 notification-worker 그룹,
API는 notification-sse-{instanceId} 그룹을 사용한다.
API는 전달 consumer의 파티션 할당 전 SSE 구독을 503으로 거절한다.

실행 명령은 저장소 루트에서 ./gradlew :api:bootRun 및 ./gradlew :worker:bootRun 이다.
API에는 기존 JWT/OpenAI 등 원래 실행 설정도 필요하다.

## 다른 알림 추가

같은 1:1 payload를 사용하는 알림:

1. NotificationEvents에 업무별 팩토리를 추가한다. receiverId, 문구, 중요도와 안정된 업무 중복 키를 구성한다.
2. SingleNotificationHandler.supportedTypes에 해당 NotificationType을 등록한다.
3. 해당 업무의 활성 트랜잭션 안에서 이벤트를 발행한다. 공통 AFTER_COMMIT, 저장, Kafka, SSE는 재사용한다.
4. 업무 커밋/롤백, 재전송 중복, 수신자와 문구 검증 테스트를 추가한다.

새로운 유형이라면 NotificationType과 DB CHECK를 새 마이그레이션으로 함께 확장한다.
기존 V3 파일을 수정하지 않는다. 업무 도메인 수정은 프로젝트 승인 규칙을 따른다.

수신자를 조회해야 하는 1:N은 별도 payload와 NotificationRequestHandler 구현체를 추가한다.
생성 listener의 switch문을 수정할 필요는 없다. 발행 측 payload/key 처리도 함께 추가하고,
대량 대상은 페이지 단위 커밋 및 전체 처리 시간 정책을 별도로 구현해야 한다.
현재 API 커밋 리스너는 SingleNotificationCreateEvent만 처리한다.

## 중복 및 실패 정책

- UNIQUE(receiver_id, deduplication_key)와 ON CONFLICT DO NOTHING으로 동시 중복도 막는다.
- 읽음 처리 후에도 행을 보존하므로 같은 업무의 재발행은 새 알림을 만들지 않는다.
- 같은 권한을 다시 지정하면 알림을 발행하지 않는다. 실제 권한 변경마다 새 changeEventId를 만든다.
  Kafka 재시도 및 수동 재발행은 원래 메시지의 eventId/deduplicationKey를 유지한다.
- 잘못된 JSON/type/version/필수값, 수신자 없음은 재시도하지 않고 실패 메타데이터를 로그에 기록한다.
- DB 등 일시 오류는 1초 간격 2회 재시도한다. 소진 시 NOTIFICATION_FAILED 로그에
  topic/partition/offset/group을 남기고 다음 메시지로 진행한다.
- DB 저장 후 전달 발행은 최대 12초 결과를 기다린다. 실패 시
  NOTIFICATION_BROADCAST_FAILED를 기록하고 원본 처리를 완료한다. DB 목록으로 복구한다.
- 권한 변경 커밋 후 생성 전송 실패는 NOTIFICATION_CREATE_FAILED 로그로 관측한다.
  이미 성공한 권한 변경을 실패 응답으로 바꾸지 않는다.
- outbox/DLT/영구 실패 저장소는 없다. 생성 전 프로세스 종료 등 일부 유실을 허용한다.
  운영 시 실패 로그를 중앙 보관해야 한다. Kafka 보관 중인 실패는 기록된 topic/partition/offset의
  원문을 조회하여 원래 키와 메시지 그대로 생성 토픽에 재발행한다.
  전체 consumer group offset을 되돌리는 방식은 사용하지 않는다.
- SSE 큐 포화 시 해당 연결을 닫는다. 전달 성공을 읽음 처리로 간주하지 않는다.
- LastEventId 재생은 없다. 프런트의 재접속/토큰 갱신 후 재구독·목록 갱신 개선은 별도 작업이다.
  권한 변경은 토큰을 무효화하므로 대상 사용자가 재로그인 후 목록에서 확인하는 경로도 필요하다.

## 검증

2026-09-14: 관련 테스트 29개 통과(support 2, worker 8, api 19), 실패/생략 0개.
API와 worker의 bootJar 생성 및 git diff --check도 통과했다.
임시 PostgreSQL 컨테이너는 검증 후 제거했다.

./gradlew :support:test --tests '*NotificationTransportTest'
./gradlew :api:test --tests '*Notification*' --tests '*AdminServiceTest'
./gradlew :worker:test

실제 PostgreSQL 통합 테스트는 TEST_NOTIFICATION_DB_URL이 있을 때 실행한다.
전용 notification_test DB, 사용자/비밀번호 notification_test만 사용한다.
테스트는 이 DB의 users/notification 테이블을 재생성하므로 개발·운영 DB를 지정하면 안 된다.
임베디드 Kafka를 띄우며 기존 브로커/토픽은 변경하지 않는다.

예:
TEST_NOTIFICATION_DB_URL=jdbc:postgresql://localhost:55439/notification_test ./gradlew :worker:test

실제 DB에서 V3를 적용한 알림 테이블의 동시 중복·읽은 알림 재발행·롤백·삭제 사용자 제외,
Kafka 생성→worker 저장→전달 수신을 검증한다.
테스트 스키마의 users는 최소 필드만 사용하므로 전체 V1/V2 마이그레이션 검증과는 구분한다.
API 테스트는 Spring 커밋/롤백 리스너, 권한 변경 이벤트, MockMvc SSE 수신을 확인한다.
실제 브라우저·로드밸런서 통합 검증은 별도다.

### 실제 권한 변경 알림 검증 (2026-09-14)

본 프로젝트 Kafka(9092), PostgreSQL, API(8080), worker를 실행한 상태에서
`python3 scripts/verify-notification.py`로 검증했다.

- 기존 관리자 계정으로 실제 로그인하고 별도 테스트 계정을 생성했다.
- 테스트 사용자의 JWT로 SSE 연결 후 관리자가 USER → ADMIN 권한 변경을 요청했다.
- `notifications` 이벤트를 실제 HTTP SSE 스트림으로 수신했다.
- 권한 변경으로 기존 토큰이 무효화된 뒤 재로그인하여 같은 알림 ID가 목록에 있는지 확인했다.
- ADMIN → ADMIN 재지정은 알림 개수를 늘리지 않았다.
- 테스트 후 해당 계정을 USER로 복구하고 잠갔다. 계정·알림 행은 검증 기록으로 남긴다.
- 스크립트 결과는 PASS였고 알림 생성/전달 실패 로그는 없었다.

토큰과 비밀번호는 출력하거나 이 문서에 기록하지 않는다.
이 검증은 실제 인증·Kafka·DB·SSE 경로에 대한 것으로 프런트 화면 표시까지 확인한 것은 아니다.
