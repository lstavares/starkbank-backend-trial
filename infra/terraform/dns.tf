resource "aws_route53_zone" "root" {
  count = var.route53_zone_enabled ? 1 : 0

  name    = local.root_domain_name
  comment = "Public hosted zone for ${local.root_domain_name}."

  tags = {
    Name = "${local.name_prefix}-root-zone"
  }
}

resource "aws_route53_record" "root_null_mx" {
  count = var.route53_zone_enabled && var.preserve_root_email_block_records ? 1 : 0

  zone_id = aws_route53_zone.root[0].zone_id
  name    = local.root_domain_name
  type    = "MX"
  ttl     = 300
  records = ["0 ."]
}

resource "aws_route53_record" "root_spf_deny_all" {
  count = var.route53_zone_enabled && var.preserve_root_email_block_records ? 1 : 0

  zone_id = aws_route53_zone.root[0].zone_id
  name    = local.root_domain_name
  type    = "TXT"
  ttl     = 300
  records = ["v=spf1 -all"]
}

resource "aws_acm_certificate" "app" {
  count = local.managed_https_active ? 1 : 0

  domain_name       = local.app_domain_name
  validation_method = "DNS"

  lifecycle {
    create_before_destroy = true
  }

  tags = {
    Name = "${local.name_prefix}-app-certificate"
  }
}

resource "aws_route53_record" "app_certificate_validation" {
  for_each = local.managed_https_active ? {
    for option in aws_acm_certificate.app[0].domain_validation_options : option.domain_name => {
      name   = option.resource_record_name
      record = option.resource_record_value
      type   = option.resource_record_type
    }
  } : {}

  allow_overwrite = true
  name            = each.value.name
  records         = [each.value.record]
  ttl             = 300
  type            = each.value.type
  zone_id         = aws_route53_zone.root[0].zone_id
}

resource "aws_acm_certificate_validation" "app" {
  count = local.managed_https_active ? 1 : 0

  certificate_arn         = aws_acm_certificate.app[0].arn
  validation_record_fqdns = [for record in aws_route53_record.app_certificate_validation : record.fqdn]
}

resource "aws_route53_record" "app_alias" {
  count = local.managed_https_active ? 1 : 0

  zone_id = aws_route53_zone.root[0].zone_id
  name    = local.app_domain_name
  type    = "A"

  alias {
    name                   = aws_lb.app.dns_name
    zone_id                = aws_lb.app.zone_id
    evaluate_target_health = true
  }
}
