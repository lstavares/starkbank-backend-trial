locals {
  azs         = slice(data.aws_availability_zones.available.names, 0, var.availability_zone_count)
  name_prefix = var.name_prefix

  database_url = "jdbc:postgresql://${aws_db_instance.postgres.address}:${var.database_port}/${var.database_name}"

  github_oidc_provider_arn = var.create_github_oidc_provider ? aws_iam_openid_connect_provider.github[0].arn : var.github_oidc_provider_arn

  common_tags = merge(
    {
      Application = "starkbank-backend-trial"
      Environment = "demo"
      ManagedBy   = "terraform"
    },
    var.tags
  )
}
