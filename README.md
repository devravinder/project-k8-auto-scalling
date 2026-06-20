# Auto-Scaling Demo with Docker + Kubernetes

A minimal Spring Boot that demonstrates Kubernetes Horizontal Pod Autoscaler (HPA) in action using JMeter load testing.

## Architecture

```text
[JMeter] --> [Spring Boot Backend (auto-scaled)]
                                               |
                                    [HPA: scales 1-10 pods when CPU > 50%]
```

## Prerequisites

- Docker Desktop (with Kubernetes enabled)
- kubectl
- Kind ( to create cluster on local docker)
- Lens ( k8slens ) ( to see k8 nodes ) 
- Apache JMeter (for load testing)
- Metrics Server (for HPA to work)

## Setup Instructions

### 1. Enable Kubernetes in Docker Desktop

Settings → Kubernetes → Enable Kubernetes → Apply & Restart

### 2. Create K8 Cluster

- Set up Kubernetes cluster:-
    - `kind create cluster --name local-cluster`
      - to see info `kubectl cluster-info --context kind-local-cluster`
      - or `kubectl cluster-info` if kubectl context to "kind-local-cluster"
      - to check context `kubectl config current-context`
      

- Delete the cluster:-
    - `kind delete cluster --name local-cluster`

- to see clusters
    - `kind get clusters`


### 3. Install Metrics Server

HPA needs metrics-server to read CPU usage:

```bash
kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml
```
or
```bash
kubectl apply -f k8s/metrics-server.yaml
```

For Docker Desktop, to skip TLS verification:

```bash
kubectl patch deployment metrics-server -n kube-system --type='json' \
-p='[{"op":"add","path":"/spec/template/spec/containers/0/args/-","value":"--kubelet-insecure-tls"}]'
```

### 3. Build & Load Docker Images

```bash
# 1. Build the backend image on the host docker daemon
docker build -t auto-scale-app .

# 2. Load the image into the Kind cluster
# Why: Kind cluster nodes run as separate Docker containers and do not share the host's
# Docker daemon image cache. If you do not load the image, the pods will fail with ErrImageNeverPull.
kind load docker-image auto-scale-app:latest --name local-cluster
```

> [!IMPORTANT]
> You must run `kind load docker-image` every time you rebuild the Docker image, so the updated image is transferred into the Kind cluster nodes.

### 4. Deploy to Kubernetes

```bash
kubectl apply -f k8s/backend-deployment.yaml
kubectl apply -f k8s/backend-hpa.yaml
```

### 5. Verify Deployment

```bash
kubectl get pods
kubectl get services
kubectl get hpa
```

### 6. Access the App

Get the app NodePort:

```bash
kubectl get service backend-service
```

## Load Testing with JMeter

### Find the backend port for direct testing

```bash
kubectl port-forward service/backend-service 8080:8080
```
```bash
# in background (  redirect both stdout and stderr )
 kubectl port-forward service/backend-service 8080:8080 >/dev/null 2>&1 &
```

### Run JMeter Test (CLI mode)

```bash
jmeter -n -t jmeter/test-plans/load-api.jmx -Jhost=localhost -Jport=8080

## or

jmeter -n -t "jmeter/test-plans/load-api.jmx"
```

### Watch Auto-Scaling Happen

```bash
# In a separate terminal, watch HPA
kubectl get hpa -w
```

```bash
# Or watch pods
kubectl get pods -w
```

## What Happens

1. JMeter sends 100 concurrent threads, each making 50 requests to `/api/load`
2. The `/api/load` endpoint performs CPU-intensive calculations
3. CPU usage exceeds the 50% threshold
4. HPA detects high CPU and scales pods from 1 up to 10
5. After load stops, pods scale back down (after ~5 min cooldown)

## Key Configuration

| Setting | Value | File                             |
|----------|--------|----------------------------------|
| CPU Request | 100m | `k8s/backend-deployment.yaml`    |
| CPU Limit | 500m | `k8s/backend-deployment.yaml`    |
| Scale Trigger | 50% CPU | `k8s/backend-hpa.yaml`           |
| Min Replicas | 1 | `k8s/backend-hpa.yaml`           |
| Max Replicas | 10 | `k8s/backend-hpa.yaml`           |
| JMeter Threads | 100 | `jmeter/test-plans/load-api.jmx` |

## Project Structure

```text
.
├── backend/
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/java/com/example/
│       ├── AutoscaleDemoApplication.java
│       └── LoadController.java
│
├── k8s/
│   ├── backend-deployment.yaml
│   ├── backend-hpa.yaml
│   └── frontend-deployment.yaml
│
├── jmeter/
│   └── load-test.jmx
│
└── README.md
```

## Troubleshooting

- **HPA shows `<unknown>` for CPU**
    - Metrics server is not running.
    - Check:
      ```bash
      kubectl get pods -n kube-system | grep metrics
      ```

- **Pods not scaling**
    - Wait 1–2 minutes for HPA to react.
    - Check:
      ```bash
      kubectl describe hpa backend-hpa
      ```

- **ImagePullBackOff / ErrImageNeverPull**
    - The image must be loaded into the Kind cluster after being built:
      ```bash
      kind load docker-image auto-scale-app:latest --name local-cluster
      ```
    - The deployment manifest must use `imagePullPolicy: Never` (or `IfNotPresent`) to prevent Kubernetes from attempting to pull the image from a remote registry.
    - If you update your code and rebuild the image, you must load it again and restart the deployment:
      ```bash
      kubectl rollout restart deployment/backend-deployment
      ```

- **HPA scales to max replicas immediately on startup**
    - **Why**: JVM startup (class loading, JIT compilation, Spring initialization) is CPU intensive. Since the CPU request is set to `100m` (0.1 core) and limit is `500m`, the startup CPU usage easily exceeds 100% of the requested CPU. This causes HPA to detect >50% CPU and scale up to 10 replicas.
    - **Resolution**: This is normal behavior for Java apps with low CPU requests. Once all pods finish initialization, they will idle around 2m CPU, and the HPA will automatically scale down back to 1 replica after the cooldown period (~5 minutes).
