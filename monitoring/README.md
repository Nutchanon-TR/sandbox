# Local Monitoring

This folder contains the local Prometheus and Grafana setup for the Sandbox Docker Compose stack.

## Run

Start the app stack together with monitoring:

```powershell
docker compose -f docker-compose.yml -f docker-compose.local.yml -f docker-compose.monitoring.yml up -d --build
```

Open:

- Prometheus: http://localhost:9090
- Grafana: http://localhost:3001
- Grafana login: `admin` / `admin`

## What Gets Scraped

Prometheus scrapes Spring Boot Actuator metrics from the app containers:

- `user-service:80/actuator/prometheus`
- `chat-service:80/actuator/prometheus`
- `bpost-service:80/actuator/prometheus`
- `jobjab-service:80/actuator/prometheus`

The Grafana datasource and the starter dashboard are provisioned from files in this folder. Runtime Grafana state is stored in the `grafana_data` Docker volume.

## Notes

This setup is for local Docker Compose. If the backend services are run directly on the host instead of Docker Compose, the Prometheus targets in `prometheus/prometheus.yml` need to be changed to `host.docker.internal:<port>`.

## Cloud Self-Hosted Monitoring

The GitHub Actions ACA workflow also builds and deploys self-hosted monitoring containers:

- `prometheus-service`: internal ingress, target port `9090`
- `grafana-service`: external ingress, target port `3000`

Cloud-specific files:

- `prometheus/Dockerfile`
- `prometheus/prometheus.cloud.yml`
- `grafana/Dockerfile`
- `grafana/cloud/provisioning/`

Prometheus scrapes the backend apps through ACA internal ingress:

- `user-service:80/actuator/prometheus`
- `chat-service:80/actuator/prometheus`
- `bpost-service:80/actuator/prometheus`
- `jobjab-service:80/actuator/prometheus`

Required GitHub secret:

- `GRAFANA_ADMIN_PASSWORD`

Optional GitHub variables:

- `MONITORING_MIN_REPLICAS` defaults to `0`
- `MONITORING_MAX_REPLICAS` defaults to `1`

With `MONITORING_MIN_REPLICAS=0`, Prometheus can scale to zero and will not collect continuous history while idle. Set it to `1` when you want continuous cloud monitoring.
