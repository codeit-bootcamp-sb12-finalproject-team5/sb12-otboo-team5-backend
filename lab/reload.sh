#!/usr/bin/env bash
# lab 컨테이너에 코드 변경을 반영한다. JAR을 먼저 만들지 않으면 예전 코드가 그대로 들어가므로
# 빌드와 재생성을 한 번에 묶었다.
#
#   ./lab/reload.sh              # 앱 4개 전부
#   ./lab/reload.sh api-1 api-2  # 지정한 것만
set -euo pipefail

cd "$(dirname "$0")/.."
COMPOSE=(docker compose -f lab/docker-compose.yml)

services=("$@")
if [ ${#services[@]} -eq 0 ]; then
    services=(api-1 api-2 worker batch)
fi

# 서비스 이름 → Gradle 모듈. api-1과 api-2는 같은 JAR을 쓰므로 한 번만 빌드한다.
modules=()
for service in "${services[@]}"; do
    case "$service" in
        api-1|api-2) module=":api:bootJar" ;;
        worker)      module=":worker:bootJar" ;;
        batch)       module=":batch:bootJar" ;;
        gateway)     continue ;;  # nginx는 설정 파일만 쓴다. 빌드할 것이 없다.
        *) echo "알 수 없는 서비스: $service" >&2; exit 1 ;;
    esac
    [[ " ${modules[*]-} " == *" $module "* ]] || modules+=("$module")
done

if [ ${#modules[@]} -gt 0 ]; then
    echo "==> JAR 빌드: ${modules[*]}"
    ./gradlew "${modules[@]}" -x test --console=plain -q
fi

echo "==> 이미지 빌드 및 컨테이너 재생성: ${services[*]}"
"${COMPOSE[@]}" up -d --build "${services[@]}"

# 기동을 기다린다. 컨테이너가 'Up'이어도 Spring이 아직 뜨는 중이면 요청은 502가 난다.
echo "==> 기동 대기"
for service in "${services[@]}"; do
    container=$("${COMPOSE[@]}" ps -q "$service")
    [ -n "$container" ] || continue
    printf '  %-8s' "$service"
    for _ in $(seq 1 60); do
        if docker logs "$container" 2>&1 | grep -q "Started .*Application in"; then
            echo "준비됨"
            continue 2
        fi
        if [ "$(docker inspect -f '{{.State.Running}}' "$container")" != "true" ]; then
            echo "기동 실패 — docker logs $container 확인"
            continue 2
        fi
        sleep 2
    done
    echo "시간 초과 — docker logs $container 확인"
done

# nginx는 업스트림 호스트명을 기동 시 한 번만 해석하고 그 IP를 계속 쓴다. 컨테이너를 다시 만들면
# IP가 바뀌므로, 리로드해서 다시 해석하게 하지 않으면 없는 주소로 붙어 모든 요청이 502가 된다.
if [[ " ${services[*]} " == *" api-1 "* || " ${services[*]} " == *" api-2 "* ]]; then
    gateway=$("${COMPOSE[@]}" ps -q gateway)
    if [ -n "$gateway" ] && [ "$(docker inspect -f '{{.State.Running}}' "$gateway")" = "true" ]; then
        echo "==> 게이트웨이 업스트림 재해석"
        if docker exec "$gateway" nginx -t >/dev/null 2>&1; then
            docker exec "$gateway" nginx -s reload
            echo "  완료"
        else
            # 설정이 깨진 채로 reload하면 nginx가 옛 설정을 그대로 유지해 원인을 찾기 어렵다.
            echo "  nginx 설정 오류 — reload를 건너뛴다:"
            docker exec "$gateway" nginx -t 2>&1 | sed 's/^/    /'
        fi
    fi
fi

echo
echo "수집 상태: http://localhost:9090/targets"
