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
GSA=labs-sa
KSA=gke-sa
PROJECT_NUMBER=230862495170
CSI_NAMESPACE=csi-secrets
LOCAL_SECRET_NAME=labs-soft-npd-gke-deploy-dev-envsp
ENV_VARS_SECRET=env-vars-dev
REDIS_SECRET=labs-soft-npd-gke-deploy-dev-redis-password
KAFKA_SECRET=labs-soft-npd-gke-deploy-dev-kafka-password
SECRET_PROVIDER=gcp
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
  --release-channel=stable  \
  --enable-secret-manager 
```

#### Create Standard Cluster

```sh
# Create for the region (zones: us-east1-a,us-east1-b, us-east1-c)
# e2-medium , e2-small
gcloud container clusters create "$CLUSTER_NAME" \
  --zone "$ZONE" \
  --machine-type=e2-medium \
  --num-nodes=1 \
  --disk-size=20 \
  --image-type=COS_CONTAINERD \
  --workload-pool="$PROJECT_ID.svc.id.goog" \
  --monitoring=NONE \
  --enable-autoscaling --min-nodes 1 --max-nodes 3
```

#### Get kubectl credential and create namespace

```sh
gcloud container clusters get-credentials "$CLUSTER_NAME" \
  --region="$REGION" \
  --project="$PROJECT_ID"

kubectl create namespace $NAMESPACE
```

#### Create GCP Secret Manager

```sh
# if not gcp.secretSync.enabled ()
gcloud secrets create $ENV_VARS_SECRET \
    --project=$PROJECT_ID \
    --replication-policy="automatic" \
    --data-file=charts/secrets/secrets.env

# Individual secrets as text
echo -n 'password' | gcloud secrets create $REDIS_SECRET \
  --replication-policy="automatic" \
  --data-file=-

# Individual secrets as text
echo -n 'password' | gcloud secrets create $KAFKA_SECRET \
  --replication-policy="automatic" \
  --data-file=-
```

#### Create Kubenetes Service Account KSA (Compatible with Autopilot cluster)

```sh
# 1. Crea Kubernetes service account
kubectl -n $NAMESPACE get sa $KSA || kubectl -n $NAMESPACE create sa $KSA

# 2. Otorga el rol de "Secret Manager Secret Accessor" a la GSA
gcloud secrets add-iam-policy-binding "$ENV_VARS_SECRET" \
  --project="$PROJECT_ID" \
  --role="roles/secretmanager.secretAccessor" \
  --member="principal://iam.googleapis.com/projects/$PROJECT_NUMBER/locations/global/workloadIdentityPools/$PROJECT_ID.svc.id.goog/subject/ns/$NAMESPACE/sa/$KSA"

# ------
# In case of give acccess to each secret (Multi secret file)
gcloud secrets add-iam-policy-binding "$REDIS_SECRET" \
  --project="$PROJECT_ID" \
  --role="roles/secretmanager.secretAccessor" \
  --member="principal://iam.googleapis.com/projects/$PROJECT_NUMBER/locations/global/workloadIdentityPools/$PROJECT_ID.svc.id.goog/subject/ns/$NAMESPACE/sa/$KSA"

gcloud secrets add-iam-policy-binding "$KAFKA_SECRET" \
  --project="$PROJECT_ID" \
  --role="roles/secretmanager.secretAccessor" \
  --member="principal://iam.googleapis.com/projects/$PROJECT_NUMBER/locations/global/workloadIdentityPools/$PROJECT_ID.svc.id.goog/subject/ns/$NAMESPACE/sa/$KSA"  

# Checking
gcloud secrets get-iam-policy $ENV_VARS_SECRET --project "$PROJECT_ID"  
gcloud secrets get-iam-policy $REDIS_SECRET --project "$PROJECT_ID"
gcloud secrets get-iam-policy $KAFKA_SECRET --project "$PROJECT_ID"

```


#### Create Secret in Kubernetes (Values.secret.provider=local)

```sh
kubectl create secret generic $LOCAL_SECRET_NAME \
  --from-file=secrets.env=charts/secrets/secrets.env \
  -n $NAMESPACE

```


#### Create Google Service Account GSA (Compatible with Standard cluster)

```sh
# 2) Instalar el Secrets Store CSI Driver (OSS) y el provider-gcp
helm repo add secrets-store-csi-driver https://kubernetes-sigs.github.io/secrets-store-csi-driver/charts
helm repo update

# Driver
helm upgrade --install csi-secrets-store secrets-store-csi-driver/secrets-store-csi-driver \
  --namespace kube-system \
  --set syncSecret.enabled=true

