#!/bin/bash

set -e
date

# SECRET_ENV_PATH="/etc/secrets/secrets.env"

# max_wait=30
# waited=0

# while [ ! -f "$SECRET_ENV_PATH" ] && [ $waited -lt $max_wait ]; do
#   sleep 1
#   waited=$((waited + 1))
#   echo "waiting for mount the volume."
# done

# if [ -f "$SECRET_ENV_PATH" ]; then
#   echo "Sanitizing $SECRET_ENV_PATH"
#   tr -d '\r' < "$SECRET_ENV_PATH" > "/tmp/cleaned_secrets.env"
#   SECRET_ENV_PATH="/tmp/cleaned_secrets.env"

#   echo "Loading environment variables from $SECRET_ENV_PATH"
#   set -a
#   . "$SECRET_ENV_PATH"
#   set +a
# else
#   echo "ERROR: $SECRET_ENV_PATH not found after $max_wait seconds."
# fi

echo "Starting Java application"
exec java -jar "/app/${JAR_NAME}.jar" "$@"
