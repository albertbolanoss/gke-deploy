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
# Create the topic uppercase
docker exec -it broker sh /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --topic uppercase

# Produce
/opt/kafka/bin/kafka-console-producer.sh --broker-list localhost:9092 --topic uppercase --property parse.key=true --property key.separator=:

# Get the full name of the key store 
/opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list | grep uppercase-store

# Consume message from ktable
/opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic Uppercase-key-store-changelog \
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
CLUSTER_NAME=labs-kube
NAMESPACE=labs-dev
ENV_VARS_SECRET=env-vars-dev
GSA=labs-sa
KSA=gke-sa
PROJECT_NUMBER=230862495170
CSI_NAMESPACE=csi-secrets
SECRET_NAME=labs-soft-npd-gke-deploy-dev-envfile
```
#### Enable needed APIs 

```sh
gcloud services enable container.googleapis.com secretmanager.googleapis.com iam.googleapis.com --project $PROJECT_ID
```

#### Create Autopilot Cluster

```sh
# Create for the region (zones: us-east1-a,us-east1-b, us-east1-c)
gcloud container clusters create-auto "$CLUSTER_NAME" \
  --region="$REGION" \
  --project="$PROJECT_ID" \
  --release-channel=stable \
  --enable-secret-manager 

# Get credential of kubectl
gcloud container clusters get-credentials "$CLUSTER_NAME" \
  --region="$REGION" \
  --project="$PROJECT_ID"

kubectl create namespace $NAMESPACE

```
#### Create GCP Secret Manager (Values.secret.provider=gke)

```sh
gcloud secrets create $ENV_VARS_SECRET \
    --project=$PROJECT_ID \
    --replication-policy="automatic" \
    --data-file=charts/secrets/secrets.env
```

#### Create Secret in Kubernetes (Values.secret.provider=local)

```sh
kubectl create secret generic $SECRET_NAME \
  --from-file=secrets.env=charts/secrets/secrets.env \
  -n $NAMESPACE
```


#### Create GSA and Grant permision to secret manager using GKE provider (Compatible with Autopilot cluster)

```sh
# 1. Crea una cuenta de servicio de Google (GSA) para tu aplicación
gcloud iam service-accounts create $GSA \
    --display-name="Service Account for Labs" \
    --project=$PROJECT_ID

# 2. Otorga el rol de "Secret Manager Secret Accessor" a la GSA
gcloud secrets add-iam-policy-binding "$ENV_VARS_SECRET" \
  --project="$PROJECT_ID" \
  --role="roles/secretmanager.secretAccessor" \
  --member="principal://iam.googleapis.com/projects/$PROJECT_NUMBER/locations/global/workloadIdentityPools/$PROJECT_ID.svc.id.goog/subject/ns/$NAMESPACE/sa/$KSA"
```

#### Create GSA and Grant permision to secret manager using GCP provider (Compatible with Standard cluster)

```sh
# 1. Crea una cuenta de servicio de Google (GSA) para tu aplicación
gcloud iam service-accounts create $GSA \
    --display-name="Service Account for Labs" \
    --project=$PROJECT_ID

# 2. Otorga el rol de "Secret Manager Secret Accessor" a la GSA

# using GCP provider
# 2. Otorga el rol de "Secret Manager Secret Accessor" a la GSA
gcloud secrets add-iam-policy-binding $ENV_VARS_SECRET \
    --project=$PROJECT_ID \
    --role="roles/secretmanager.secretAccessor" \
    --member="serviceAccount:$GSA@$PROJECT_ID.iam.gserviceaccount.com"

kubectl create serviceaccount $KSA -n $NAMESPACE

# Annotate
kubectl annotate serviceaccount \
  --namespace $NAMESPACE \
  $KSA iam.gke.io/gcp-service-account=$GSA@$PROJECT_ID.iam.gserviceaccount.com

# Link with Workload Identity
gcloud iam service-accounts add-iam-policy-binding \
  $GSA@$PROJECT_ID.iam.gserviceaccount.com \
  --role="roles/iam.workloadIdentityUser" \
  --member="serviceAccount:$PROJECT_ID.svc.id.goog[$NAMESPACE/$KSA]" \
  --project $PROJECT_ID -->

