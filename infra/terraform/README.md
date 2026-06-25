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
- `image_tag = "latest"`, so the initial ECS task definition points to an explicit bootstrap image tag instead of a placeholder.
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

## Image Tags and Deploy Workflow

Terraform uses `image_tag` only for the initial ECS task definition. The default is `latest`, and validation rejects empty values or `placeholder`.

The manual GitHub Actions deploy workflow pushes two ECR tags:

- `${{ github.sha }}` for the immutable image deployed to ECS.
- `latest` for the explicit Terraform bootstrap image tag.

The deploy workflow keeps `INVOICE_SCHEDULER_ENABLED=false` by default. Setting it to `true` emits invoices in the Stark Bank Sandbox and must only happen after HTTPS webhook cutover is complete.

## Webhook Cutover

The final Stark Bank webhook endpoint for AWS must use HTTPS:

```text
https://starkbank-trial.tavares-dev.com.br/webhooks/starkbank
```

The ALB HTTP listener is only for smoke tests such as `/health`. Ngrok remains a local/fallback tool and should not sit in front of AWS for the end-to-end scheduler battery.

Before enabling the AWS scheduler, confirm HTTPS, Secrets Manager values, RDS/Flyway, one active ECS task, and the Stark webhook pointing to the AWS endpoint. Do not keep local/ngrok and AWS processing the same `invoice` webhooks at the same time.

Rollback starts by deploying a new task definition with `INVOICE_SCHEDULER_ENABLED=false`. Keep the task running for pending webhook events, scale to `desired_count=0` only after events stop, and restore the Stark webhook to local/ngrok only if the local fallback is needed.

## Route 53 Phase 1

Set `route53_zone_enabled=true` to create only the public Route 53 hosted zone for `tavares-dev.com.br`.

Phase 1 also preserves the currently identified root records when `preserve_root_email_block_records=true`:

- null MX;
- TXT SPF `v=spf1 -all`.

It does not create ACM certificates, ACM validation records, HTTPS listeners, or application alias records.

After the approved Phase 1 apply, copy the 4 values from the `route53_name_servers` output and configure them in the domain registrar panel for `tavares-dev.com.br`.

Validate delegation with:

```bash
dig +short NS tavares-dev.com.br
```

Only start Phase 2 after those nameservers are visible publicly.

## HTTPS Phase 2

Set these variables after Route 53 nameservers have propagated:

```hcl
route53_zone_enabled  = true
managed_https_enabled = true
app_domain_name       = "starkbank-trial.tavares-dev.com.br"
redirect_http_to_https = true
```

Phase 2 creates:

- ACM certificate for `starkbank-trial.tavares-dev.com.br`;
- Route 53 ACM validation records;
- ACM certificate validation;
- ALB HTTPS listener on 443;
- ALB security group ingress for 443;
- Route 53 alias `A` record from the app domain to the ALB;
- HTTP 80 to HTTPS 443 redirect when `redirect_http_to_https=true`.

It does not change the Stark webhook, scheduler settings, ECS desired count, Java code, or secrets.

Validate after apply:

```bash
dig +short starkbank-trial.tavares-dev.com.br
curl -i https://starkbank-trial.tavares-dev.com.br/health
curl -I http://starkbank-trial.tavares-dev.com.br/health
```

## Variables

Copy `terraform.tfvars.example` to a local ignored `terraform.tfvars` only if you need local overrides. Keep real secrets out of tfvars. Fill Secrets Manager values after apply through AWS tooling.

Before running `terraform plan`, replace `SEU_IP_PUBLICO/32` with a real public CIDR such as your current IP plus `/32`.

Example local `terraform.tfvars` shape:

```hcl
aws_region = "us-east-1"
name_prefix = "starkbank-trial"

desired_count = 0
image_tag = "latest"

spring_profiles_active = "aws"
invoice_scheduler_enabled = false
invoice_interval_hours = 3
invoice_max_batches = 8

certificate_arn = ""

root_domain_name = "tavares-dev.com.br"
app_domain_name = "starkbank-trial.tavares-dev.com.br"
route53_zone_enabled = false
preserve_root_email_block_records = true
managed_https_enabled = false
redirect_http_to_https = true

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
