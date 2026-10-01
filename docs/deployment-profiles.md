# 실행 프로필

API, Batch, Worker 모두 다음 설정 구조를 사용한다.

- `application.yaml`: 공통 설정. 프로필 미지정 시 `dev` 사용.
- `application-dev.yaml`: 기존 개발용 기본값과 `optional:dotenv:../` import.
- `application-prod.yaml`: 운영 연결 정보. 컨테이너 환경변수로 주입.

## 개발

```dotenv
SPRING_PROFILES_ACTIVE=dev
KAFKA_BOOTSTRAP_SERVERS=localhost:9092
```

기존 `.env` 변수 이름은 그대로 사용한다. `dotenv:../`의 상대 경로는
실행 작업 디렉터리를 기준으로 하므로 기존 실행 디렉터리를 유지한다.

## 운영

세 컨테이너 모두 실행 환경에서 다음 값을 전달한다.
아래 호스트는 예시이며 실제 Private DNS 또는 서비스 엔드포인트로 교체한다.

```dotenv
SPRING_PROFILES_ACTIVE=prod
POSTGRES_HOST=postgres.internal
POSTGRES_PORT=5432
POSTGRES_DB=otboo
POSTGRES_USER=otboo
POSTGRES_PASSWORD=<secret>
KAFKA_BOOTSTRAP_SERVERS=kafka.internal:9092
NOTIFICATION_KAFKA_ENABLED=true
```

API와 Batch에는 `REDIS_HOST`, `REDIS_PORT`도 전달한다.
Worker는 기존 `NOTIFICATION_DB_URL`로 JDBC URL을 재정의할 수 있다.
API의 OAuth, JWT, AI, S3 등 공통 설정에서 참조하는 기존 환경변수도 필요하다.
API 운영 프로필에서는 `OAUTH_FRONTEND_REDIRECT_URI`와 `ADMIN_PASSWORD`가 필수다.

운영 프로필은 Kafka와 DB/Redis 호스트에 localhost 기본값을 두지 않는다.
Kafka 알림 기능은 dev에서 기본 비활성화, prod에서 기본 활성화이며
`NOTIFICATION_KAFKA_ENABLED`로 재정의할 수 있다.
API의 S3 객체 prefix는 dev의 `otboo-*-dev`, prod의 `otboo-*-prod`로 나뉜다.

Kafka Producer와 Consumer는 Spring `KafkaProperties`를 사용하고,
Batch 관리 클라이언트는 자동 구성된 `KafkaAdmin`의 설정을 사용한다.
따라서 세 앱 모두 `KAFKA_BOOTSTRAP_SERVERS`가 연결 주소로 적용된다.
여러 브로커는 `broker1:9092,broker2:9092`처럼 쉼표로 구분한다.
같은 Docker Compose 네트워크에서는 `kafka:9092`를 사용할 수 있다.
다른 서버에서 접속할 때는 Kafka 서버의 `advertised.listeners` 역시
클라이언트에서 접근 가능한 주소여야 한다.

Secrets Manager를 사용하는 경우 각 키를 같은 이름의 컨테이너 환경변수로
매핑한다. Secret을 등록하는 것만으로 애플리케이션에 주입되지는 않는다.
`.env` 파일은 이미지에 포함하지 않는다.

## 별도로 결정할 운영 정책

이번 분리는 기존 Flyway 활성화 설정(API/Batch 활성화, Worker 비활성화)과
Batch의 시작 시 Job 자동 실행 설정을 유지한다.
마이그레이션 담당과 Batch 시작 시 실행 여부는 배포 구성에 맞춰 결정한다.
API의 WebSocket 허용 origin과 S3의 고정 키 인증도 별도 운영 설정 대상이다.
