# 알림 worker와 메시지 조립

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
ROLE_CHANGED, FEED_LIKED, FEED_COMMENTED, FOLLOWED, DM_RECEIVED를 처리한다. 존재하며 탈퇴하지 않은 사용자를 대상으로 알림을 저장한다.
잠긴 사용자도 DB 알림은 보존한다. 기존 권한 변경의 세션 무효화 동작은 유지한다.

NotificationSaveService의 트랜잭션이 커밋된 다음 notification-broadcasting으로 전송한다.
API마다 서로 다른 consumer group으로 받아 NotificationSseService의 제한된 전송 큐에 넣는다.
이벤트 이름은 notifications이며 데이터는 기존 NotificationDto다.

## 실행 설정

이번 조립 작업에서는 빌드·환경·Docker를 변경하지 않았다. API와 worker 모두
NOTIFICATION_KAFKA_ENABLED=true를 명시해야 Kafka 기능이 활성화된다.
비활성 상태에서는 권한 변경과 기존 알림 조회/SSE 연결만 동작하며 새 알림을 생성하지 않는다.

필요한 설정:

- KAFKA_BOOTSTRAP_SERVERS: 사용할 브로커 주소. 기본 localhost:9092.
- API 그룹 ID는 시작 시 UUIDv7으로 생성한다. 현재 코드는 NOTIFICATION_INSTANCE_ID를 읽지 않는다.
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
API는 notification-sse-{시작 시 생성한 UUIDv7} 그룹을 사용한다.
API는 전달 consumer의 파티션 할당 전 SSE 구독을 503으로 거절한다.

실행 명령은 저장소 루트에서 ./gradlew :api:bootRun 및 ./gradlew :worker:bootRun 이다.
API에는 기존 JWT/OpenAI 등 원래 실행 설정도 필요하다.

## API는 원본 이벤트, worker는 조회·조립 (schemaVersion 2)

알림 생성 토픽에는 제목·본문·작성자 이름을 넣지 않는다. API의 NotificationEvents는 순수 이벤트 팩토리이며 Repository를 주입받지 않는다. 각 업무 서비스의 신규 발행 연결은 아직 하지 않았다. 기존 AdminService의 roleChanged 호출은 유지한다.

| 호출 | 생성 payload | worker 조회·조립 |
| --- | --- | --- |
| roleChanged(receiverId, role) | RoleChangedNotificationEvent | 변경 당시 role로 문구 작성. 지연 후 현재 권한을 다시 읽지 않음 |
| feedLiked(likeId) | NotificationSourceEvent(sourceId) | feed_like→feed 소유자·좋아요 작성자 조회 |
| commentCreated(commentId) | NotificationSourceEvent(sourceId) | feed_comment의 실제 content·작성자·피드 소유자 조회 |
| followCreated(followId) | NotificationSourceEvent(sourceId) | follow의 팔로워 이름·팔로우 대상 조회. 본문은 빈 문자열 |
| directMessageReceived(messageId, receiverId) | DirectMessageNotificationEvent | direct_message 본문·발신자 이름 조회, 수신자 방 멤버십·입장 시각·퇴장 상태 검증 |
| feedCreated(feedId) | FeedNotificationCreateEvent(feedId, afterReceiverId) | feed의 작성자·이름 조회 후 팔로워 페이지 처리 |

호출 예시(발행 서비스 연결은 후속 작업):

```java
NotificationEvents.commentCreated(comment.getId());
NotificationEvents.directMessageReceived(message.getId(), receiverId);
NotificationEvents.followCreated(follow.getId());
```

NotificationSourceRepository의 JDBC 조회는 worker 안에 있다. 원본 도메인 Repository·서비스는 수정하지 않았다. 삭제된 작성자·삭제/비공개 피드·접근할 수 없는 DM 등 원본을 조회할 수 없으면 NOTIFICATION_SOURCE_NOT_FOUND로 저장하지 않는다. 이 오류는 재시도 없이 실패 로그로 처리하고, DB 일시 오류는 제한 재시도한다. 메시지는 발행 시점이 아니라 worker 조회 시점의 본문·작성자 이름을 사용한다. 댓글 수정·삭제·팔로우 취소가 먼저 반영되면 알림 내용 또는 생성 여부도 달라진다.

worker가 만든 NotificationContent는 저장용 DTO이며 Kafka 생성 이벤트가 아니다. 본문 `""`과 공백은 허용하고 null은 허용하지 않는다. 제목은 필수다. 저장 제한에 맞춰 제목 100자·본문 1,000자까지 Unicode 코드 포인트 단위 미리보기를 사용하며 원본을 수정하지 않는다. `[DM] 발신자 이름` 제목과 실제 DM/댓글 본문을 저장한다.

날씨는 배치 판정값 gridId·precipitationType·firstRainAt을 전달하고 worker가 KST로 변환해 날짜 문구를 작성한다. 실제 배치 판정·발행은 아직 미연결이다.

## 페이지 처리

날씨/피드는 수신자 501명을 조회해 최대 500명을 한 트랜잭션으로 저장한다. 다음 페이지가 있으면 조회한 마지막 사용자 ID를 커서로 같은 생성 토픽에 인계한다. 모두 중복으로 저장이 0건이어도 다음 페이지는 계속한다. 날씨 Kafka key는 gridId, 새 피드 key는 feedId다. 단일 원본 이벤트는 sourceId, 권한 변경/DM은 receiverId로 발행한다.

