# Nexus POC — Deployment

## Docker Compose Setup

All infrastructure and backend services are defined in `docker-compose.yml` at the repo root:

```yaml
version: '3.8'
services:
  zookeeper:
    image: confluentinc/cp-zookeeper:7.5.0
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
    ports: ["2181:2181"]

  kafka:
    image: confluentinc/cp-kafka:7.5.0
    depends_on: [zookeeper]
    ports: ["9092:9092"]
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: INTERNAL:PLAINTEXT,EXTERNAL:PLAINTEXT
      KAFKA_LISTENERS: INTERNAL://0.0.0.0:29092,EXTERNAL://0.0.0.0:9092
      KAFKA_ADVERTISED_LISTENERS: INTERNAL://kafka:29092,EXTERNAL://localhost:9092
      KAFKA_INTER_BROKER_LISTENER_NAME: INTERNAL
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"

  postgres:
    image: postgres:15
    environment:
      POSTGRES_DB: nexus
      POSTGRES_USER: nexus
      POSTGRES_PASSWORD: nexus
    ports: ["5432:5432"]
    volumes: ["postgres_data:/var/lib/postgresql/data"]

  le-simulator:
    build: ./le-simulator
    depends_on: [kafka]
    restart: on-failure
    ports: ["8081:8081"]
    environment:
      SPRING_KAFKA_BOOTSTRAP_SERVERS: kafka:29092
      SERVER_PORT: 8081

  nexus-api:
    build: ./nexus-api
    depends_on: [kafka, postgres]
    restart: on-failure
    ports: ["8083:8083"]
    environment:
      SPRING_KAFKA_BOOTSTRAP_SERVERS: kafka:29092
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/nexus
      SPRING_DATASOURCE_USERNAME: nexus
      SPRING_DATASOURCE_PASSWORD: nexus
      NEXUS_TRANSFORMER_URL: http://nexus-transformer:8082
      SERVER_PORT: 8083

  nexus-transformer:
    build: ./nexus-transformer
    depends_on: [kafka, nexus-api]
    restart: on-failure
    ports: ["8082:8082"]
    environment:
      SPRING_KAFKA_BOOTSTRAP_SERVERS: kafka:29092
      NEXUS_API_URL: http://nexus-api:8083
      SERVER_PORT: 8082

  rules-engine:
    build: ./rules-engine
    depends_on: [kafka, postgres]
    restart: on-failure
    ports: ["8080:8080"]
    environment:
      SPRING_KAFKA_BOOTSTRAP_SERVERS: kafka:29092
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/nexus
      SPRING_DATASOURCE_USERNAME: nexus
      SPRING_DATASOURCE_PASSWORD: nexus
      SERVER_PORT: 8080

volumes:
  postgres_data:
```

## Port Mapping

| Service | Port | Purpose |
|---|---|---|
| Zookeeper | 2181 | Kafka coordination |
| Kafka | 9092 | Message broker |
| PostgreSQL | 5432 | Database |
| LE Simulator | 8081 | Simulator REST API |
| Nexus Transformer | 8082 | Transaction query API |
| Nexus API | 8083 | BFF for UI |
| Rules Engine | 8080 | Rules CRUD, ledger, WebSocket |

## Startup Instructions

**1. Build all Java services:**

```bash
for svc in le-simulator nexus-transformer nexus-api rules-engine; do
  (cd $svc && ./gradlew build -x test)
done
```

**2. Start infrastructure and services:**

```bash
docker compose up --build
```

This starts Zookeeper, Kafka, PostgreSQL, and all 4 Java services. Flyway migrations run automatically on first startup.

**3. Start the UI dev server:**

```bash
cd ui && npm install && npm run dev
```

The UI is available at `http://localhost:5173`.

**Startup order:** Docker Compose handles dependency ordering. `nexus-transformer` waits for `nexus-api`; all services use `restart: on-failure` to handle transient startup races with Kafka.

## AWS Deployment Path

Note: production deployment is out of scope for the POC. The architecture is AWS-ready; this table documents the intended mapping for a future production deployment.

| POC Component | AWS Service | Notes |
|---|---|---|
| LE Simulator | ECS/Fargate task | Single task; internal ALB |
| Nexus Transformer | ECS/Fargate service | Auto-scaling on consumer lag |
| Rules Engine (BFF) | ECS/Fargate service | Public ALB; WebSocket support |
| Nexus API | ECS/Fargate service | Internal ALB |
| Kafka | Amazon MSK | 3 brokers, 3 AZs, IAM auth |
| PostgreSQL | Amazon RDS | Multi-AZ; db.t3.medium |
| UI | S3 + CloudFront | Static hosting |
| Networking | VPC + private subnets | ALB in public subnet |
| Secrets | AWS Secrets Manager | DB credentials, Kafka auth |
| Logging | CloudWatch Logs | Container logs aggregated |
