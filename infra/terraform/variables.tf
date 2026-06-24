variable "aws_region" {
  description = "AWS region used for all resources."
  type        = string
  default     = "us-east-1"
}

variable "name_prefix" {
  description = "Prefix used to name AWS resources."
  type        = string
  default     = "starkbank-trial"
}

variable "tags" {
  description = "Additional tags applied to all resources."
  type        = map(string)
  default     = {}
}

variable "vpc_cidr_block" {
  description = "CIDR block for the demo VPC."
  type        = string
  default     = "10.42.0.0/16"
}

variable "availability_zone_count" {
  description = "Number of availability zones used by the demo VPC."
  type        = number
  default     = 2

  validation {
    condition     = var.availability_zone_count >= 2 && var.availability_zone_count <= 3
    error_message = "availability_zone_count must be between 2 and 3."
  }
}

variable "allowed_http_cidr_blocks" {
  description = "CIDR blocks allowed to reach the public ALB over HTTP for smoke tests."
  type        = list(string)
  default     = ["0.0.0.0/0"]
}

variable "allowed_https_cidr_blocks" {
  description = "CIDR blocks allowed to reach the public ALB over HTTPS when a certificate is configured."
  type        = list(string)
  default     = ["0.0.0.0/0"]
}

variable "certificate_arn" {
  description = "Optional ACM certificate ARN. When empty, HTTPS listener is not created."
  type        = string
  default     = ""
}

variable "root_domain_name" {
  description = "Root domain name managed by the optional public Route 53 hosted zone."
  type        = string
  default     = "tavares-dev.com.br"
}

variable "route53_zone_enabled" {
  description = "Whether Terraform should create the public Route 53 hosted zone for the root domain."
  type        = bool
  default     = false
}

variable "preserve_root_email_block_records" {
  description = "Whether Terraform should preserve the current root null MX and SPF deny-all records in Route 53."
  type        = bool
  default     = true
}

variable "managed_https_enabled" {
  description = "Reserved for the future ACM/HTTPS phase. Keep false during Route 53 phase 1."
  type        = bool
  default     = false
}

variable "container_name" {
  description = "Container name used by the ECS task definition and GitHub Actions deployment."
  type        = string
  default     = "starkbank-backend-trial"
}

variable "container_port" {
  description = "Internal HTTP port exposed by the Spring Boot container."
  type        = number
  default     = 8080
}

variable "image_tag" {
  description = "Initial image tag used by Terraform for the ECS task definition. GitHub Actions updates this later."
  type        = string
  default     = "latest"

  validation {
    condition     = trimspace(var.image_tag) != "" && lower(trimspace(var.image_tag)) != "placeholder"
    error_message = "image_tag must not be empty or placeholder."
  }
}

variable "spring_profiles_active" {
  description = "Spring profile passed to the ECS task runtime."
  type        = string
  default     = "aws"
}

variable "desired_count" {
  description = "Initial ECS desired count. Keep 0 until secrets, domain, and certificate are ready."
  type        = number
  default     = 0

  validation {
    condition     = contains([0, 1], var.desired_count)
    error_message = "desired_count must be 0 or 1 for this demo deployment."
  }
}

variable "task_cpu" {
  description = "Fargate task CPU units."
  type        = number
  default     = 512
}

variable "task_memory" {
  description = "Fargate task memory in MiB."
  type        = number
  default     = 1024
}

variable "log_retention_days" {
  description = "CloudWatch Logs retention in days."
  type        = number
  default     = 14
}

variable "database_name" {
  description = "Initial PostgreSQL database name."
  type        = string
  default     = "starkbank_trial"
}

variable "database_username" {
  description = "PostgreSQL master username."
  type        = string
  default     = "starkbank"
}

variable "database_port" {
  description = "PostgreSQL port."
  type        = number
  default     = 5432
}

variable "database_instance_class" {
  description = "RDS instance class for the demo database."
  type        = string
  default     = "db.t4g.micro"
}

variable "database_allocated_storage" {
  description = "Initial RDS allocated storage in GiB."
  type        = number
  default     = 20
}

variable "database_engine_version" {
  description = "Optional PostgreSQL engine version. Null lets AWS choose the current default."
  type        = string
  default     = null
}

variable "database_backup_retention_days" {
  description = "RDS automated backup retention in days."
  type        = number
  default     = 1
}

variable "database_skip_final_snapshot" {
  description = "Whether to skip the final snapshot when destroying the demo database."
  type        = bool
  default     = true
}

variable "starkbank_environment" {
  description = "Stark Bank environment passed to the application."
  type        = string
  default     = "sandbox"

  validation {
    condition     = contains(["sandbox", "production"], var.starkbank_environment)
    error_message = "starkbank_environment must be sandbox or production."
  }
}

variable "invoice_scheduler_enabled" {
  description = "Whether the invoice scheduler starts inside the ECS task."
  type        = bool
  default     = false
}

variable "invoice_interval_hours" {
  description = "Invoice scheduler interval in hours."
  type        = number
  default     = 3
}

variable "invoice_max_batches" {
  description = "Maximum scheduled invoice batches."
  type        = number
  default     = 8
}

variable "secrets_recovery_window_days" {
  description = "Recovery window for manually managed Secrets Manager secrets."
  type        = number
  default     = 7
}

variable "github_repository" {
  description = "GitHub repository allowed to assume the OIDC deploy role, in owner/name format."
  type        = string
  default     = "lstavares/starkbank-backend-trial"
}

variable "github_oidc_subjects" {
  description = "Allowed GitHub OIDC subject claims for the deploy role."
  type        = list(string)
  default     = ["repo:lstavares/starkbank-backend-trial:*"]
}

variable "create_github_oidc_provider" {
  description = "Whether Terraform should create the GitHub Actions OIDC provider."
  type        = bool
  default     = true
}

variable "github_oidc_provider_arn" {
  description = "Existing GitHub Actions OIDC provider ARN, required when create_github_oidc_provider is false."
  type        = string
  default     = ""
}