# Provider GCP
kubectl apply -n kube-system \
  -f https://raw.githubusercontent.com/GoogleCloudPlatform/secrets-store-csi-driver-provider-gcp/main/deploy/provider-gcp-plugin.yaml

# Verificación
kubectl -n kube-system get ds | grep -E 'secrets-store|provider-gcp'
kubectl get csidrivers | grep secrets-store.csi.k8s.io

# KSA (la crea Helm también, pero la anotación requiere conocer el GSA)
kubectl create serviceaccount "$KSA" -n "$NAMESPACE" || true

# GSA
gcloud iam service-accounts create "$GSA" \
  --display-name="GSA for Secrets Store CSI" \
  --project="$PROJECT_ID"

GSA_EMAIL="${GSA}@${PROJECT_ID}.iam.gserviceaccount.com"

# Permitir que la KSA asuma la identidad de la GSA (Workload Identity)
gcloud iam service-accounts add-iam-policy-binding "$GSA_EMAIL" \
  --role roles/iam.workloadIdentityUser \
  --member "serviceAccount:${PROJECT_ID}.svc.id.goog[${NAMESPACE}/${KSA}]"

# Anotar la KSA con la GSA
kubectl annotate serviceaccount -n "$NAMESPACE" "$KSA" \
  iam.gke.io/gcp-service-account="$GSA_EMAIL" --overwrite

# Dar permiso a la GSA para leer el secreto secrets.env
gcloud secrets add-iam-policy-binding $ENV_VARS_SECRET \
  --project "$PROJECT_ID" \
  --role roles/secretmanager.secretAccessor \
  --member "serviceAccount:${GSA_EMAIL}"

#In case multi secret files

# Dar permiso a la GSA para leer el secreto individual REDIS_PASSWORD
gcloud secrets add-iam-policy-binding $REDIS_SECRET \
  --project "$PROJECT_ID" \
  --role roles/secretmanager.secretAccessor \
  --member "serviceAccount:${GSA_EMAIL}"

gcloud secrets add-iam-policy-binding $KAFKA_SECRET \
  --project "$PROJECT_ID" \
  --role roles/secretmanager.secretAccessor \
  --member "serviceAccount:${GSA_EMAIL}"


gcloud secrets get-iam-policy $ENV_VARS_SECRET --project "$PROJECT_ID"  
gcloud secrets get-iam-policy $REDIS_SECRET --project "$PROJECT_ID"
gcloud secrets get-iam-policy $KAFKA_SECRET --project "$PROJECT_ID"

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
# Delete the secret Managers file
gcloud secrets delete $ENV_VARS_SECRET --quiet

gcloud secrets delete $REDIS_SECRET --quiet

gcloud secrets delete $KAFKA_SECRET --quiet

# Delete Autopilot cluster
gcloud container clusters delete "$CLUSTER_NAME" \
  --region="$REGION" \
  --project="$PROJECT_ID" \
  --quiet

# Delete Standard cluster
gcloud container clusters delete "$CLUSTER_NAME" \
  --zone="$ZONE" \
  --project="$PROJECT_ID" \
  --quiet

# Delete GSA (for standard cluster)
gcloud iam service-accounts delete "$GSA@$PROJECT_ID.iam.gserviceaccount.com" \
  --project="$PROJECT_ID"
```

#### Aditional commands

```sh

# Check the pods status
kubectl get pod -n $NAMESPACE

helm get manifest labs-deploy -n labs-dev | less

kubectl describe pod -n $NAMESPACE

kubectl logs -f -n $NAMESPACE labs-soft-npd-gke-deploy-dev-deploy-6557f9d565-rc7zc

kubectl exec -it -n $NAMESPACE labs-soft-npd-gke-deploy-dev-deploy-6557f9d565-rc7zc -- sh

helm uninstall labs-deploy -n $NAMESPACE

kubectl get events -n $NAMESPACE

# Ver que el add-on está habilitado en el cluster
gcloud container clusters describe "$CLUSTER_NAME" --location "$REGION" \
  --project "$PROJECT_ID" | grep -A4 secretManagerConfig

# Ver el volumen montado en el Pod
kubectl exec -it deploy/labs-soft-npd-gke-deploy-dev-deploy -- sh -c 'ls -l /etc/secrets && echo && cat /etc/secrets/secrets.env | sed "s/=.*/=****/g"'

# Verifica que el cluster tenga habilitado secret manager
gcloud container clusters describe "$CLUSTER_NAME" --location "$REGION" \
  | grep secretManagerConfig -A 4
kubectl get csidrivers | grep secrets-store-gke
kubectl -n kube-system get pods -l "k8s-app in (secrets-store-gke, secrets-store-provider-gke)"
kubectl -n kube-system get ds | grep -E 'secrets-store|provider-gke' || true

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