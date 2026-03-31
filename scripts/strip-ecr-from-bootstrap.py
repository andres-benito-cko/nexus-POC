#!/usr/bin/env python3
"""
Remove ECR-related resources from the CDK bootstrap CloudFormation template.
The org-level SCP denies ecr:CreateRepository in this account.

Usage: python3 scripts/strip-ecr-from-bootstrap.py cdk/bootstrap-template.yml
Edits the file in-place.
"""

import sys
import re

if len(sys.argv) != 2:
    print(f"Usage: {sys.argv[0]} <bootstrap-template.yml>")
    sys.exit(1)

path = sys.argv[1]
with open(path) as f:
    content = f.read()

# Resources to remove (they reference ECR or depend on ECR)
ecr_keys = [
    'ContainerAssetsRepositoryName',
    'HasCustomContainerAssetsRepositoryName',
    'ContainerAssetsRepository',
    'ImagePublishingRoleDefaultPolicy',
    'ImageRepositoryName',
]

removed = []
for key in ecr_keys:
    # Match a top-level YAML key block (key: followed by indented content)
    pattern = rf'(?m)^  {re.escape(key)}:(?:\n(?:    .*|\s*))*'
    before = content
    content = re.sub(pattern, '', content)
    if content != before:
        removed.append(key)

with open(path, 'w') as f:
    f.write(content)

if removed:
    print(f"Removed ECR entries: {', '.join(removed)}")
else:
    print("Warning: no ECR entries found — template may already be stripped or format changed.")
