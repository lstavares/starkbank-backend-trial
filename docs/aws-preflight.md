# AWS Preflight

Este preflight prepara a stack AWS opcional do Stark Bank Backend Trial para revisão. Nenhum recurso foi criado por este documento e `terraform apply` exige aprovação explícita em uma etapa posterior.

## Ambiente Local

- Região padrão: `us-east-1`.
- Autenticação local: AWS IAM Identity Center com profile `starkbank-trial`.
- `AWS_PROFILE=starkbank-trial` é usado por AWS CLI e Terraform, não pela aplicação Spring.
- Login recomendado quando a sessão expirar:

```bash
aws sso login --profile starkbank-trial
```

- Validação de identidade, sem imprimir credenciais:

```bash
export AWS_PROFILE=starkbank-trial
export AWS_REGION=us-east-1
aws sts get-caller-identity
```

Não use access key ou secret key hardcoded. Não coloque credenciais em arquivos versionados, variáveis de workflow visíveis ou mensagens.

## Recursos Planejados

- VPC demo/econômica sem NAT Gateway.
- Subnets públicas para ALB e ECS Fargate.
- Subnets privadas para RDS PostgreSQL.
- ECR privado.
- ECS Fargate service com `desired_count=0` inicialmente.
- ECS task com `SPRING_PROFILES_ACTIVE=aws`.
- Scheduler da aplicação desligado inicialmente com `INVOICE_SCHEDULER_ENABLED=false`.
- ALB público com HTTP para smoke test técnico.
- HTTPS apenas se `certificate_arn` for informado.
- RDS PostgreSQL single-AZ para demo.
- Secrets Manager para `STARKBANK_PRIVATE_KEY`, `STARKBANK_PROJECT_ID` e senha gerenciada do RDS.
- CloudWatch Logs.
- IAM roles para ECS task execution, ECS task e GitHub Actions OIDC.

## Domínio e HTTPS

Domínio, hosted zone e certificado ACM ainda estão pendentes. O listener HTTP existe apenas para smoke test técnico do ALB.

O webhook público real da Stark deve usar uma URL HTTPS final:

```text
https://<dominio-final>/webhooks/starkbank
```

Antes de ativar a demo, será necessário definir uma destas alternativas:

- informar um `certificate_arn` de ACM já validado;
- criar/validar certificado ACM com DNS editável;
- usar Route 53 futuramente para automatizar validação DNS.

Ngrok é apenas fallback/local para desenvolvimento. Não use ngrok como camada temporária na frente da AWS para a bateria end-to-end, porque o teste precisa validar o domínio final, ACM, ALB HTTPS e ECS.

## Liga/Desliga e Limitações

O default `desired_count=0` evita task rodando automaticamente antes de secrets e HTTPS estarem prontos. Com `desired_count=0`, a aplicação fica indisponível, o webhook não recebe eventos e o scheduler não roda.

Para uma demo ativa, use `desired_count=1` depois das decisões de secrets, domínio e HTTPS. O default da stack AWS mantém `invoice_scheduler_enabled=false`, então o scheduler continua desligado até ser ativado de forma explícita. Não use mais de uma task com `INVOICE_SCHEDULER_ENABLED=true`, porque o scheduler é in-process e pode emitir batches duplicados.

Antes de habilitar o scheduler AWS:

- confirme app AWS saudável e `/health` via HTTPS;
- confirme RDS acessível e Flyway aplicado;
- confirme secrets Stark Bank preenchidos no Secrets Manager;
- aponte o webhook da Stark para `https://<dominio-final>/webhooks/starkbank`;
- pare ou isole o app local/ngrok para evitar processamento duplo;
- mantenha apenas uma task ECS ativa;
- use `INVOICE_SCHEDULER_ENABLED=true` somente no momento aprovado da bateria;
- mantenha `INVOICE_MAX_BATCHES=8`.

Não mantenha duas subscriptions `invoice` ativas na Stark apontando para ambientes diferentes durante a bateria.

Para rollback, publique uma nova task definition com `INVOICE_SCHEDULER_ENABLED=false`, mantenha a task viva para eventos pendentes, escale para `desired_count=0` somente depois que os eventos cessarem e restaure o webhook local/ngrok apenas se precisar voltar ao fluxo local.

## Execução Local da Aplicação

`SPRING_PROFILES_ACTIVE` escolhe a configuração da aplicação Spring Boot.

Scheduler desligado:

```bash
source ~/.starkbank/starkbank-trial.env
SPRING_PROFILES_ACTIVE=local SERVER_PORT=18080 INVOICE_SCHEDULER_ENABLED=false ./mvnw spring-boot:run
```

Scheduler ligado:

```bash
source ~/.starkbank/starkbank-trial.env
SPRING_PROFILES_ACTIVE=local SERVER_PORT=18080 INVOICE_SCHEDULER_ENABLED=true ./mvnw spring-boot:run
```

No ECS/Fargate, use `SPRING_PROFILES_ACTIVE=aws`; Terraform já injeta esse valor via task definition.

## Terraform Local

```bash
export AWS_PROFILE=starkbank-trial
export AWS_REGION=us-east-1

aws sts get-caller-identity
terraform -chdir=infra/terraform plan
```

Não execute `terraform apply` nesta etapa.

## Custos e Riscos

Mesmo com ECS em `0`, ALB, RDS, storage, snapshots, Secrets Manager e CloudWatch podem gerar custo. RDS pode ser parado temporariamente pelo console/CLI, respeitando as limitações de restart automático da AWS.

Antes de qualquer `apply`, revise:

- estimativa de custo;
- teardown;
- domínio/certificado;
- valores dos secrets no Secrets Manager;
- GitHub OIDC e variáveis do repositório;
- se o scheduler deve ficar habilitado.
