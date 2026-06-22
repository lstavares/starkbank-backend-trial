# AWS Preflight

Este preflight prepara a stack AWS opcional do Stark Bank Backend Trial para revisão. Nenhum recurso foi criado por este documento e `terraform apply` exige aprovação explícita em uma etapa posterior.

## Ambiente Local

- Região padrão: `us-east-1`.
- Autenticação local: AWS IAM Identity Center com profile `starkbank-trial`.
- Login recomendado quando a sessão expirar:

```bash
aws sso login --profile starkbank-trial
```

- Validação de identidade, sem imprimir credenciais:

```bash
aws sts get-caller-identity --profile starkbank-trial
```

Não use access key ou secret key hardcoded. Não coloque credenciais em arquivos versionados, variáveis de workflow visíveis ou mensagens.

## Recursos Planejados

- VPC demo/econômica sem NAT Gateway.
- Subnets públicas para ALB e ECS Fargate.
- Subnets privadas para RDS PostgreSQL.
- ECR privado.
- ECS Fargate service com `desired_count=0` inicialmente.
- ALB público com HTTP para smoke test técnico.
- HTTPS apenas se `certificate_arn` for informado.
- RDS PostgreSQL single-AZ para demo.
- Secrets Manager para `STARKBANK_PRIVATE_KEY`, `STARKBANK_PROJECT_ID` e senha gerenciada do RDS.
- CloudWatch Logs.
- IAM roles para ECS task execution, ECS task e GitHub Actions OIDC.

## Domínio e HTTPS

Domínio, hosted zone e certificado ACM ainda estão pendentes. O listener HTTP existe apenas para smoke test técnico do ALB.

O webhook público real da Stark deve usar uma URL HTTPS final. Antes de ativar a demo, será necessário definir uma destas alternativas:

- informar um `certificate_arn` de ACM já validado;
- criar/validar certificado ACM com DNS editável;
- usar Route 53 futuramente para automatizar validação DNS.

## Liga/Desliga e Limitações

O default `desired_count=0` evita task rodando automaticamente antes de secrets e HTTPS estarem prontos. Com `desired_count=0`, a aplicação fica indisponível, o webhook não recebe eventos e o scheduler não roda.

Para uma demo ativa, use `desired_count=1`. Não use mais de uma task com `INVOICE_SCHEDULER_ENABLED=true`, porque o scheduler é in-process e pode emitir batches duplicados.

## Custos e Riscos

Mesmo com ECS em `0`, ALB, RDS, storage, snapshots, Secrets Manager e CloudWatch podem gerar custo. RDS pode ser parado temporariamente pelo console/CLI, respeitando as limitações de restart automático da AWS.

Antes de qualquer `apply`, revise:

- estimativa de custo;
- teardown;
- domínio/certificado;
- valores dos secrets no Secrets Manager;
- GitHub OIDC e variáveis do repositório;
- se o scheduler deve ficar habilitado.
