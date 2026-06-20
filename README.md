# Auto-Scaling Demo with Docker + Kubernetes

A minimal Spring Boot that demonstrates Kubernetes Horizontal Pod Autoscaler (HPA) in action using JMeter load testing.

## Architecture

```text
[JMeter] --> [Spring Boot Backend (auto-scaled)]
                                               |
                                    [HPA: scales 1-10 pods when CPU > 50%]
```

## How Auto-Scaling Works

Here is a simple explanation of how the different components coordinate to auto-scale the app:

### 1. Key Components
* **Metrics Server (`k8s/metrics-server.yaml`)**: Constantly monitors the cluster and records how much CPU and memory each running pod is using.
* **Horizontal Pod Autoscaler (HPA) (`k8s/backend-hpa.yaml`)**: Polling the Metrics Server (every 15s) to check the average CPU utilization across all pods against the target threshold.
* **Deployment (`k8s/backend-deployment.yaml`)**: Controls the replica set. HPA instructs it to spin up or scale down pods.

### 2. Workflow Sequence
1. **CPU Baseline**: Each pod specifies a CPU **Request** of `100m` (0.1 core) as its baseline.
2. **Traffic Load**: JMeter triggers heavy load on the `/api/load` endpoint, causing CPU usage on the pods to rise to `500m` (the set limit, representing `500%` of the requested baseline).
3. **Threshold Check**: The HPA checks CPU usage and notices it has exceeded the target threshold of **`50%`**.
4. **Scale Up**: HPA instructs the deployment to scale up the number of running pods (up to `10` maximum) to distribute the load.
5. **Cooldown & Scale Down**: Once the traffic stops, CPU usage drops. HPA waits for a cooldown period (~5 minutes) and then safely terminates the idle pods down to `1` replica to save resources.

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
# or
# in background (  redirect both stdout and stderr )
 kubectl port-forward service/backend-service 8080:8080 >/dev/null 2>&1 &
```

### Run JMeter Test (CLI mode)

**For CPU Scaling Test:**
```bash
# Run test targeting the CPU-intensive /api/load endpoint
jmeter -n -t jmeter/test-plans/load-api.jmx -Jhost=localhost -Jport=8080
```

**For Memory (RAM) Scaling Test:**
Ensure you have applied the memory HPA: `kubectl apply -f k8s/backend-hpa-memory.yaml`.
```bash
# Run test targeting the memory-intensive /api/memory-load endpoint
jmeter -n -t jmeter/test-plans/load-memory.jmx -Jhost=localhost -Jport=8080
```

### Watch Auto-Scaling Happen

```bash
# In a separate terminal, watch HPA
kubectl get hpa -w

# Or watch pods
kubectl get pods -w
```

## What Happens

### A. During CPU Load:
1. JMeter sends 100 concurrent threads, each making requests to `/api/load`.
2. The `/api/load` endpoint performs CPU-intensive calculations.
3. CPU usage exceeds the 50% threshold.
4. HPA detects high CPU and scales pods from 1 up to 10.
5. After load stops, pods scale back down (after ~5 min cooldown).

### B. During Memory Load:
1. JMeter requests call `/api/memory-load` to allocate chunks of memory.
2. Each call allocates 50MB of memory which is stored in a referenced list.
3. Average pod memory usage exceeds the 60% threshold (approx 153.6Mi of the 256Mi requested).
4. HPA detects high memory utilization and scales pods.
5. The memory load API automatically clears allocated chunks after 2 minutes to free memory and prevent container OOMs. After the cooldown period (~5 min), HPA scales the pods back down.

## Key Configuration

| Setting | Value | File                             |
|----------|--------|----------------------------------|
| CPU Request | 100m | `k8s/backend-deployment.yaml`    |
| CPU Limit | 500m | `k8s/backend-deployment.yaml`    |
| Memory Request | 256Mi | `k8s/backend-deployment.yaml`    |
| Memory Limit | 512Mi | `k8s/backend-deployment.yaml`    |
| CPU Scale Trigger | 50% CPU | `k8s/backend-hpa-memory.yaml`    |
| Memory Scale Trigger | 60% Memory | `k8s/backend-hpa-memory.yaml`    |
| Min Replicas | 1 | `k8s/backend-hpa-memory.yaml`    |
| Max Replicas | 10 | `k8s/backend-hpa-memory.yaml`    |
| JMeter Threads | 100 | `jmeter/test-plans/*.jmx`        |

## Project Structure

```text
.
├── Dockerfile
├── settings.gradle
├── build.gradle
├── src/
│   └── main/java/com/paravar/auto_scaling/
│       ├── AutoScalingApplication.java
│       └── LoadController.java
│
├── k8s/
│   ├── backend-deployment.yaml
│   ├── backend-hpa.yaml (CPU only)
│   ├── backend-hpa-memory.yaml (CPU + Memory)
│   └── metrics-server.yaml
│
├── jmeter/
│   └── test-plans/
│       ├── load-api.jmx
│       └── load-memory.jmx
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
