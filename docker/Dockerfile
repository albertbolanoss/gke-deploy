FROM eclipse-temurin:17.0.8.1_1-jre-jammy

ENV JAR_NAME=gke_deploy
ENV APP_PORT=9081

WORKDIR /app

COPY docker/${JAR_NAME}.jar /app/${JAR_NAME}.jar

RUN sh -c "touch ./${JAR_NAME}.jar"

EXPOSE ${APP_PORT}

