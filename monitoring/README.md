# 로컬 모니터링 (Prometheus + Grafana)

배치 앱의 `/actuator/prometheus`를 Prometheus가 15초마다 수집하고, Grafana가 그 데이터를 그래프로 보여준다.

```
배치 앱 :8082/actuator/prometheus  ←수집─  Prometheus :9090  ←조회─  Grafana :3000
```

Grafana는 앱을 직접 읽지 않고 반드시 Prometheus를 거칩니다

## 실행

저장소 루트에서 실행 합니다 배치 앱(`./gradlew :batch:bootRun`)이 8082에서 떠 있어야 합니다

```bash
docker compose -f monitoring/docker-compose.yml up -d
```

| 주소 | 용도                                  |
|---|-------------------------------------|
| http://localhost:9090/targets | 수집 대상 상태. `otboo-batch`가 `UP`이어야 한다 |
| http://localhost:3000 | Grafana. 기본 계정 `otboo` / `otboo1!`  |
| Grafana → Dashboards → OTBOO → OTBOO Batch | 준비된 대시보드                            |


중지와 초기화:

```bash
docker compose -f monitoring/docker-compose.yml down
```

```bash
docker compose -f monitoring/docker-compose.yml down -v
```

`-v`를 붙이면 수집한 데이터와 Grafana 설정(볼륨)까지 지우게 됩니다. Prometheus는 최대 15일치를 보관합니다.

## 대시보드 구성

| 구역 | 패널 | 출처 |
|---|---|---|
| 배치 Job | 앱 연결, 실행 중인 Job, 마지막 수집 성공 후 경과, Job 실행 횟수, Job·Step 평균 소요 시간 | `up`, Spring Batch 기본 지표, 커스텀 `last_success_timestamp` |
| 날씨 수집 | 격자 처리, DB 저장 행, KMA 호출 시간·횟수, 처리·저장 실패 | 커스텀 `otboo_weather_collection_*`, Spring Batch 기본 지표 |
| 날씨 알림 | 알림 발행 수, Kafka 발행 실패·평균 시간 | 커스텀 `otboo_weather_notification_published_total`, `spring_kafka_template_seconds` |
| 앱 상태 | 힙 메모리, DB 커넥션, CPU | JVM·HikariCP 기본 지표 |

화면에서 패널을 고칠 수는 있지만, 남기려면 대시보드 설정 → JSON Model을 복사해 `grafana/dashboards/otboo-batch.json`에 덮어씁니다. 파일을 바꾸면 30초 안에 다시 불러오게 됩니다.
