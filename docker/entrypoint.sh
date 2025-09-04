#!/bin/bash

set -e
date

echo "Starting Java application"
exec java -jar "/app/${JAR_NAME}.jar" "$@"
