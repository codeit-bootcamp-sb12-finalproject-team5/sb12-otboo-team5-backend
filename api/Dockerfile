FROM eclipse-temurin:17-jre

ARG JAR_FILE
WORKDIR /app

RUN useradd --system --create-home --uid 10001 otboo
COPY --chown=otboo:otboo ${JAR_FILE} /app/app.jar

RUN if ! head -c 2 /app/app.jar | grep -q PK; then \
      echo "ERROR: JAR_FILE argument is invalid. Run bootJar first."; \
      exit 1; \
    fi

USER otboo

ENV JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+ExitOnOutOfMemoryError -Duser.timezone=Asia/Seoul"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
