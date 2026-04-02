import * as cdk from 'aws-cdk-lib/core';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as iam from 'aws-cdk-lib/aws-iam';
import * as secretsmanager from 'aws-cdk-lib/aws-secretsmanager';
import { Construct } from 'constructs';
import { CfnInstance } from 'aws-cdk-lib/aws-ec2';

// Fixed private IPs within subnet 10.144.177.0/26 (usable: .4–.62)
const INFRA_IP   = '10.144.177.10'; // zookeeper + kafka + postgres
const API_IP     = '10.144.177.20'; // nexus-api + nexus-transformer
const WORKERS_IP = '10.144.177.30'; // le-simulator + rules-engine + ui

export class NexusPocStack extends cdk.Stack {
  constructor(scope: Construct, id: string, props?: cdk.StackProps) {
    super(scope, id, props);

    // ---------------------------------------------------------------------------
    // VPC — existing VPC from networking-updater (core_network connectivity)
    // ---------------------------------------------------------------------------
    const vpc = ec2.Vpc.fromLookup(this, 'Vpc', {
      vpcId: 'vpc-06b9709ddf6203ec2',
    });

    const subnet = ec2.Subnet.fromSubnetAttributes(this, 'Subnet', {
      subnetId: 'subnet-0076d4d589390d99d',
      availabilityZone: 'eu-west-1a',
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
    environment:
      ALLOW_ANONYMOUS_LOGIN: "yes"
  kafka:
    image: public.ecr.aws/bitnami/kafka:3.5
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
`),
      'cd /home/ec2-user/nexus-POC',
      'sudo -u ec2-user docker-compose -f docker-compose.yml -f docker-compose.override.yml up -d zookeeper kafka postgres',
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
    environment:
      SPRING_KAFKA_BOOTSTRAP_SERVERS: "${INFRA_IP}:9092"
      SPRING_DATASOURCE_URL: "jdbc:postgresql://${INFRA_IP}:5432/nexus"
  nexus-transformer:
    environment:
      SPRING_KAFKA_BOOTSTRAP_SERVERS: "${INFRA_IP}:9092"
`),
      'cd /home/ec2-user/nexus-POC',
      waitForPort(INFRA_IP, 9092),
      'sudo -u ec2-user docker-compose -f docker-compose.yml -f docker-compose.override.yml up --build -d --no-deps nexus-api nexus-transformer',
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
    // Instance 3 — workers + UI (le-simulator + rules-engine + npm dev)
    // ---------------------------------------------------------------------------
    const workersUD = ec2.UserData.forLinux();
    workersUD.addCommands(
      ...commonSetup('workers'),
      'dnf install -y nodejs',
      writeOverride(`
version: '3.8'
services:
  le-simulator:
    environment:
      SPRING_KAFKA_BOOTSTRAP_SERVERS: "${INFRA_IP}:9092"
  rules-engine:
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
      // UI: serve pre-built dist/ via zero-dep Node.js proxy server (npm registry is blocked)
      `sudo -u ec2-user bash -c 'VITE_BACKEND_URL=http://${API_IP}:8083 VITE_SIMULATOR_URL=http://${WORKERS_IP}:8081 nohup node /home/ec2-user/nexus-POC/ui/server.cjs > /home/ec2-user/vite.log 2>&1 &'`,
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
    // Outputs
    // ---------------------------------------------------------------------------
    const profile = 'cko-financial-infrastructure-tooling-qa-OktaIDP-breakglass';
    const ssmBase = `aws ssm start-session --region ${this.region} --profile ${profile} --target`;

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
      description: 'Forward UI (port 5173) to localhost:5173 via SSM',
    });
    new cdk.CfnOutput(this, 'InitLogs', {
      value: 'tail -f /var/log/nexus-poc-{infra,api,workers}.log',
      description: 'Watch startup logs after SSM connect',
    });
  }
}
