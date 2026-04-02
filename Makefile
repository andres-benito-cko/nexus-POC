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
	aws secretsmanager create-secret \
	  --name nexus-poc/github-token \
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
	CDK_DEFAULT_ACCOUNT=591127500072 npm_config_cache=$(TMPDIR)/npm-cache \
	  npx --prefix cdk cdk deploy \
	  --profile $(AWS_PROFILE) \
	  --require-approval never

destroy:
	CDK_DEFAULT_ACCOUNT=591127500072 npm_config_cache=$(TMPDIR)/npm-cache \
	  npx --prefix cdk cdk destroy \
	  --profile $(AWS_PROFILE) \
	  --force

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
	  --parameters '{"command":["sudo -u ec2-user git -C /home/ec2-user/nexus-POC pull && sudo -u ec2-user docker-compose -f /home/ec2-user/nexus-POC/docker-compose.yml -f /home/ec2-user/nexus-POC/docker-compose.override.yml --project-directory /home/ec2-user/nexus-POC up --build -d --no-deps nexus-api nexus-transformer"]}'

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
	  --parameters '{"command":["sudo -u ec2-user git -C /home/ec2-user/nexus-POC pull && pkill -f server.cjs; sudo -u ec2-user bash -c \"VITE_BACKEND_URL=http://10.144.177.20:8083 VITE_SIMULATOR_URL=http://10.144.177.30:8081 nohup node /home/ec2-user/nexus-POC/ui/server.cjs > /home/ec2-user/vite.log 2>&1 &\""]}'
