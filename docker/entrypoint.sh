
#!/bin/bash

set -e
date

SECRETS_DIR="/etc/secrets"
TEMP_DIR="/tmp/cleaned_secrets"
max_wait=30
waited=0

echo "Starting Entrypoint."

# wait until almost one file exist
while [ -z "$(ls -A "$SECRETS_DIR" 2>/dev/null)" ] && [ $waited -lt $max_wait ]; do
  sleep 1
  waited=$((waited + 1))
  echo "Waiting for secrets to be mounted..."
done
if [ -z "$(ls -A "$SECRETS_DIR" 2>/dev/null)" ]; then
  echo "ERROR: No secrets found in $SECRETS_DIR after $max_wait seconds."
  exit 1
fi

# Create the tmp/cleaned_secrets folder
mkdir -p "$TEMP_DIR"

# Sanitizing an load the environment variables
load_env_from_file() {
  local secret_file="$1"
  local cleaned_file="$TEMP_DIR/$(basename "$secret_file")"

  echo "Sanitizing $secret_file"
  tr -d '\r' < "$secret_file" > "$cleaned_file"

  echo "Loading environment variables from $cleaned_file"
  set -a
  . "$cleaned_file"
  set +a
}

# Process each found file in /etc/secrets
for secret_file in "$SECRETS_DIR"/*; do
  [ -f "$secret_file" ] && load_env_from_file "$secret_file"
done

echo "Starting Java application"
exec java -jar "/app/${JAR_NAME}.jar" "$@"