원본 eventId·발생 시각·중복 키를 후속 작업에서 유지한다. 날씨 중복 키는 WEATHER_RAIN:KST대상날짜, 나머지는 TYPE:원본업무ID다. 권한 변경은 호출마다 UUIDv7을 새로 생성한다. API의 공통 AFTER_COMMIT 리스너가 이벤트를 전송하고, DB 저장 이후의 전달 계약과 SSE 데이터는 기존 NotificationDto를 유지한다.

## 계약 변경과 배포

생성 메시지는 기존 문구 payload와 구분해 schemaVersion을 **2**로 올렸다. 전달 토픽/SSE 계약은 버전 1을 유지한다. API와 worker를 함께 배포해야 하며 현재 실행 중인 서버는 이번 작업에서 재시작하지 않았다. 기존 버전 1 생성 메시지를 새 worker에 그대로 재발행하면 입력 오류로 거절된다. 구버전 큐를 기존 worker로 처리할지 원본 ID로 변환할지 배포 전에 결정한다. 임의의 API 제목·본문을 그대로 저장하는 호환 경로는 두지 않았다.

## 중복 및 실패 정책

- UNIQUE(receiver_id, deduplication_key)와 ON CONFLICT DO NOTHING으로 동시 중복도 막는다.
- 읽음 처리 후에도 행을 보존하므로 같은 업무의 재발행은 새 알림을 만들지 않는다.
- 같은 권한을 다시 지정하면 알림을 발행하지 않는다. 실제 권한 변경마다 새 changeEventId를 만든다.
  Kafka 재시도 및 수동 재발행은 원래 메시지의 eventId/deduplicationKey를 유지한다.
- 잘못된 JSON/type/version/필수값, 수신자 없음은 재시도하지 않고 실패 메타데이터를 로그에 기록한다.
- DB 등 일시 오류는 1초 간격 2회 재시도한다. 소진 시 NOTIFICATION_FAILED 로그에
  topic/partition/offset/group을 남기고 다음 메시지로 진행한다.
- DB 저장 후 전달 발행의 결과 대기 예산은 페이지 전체 12초다. 예산이 끝나면 나머지 실시간 전송을 생략하고 다음 페이지를 인계한다. send 자체의 max.block.ms 대기는 별도로 최대 2초가 추가될 수 있다. DB 목록으로 누락을 보완한다.
- 다음 페이지 발행은 별도로 최대 12초 기다린다. 실패 시 NOTIFICATION_CONTINUATION_FAILED 예외를 전파해 현재 레코드를 재시도한다. 저장 후 SSE 실패와 달리 인계 실패를 성공으로 삼키지 않는다.
- 인계 성공 후 offset 커밋 전 종료하면 다음 작업이 중복될 수 있다. UNIQUE는 저장 중복을 막지만 페이지 작업 자체의 exactly-once를 보장하지 않는다.
- 재시도 소진 시 현재 공통 recoverer는 NOTIFICATION_FAILED를 기록하고 진행한다. 이 경우 후속 수신자가 누락될 수 있다. 로그의 topic/partition/offset에 있는 원본 레코드를 Kafka 보관 기간 안에 추출해 payload의 커서·eventId·중복 키를 그대로 재발행한다. 전체 worker 그룹 offset을 되돌리면 무관한 메시지도 재처리되므로 복구 대상으로 사용하지 않는다. 영구 실패 저장소·자동 복구는 미구현이며 로그와 원본 레코드가 없어지면 복구를 보장하지 않는다.
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

## 조립 검증 (2026-09-15)

임시 PostgreSQL 16과 임베디드 Kafka를 사용했다. 최종 worker 33개·API 알림 20개 테스트가 실패·건너뜀 없이 통과했다. git diff --check도 통과했다. 임시 컨테이너는 검증 후 제거했고 기존 개발 DB·브로커·서버는 재시작하지 않았다.

- KST·UTC·음수 오프셋·연도 경계 문구, 0/1/500/501명 페이지 경계.
- 저장 중복만 있는 페이지의 후속 인계, 다음 작업 발행 실패 전파, 전달 실패 후 인계 유지.
- 실제 SQL로 1만 명 날씨 알림 저장 및 삭제 사용자 제외, 원본 재처리 시 중복 페이지를 지나 마지막 페이지의 누락 행 복구.
- 실제 팔로워 커서 조회, 페이지 중간 INSERT 실패 시 전체 롤백.
- 단일 5개 유형 처리, 이벤트 팩토리 문구·업무 ID, API 공통 피드 리스너 커밋/롤백 경계.

서비스 발행 연결, 실제 날씨 KMA→배치→SSE 수신, 브라우저·다중 API 및 운영 성능 목표는 이번 검증 범위가 아니다. DB 지연에는 별도 상한이 없으므로 500명 페이지 자체가 운영 poll 제한 이내인지 추가 측정해야 한다. 조회 인덱스·영구 실패 보관은 별도 검토 대상이다.

최근 검증 항목: 원본 ID만 포함하는 직렬화, worker의 권한/댓글/DM 문구 조립, 빈 본문, 긴 Unicode 미리보기, 원본 없음, DM 멤버십, schemaVersion 1 생성 요청 거절. 이전 조립 테스트 수치는 해당 시점 기록이다.

원본 ID 계약 전환 최종 검증: worker 38개, API 알림/관리자 26개, support 4개가 실패·건너뜀 없이 통과했다. 임시 PostgreSQL 컨테이너는 제거했다. 별도로 실행된 전체 API 테스트는 기본 DB 접속 오류로 contextLoads가 실패했으므로 전체 프로젝트 통과로 기록하지 않는다.
