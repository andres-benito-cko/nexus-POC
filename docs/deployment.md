# Nexus POC — Deployment Guide

> **For AI agents:** This doc is the authoritative redeployment reference. Read it fully before touching the stack.

## Architecture

Three EC2 `t3.medium` instances in a private subnet (`subnet-0076d4d589390d99d`, `10.144.177.0/26`, `eu-west-1a`) inside VPC `vpc-06b9709ddf6203ec2` (account `591127500072`).

| Instance | Fixed IP | Services |
|---|---|---|
| InfraInstance | `10.144.177.10` | zookeeper, kafka, postgres |
| ApiInstance | `10.144.177.20` | nexus-api (8083), nexus-transformer (8082), ai-generator (8084) |
| WorkersInstance | `10.144.177.30` | le-simulator (8081), rules-engine (8080), UI (5173) |

Container orchestration: docker-compose v1 (installed via pip in a venv at `/opt/dc-venv`).

All docker-compose services run with `restart: unless-stopped` so they survive Docker daemon restarts and instance reboots. The UI Node.js server runs as a systemd service (`nexus-ui.service`) for the same reason.

## Prerequisites

### 1. AWS auth (Okta)
```bash
okta-aws-cli \
  --org-domain checkout.okta.com \
  --oidc-client-id 0oar3nsvk7VtIvsL3357 \
  --aws-acct-fed-app-id 0oaricq7oliS1Yb8G357 \
  -p cko-financial-infrastructure-tooling-qa-OktaIDP-breakglass \
  --write-aws-credentials
```
`--write-aws-credentials` is required — without it credentials go to stdout only.
Session lasts ~1 hour; re-run when you see `ExpiredTokenException`.

### 2. SSM Session Manager plugin
Required for `make tunnel`. Install once:
```bash
curl -sL "https://s3.amazonaws.com/session-manager-downloads/plugin/latest/mac_arm64/session-manager-plugin.pkg" -o /tmp/smp.pkg
sudo installer -pkg /tmp/smp.pkg -target /
sudo ln -sf /usr/local/sessionmanagerplugin/bin/session-manager-plugin /usr/local/bin/session-manager-plugin
```

### 3. CDK bootstrap (once per account/region)
```bash
make bootstrap-cdk
```
This strips ECR resources from the bootstrap template (the org SCP blocks `ecr:CreateRepository`).

### 4. GitHub token in Secrets Manager (once)
```bash
make store-github-token TOKEN=ghp_xxxx
```
Stored at `nexus-poc/github-token`. The instances fetch it at boot to clone the repo.

To rotate the token (e.g. if exposed in logs): `make store-github-token TOKEN=<new_token>`

## Firewall / Network Constraints

The Cloud WAN firewall blocks most external traffic. **Allowed outbound:**
- `github.com` — repo clone
- `pypi.org` — docker-compose pip install
- `public.ecr.aws` — Docker image pulls
- AWS service endpoints (SSM, Secrets Manager, S3 for CDK assets)

**Blocked:**
- `registry-1.docker.io` (Docker Hub) — use `public.ecr.aws/docker/library/*` mirrors
- `plugins.gradle.org`, `repo1.maven.org` — Gradle/Maven cannot download plugins or deps
- `registry.npmjs.org` — npm cannot install packages

### Consequence: all artifacts must be pre-built locally

| Artifact | Source | Where committed |
|---|---|---|
| Java JARs | `./gradlew bootJar` via local Docker | `{service}/build/libs/*.jar` |
| React UI | `npm run build` locally | `ui/dist/` |
| Docker images | ECR Public mirrors only | referenced in `docker-compose.override.yml` |

## SCP Constraints

Policy `p-n94gdmkj` (`qa-Restrictions`) in `cko-core-platform/multi-account-org-policies/terraform/200-scp/policies/ou/qa/Restrictions.tf.json`:

- **DenyIMDSv1**: `ec2:RunInstances` requires `ec2:MetadataHttpTokens = "required"`. Must be set via a `CfnLaunchTemplate` — setting it on `CfnInstance.metadataOptions` does NOT work (CloudFormation uses `ModifyInstanceMetadataOptions` post-creation, which bypasses the SCP condition key).
- **DenyUnencryptedVolumes**: all EBS volumes must be encrypted.
- **RequireSmallInstanceType**: only `t3.medium` and smaller are allowed for this account.
- **DenyNetworking**: blocks VPC/subnet creation for non-networking roles — cannot add public subnets, IGW, or ALB.

