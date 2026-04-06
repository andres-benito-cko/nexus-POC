import * as cdk from 'aws-cdk-lib/core';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as iam from 'aws-cdk-lib/aws-iam';
import * as secretsmanager from 'aws-cdk-lib/aws-secretsmanager';
import * as elbv2 from 'aws-cdk-lib/aws-elasticloadbalancingv2';
import * as elbv2targets from 'aws-cdk-lib/aws-elasticloadbalancingv2-targets';
import * as acm from 'aws-cdk-lib/aws-certificatemanager';
import * as acmpca from 'aws-cdk-lib/aws-acmpca';
import * as route53 from 'aws-cdk-lib/aws-route53';
import * as route53targets from 'aws-cdk-lib/aws-route53-targets';
import { Construct } from 'constructs';
import { CfnInstance } from 'aws-cdk-lib/aws-ec2';

// Fixed private IPs within subnet 10.144.177.0/26 (usable: .4–.62)
const INFRA_IP   = '10.144.177.10'; // zookeeper + kafka + postgres
const API_IP     = '10.144.177.20'; // nexus-api + nexus-transformer
const WORKERS_IP = '10.144.177.30'; // le-simulator + rules-engine + ui

// ---------------------------------------------------------------------------
// Networking constants — discovered via AWS CLI, do not change without
// re-checking the account state.
// ---------------------------------------------------------------------------
// Org subordinate private CA (account 471112826941 — shared PKI infra)
const PRIVATE_CA_ARN =
  'arn:aws:acm-pca:eu-west-1:471112826941:certificate-authority/31f7e6a9-1d5f-4776-80b0-0d0d5c3b7be3';
// Private Route53 zone: financial-infrastructure-tooling.qa.ckotech.internal
const HOSTED_ZONE_ID   = 'Z0439559123PXDIXAWMOW';
const HOSTED_ZONE_NAME = 'financial-infrastructure-tooling.qa.ckotech.internal';
// Cloudflare non-prod VPN managed prefix list (owner: 796217803085)
const VPN_PREFIX_LIST_ID = 'pl-0afa2d775d5677fe7';
// UI hostname — full URL: https://nexus-poc.<HOSTED_ZONE_NAME>
const UI_HOSTNAME = 'nexus-poc';

