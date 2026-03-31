#!/usr/bin/env node
import * as cdk from 'aws-cdk-lib/core';
import { NexusPocStack } from '../lib/nexus-poc-stack';

const app = new cdk.App();
new NexusPocStack(app, 'NexusPocStack', {
  env: {
    account: process.env.CDK_DEFAULT_ACCOUNT,
    region: 'eu-west-1',
  },
});
