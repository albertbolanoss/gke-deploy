FROM eclipse-temurin:17.0.8.1_1-jre-jammy

ENV JAR_NAME=gke_deploy
ENV APP_PORT=9081

WORKDIR /app

COPY --chmod=0755 docker/entrypoint.sh /app/entrypoint.sh
COPY docker/${JAR_NAME}.jar /app/${JAR_NAME}.jar
# the next line is just because the pipeline is requesting this file
COPY docker/application.yml /tmp/application.yml

EXPOSE ${APP_PORT}

ENTRYPOINT ["sh", "/app/entrypoint.sh"]