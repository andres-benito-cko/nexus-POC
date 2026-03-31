import * as cdk from 'aws-cdk-lib/core';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as iam from 'aws-cdk-lib/aws-iam';
import * as secretsmanager from 'aws-cdk-lib/aws-secretsmanager';
import { Construct } from 'constructs';

export class NexusPocStack extends cdk.Stack {
  constructor(scope: Construct, id: string, props?: cdk.StackProps) {
    super(scope, id, props);

    // ---------------------------------------------------------------------------
    // VPC — single public subnet, no NAT gateway (POC cost saving ~$35/month)
    // ---------------------------------------------------------------------------
    const vpc = new ec2.Vpc(this, 'Vpc', {
      maxAzs: 1,
      subnetConfiguration: [
        {
          name: 'Public',
          subnetType: ec2.SubnetType.PUBLIC,
          cidrMask: 24,
        },
      ],
      natGateways: 0,
    });

    // ---------------------------------------------------------------------------
    // Security group
    // Port 5173 — Vite dev server (proxies all /api, /ws, /simulate calls)
    // Ports 8080-8083 — direct backend access (optional but useful for testing)
    // No SSH — use SSM Session Manager instead (no key pair needed)
    // ---------------------------------------------------------------------------
    const sg = new ec2.SecurityGroup(this, 'SecurityGroup', {
      vpc,
      description: 'Nexus POC',
      allowAllOutbound: true,
    });
    sg.addIngressRule(ec2.Peer.anyIpv4(), ec2.Port.tcp(5173), 'UI (Vite)');
    sg.addIngressRule(ec2.Peer.anyIpv4(), ec2.Port.tcp(8080), 'Rules Engine');
    sg.addIngressRule(ec2.Peer.anyIpv4(), ec2.Port.tcp(8081), 'LE Simulator');
    sg.addIngressRule(ec2.Peer.anyIpv4(), ec2.Port.tcp(8082), 'Nexus Transformer');
    sg.addIngressRule(ec2.Peer.anyIpv4(), ec2.Port.tcp(8083), 'Nexus API');

    // ---------------------------------------------------------------------------
    // IAM role — SSM access + read the GitHub token secret
    // ---------------------------------------------------------------------------
    const role = new iam.Role(this, 'InstanceRole', {
      assumedBy: new iam.ServicePrincipal('ec2.amazonaws.com'),
      managedPolicies: [
        // Enables SSM Session Manager (connect without SSH/key pair)
        iam.ManagedPolicy.fromAwsManagedPolicyName('AmazonSSMManagedInstanceCore'),
      ],
    });

    // Reference the GitHub token secret (created once manually — see Makefile)
    const githubToken = secretsmanager.Secret.fromSecretNameV2(
      this, 'GithubToken', 'nexus-poc/github-token'
    );
    githubToken.grantRead(role);

    // ---------------------------------------------------------------------------
    // User data — runs once on first boot
    // ---------------------------------------------------------------------------
    const userData = ec2.UserData.forLinux();
    userData.addCommands(
      'set -ex',
      'exec > >(tee /var/log/nexus-poc-init.log) 2>&1',

      '# --- Docker ---',
      'dnf install -y docker git',
      'systemctl start docker',
      'systemctl enable docker',
      'usermod -aG docker ec2-user',

      '# --- Docker Compose v2 ---',
      'mkdir -p /usr/local/lib/docker/cli-plugins',
      'ARCH=$(uname -m)',
      'curl -SL "https://github.com/docker/compose/releases/latest/download/docker-compose-linux-${ARCH}" \\',
      '  -o /usr/local/lib/docker/cli-plugins/docker-compose',
      'chmod +x /usr/local/lib/docker/cli-plugins/docker-compose',

      '# --- Clone repo ---',
      `GITHUB_TOKEN=$(aws secretsmanager get-secret-value \\`,
      `  --secret-id nexus-poc/github-token \\`,
      `  --region ${this.region} \\`,
      `  --query SecretString \\`,
      `  --output text)`,
      'git clone "https://${GITHUB_TOKEN}@github.com/andres-benito-cko/nexus-POC.git" /home/ec2-user/nexus-POC',
      'chown -R ec2-user:ec2-user /home/ec2-user/nexus-POC',

      '# --- Start services ---',
      '# Run as ec2-user so docker socket permissions work correctly',
      'cd /home/ec2-user/nexus-POC',
      'sudo -u ec2-user docker compose -f docker-compose.yml -f docker-compose.aws.yml up --build -d',
    );

    // ---------------------------------------------------------------------------
    // EC2 instance
    // t3.xlarge (4 vCPU / 16GB): headroom for 4 Spring Boot services + Kafka +
    // Zookeeper + PostgreSQL + Docker build cache
    // 50GB root volume: Docker image layers for 4 multi-stage Java builds
    // ---------------------------------------------------------------------------
    const instance = new ec2.Instance(this, 'Instance', {
      vpc,
      vpcSubnets: { subnetType: ec2.SubnetType.PUBLIC },
      instanceType: ec2.InstanceType.of(ec2.InstanceClass.T3, ec2.InstanceSize.XLARGE),
      machineImage: ec2.MachineImage.latestAmazonLinux2023(),
      securityGroup: sg,
      role,
      userData,
      blockDevices: [
        {
          deviceName: '/dev/xvda',
          volume: ec2.BlockDeviceVolume.ebs(50),
        },
      ],
    });

    // Elastic IP — address survives stop/start cycles
    const eip = new ec2.CfnEIP(this, 'EIP', {
      instanceId: instance.instanceId,
    });

    // ---------------------------------------------------------------------------
    // Outputs
    // ---------------------------------------------------------------------------
    new cdk.CfnOutput(this, 'PublicIp', {
      value: eip.ref,
      description: 'EC2 public IP (stable across stop/start)',
    });
    new cdk.CfnOutput(this, 'UiUrl', {
      value: `http://${eip.ref}:5173`,
      description: 'Nexus POC UI',
    });
    new cdk.CfnOutput(this, 'SsmConnect', {
      value: `aws ssm start-session --target ${instance.instanceId} --region ${this.region} --profile cko-financial-infrastructure-tooling-qa-OktaIDP-breakglass`,
      description: 'Connect to the instance (no SSH key needed)',
    });
    new cdk.CfnOutput(this, 'InstanceId', {
      value: instance.instanceId,
    });
    new cdk.CfnOutput(this, 'InitLog', {
      value: 'tail -f /var/log/nexus-poc-init.log',
      description: 'Command to watch startup progress after SSM connect',
    });
  }
}
