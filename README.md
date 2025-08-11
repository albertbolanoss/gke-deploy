# Getting Started

## Pre requisites

- Java 17
- Kafka, Redis or Docker

### Create Kafka Broker and Redis

```sh
docker network create kafka-network

docker run -d \
--name broker \
--network kafka-network \
-p 9092:9092 \
-e KAFKA_LISTENERS=PLAINTEXT://broker:9092 \
-e KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://broker:9092 \
-e KAFKA_BROKER_ID=1 \
-e KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=3 \
-e KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR=1 \
-e KAFKA_TRANSACTION_STATE_LOG_MIN_ISR=1 \
apache/kafka:latest

docker run -d \
  --name redis \
  --network kafka-network \
  -p 6379:6379 \
  redis:latest \
  redis-server --requirepass "password"

```

### Run Kafka and redis

```sh
docker start broker
docker start redis
```

### Create the topic

```sh
# Create the topic repartitioner-uppercase
docker exec -it broker sh
/opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --topic repartitioner-uppercase

# Produce
/opt/kafka/bin/kafka-console-producer.sh --broker-list localhost:9092 --topic repartitioner-uppercase --property parse.key=true --property key.separator=:

# Get the full name of the key store 
/opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list | grep uppercase-key-store

# Consume message from ktable
/opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic Repartitioner-uppercase-key-store-changelog \
  --from-beginning \
  --property print.key=true \
  --property print.value=true \
  --property key.separator=" => " \
  --isolation-level read_committed
```

### Build Docker application Image

```sh
docker build -f docker/Dockerfile -t gkedeploy:0.0.1 .
docker tag gkedeploy:0.0.1 luigisamurai/gkedeploy:0.0.1
docker push luigisamurai/gkedeploy:0.0.1

docker run --name gkedeploy \
  --network kafka-network \
  -e KAFKA_BOOTSTRAP_SERVERS="broker:9092" \
  -e REDIS_HOST="redis" \
  -e REDIS_PASSWORD="password" \
  -p 9081:9081 \
  luigisamurai/gkedeploy:0.0.1
```