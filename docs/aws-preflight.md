# AWS Preflight

Este preflight documenta os cuidados de revisão da stack AWS do Stark Bank Backend Trial. A arquitetura executada e as evidências operacionais estão em [aws-architecture.md](aws-architecture.md). Nenhum recurso é criado por este documento e `terraform apply` exige aprovação explícita.

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

## Recursos da Stack

- VPC demo/econômica sem NAT Gateway.
- Subnets públicas para ALB e ECS Fargate.
- Subnets privadas para RDS PostgreSQL.
- ECR privado.
- ECS Fargate service com escala manual entre `desired_count=0` e `desired_count=1`.
- ECS task com `SPRING_PROFILES_ACTIVE=aws`.
- Scheduler da aplicação controlado por `INVOICE_SCHEDULER_ENABLED`.
- ALB público com HTTPS e HTTP redirecionando para HTTPS.
- Route 53 com hosted zone de `tavares-dev.com.br`.
- ACM com certificado TLS para `starkbank-trial.tavares-dev.com.br`.
- RDS PostgreSQL single-AZ para demo.
- Secrets Manager para `STARKBANK_PRIVATE_KEY`, `STARKBANK_PROJECT_ID` e senha gerenciada do RDS.
- CloudWatch Logs.
- IAM roles para ECS task execution, ECS task e GitHub Actions OIDC.

## Domínio e HTTPS

O domínio `starkbank-trial.tavares-dev.com.br`, a hosted zone Route 53, o certificado ACM, o listener HTTPS e o redirect HTTP para HTTPS foram configurados na versão AWS validada.

O webhook público real da Stark usa a URL HTTPS final:

```text
https://starkbank-trial.tavares-dev.com.br/webhooks/starkbank
```

O DNS foi preparado em duas fases:

- Fase 1: criar somente a hosted zone pública Route 53 para `tavares-dev.com.br`, preservar MX nulo e TXT SPF `v=spf1 -all`, e copiar o output `route53_name_servers` para o painel do registrador.
- Fase 2: após propagação dos nameservers, criar ACM, validação DNS, listener HTTPS, liberação 443 no security group, alias do subdomínio e redirect HTTP para HTTPS quando `redirect_http_to_https=true`.

Após o apply da Fase 1, valide a propagação com:

```bash
dig +short NS tavares-dev.com.br
```

Com `managed_https_enabled=true`, a stack usa o Route 53 criado na Fase 1 para validar ACM e servir:

```text
https://starkbank-trial.tavares-dev.com.br
```

O cutover validado usa o Project Stark AWS separado e o webhook ngrok removido desse Project para evitar duplicidade.

Ngrok é apenas fallback/local para desenvolvimento. Não use ngrok como camada temporária na frente da AWS para a bateria end-to-end, porque o teste precisa validar o domínio final, ACM, ALB HTTPS e ECS.

## Liga/Desliga e Limitações

O default `desired_count=0` evita task rodando automaticamente fora da janela aprovada. Com `desired_count=0`, a aplicação fica indisponível, o webhook não recebe eventos e o scheduler não roda.

Para uma demo ativa, use `desired_count=1` depois de confirmar secrets, domínio e HTTPS. O default da stack AWS mantém `invoice_scheduler_enabled=false`, então o scheduler continua desligado até ser ativado de forma explícita. Não use mais de uma task com `INVOICE_SCHEDULER_ENABLED=true`, porque o scheduler é in-process e pode emitir batches duplicados.

Antes de habilitar o scheduler AWS:

- confirme app AWS saudável e `/health` em `https://starkbank-trial.tavares-dev.com.br/health`;
- confirme RDS acessível e Flyway aplicado;
- confirme secrets Stark Bank preenchidos no Secrets Manager;
- confirme o webhook da Stark apontando para `https://starkbank-trial.tavares-dev.com.br/webhooks/starkbank`;
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