# Secrets Store CSI Driver con provider GCP

#3. Install Secret CRDs and Store CSI Driver
# 3.1 Install repositories
kubectl apply -f https://raw.githubusercontent.com/kubernetes-sigs/secrets-store-csi-driver/main/config/crd/bases/secrets-store.csi.x-k8s.io_secretproviderclasses.yaml
helm repo add secrets-store-csi-driver https://kubernetes-sigs.github.io/secrets-store-csi-driver/charts
helm repo update 

# Create namespace and install csi driver
kubectl create namespace $CSI_NAMESPACE
helm install csi-secrets-store secrets-store-csi-driver/secrets-store-csi-driver --namespace $CSI_NAMESPACE

#Check the CRDs
kubectl get crds | grep secretproviderclass
kubectl api-resources | grep -i secretproviderclass
kubectl explain secretproviderclass

# Install gcp plugins (optional) 
kubectl apply -f https://raw.githubusercontent.com/GoogleCloudPlatform/secrets-store-csi-driver-provider-gcp/main/deploy/provider-gcp-plugin.yaml --namespace $CSI_NAMESPACE


gcloud container clusters update "$CLUSTER_NAME" \
  --region="$REGION" \
  --project="$PROJECT_ID" \
  --release-channel=stable

# secrets-store-csi-driver-xxxxx   Running
kubectl get pods -n $CSI_NAMESPACE


# kubectl create ns $CSI_NAMESPACE

# helm repo add secrets-store-csi-driver https://kubernetes-sigs.github.io/secrets-store-csi-driver/charts
# helm repo update

# helm install csi secrets-store-csi-driver/secrets-store-csi-driver \
#   -n $CSI_NAMESPACE \
#   --set grpcSupportedProviders="gcp"

# gcloud container clusters update "$CLUSTER_NAME" \
#   --region="$REGION" \
#   --project="$PROJECT_ID" \
#   --release-channel=stable

# kubectl get pods -n $CSI_NAMESPACE
```

#### Install dependencies and service

```sh
# Install dependencies and service
helm install broker2 charts/broker -n $NAMESPACE
helm install redis charts/redis -n $NAMESPACE
helm install labs-deploy charts/app -n $NAMESPACE

# Other way to install bitnami/kafka
helm repo add strimzi https://strimzi.io/charts/
helm repo update
helm install strimzi strimzi/strimzi-kafka-operator -n "$NAMESPACE"
broker: broker2-kafka-bootstrap.labs-dev.svc.cluster.local:9092
```

#### Install Argo CI /CD

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

#### Install Splunk

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

#### Clean up
```sh
gcloud secrets delete $ENV_VARS_SECRET --quiet

gcloud container clusters delete "$CLUSTER_NAME" \
  --region="$REGION" \
  --project="$PROJECT_ID" \
  --quiet
```

#### Aditional commands

```sh

# Check the pods status
kubectl get pod -n $NAMESPACE

kubectl describe pod labs-soft-npd-gke-deploy-dev-deploy-7f75bff4dd-9pjsr -n $NAMESPACE

# Ver que el add-on está habilitado en el cluster
gcloud container clusters describe "$CLUSTER_NAME" --location "$REGION" \
  --project "$PROJECT_ID" | grep -A4 secretManagerConfig

# Ver el volumen montado en el Pod
kubectl exec -it deploy/labs-soft-npd-gke-deploy-dev-deploy -- sh -c 'ls -l /etc/secrets && echo && cat /etc/secrets/secrets.env | sed "s/=.*/=****/g"'

# Create the topics with bitname
kubectl exec -it broker2 -n $NAMESPACE -- sh
# In the container terminal
/opt/bitnami/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --topic repartitioner-uppercase --partitions 3 --replication-factor 1
/opt/bitnami/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:9092 --topic repartitioner-uppercase --property parse.key=true --property key.separator=:

# Create the topics with strimzi
kubectl run kafka-client -ti --image=strimzi/kafka:0.39.0-kafka-3.7.0 --rm=true --restart=Never -n $NAMESPACE -- bash
/opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --topic repartitioner-uppercase --partitions 3 --replication-factor 1
/opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:9092 --topic repartitioner-uppercase --property parse.key=true --property key.separator=:


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