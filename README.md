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
docker exec -it broker sh /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --topic repartitioner-uppercase

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

## Gcloud commands

| Declaration                                                                 | Description                                                      |
|-----------------------------------------------------------------------------|------------------------------------------------------------------|
| `gcloud init`                                                               | Init the gcloud configuration.                                   |
| `gcloud config list`                                                        | Check the registered of account.                                 |
| `gcloud auth list`                                                          | show the account and how to switch.                              |
| `gcloud config set <account>`                                               | Allow to change of the account.                                  |
| `gcloud container clusters list`                                            | Show the cluster list.                                           |
| `kubectl config current-context`                                            | Show kubectl current context.                                    |
| `kubectl config view --minify`                                              | show more information about the kubectl current context.         |
| `kubectl config get-contexts`                                               | show all the kubectl contexts.                                   |
| `kubectl config use-context <nombre-del-contexto>`                          | switch to a specify context.                                     |
| `gcloud container clusters get-credentials labs-kube --region us-central1`  | Get the credential to register cloud cluster in kubectl context. |
| `kubectl config delete-context gke_safari-gke-462517_us-central1_labs-kube` | Delete the context.                                              |
| `gcloud config set compute/region us-east1 `                                | Change the region.                                               |



## Deploying in GKE

### Pre requirements

#### Set environment variables

```sh
REGION=us-east1
ZONE=us-east1-b
PROJECT_ID=safari-gke-462517
CLUSTER_NAME=labs-kube-reg
NAMESPACE=labs-dev
```
#### Create Autopilot Cluster

```sh
# Create for the region (zones: us-east1-a,us-east1-b, us-east1-c)
gcloud container clusters create-auto "$CLUSTER_NAME" \
  --region="$REGION" \
  --project="$PROJECT_ID" \
  --enable-secret-manager  

  
# Get credential of kubectl
gcloud container clusters get-credentials "$CLUSTER_NAME" \
  --region="$REGION" \
  --project="$PROJECT_ID"
  
# Create namespace
kubectl create namespace $NAMESPACE
```

#### Create Kafka Broker and Redis

```sh
# Install
helm install broker2 charts/broker -n $NAMESPACE
helm install redis charts/redis -n $NAMESPACE

# Check the pods status
kubectl get pod -n $NAMESPACE

# Create the topics
kubectl exec -it broker2 -n $NAMESPACE -- sh
# In the container terminal
/opt/bitnami/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --topic repartitioner-uppercase --partitions 3 --replication-factor 1
/opt/bitnami/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:9092 --topic repartitioner-uppercase --property parse.key=true --property key.separator=:
```

#### Install service
```sh
# Install
helm install labs-deploy charts/app -n $NAMESPACE

# Checking
kubectl get pod -n $NAMESPACE
POD_NAME=$(kubectl get pods -n $NAMESPACE -o jsonpath='{.items[1].metadata.name}')
kubectl describe pod $POD_NAME -n $NAMESPACE
kubectl logs -f $POD_NAME -n $NAMESPACE

# port forward
kubectl port-forward svc/labs-soft-npd-gke-deploy-dev-svc 8080:80 -n $NAMESPACE
echo "Application accessible at: http://127.0.0.1:8080/actuator/health"


kubectl exec -it broker2 -n $NAMESPACE -- sh

/opt/bitnami/kafka/bin/kafka-console-producer.sh \
  --bootstrap-server localhost:9092 \
  --topic repartitioner-uppercase \
  --property "parse.key=true" \
  --property "key.separator=:"
```

#### Clean up
```sh
gcloud container clusters delete "$CLUSTER_NAME" \
  --region="$REGION" \
  --project="$PROJECT_ID" \
  --quiet
```

- Install Argo CI /CD

```sh
kubectl create ns argocd

helm repo add argo https://argoproj.github.io/argo-helm
helm repo update
helm install argocd argo/argo-cd --namespace argocd

# Get the initial password to access to the API
kubectl get secret -n argocd
kubectl get secret argocd-initial-admin-secret -n argocd -o yaml
echo "PASSWORD_BASE64" | base64 --decode

# Create the port-forward to access 
kubectl port-forward svc/argocd-server -n argocd 8080:443

# Testing
argocd version
API: http://localhost:8080
```

- Install Splunk

```sh
kubectl create namespace splunk-operator

helm repo add splunk https://splunk.github.io/splunk-operator/
helm repo update

# Install Operator with CRDs:
helm install splunk-operator splunk/splunk-operator \
  --namespace splunk-operator \
  --set installCRDs=true
  
kubectl apply -f deployment/splunk/splunk-standalone.yaml
kubectl get pods -n splunk-operator
kubectl port-forward -n splunk-operator svc/splunk-s1-standalone 8000:8000
```


1. Install services
```sh
helm install broker2 charts/broker -n $Namespace
helm install redis charts/redis -n $Namespace
helm install kconsumer charts/app -n $Namespace
```




