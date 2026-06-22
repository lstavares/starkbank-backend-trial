resource "aws_secretsmanager_secret" "starkbank_private_key" {
  name                    = "${local.name_prefix}/starkbank/private-key"
  description             = "Stark Bank private key content. Fill manually after Terraform apply."
  recovery_window_in_days = var.secrets_recovery_window_days

  tags = {
    Name = "${local.name_prefix}-starkbank-private-key"
  }
}

resource "aws_secretsmanager_secret" "starkbank_project_id" {
  name                    = "${local.name_prefix}/starkbank/project-id"
  description             = "Stark Bank project id. Fill manually after Terraform apply."
  recovery_window_in_days = var.secrets_recovery_window_days

  tags = {
    Name = "${local.name_prefix}-starkbank-project-id"
  }
}
