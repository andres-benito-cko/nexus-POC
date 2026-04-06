.PHONY: bootstrap-cdk deploy destroy connect-infra connect-api connect-workers logs-infra logs-api logs-workers redeploy-api redeploy-workers restart-ui store-github-token tunnel

AWS_PROFILE  ?= cko-financial-infrastructure-tooling-qa-OktaIDP-breakglass
AWS_REGION   ?= eu-west-1
STACK_NAME   ?= NexusPocStack

_output = $(shell aws cloudformation describe-stacks \
            --stack-name $(STACK_NAME) \
            --profile $(AWS_PROFILE) \
            --region $(AWS_REGION) \
            --query "Stacks[0].Outputs[?OutputKey==\`$(1)\`].OutputValue" \
            --output text 2>/dev/null)

INFRA_INSTANCE_ID   = $(call _output,InfraInstanceId)
API_INSTANCE_ID     = $(call _output,ApiInstanceId)
WORKERS_INSTANCE_ID = $(call _output,WorkersInstanceId)

# --- One-time setup -----------------------------------------------------------

store-github-token:
	aws secretsmanager put-secret-value \
	  --secret-id nexus-poc/github-token \
	  --secret-string "$(TOKEN)" \
	  --region $(AWS_REGION) \
	  --profile $(AWS_PROFILE)

bootstrap-cdk:
	cd cdk && npm install
	cd cdk && npx cdk bootstrap --show-template > bootstrap-template.yml
	python3 scripts/strip-ecr-from-bootstrap.py cdk/bootstrap-template.yml
	cd cdk && npx cdk bootstrap \
	  --template bootstrap-template.yml \
	  --profile $(AWS_PROFILE) \
	  --cloudformation-execution-policies arn:aws:iam::aws:policy/AdministratorAccess

# --- Deployment ---------------------------------------------------------------

deploy:
	cd cdk && npm install
	cd cdk && \
	  AWS_ACCESS_KEY_ID=$$(aws configure get aws_access_key_id --profile $(AWS_PROFILE)) \
	  AWS_SECRET_ACCESS_KEY=$$(aws configure get aws_secret_access_key --profile $(AWS_PROFILE)) \
	  AWS_SESSION_TOKEN=$$(aws configure get aws_session_token --profile $(AWS_PROFILE)) \
	  AWS_DEFAULT_REGION=$(AWS_REGION) \
	  CDK_DEFAULT_ACCOUNT=591127500072 \
	  CDK_DEFAULT_REGION=$(AWS_REGION) \
	  npm_config_cache=$(TMPDIR)/npm-cache \
	  npx cdk deploy --require-approval never

destroy:
	cd cdk && \
	  AWS_ACCESS_KEY_ID=$$(aws configure get aws_access_key_id --profile $(AWS_PROFILE)) \
	  AWS_SECRET_ACCESS_KEY=$$(aws configure get aws_secret_access_key --profile $(AWS_PROFILE)) \
	  AWS_SESSION_TOKEN=$$(aws configure get aws_session_token --profile $(AWS_PROFILE)) \
	  AWS_DEFAULT_REGION=$(AWS_REGION) \
	  CDK_DEFAULT_ACCOUNT=591127500072 \
	  CDK_DEFAULT_REGION=$(AWS_REGION) \
	  npm_config_cache=$(TMPDIR)/npm-cache \
	  npx cdk destroy --force

# --- Connect ------------------------------------------------------------------

connect-infra:
	aws ssm start-session \
	  --target $(INFRA_INSTANCE_ID) \
	  --region $(AWS_REGION) \
	  --profile $(AWS_PROFILE)

connect-api:
	aws ssm start-session \
	  --target $(API_INSTANCE_ID) \
	  --region $(AWS_REGION) \
	  --profile $(AWS_PROFILE)

connect-workers:
	aws ssm start-session \
	  --target $(WORKERS_INSTANCE_ID) \
	  --region $(AWS_REGION) \
	  --profile $(AWS_PROFILE)

# --- Logs ---------------------------------------------------------------------

logs-infra:
	aws ssm start-session \
	  --target $(INFRA_INSTANCE_ID) \
	  --region $(AWS_REGION) \
	  --profile $(AWS_PROFILE) \
	  --document-name AWS-StartNonInteractiveCommand \
	  --parameters '{"command":["tail -f /var/log/nexus-poc-infra.log"]}'

logs-api:
	aws ssm start-session \
	  --target $(API_INSTANCE_ID) \
	  --region $(AWS_REGION) \
	  --profile $(AWS_PROFILE) \
	  --document-name AWS-StartNonInteractiveCommand \
	  --parameters '{"command":["tail -f /var/log/nexus-poc-api.log"]}'

logs-workers:
	aws ssm start-session \
	  --target $(WORKERS_INSTANCE_ID) \
	  --region $(AWS_REGION) \
	  --profile $(AWS_PROFILE) \
	  --document-name AWS-StartNonInteractiveCommand \
	  --parameters '{"command":["tail -f /var/log/nexus-poc-workers.log"]}'

# --- Tunnel -------------------------------------------------------------------

# Forward UI port to localhost:5173 via SSM; then open http://localhost:5173
tunnel:
	aws ssm start-session \
	  --target $(WORKERS_INSTANCE_ID) \
	  --region $(AWS_REGION) \
	  --profile $(AWS_PROFILE) \
	  --document-name AWS-StartPortForwardingSession \
	  --parameters '{"portNumber":["5173"],"localPortNumber":["5173"]}'

# --- Redeploy (no CDK) --------------------------------------------------------

redeploy-api:
	aws ssm start-session \
	  --target $(API_INSTANCE_ID) \
	  --region $(AWS_REGION) \
	  --profile $(AWS_PROFILE) \
	  --document-name AWS-StartNonInteractiveCommand \
	  --parameters '{"command":["sudo -u ec2-user git -C /home/ec2-user/nexus-POC pull && sudo -u ec2-user docker-compose -f /home/ec2-user/nexus-POC/docker-compose.yml -f /home/ec2-user/nexus-POC/docker-compose.override.yml --project-directory /home/ec2-user/nexus-POC up --build -d --no-deps nexus-api nexus-transformer ai-generator"]}'

redeploy-workers:
	aws ssm start-session \
	  --target $(WORKERS_INSTANCE_ID) \
	  --region $(AWS_REGION) \
	  --profile $(AWS_PROFILE) \
	  --document-name AWS-StartNonInteractiveCommand \
	  --parameters '{"command":["sudo -u ec2-user git -C /home/ec2-user/nexus-POC pull && sudo -u ec2-user docker-compose -f /home/ec2-user/nexus-POC/docker-compose.yml -f /home/ec2-user/nexus-POC/docker-compose.override.yml --project-directory /home/ec2-user/nexus-POC up --build -d --no-deps le-simulator rules-engine"]}'

restart-ui:
	aws ssm start-session \
	  --target $(WORKERS_INSTANCE_ID) \
	  --region $(AWS_REGION) \
	  --profile $(AWS_PROFILE) \
	  --document-name AWS-StartNonInteractiveCommand \
	  --parameters '{"command":["sudo -u ec2-user git -C /home/ec2-user/nexus-POC pull && systemctl restart nexus-ui"]}'

# --- AI Generator ---
sync-domain:
	./scripts/sync-domain.sh

build-generator: sync-domain
	cd ai-generator && ./gradlew build -x test