## Full Deploy (fresh stack)

### Step 1 — Pre-build Java JARs (if any Java service changed)
Requires Docker running locally. Uses the `gradle:8.7-jdk17` image — no local JDK needed.
```bash
cd Projects/nexus-POC
for svc in le-simulator nexus-transformer nexus-api rules-engine ai-generator; do
  docker run --rm \
    -v "$(pwd)/$svc:/app" \
    -w /app \
    gradle:8.7-jdk17 \
    gradle bootJar --no-daemon -x test -q
done
git add -f le-simulator/build/libs/le-simulator-0.0.1-SNAPSHOT.jar \
           nexus-transformer/build/libs/nexus-transformer-0.0.1-SNAPSHOT.jar \
           nexus-api/build/libs/nexus-api-0.0.1-SNAPSHOT.jar \
           rules-engine/build/libs/rules-engine-0.0.1-SNAPSHOT.jar \
           ai-generator/build/libs/ai-generator-0.0.1-SNAPSHOT.jar
```

### Step 2 — Pre-build UI (if UI changed)
Requires Node.js locally.
```bash
cd ui
rm -rf node_modules && npm install
VITE_BACKEND_URL=http://10.144.177.20:8083 \
VITE_SIMULATOR_URL=http://10.144.177.30:8081 \
npm run build
cd ..
git add ui/dist
```

### Step 3 — Commit and push
```bash
git commit -m "chore: rebuild artifacts"
git push
```

### Step 4 — Deploy CDK stack
```bash
make deploy
```
The Makefile extracts `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, and `AWS_SESSION_TOKEN` from the `cko-financial-infrastructure-tooling-qa-OktaIDP-breakglass` profile and passes them as env vars to CDK. This is required because `npx cdk` does not propagate `--profile` into the subprocess that does CloudFormation calls and asset uploads.

The stack takes ~3 minutes. Each instance bootstraps in parallel:
- **Infra**: installs Docker, clones repo, pulls bitnami/zookeeper + bitnami/kafka + postgres from ECR Public, starts containers with `restart: unless-stopped`.
- **API**: same setup, waits for `kafka:9092` to be reachable, starts nexus-api + nexus-transformer + ai-generator with `restart: unless-stopped`. The ai-generator uses the EC2 instance role for Bedrock access (no explicit AWS credentials needed).
- **Workers**: same setup, waits for `kafka:9092`, starts le-simulator + rules-engine with `restart: unless-stopped`, then installs and starts `nexus-ui.service` (systemd) to serve the pre-built UI.

Monitor progress via SSM (separate terminal windows):
```bash
make logs-infra    # tail /var/log/nexus-poc-infra.log
make logs-api      # tail /var/log/nexus-poc-api.log
make logs-workers  # tail /var/log/nexus-poc-workers.log
```

## Partial Redeploy (running stack)

> **Note:** `git pull` must always run as `ec2-user` (not root). The Makefile targets handle this correctly.

### Java services changed
```bash
# 1. Rebuild JARs locally (Step 1 above)
# 2. git add -f + commit + push
make redeploy-api      # git pull + rebuild nexus-api + nexus-transformer + ai-generator
make redeploy-workers  # git pull + rebuild le-simulator + rules-engine
```

### UI changed
```bash
# 1. Rebuild dist/ locally (Step 2 above)
# 2. git add + commit + push
make restart-ui        # git pull + restart nexus-ui systemd service
```

### Only docker-compose override needs changing
Connect to the instance and rewrite the override file directly, then `docker-compose up -d`:
```bash
make connect-workers   # SSM interactive session
# edit /home/ec2-user/nexus-POC/docker-compose.override.yml
# then:
sudo -u ec2-user docker-compose \
  -f /home/ec2-user/nexus-POC/docker-compose.yml \
  -f /home/ec2-user/nexus-POC/docker-compose.override.yml \
  --project-directory /home/ec2-user/nexus-POC \
  up -d --no-deps <service>
