locals {
  azs         = slice(data.aws_availability_zones.available.names, 0, var.availability_zone_count)
  name_prefix = var.name_prefix

  root_domain_name = trimsuffix(var.root_domain_name, ".")
  app_domain_name  = trimsuffix(var.app_domain_name, ".")

  database_url = "jdbc:postgresql://${aws_db_instance.postgres.address}:${var.database_port}/${var.database_name}"

  github_oidc_provider_arn = var.create_github_oidc_provider ? aws_iam_openid_connect_provider.github[0].arn : var.github_oidc_provider_arn

  managed_https_active = var.route53_zone_enabled && var.managed_https_enabled
  https_requested      = local.managed_https_active || var.certificate_arn != ""
  https_certificate_arn = local.managed_https_active ? (
    aws_acm_certificate_validation.app[0].certificate_arn
  ) : var.certificate_arn

  https_url   = "https://${local.app_domain_name}"
  webhook_url = "${local.https_url}/webhooks/starkbank"

  common_tags = merge(
    {
      Application = "starkbank-backend-trial"
      Environment = "demo"
      ManagedBy   = "terraform"
    },
    var.tags
  )
}
