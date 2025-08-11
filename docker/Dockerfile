FROM eclipse-temurin:17.0.8.1_1-jre-jammy

ENV JAR_NAME=gke_deploy
ENV APP_PORT=9081

WORKDIR /app

COPY --chmod=0755 entrypoint.sh /app/entrypoint.sh
COPY ${JAR_NAME}.jar /app/${JAR_NAME}.jar
# the next line is just because the pipeline is requesting this file
COPY application.yml /tmp/application.yml

EXPOSE ${APP_PORT}

ENTRYPOINT ["sh", "/app/entrypoint.sh"]