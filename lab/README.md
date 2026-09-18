# lab — 여러 대 구성 모니터링 확인용 스택

API 2대 + worker 1대 + batch 1대를 컨테이너로 띄워서, Prometheus가 네 프로세스를 모두
잡는지 확인한다. 배포 전에 포트 구성과 수집 경로를 미리 검증하는 용도다.

## 포트 규칙

서비스 포트와 관리 포트를 다른 대역으로 나눈다. 관리 포트는 배포 시 외부에 열지 않고,
Prometheus의 보안그룹에서 오는 트래픽만 허용한다.

| 프로세스 | 서비스 포트 | 관리 포트 |
| --- | --- | --- |
| gateway (nginx) | 8080 | 없음 |
| api-1 | 8080 (컨테이너 내부) | 9101 |
| api-2 | 8081 (컨테이너 내부) | 9102 |
| worker | 없음 (`server.port=-1`) | 9103 |
| batch | 없음 (`server.port=-1`) | 9104 |

프론트는 gateway의 8080 한 곳만 본다. `vite.config.ts`의 프록시 대상이 `localhost:8080`으로
고정돼 있는데, 배포에서 ALB가 하는 일을 nginx가 대신하므로 프론트는 고칠 필요가 없다.
API가 몇 대인지도 알 필요가 없다.

worker와 batch는 외부에 제공하는 API가 없다. actuator를 HTTP로 내보내려고 웹 서버를
띄우는 것뿐이라 관리 포트만 연다.

## 실행

JAR을 먼저 만든다. 이미지는 이 JAR을 복사하기만 한다.

```bash
./gradlew :api:bootJar :worker:bootJar :batch:bootJar -x test
docker compose -f lab/docker-compose.yml up -d --build
```

인프라(postgres / redis / kafka)는 새로 띄우지 않고 저장소 루트의 `docker-compose.yml`로
이미 떠 있는 것을 그대로 쓴다. 그것들이 내려가 있으면 앱이 기동에 실패한다.

```bash
docker compose up -d        # 저장소 루트에서
```

내릴 때는 이렇게 한다. 인프라는 건드리지 않는다.

```bash
docker compose -f lab/docker-compose.yml down
```

## 확인

```bash
curl -s localhost:9101/actuator/prometheus | grep -c '^jvm_'
docker logs -f otboo-lab-gateway          # 어느 인스턴스가 받았는지 실시간 확인
open http://localhost:9090/targets       # 네 타깃이 모두 UP인지
open http://localhost:3000               # OTBOO API 대시보드
```

Prometheus는 호스트 포트가 아니라 **컨테이너 이름**으로 붙는다(`otboo-lab-api-1:9101`).
앱 컨테이너가 `otboo-monitoring_default` 네트워크에도 참여하기 때문이다.

## 주의할 점

**로컬 bootRun과 포트가 겹친다.** 8080에서 `./gradlew :api:bootRun`이 돌고 있으면
gateway가 기동하지 못한다. bootRun을 내리거나, 호스트 포트를 바꿔서 띄운다.

```bash
GATEWAY_PORT=18000 MGMT1_PORT=19101 MGMT4_PORT=19104 \
  docker compose -f lab/docker-compose.yml up -d
```

**기동 직후 잠깐 502가 난다.** API가 아직 뜨는 중이면 nginx가 upstream을 죽은 것으로
보고 `no live upstreams`를 낸다. 10초 뒤 자동으로 복구되므로 기다렸다 다시 요청한다.

**개발 DB를 그대로 쓴다.** 인프라를 공유하므로 이 스택이 쓰는 데이터는 평소 개발 데이터와
같은 곳에 쌓인다. 그래서 batch는 뜨자마자 날씨 수집 Job을 돌리지 않도록
`SPRING_BATCH_JOB_ENABLED=false`로 막아두었다. Job 지표까지 보려면 `true`로 바꾼다.
기상청 API를 실제로 호출하고 DB에 쓴다.

**JAR을 다시 만들어야 반영된다.** 코드를 고친 뒤 `--build`만 하면 예전 JAR이 그대로
들어간다. 반드시 `bootJar`를 먼저 돌린다.
