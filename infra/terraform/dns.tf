resource "aws_route53_zone" "root" {
  count = var.route53_zone_enabled ? 1 : 0

  name    = trimsuffix(var.root_domain_name, ".")
  comment = "Public hosted zone for ${trimsuffix(var.root_domain_name, ".")}."

  tags = {
    Name = "${local.name_prefix}-root-zone"
  }
}

resource "aws_route53_record" "root_null_mx" {
  count = var.route53_zone_enabled && var.preserve_root_email_block_records ? 1 : 0

  zone_id = aws_route53_zone.root[0].zone_id
  name    = trimsuffix(var.root_domain_name, ".")
  type    = "MX"
  ttl     = 300
  records = ["0 ."]
}

resource "aws_route53_record" "root_spf_deny_all" {
  count = var.route53_zone_enabled && var.preserve_root_email_block_records ? 1 : 0

  zone_id = aws_route53_zone.root[0].zone_id
  name    = trimsuffix(var.root_domain_name, ".")
  type    = "TXT"
  ttl     = 300
  records = ["v=spf1 -all"]
}
