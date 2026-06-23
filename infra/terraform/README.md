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
- `spring_profiles_active = "aws"`, so the ECS task loads `application-aws.yml`.
- `invoice_scheduler_enabled = false`, so the AWS task starts with the in-process scheduler disabled.
- No NAT Gateway, to reduce demo cost.
- No hardcoded AWS account id, real ARN, domain, certificate, password, Stark Bank project id, or private key.
- HTTPS is created only when `certificate_arn` is set.
- HTTP is only for ALB smoke tests. Stark Bank webhooks must use HTTPS.

## AWS CLI Profile vs Spring Profile

`AWS_PROFILE=starkbank-trial` is only for Terraform and local AWS CLI authentication. It chooses the AWS identity used to review or provision infrastructure.

`SPRING_PROFILES_ACTIVE=local` or `SPRING_PROFILES_ACTIVE=aws` is only for the Spring Boot runtime. It chooses which application configuration file Spring loads.

Use the AWS CLI profile for local Terraform commands:

```bash
export AWS_PROFILE=starkbank-trial
export AWS_REGION=us-east-1

aws sts get-caller-identity
terraform -chdir=infra/terraform plan
```

Do not commit `tfplan`, `plan.txt`, `.terraform/`, `terraform.tfstate*`, or real `*.tfvars` files.

## Local Application Runtime

Run the application locally with the Spring `local` profile. Load your private local environment file first, but keep it outside Git.

Scheduler disabled:

```bash
source ~/.starkbank/starkbank-trial.env
SPRING_PROFILES_ACTIVE=local SERVER_PORT=18080 INVOICE_SCHEDULER_ENABLED=false ./mvnw spring-boot:run
```

Scheduler enabled:

```bash
source ~/.starkbank/starkbank-trial.env
SPRING_PROFILES_ACTIVE=local SERVER_PORT=18080 INVOICE_SCHEDULER_ENABLED=true ./mvnw spring-boot:run
```

## ECS Application Runtime

Terraform passes these runtime variables to the ECS task definition:

```text
SPRING_PROFILES_ACTIVE=aws
INVOICE_SCHEDULER_ENABLED=false
```

The `aws` Spring profile expects database and Stark Bank sensitive values from environment variables or ECS secrets. It does not contain real secrets.

## Variables

Copy `terraform.tfvars.example` to a local ignored `terraform.tfvars` only if you need local overrides. Keep real secrets out of tfvars. Fill Secrets Manager values after apply through AWS tooling.

Before running `terraform plan`, replace `SEU_IP_PUBLICO/32` with a real public CIDR such as your current IP plus `/32`.

Example local `terraform.tfvars` shape:

```hcl
aws_region = "us-east-1"
name_prefix = "starkbank-trial"

desired_count = 0

spring_profiles_active = "aws"
invoice_scheduler_enabled = false
invoice_interval_hours = 3
invoice_max_batches = 8

certificate_arn = ""

# Replace SEU_IP_PUBLICO/32 before running terraform plan.
allowed_http_cidr_blocks = ["SEU_IP_PUBLICO/32"]
allowed_https_cidr_blocks = ["0.0.0.0/0"]

starkbank_environment = "sandbox"
database_instance_class = "db.t4g.micro"

tags = {
  Project     = "starkbank-backend-trial"
  Purpose     = "demo"
  Environment = "sandbox"
  Owner       = "leandro"
}
```
