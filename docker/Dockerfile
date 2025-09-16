FROM eclipse-temurin:17.0.8.1_1-jre-jammy

ENV JAR_NAME=gke_deploy
ENV APP_PORT=9081

WORKDIR /app

COPY ${JAR_NAME}.jar /app/${JAR_NAME}.jar

RUN sh -c "touch ./${JAR_NAME}.jar"

EXPOSE ${APP_PORT}

RUN ls -l /etc/secrets

ENTRYPOINT ["/bin/sh", "-c", "echo 'Verifying secrets...' && ls -l /etc/secrets/ && echo 'Starting application...' && . /etc/secrets/secrets.env && exec java -jar /app/${JAR_NAME}.jar"]
