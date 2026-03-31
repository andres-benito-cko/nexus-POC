.PHONY: bootstrap-cdk deploy destroy connect logs redeploy store-github-token

AWS_PROFILE  ?= cko-financial-infrastructure-tooling-qa-OktaIDP-breakglass
AWS_REGION   ?= eu-west-1
STACK_NAME   ?= NexusPocStack

# Retrieve the instance ID from the deployed stack
INSTANCE_ID  = $(shell aws cloudformation describe-stacks \
                  --stack-name $(STACK_NAME) \
                  --profile $(AWS_PROFILE) \
                  --region $(AWS_REGION) \
                  --query 'Stacks[0].Outputs[?OutputKey==`InstanceId`].OutputValue' \
                  --output text 2>/dev/null)

PUBLIC_IP    = $(shell aws cloudformation describe-stacks \
                  --stack-name $(STACK_NAME) \
                  --profile $(AWS_PROFILE) \
                  --region $(AWS_REGION) \
                  --query 'Stacks[0].Outputs[?OutputKey==`PublicIp`].OutputValue' \
                  --output text 2>/dev/null)

# --- One-time setup -----------------------------------------------------------

# Store your GitHub PAT in Secrets Manager (run once before bootstrap-cdk)
# Usage: make store-github-token TOKEN=ghp_xxxx
store-github-token:
	aws secretsmanager create-secret \
	  --name nexus-poc/github-token \
	  --secret-string "$(TOKEN)" \
	  --region $(AWS_REGION) \
	  --profile $(AWS_PROFILE)

# Bootstrap CDK in the account (run once). Uses a custom template to avoid
# creating an ECR repo — ecr:CreateRepository is blocked by org SCP.
bootstrap-cdk:
	cd cdk && npm ci
	cd cdk && npx cdk bootstrap --show-template > bootstrap-template.yml
	python3 scripts/strip-ecr-from-bootstrap.py cdk/bootstrap-template.yml
	cd cdk && npx cdk bootstrap \
	  --template bootstrap-template.yml \
	  --profile $(AWS_PROFILE) \
	  --cloudformation-execution-policies arn:aws:iam::aws:policy/AdministratorAccess

# --- Deployment ---------------------------------------------------------------

deploy:
	cd cdk && npm ci
	cd cdk && npx cdk deploy \
	  --profile $(AWS_PROFILE) \
	  --require-approval never

destroy:
	cd cdk && npx cdk destroy \
	  --profile $(AWS_PROFILE) \
	  --force

# --- Operations ---------------------------------------------------------------

# Open a shell on the EC2 instance (no SSH key needed — uses SSM)
connect:
	aws ssm start-session \
	  --target $(INSTANCE_ID) \
	  --region $(AWS_REGION) \
	  --profile $(AWS_PROFILE)

# Watch the startup init log (run right after deploy)
logs:
	aws ssm start-session \
	  --target $(INSTANCE_ID) \
	  --region $(AWS_REGION) \
	  --profile $(AWS_PROFILE) \
	  --document-name AWS-StartNonInteractiveCommand \
	  --parameters '{"command":["tail -f /var/log/nexus-poc-init.log"]}'

# Pull latest code and restart services (no CDK redeploy needed)
redeploy:
	aws ssm start-session \
	  --target $(INSTANCE_ID) \
	  --region $(AWS_REGION) \
	  --profile $(AWS_PROFILE) \
	  --document-name AWS-StartNonInteractiveCommand \
	  --parameters '{"command":["cd /home/ec2-user/nexus-POC && git pull && docker compose -f docker-compose.yml -f docker-compose.aws.yml up --build -d"]}'

# Print the UI URL
url:
	@echo "http://$(PUBLIC_IP):5173"
