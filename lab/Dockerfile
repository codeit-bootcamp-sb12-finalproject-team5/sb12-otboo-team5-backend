# 모든 모듈이 공유하는 이미지. JAR 경로만 바꿔서 api / worker / batch를 만든다.
# 빌드 컨텍스트는 저장소 루트다: docker build -f lab/Dockerfile --build-arg JAR_FILE=api/build/libs/... .
FROM eclipse-temurin:17-jre

ARG JAR_FILE
WORKDIR /app

# root로 돌리지 않는다. 컨테이너가 뚫려도 할 수 있는 일이 줄어든다.
RUN useradd --system --create-home --uid 10001 otboo
COPY --chown=otboo:otboo ${JAR_FILE} /app/app.jar

# JAR_FILE을 빠뜨리면 엉뚱한 파일이 복사되어 "Invalid or corrupt jarfile"로 기동에서야 터진다.
# 빌드 시점에 잡는다. JAR은 zip이라 PK로 시작한다.
RUN if ! head -c 2 /app/app.jar | grep -q PK; then \
      echo "ERROR: JAR_FILE 인자가 잘못되었습니다. bootJar를 먼저 실행했는지 확인하십시오."; \
      exit 1; \
    fi

USER otboo

# 컨테이너에 걸린 메모리 제한을 JVM이 인식하게 한다. 없으면 호스트 전체 메모리를 기준으로
# 힙을 잡아서 OOMKilled로 죽는다.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+ExitOnOutOfMemoryError -Duser.timezone=Asia/Seoul"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
