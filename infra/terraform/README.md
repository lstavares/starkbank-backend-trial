# AWS Terraform Stack

This Terraform stack prepares an optional AWS demo deployment for the Stark Bank Backend Trial. It is intentionally safe to review before any real resource creation: run `terraform plan`, review the output, and only run `terraform apply` after explicit approval.

## What It Creates

- VPC with public subnets for the ALB and ECS tasks.
- Private subnets for RDS PostgreSQL.
- ECR repository for the application image.
- ECS Fargate cluster, task definition, and service.
- Public ALB, HTTP listener for smoke tests, and optional HTTPS listener.
- RDS PostgreSQL single-AZ demo database.
- Secrets Manager placeholders for Stark Bank credentials and RDS managed password.
- CloudWatch log group.
- IAM roles for ECS and GitHub Actions OIDC.

## Safety Defaults

- `desired_count = 0`, so no ECS task starts immediately.
- No NAT Gateway, to reduce demo cost.
- No hardcoded AWS account id, real ARN, domain, certificate, password, Stark Bank project id, or private key.
- HTTPS is created only when `certificate_arn` is set.
- HTTP is only for ALB smoke tests. Stark Bank webhooks must use HTTPS.

## Local Commands

Use the local SSO profile:

```bash
aws sso login --profile starkbank-trial
AWS_PROFILE=starkbank-trial AWS_REGION=us-east-1 terraform -chdir=infra/terraform init
AWS_PROFILE=starkbank-trial AWS_REGION=us-east-1 terraform -chdir=infra/terraform validate
AWS_PROFILE=starkbank-trial AWS_REGION=us-east-1 terraform -chdir=infra/terraform plan -out=tfplan
terraform -chdir=infra/terraform show -no-color tfplan > plan.txt
```

Do not commit `tfplan`, `plan.txt`, `.terraform/`, `terraform.tfstate*`, or real `*.tfvars` files.

## Variables

Copy `terraform.tfvars.example` to a local ignored `terraform.tfvars` only if you need local overrides. Keep real secrets out of tfvars. Fill Secrets Manager values after apply through AWS tooling.
