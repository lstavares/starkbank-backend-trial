output "aws_region" {
  description = "AWS region used by this stack."
  value       = var.aws_region
}

output "ecr_repository_url" {
  description = "ECR repository URL used by the deploy workflow."
  value       = aws_ecr_repository.app.repository_url
}

output "ecs_cluster_name" {
  description = "ECS cluster name."
  value       = aws_ecs_cluster.app.name
}

output "ecs_service_name" {
  description = "ECS service name."
  value       = aws_ecs_service.app.name
}

output "ecs_task_definition_family" {
  description = "ECS task definition family."
  value       = aws_ecs_task_definition.app.family
}

output "ecs_container_name" {
  description = "Container name expected by the deploy workflow."
  value       = var.container_name
}

output "alb_dns_name" {
  description = "Public ALB DNS name. Use HTTP only for technical smoke tests until HTTPS is configured."
  value       = aws_lb.app.dns_name
}

output "http_url" {
  description = "Temporary HTTP smoke test URL."
  value       = "http://${aws_lb.app.dns_name}"
}

output "https_enabled" {
  description = "Whether Terraform created an HTTPS listener."
  value       = local.https_requested
}

output "root_domain_name" {
  description = "Root domain name used by the optional Route 53 hosted zone."
  value       = local.root_domain_name
}

output "route53_zone_id" {
  description = "Public Route 53 hosted zone id for the root domain, when enabled."
  value       = var.route53_zone_enabled ? aws_route53_zone.root[0].zone_id : null
}

output "route53_name_servers" {
  description = "Nameservers to configure at the domain registrar after Route 53 phase 1 apply."
  value       = var.route53_zone_enabled ? aws_route53_zone.root[0].name_servers : []
}

output "app_domain_name" {
  description = "Application domain name served through the ALB when HTTPS is enabled."
  value       = local.app_domain_name
}

output "https_url" {
  description = "Final HTTPS URL for the application domain."
  value       = local.https_url
}

output "webhook_url" {
  description = "Final Stark Bank webhook URL for the application domain."
  value       = local.webhook_url
}

output "acm_certificate_arn" {
  description = "ACM certificate ARN used by the HTTPS listener, when enabled."
  value       = local.https_certificate_arn == "" ? null : local.https_certificate_arn
}

output "github_deploy_role_arn" {
  description = "GitHub Actions role to store in the AWS_ROLE_TO_ASSUME repository variable."
  value       = aws_iam_role.github_deploy.arn
}

output "secret_arns" {
  description = "Secrets consumed by the ECS task."
  value = {
    database_password     = aws_db_instance.postgres.master_user_secret[0].secret_arn
    starkbank_private_key = aws_secretsmanager_secret.starkbank_private_key.arn
    starkbank_project_id  = aws_secretsmanager_secret.starkbank_project_id.arn
  }
  sensitive = true
}