```

## Access the UI

### Via internal URL (primary — requires corp VPN or Cloud WAN)
```
https://nexus-poc.financial-infrastructure-tooling.qa.ckotech.internal
```
An internal ALB sits in front of the Workers instance (port 5173). The cert is issued by the org subordinate CA — trusted by corp browsers automatically. Requires Cloudflare non-prod VPN or a machine on the Checkout Cloud WAN.

### Via SSM tunnel (fallback — works without VPN)
```bash
make tunnel   # forwards WorkersInstance:5173 to localhost:5173
# then open http://localhost:5173
```

### Via raw IP (corp network only)
If on the Checkout corporate network with Cloud WAN routing:
```
http://10.144.177.30:5173
```

## Internal ALB Setup

The stack creates the following resources for the internal URL:

| Resource | Value |
|---|---|
| ALB | Internal, `eu-west-1a` + `eu-west-1b` subnets |
| ALB security group | Inbound 443 from prefix list `pl-0afa2d775d5677fe7` (non-prod-cloudflare-pl) |
| Private certificate | `nexus-poc.financial-infrastructure-tooling.qa.ckotech.internal` via org CA |
| Org CA ARN | `arn:aws:acm-pca:eu-west-1:471112826941:certificate-authority/31f7e6a9-1d5f-4776-80b0-0d0d5c3b7be3` |
| Target group | Workers instance `i-09c6cdc97b7f62684`, port 5173, health check `GET /` |
| SSL policy | `ELBSecurityPolicy-TLS13-1-2-2021-06` (TLS 1.2+) |
| Route53 zone | `financial-infrastructure-tooling.qa.ckotech.internal` (ID: `Z0439559123PXDIXAWMOW`) |
| DNS record | `nexus-poc` A alias → ALB |

The pattern mirrors `engineering-team-metrics` in the `cko-card-processing` account.

## UI Server

The UI is served by `ui/server.cjs` — a zero-dependency Node.js HTTP server that:
- Serves pre-built static files from `ui/dist/` with SPA fallback to `index.html`
- Proxies `GET|POST /api/*` to nexus-api at `VITE_BACKEND_URL` (strips `/api` prefix)
- Proxies `/simulate/*` to le-simulator at `VITE_SIMULATOR_URL`
- Proxies WebSocket upgrades on `/ws` to nexus-api

Named `.cjs` because `ui/package.json` has `"type": "module"`.

On EC2 it runs as a systemd service (`/etc/systemd/system/nexus-ui.service`) so it restarts on crash or reboot. Useful commands:
```bash
systemctl status nexus-ui
journalctl -u nexus-ui -f    # live logs (also mirrored to ~/vite.log)
systemctl restart nexus-ui
```

The WebSocket hooks use `wss://` when the page is loaded over HTTPS (the internal URL) and `ws://` for plain HTTP (tunnel fallback). This is handled in `ui/src/hooks/useWebSocket.ts` and `useManualWebSocket.ts`.

## Fresh Stack Behaviour Notes

- **`/api/configs/active` returns 404 on first boot** — this is expected. No engine config has been activated yet. Use the UI Config page to create and activate one.
- **Kafka connection WARNs in nexus-api/nexus-transformer logs** — these appear transiently while Kafka is starting. The services reconnect automatically; WARNs stop within ~60 seconds.
- **rules-engine crash-loops until postgres is ready** — it retries via `restart: unless-stopped` and stabilises once Flyway migrations complete.

## Docker Images Used

All images pulled from `public.ecr.aws` (Docker Hub is blocked):

| Service | Image |
|---|---|
| zookeeper | `public.ecr.aws/bitnami/zookeeper:3.8` |
| kafka | `public.ecr.aws/bitnami/kafka:3.5` |
| postgres | `public.ecr.aws/docker/library/postgres:15` |
| nexus-api | built from `public.ecr.aws/docker/library/eclipse-temurin:17-jre` |
| nexus-transformer | built from `public.ecr.aws/docker/library/eclipse-temurin:17-jre` |
| rules-engine | built from `public.ecr.aws/docker/library/eclipse-temurin:17-jre` |
| le-simulator | built from `public.ecr.aws/docker/library/eclipse-temurin:17-jre` |
| ai-generator | built from `public.ecr.aws/docker/library/eclipse-temurin:17-jre` |

## Known Issues and Fixes

### bitnami/kafka advertised listeners
bitnami/kafka reads `KAFKA_ADVERTISED_LISTENERS` (Confluent-style, without `CFG_`) from the environment in addition to `KAFKA_CFG_ADVERTISED_LISTENERS`. The base `docker-compose.yml` sets `KAFKA_ADVERTISED_LISTENERS: INTERNAL://kafka:29092,EXTERNAL://localhost:9092`. Docker-compose env maps **merge** on override — so the infra override must explicitly override `KAFKA_ADVERTISED_LISTENERS` (not just add the CFG_ variant):
```yaml
kafka:
  environment:
    KAFKA_ADVERTISED_LISTENERS: "INTERNAL://kafka:29092,EXTERNAL://10.144.177.10:9092"
    KAFKA_CFG_ADVERTISED_LISTENERS: "INTERNAL://kafka:29092,EXTERNAL://10.144.177.10:9092"
```

### Flyway conflict between nexus-api and rules-engine
Both services share the `nexus` postgres database. nexus-api creates `flyway_schema_history` with its own V1 migration. rules-engine has its own independent V1-V11 migrations. Without intervention, rules-engine fails with `Found non-empty schema but no schema history table`. Fix: give rules-engine its own Flyway table and baseline at V0 so all its migrations run fresh:
```yaml
rules-engine:
  environment:
    SPRING_FLYWAY_TABLE: "rules_engine_schema_history"
    SPRING_FLYWAY_BASELINE_ON_MIGRATE: "true"
    SPRING_FLYWAY_BASELINE_VERSION: "0"
```

### docker-compose v1 dependency pinning
docker-compose v1 (via pip) requires exact versions:
```
docker>=5.0.3,<6    # v6 removed requests-unixsocket; v7 removed ssl_version kwarg
requests-unixsocket # needed for http+docker:// scheme
requests<2.28       # 2.28+ rejects http+docker:// scheme
```
Installed in `/opt/dc-venv`. `docker-compose` symlinked to `/usr/local/bin/docker-compose`.

### Git ownership error in SSM `send-command`
SSM `send-command` runs as root. `git` refuses to operate on a repo owned by `ec2-user`. Always use:
```bash
sudo -u ec2-user git -C /home/ec2-user/nexus-POC pull
```

### IMDSv2 SCP and LaunchTemplate
The `DenyIMDSv1` SCP condition key (`ec2:MetadataHttpTokens`) is only present in the RunInstances API call when specified via a LaunchTemplate. The CDK stack creates a `CfnLaunchTemplate` with `httpTokens: required` and attaches it to each instance via `cfnInstance.launchTemplate`. Setting `cfnInstance.metadataOptions` directly does NOT satisfy the SCP because CloudFormation applies it via `ModifyInstanceMetadataOptions` post-creation.

### CDK deploy credential resolution
`npx cdk deploy --profile <name>` does not propagate the profile into the subprocess that uploads assets to S3 and calls CloudFormation. This manifests as "Need to perform AWS calls for account X, but no credentials have been configured" even when the profile is valid. The `make deploy` target works around this by extracting credentials from the profile and passing them as `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` / `AWS_SESSION_TOKEN` env vars.

### Kafka ZooKeeper stale ephemeral node on restart
When Kafka restarts (e.g. after a Docker daemon restart or instance reboot), it tries to register `/brokers/ids/1` in ZooKeeper. If the previous session's ephemeral node hasn't expired yet, Kafka gets `KeeperErrorCode = NodeExists` and exits fatally.

**Symptoms:** `docker ps` shows kafka as stopped/exited; kafka logs contain `Error while creating ephemeral at /brokers/ids/1, node already exists and owner ... does not match current session`.

**Fix:** Restart ZooKeeper first (which clears all ephemeral nodes), then start Kafka:
```bash
make connect-infra
docker restart nexus-poc_zookeeper_1
sleep 5
docker start nexus-poc_kafka_1
```

The CDK user data avoids this on fresh deploys by starting zookeeper + postgres first, waiting 15 s, then starting kafka.

### EC2 security group descriptions — ASCII only
`GroupDescription` and SecurityGroupIngress `Description` fields only accept `a-zA-Z0-9. _-:/()#,@[]+=&;{}!$*`. Unicode characters (em-dash, arrows, etc.) cause a 400 `InvalidRequest` from the EC2 API.

### WebSocket mixed-content over HTTPS
The UI WebSocket hooks (`useWebSocket.ts`, `useManualWebSocket.ts`) detect `window.location.protocol` and use `wss://` when the page is loaded over HTTPS. The ALB terminates TLS and forwards plain WebSocket to port 5173 on the Workers instance — the server.cjs proxy handles upgrades transparently.

## Destroy
```bash
make destroy
```