export class NexusPocStack extends cdk.Stack {
  constructor(scope: Construct, id: string, props?: cdk.StackProps) {
    super(scope, id, props);

    // ---------------------------------------------------------------------------
    // VPC — existing VPC from networking-updater (core_network connectivity)
    // ---------------------------------------------------------------------------
    const vpc = ec2.Vpc.fromLookup(this, 'Vpc', {
      vpcId: 'vpc-06b9709ddf6203ec2',
    });

    // eu-west-1a private subnet — used by all three EC2 instances
    const subnet = ec2.Subnet.fromSubnetAttributes(this, 'Subnet', {
      subnetId: 'subnet-0076d4d589390d99d',
      availabilityZone: 'eu-west-1a',
    });

    // eu-west-1b private subnet — required for ALB (needs ≥2 AZs)
    const subnetB = ec2.Subnet.fromSubnetAttributes(this, 'SubnetB', {
      subnetId: 'subnet-0fe25a34500dedb3d',
      availabilityZone: 'eu-west-1b',
    });

    // ---------------------------------------------------------------------------
    // Security group — outbound unrestricted; inbound only from within this SG
    // (all 3 instances share the SG so they can reach each other freely)
    // ---------------------------------------------------------------------------
    const sg = new ec2.SecurityGroup(this, 'SecurityGroup', {
      vpc,
      description: 'Nexus POC',
      allowAllOutbound: true,
    });
    sg.addIngressRule(sg, ec2.Port.allTraffic(), 'Inter-instance traffic within SG');

    // ---------------------------------------------------------------------------
    // IAM role — SSM + read the GitHub token secret
    // ---------------------------------------------------------------------------
    const role = new iam.Role(this, 'InstanceRole', {
      assumedBy: new iam.ServicePrincipal('ec2.amazonaws.com'),
      managedPolicies: [
        iam.ManagedPolicy.fromAwsManagedPolicyName('AmazonSSMManagedInstanceCore'),
      ],
    });
    const githubToken = secretsmanager.Secret.fromSecretNameV2(
      this, 'GithubToken', 'nexus-poc/github-token',
    );
    githubToken.grantRead(role);

    // Bedrock — ai-generator service calls Claude via the Converse API
    role.addToPolicy(new iam.PolicyStatement({
      actions: ['bedrock:InvokeModel', 'bedrock:InvokeModelWithResponseStream'],
      resources: ['arn:aws:bedrock:eu-west-1::foundation-model/anthropic.*'],
    }));

    // ---------------------------------------------------------------------------
    // LaunchTemplate — IMDSv2 required.
    // SCP p-n94gdmkj (qa-Restrictions) denies RunInstances when
    // ec2:MetadataHttpTokens != "required". Must come via LaunchTemplate so the
    // condition key is present in the RunInstances API context.
    // ---------------------------------------------------------------------------
    const launchTemplate = new ec2.CfnLaunchTemplate(this, 'LaunchTemplate', {
      launchTemplateData: {
        metadataOptions: {
          httpTokens: 'required',
          httpPutResponseHopLimit: 1,
          httpEndpoint: 'enabled',
        },
      },
    });

    // Shared block device: 50 GB gp3 encrypted (SCP UnencryptedVolumes)
    const blockDevices: ec2.BlockDevice[] = [{
      deviceName: '/dev/xvda',
      volume: ec2.BlockDeviceVolume.ebs(50, {
        volumeType: ec2.EbsDeviceVolumeType.GP3,
        encrypted: true,
      }),
    }];

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    // Common user-data: Docker, Docker Compose v2, repo clone
    const commonSetup = (logSuffix: string): string[] => [
      'set -ex',
      `exec > >(tee /var/log/nexus-poc-${logSuffix}.log) 2>&1`,
      'dnf install -y docker git python3-pip',
      'systemctl start docker && systemctl enable docker',
      'usermod -aG docker ec2-user',
      // Fetch token BEFORE pip install — pip overwrites distro which breaks awscli
      `GITHUB_TOKEN=$(aws secretsmanager get-secret-value \\`,
      `  --secret-id nexus-poc/github-token \\`,
      `  --region ${this.region} \\`,
      `  --query SecretString \\`,
      `  --output text)`,
      // Install docker-compose v1 via pip into a venv so system awscli is unaffected
      // (release-assets.githubusercontent.com and download.docker.com are blocked
      //  by the Cloud WAN firewall; pypi.org is accessible)
      'python3 -m venv /opt/dc-venv',
      // docker-compose 1.29.2 needs docker<6 + requests-unixsocket for http+docker:// scheme
      // (docker 6.x removed requests-unixsocket; docker 7.x removed ssl_version kwarg)
      '/opt/dc-venv/bin/pip install "docker-compose" "docker>=5.0.3,<6" "requests-unixsocket" "requests<2.28" --quiet',
      'ln -s /opt/dc-venv/bin/docker-compose /usr/local/bin/docker-compose',
      'git clone "https://${GITHUB_TOKEN}@github.com/andres-benito-cko/nexus-POC.git" /home/ec2-user/nexus-POC',
      'chown -R ec2-user:ec2-user /home/ec2-user/nexus-POC',
    ];

    // Write a docker-compose override file (multi-line string → heredoc)
    const writeOverride = (content: string): string => [
      "cat > /home/ec2-user/nexus-POC/docker-compose.override.yml << 'HEREDOC'",
      content.trimEnd(),
      'HEREDOC',
    ].join('\n');

    // Wait for a TCP port to accept connections (uses bash built-in /dev/tcp)
    const waitForPort = (host: string, port: number): string =>
      `until (echo > /dev/tcp/${host}/${port}) 2>/dev/null; do echo "Waiting for ${host}:${port}..."; sleep 10; done`;

    // Attach LaunchTemplate + fixed private IP to a CfnInstance
    const attachLT = (instance: ec2.Instance, privateIp: string): void => {
      const cfn = instance.node.defaultChild as CfnInstance;
      cfn.metadataOptions = undefined; // owned by LaunchTemplate
      cfn.launchTemplate = {
        launchTemplateId: launchTemplate.ref,
        version: launchTemplate.attrLatestVersionNumber,
      };
      cfn.privateIpAddress = privateIp;
    };

    // ---------------------------------------------------------------------------
    // Instance 1 — infra (zookeeper + kafka + postgres)
    // ---------------------------------------------------------------------------
    const infraUD = ec2.UserData.forLinux();
    infraUD.addCommands(
      ...commonSetup('infra'),
      writeOverride(`
version: '3.8'
services:
  zookeeper:
    image: public.ecr.aws/bitnami/zookeeper:3.8
    restart: unless-stopped
    environment:
      ALLOW_ANONYMOUS_LOGIN: "yes"
  kafka:
    image: public.ecr.aws/bitnami/kafka:3.5
    restart: unless-stopped
    environment:
      ALLOW_PLAINTEXT_LISTENER: "yes"
      KAFKA_CFG_ZOOKEEPER_CONNECT: "zookeeper:2181"
      KAFKA_CFG_LISTENER_SECURITY_PROTOCOL_MAP: "INTERNAL:PLAINTEXT,EXTERNAL:PLAINTEXT"
      KAFKA_CFG_LISTENERS: "INTERNAL://0.0.0.0:29092,EXTERNAL://0.0.0.0:9092"
      KAFKA_CFG_ADVERTISED_LISTENERS: "INTERNAL://kafka:29092,EXTERNAL://${INFRA_IP}:9092"
      KAFKA_CFG_INTER_BROKER_LISTENER_NAME: "INTERNAL"
      KAFKA_ADVERTISED_LISTENERS: "INTERNAL://kafka:29092,EXTERNAL://${INFRA_IP}:9092"
  postgres:
    image: public.ecr.aws/docker/library/postgres:15
    restart: unless-stopped
`),
      'cd /home/ec2-user/nexus-POC',
      // Start zookeeper first and wait for it before starting kafka.
      // bitnami/kafka registers an ephemeral ZK node at /brokers/ids/1; if kafka
      // starts before the old session has expired it gets NodeExists and fatally
      // shuts down. Starting zookeeper first and waiting 15 s avoids the race.
      'sudo -u ec2-user docker-compose -f docker-compose.yml -f docker-compose.override.yml up -d zookeeper postgres',
      'sleep 15',
      'sudo -u ec2-user docker-compose -f docker-compose.yml -f docker-compose.override.yml up -d kafka',
    );

    const infraInstance = new ec2.Instance(this, 'InfraInstance', {
      vpc,
      vpcSubnets: { subnets: [subnet] },
      instanceType: ec2.InstanceType.of(ec2.InstanceClass.T3, ec2.InstanceSize.MEDIUM),
      machineImage: ec2.MachineImage.latestAmazonLinux2023(),
      securityGroup: sg,
      role,
      userData: infraUD,
      blockDevices,
    });
    attachLT(infraInstance, INFRA_IP);

    // ---------------------------------------------------------------------------
    // Instance 2 — api layer (nexus-api + nexus-transformer)
    // Both services reference each other by container name → must be co-located.
    // ---------------------------------------------------------------------------
    const apiUD = ec2.UserData.forLinux();
    apiUD.addCommands(
      ...commonSetup('api'),
      writeOverride(`
version: '3.8'
services:
  nexus-api:
    restart: unless-stopped
    environment:
      SPRING_KAFKA_BOOTSTRAP_SERVERS: "${INFRA_IP}:9092"
      SPRING_DATASOURCE_URL: "jdbc:postgresql://${INFRA_IP}:5432/nexus"
  nexus-transformer:
    restart: unless-stopped
    environment:
      SPRING_KAFKA_BOOTSTRAP_SERVERS: "${INFRA_IP}:9092"
  ai-generator:
    restart: unless-stopped
    environment:
      NEXUS_TRANSFORMER_URL: "http://nexus-transformer:8082"
      AWS_REGION: "${this.region}"
`),
      'cd /home/ec2-user/nexus-POC',
      waitForPort(INFRA_IP, 9092),
      'sudo -u ec2-user docker-compose -f docker-compose.yml -f docker-compose.override.yml up --build -d --no-deps nexus-api nexus-transformer ai-generator',
    );

    const apiInstance = new ec2.Instance(this, 'ApiInstance', {
      vpc,
      vpcSubnets: { subnets: [subnet] },
      instanceType: ec2.InstanceType.of(ec2.InstanceClass.T3, ec2.InstanceSize.MEDIUM),
      machineImage: ec2.MachineImage.latestAmazonLinux2023(),
      securityGroup: sg,
      role,
      userData: apiUD,
      blockDevices,
    });
    attachLT(apiInstance, API_IP);

    // ---------------------------------------------------------------------------
    // Instance 3 — workers + UI (le-simulator + rules-engine + ui server)
    // ---------------------------------------------------------------------------
    const workersUD = ec2.UserData.forLinux();
    workersUD.addCommands(
      ...commonSetup('workers'),
      'dnf install -y nodejs',
      writeOverride(`
version: '3.8'
services:
  le-simulator:
    restart: unless-stopped
    environment:
      SPRING_KAFKA_BOOTSTRAP_SERVERS: "${INFRA_IP}:9092"
  rules-engine:
    restart: unless-stopped
    environment:
      SPRING_KAFKA_BOOTSTRAP_SERVERS: "${INFRA_IP}:9092"
      SPRING_DATASOURCE_URL: "jdbc:postgresql://${INFRA_IP}:5432/nexus"
      SPRING_FLYWAY_TABLE: "rules_engine_schema_history"
      SPRING_FLYWAY_BASELINE_ON_MIGRATE: "true"
      SPRING_FLYWAY_BASELINE_VERSION: "0"
`),
      'cd /home/ec2-user/nexus-POC',
      waitForPort(INFRA_IP, 9092),
      'sudo -u ec2-user docker-compose -f docker-compose.yml -f docker-compose.override.yml up --build -d --no-deps le-simulator rules-engine',
      // UI: systemd service so it restarts on crash or instance reboot
      // (npm registry is blocked, so server.cjs uses pre-built dist/ with no npm deps)
      `cat > /etc/systemd/system/nexus-ui.service << 'EOF'
[Unit]
Description=Nexus UI Server
After=network.target

[Service]
Type=simple
User=ec2-user
Environment="VITE_BACKEND_URL=http://${API_IP}:8083"
Environment="VITE_GENERATOR_URL=http://${API_IP}:8084"
Environment="VITE_SIMULATOR_URL=http://${WORKERS_IP}:8081"
Environment="VITE_RULES_ENGINE_URL=http://${WORKERS_IP}:8080"
ExecStart=/usr/bin/node /home/ec2-user/nexus-POC/ui/server.cjs
Restart=always
RestartSec=5
StandardOutput=append:/home/ec2-user/vite.log
StandardError=append:/home/ec2-user/vite.log

[Install]
WantedBy=multi-user.target
EOF`,
      'systemctl daemon-reload',
      'systemctl enable nexus-ui',
      'systemctl start nexus-ui',
    );

    const workersInstance = new ec2.Instance(this, 'WorkersInstance', {
      vpc,
      vpcSubnets: { subnets: [subnet] },
      instanceType: ec2.InstanceType.of(ec2.InstanceClass.T3, ec2.InstanceSize.MEDIUM),
      machineImage: ec2.MachineImage.latestAmazonLinux2023(),
      securityGroup: sg,
      role,
      userData: workersUD,
      blockDevices,
    });
    attachLT(workersInstance, WORKERS_IP);

    // ---------------------------------------------------------------------------
    // Internal ALB — HTTPS only, Cloudflare VPN ingress, forwards to UI on :5173
    //
    // Pattern mirrors engineering-team-metrics (cko-card-processing):
    //   internal ALB + ACM private cert + Route53 alias → *.ckotech.internal
    // ---------------------------------------------------------------------------

    // ALB security group — inbound 443 from Cloudflare non-prod VPN only
    const albSg = new ec2.SecurityGroup(this, 'AlbSg', {
      vpc,
      description: 'Nexus POC ALB - inbound from Cloudflare VPN only',
      allowAllOutbound: true,
    });
    albSg.addIngressRule(
      ec2.Peer.prefixList(VPN_PREFIX_LIST_ID),
      ec2.Port.tcp(443),
      'Cloudflare non-prod VPN',
    );

    // Allow the ALB to reach the workers instance on the UI port
    sg.addIngressRule(albSg, ec2.Port.tcp(5173), 'ALB to UI (port 5173)');

    // Internal ALB across two AZs (eu-west-1a + eu-west-1b)
    const alb = new elbv2.ApplicationLoadBalancer(this, 'Alb', {
      vpc,
      internetFacing: false,
      vpcSubnets: { subnets: [subnet, subnetB] },
      securityGroup: albSg,
    });

    // ACM private certificate issued by the org subordinate CA
    const cert = new acm.PrivateCertificate(this, 'UiCert', {
      domainName: `${UI_HOSTNAME}.${HOSTED_ZONE_NAME}`,
      certificateAuthority: acmpca.CertificateAuthority.fromCertificateAuthorityArn(
        this, 'PrivateCA', PRIVATE_CA_ARN,
      ),
    });

    // Target group → WorkersInstance:5173
    const uiTg = new elbv2.ApplicationTargetGroup(this, 'UiTg', {
      vpc,
      port: 5173,
      protocol: elbv2.ApplicationProtocol.HTTP,
      targetType: elbv2.TargetType.INSTANCE,
      healthCheck: {
        path: '/',
        port: '5173',
        healthyHttpCodes: '200',
        interval: cdk.Duration.seconds(30),
        unhealthyThresholdCount: 3,
      },
      targets: [new elbv2targets.InstanceIdTarget(workersInstance.instanceId, 5173)],
    });

    // HTTPS listener — TLS 1.2+ policy, forwards all traffic to UI target group
    alb.addListener('Https', {
      port: 443,
      protocol: elbv2.ApplicationProtocol.HTTPS,
      sslPolicy: elbv2.SslPolicy.RECOMMENDED_TLS,
      certificates: [cert],
      defaultTargetGroups: [uiTg],
    });

    // Route53 alias record: nexus-poc.<zone> → ALB
    const zone = route53.HostedZone.fromHostedZoneAttributes(this, 'Zone', {
      hostedZoneId: HOSTED_ZONE_ID,
      zoneName: HOSTED_ZONE_NAME,
    });

    new route53.ARecord(this, 'UiDns', {
      zone,
      recordName: UI_HOSTNAME,
      target: route53.RecordTarget.fromAlias(
        new route53targets.LoadBalancerTarget(alb),
      ),
      ttl: cdk.Duration.minutes(1),
    });

    // ---------------------------------------------------------------------------
    // Outputs
    // ---------------------------------------------------------------------------
    const profile = 'cko-financial-infrastructure-tooling-qa-OktaIDP-breakglass';
    const ssmBase = `aws ssm start-session --region ${this.region} --profile ${profile} --target`;

    new cdk.CfnOutput(this, 'UiUrl', {
      value: `https://${UI_HOSTNAME}.${HOSTED_ZONE_NAME}`,
      description: 'UI URL — requires corp VPN or Cloud WAN connectivity',
    });
    new cdk.CfnOutput(this, 'InfraInstanceId',   { value: infraInstance.instanceId });
    new cdk.CfnOutput(this, 'ApiInstanceId',     { value: apiInstance.instanceId });
    new cdk.CfnOutput(this, 'WorkersInstanceId', { value: workersInstance.instanceId });

    new cdk.CfnOutput(this, 'SsmInfra', {
      value: `${ssmBase} ${infraInstance.instanceId}`,
      description: 'Connect to infra (kafka/zookeeper/postgres)',
    });
    new cdk.CfnOutput(this, 'SsmApi', {
      value: `${ssmBase} ${apiInstance.instanceId}`,
      description: 'Connect to api (nexus-api/nexus-transformer)',
    });
    new cdk.CfnOutput(this, 'SsmWorkers', {
      value: `${ssmBase} ${workersInstance.instanceId}`,
      description: 'Connect to workers (le-simulator/rules-engine/ui)',
    });
    new cdk.CfnOutput(this, 'SsmTunnel', {
      value: `${ssmBase} ${workersInstance.instanceId} --document-name AWS-StartPortForwardingSession --parameters portNumber=5173,localPortNumber=5173`,
      description: 'Forward UI (port 5173) to localhost:5173 via SSM (fallback if not on VPN)',
    });
    new cdk.CfnOutput(this, 'InitLogs', {
      value: 'tail -f /var/log/nexus-poc-{infra,api,workers}.log',
      description: 'Watch startup logs after SSM connect',
    });
  }
}
