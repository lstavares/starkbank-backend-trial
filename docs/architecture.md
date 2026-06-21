# Arquitetura

Este documento detalha a organização técnica do Stark Bank Backend Trial. Ele descreve a arquitetura atual, os fluxos principais, a integração com a Stark Bank Java SDK, decisões de idempotência, persistência e uma proposta de evolução para cloud deployment.

## Visão Geral

```mermaid
flowchart LR
    Admin["Cliente administrativo"] --> Controllers["Controllers HTTP"]
    Stark["Stark Bank Sandbox"] --> Tunnel["ngrok / URL pública"]
    Tunnel --> Webhook["WebhookController"]
    Scheduler["InvoiceIssuingScheduler"] --> InvoiceApp["Invoice application services"]
    Controllers --> InvoiceApp
    Webhook --> WebhookApp["Webhook application services"]
    InvoiceApp --> StarkSDK["Stark Bank SDK adapter"]
    WebhookApp --> StarkSDK
    StarkSDK --> Stark
    InvoiceApp --> Repositories["Spring Data repositories"]
    WebhookApp --> Repositories
    Repositories --> Postgres["PostgreSQL"]
```

A aplicação segue uma separação simples em camadas:

- Interface HTTP: entrada de requests administrativos e webhook.
- Application services: regras de orquestração de Invoice, Webhook e Transfer.
- Infrastructure: persistência JPA e adaptação da Stark Bank Java SDK.
- Config: propriedades externas e scheduling.

## Responsabilidades por Pacote

| Pacote | Responsabilidade |
| --- | --- |
| `interfaces.web` | Controllers HTTP, status codes e envelopes de resposta. |
| `application.invoice` | Emissão manual/agendada, geração aleatória de Invoices e controle de batches. |
| `application.webhook` | Validação funcional de eventos, idempotência, resolução de valores e criação de Transfer. |
| `infrastructure.starkbank` | Wrapper da Stark Bank Java SDK, criação de Project, parsing de Event, criação de Invoice e Transfer. |
| `infrastructure.persistence` | Entidades JPA, enums e repositories. |
| `config` | `@ConfigurationProperties`, scheduler e configuração da SDK. |

## Fluxo de Emissão de Invoices

```mermaid
sequenceDiagram
    participant Client as Cliente HTTP ou Scheduler
    participant Service as InvoiceService
    participant Factory as RandomInvoiceFactory
    participant SDK as StarkBankInvoiceClient
    participant Stark as Stark Bank Sandbox
    participant DB as PostgreSQL

    Client->>Service: issueManualBatch() ou issueScheduledBatchIfAllowed()
    Service->>DB: cria invoice_batches STARTED
    Service->>Factory: generate(batchId, triggerSource)
    Factory-->>Service: 8 a 12 CreateInvoiceRequest
    Service->>SDK: createInvoices(requests)
    SDK->>Stark: Invoice.create(invoices, project)
    Stark-->>SDK: Invoices criadas
    SDK-->>Service: CreatedInvoiceResult
    Service->>DB: persiste invoice_records
    Service->>DB: marca batch SUCCEEDED ou FAILED
```

O scheduler chama o mesmo serviço usado pelo endpoint manual. A diferença é que batches agendados recebem `sequence_number` e são limitados por `invoice.scheduler.max-batches`.

## Fluxo de Webhook

```mermaid
sequenceDiagram
    participant Stark as Stark Bank
    participant Controller as WebhookController
    participant Service as WebhookService
    participant Parser as StarkBankWebhookParser
    participant DB as PostgreSQL

    Stark->>Controller: POST /webhooks/starkbank
    Controller->>Controller: exige Digital-Signature
    Controller->>Service: process(rawBody, digitalSignature)
    Service->>Parser: parseInvoiceEvent(...)
    Parser->>Parser: projectProvider.getProject()
    Parser->>Parser: Event.parse(...)
    Parser-->>Service: ParsedInvoiceEvent
    Service->>DB: insert webhook_event_records se novo
    alt evento duplicado
        Service-->>Controller: DUPLICATE
    else evento não paid
        Service->>DB: markSkipped(reason)
        Service-->>Controller: SKIPPED
    else evento paid
        Service->>Service: resolve amount e fee
        Service-->>Controller: PROCESSED, SKIPPED ou FAILED
    end
```

O app considera pago apenas evento com `subscription=invoice`, `logType=paid` e `status=paid`.

## Fluxo Paid Invoice para Transfer

```mermaid
sequenceDiagram
    participant Webhook as WebhookService
    participant Invoice as StarkBankInvoiceClient
    participant TransferService as TransferService
    participant TransferClient as StarkBankTransferClient
    participant Stark as Stark Bank Sandbox
    participant DB as PostgreSQL

    Webhook->>Webhook: valida evento paid
    alt amount ou fee ausente
        Webhook->>Invoice: getPaymentDetails(invoiceId)
        Invoice->>Stark: Invoice.get + Invoice.payment
        Stark-->>Invoice: detalhes da Invoice
    end
    Webhook->>Webhook: calcula net_amount
    Webhook->>TransferService: createTransfer(...)
    TransferService->>DB: cria transfer_records CREATED
    TransferService->>TransferClient: createTransfer(request)
    TransferClient->>Stark: Transfer.create(...)
    Stark-->>TransferClient: Transfer criada
    TransferService->>DB: marca SUCCEEDED
    Webhook->>DB: marca evento PROCESSED
```

## Integração com Stark Bank SDK

A integração está isolada em:

- `StarkBankProjectProvider`: cria `Project`, valida environment e carrega private key por path ou conteúdo inline.
- `StarkBankSdkGateway`: encapsula chamadas estáticas da SDK para facilitar testes.
- `StarkBankInvoiceClient`: cria Invoices e busca detalhes de pagamento.
- `StarkBankWebhookParser`: chama `Event.parse(rawBody, digitalSignature)` e converte para `ParsedInvoiceEvent`.
- `StarkBankTransferClient`: cria Transfer com dados de destino configurados.

O provider chama `Settings.user = project` antes de retornar o Project. Isso é importante para o parse de webhook pela SDK e está documentado como ponto de atenção em [starkbank-findings.md](starkbank-findings.md).

## Segurança do Webhook

O endpoint real é `POST /webhooks/starkbank`.

Controles atuais:

- Rejeita requests sem header `Digital-Signature`.
- Rejeita payload ausente ou JSON inválido.
- Usa `Event.parse` da Stark Bank Java SDK para validar assinatura e payload.
- Aceita apenas eventos de Invoice.
- Persiste o payload recebido para auditoria.

Controles recomendados para produção:

- HTTPS obrigatório.
- Autenticação/allowlist na borda se compatível com o provedor.
- Rate limiting.
- Logs estruturados sem payload sensível.
- Alertas para volume anormal de falhas de assinatura.

## Idempotência

```mermaid
flowchart TD
    Event["Webhook event"] --> EventKey["stark_event_id único"]
    EventKey --> Existing{"Já existe?"}
    Existing -->|Sim| Duplicate["Retorna DUPLICATE/SKIPPED/FAILED sem recriar Transfer"]
    Existing -->|Não| Persist["Persiste RECEIVED"]
    Persist --> Paid{"Evento paid?"}
    Paid -->|Não| Skipped["Marca SKIPPED"]
    Paid -->|Sim| TransferKey["external_id = transfer-{eventId}"]
    TransferKey --> TransferUnique["invoice_id e external_id únicos"]
    TransferUnique --> Create["Cria Transfer uma vez"]
```

Chaves principais:

- `invoice_batches.batch_id` único.
- `invoice_batches.sequence_number` único para `SCHEDULED`.
- `invoice_records.stark_invoice_id` único.
- `webhook_event_records.stark_event_id` único.
- `transfer_records.invoice_id` único.
- `transfer_records.external_id` único.

## Transações

O projeto usa `TransactionTemplate` para separar operações locais importantes:

- Criar batch como `STARTED`.
- Persistir invoices e finalizar batch.
- Criar evento de webhook de forma idempotente.
- Atualizar status de evento.
- Criar e atualizar Transfer.

Chamadas externas à Stark Bank não ficam dentro de uma única transação longa de banco. A aplicação registra estado local antes/depois e preserva falhas para auditoria.

## Persistência

```mermaid
erDiagram
    invoice_batches ||--o{ invoice_records : "batch_id lógico"
    webhook_event_records ||--o| transfer_records : "event_id externo"

    invoice_batches {
        string batch_id
        string trigger_source
        int sequence_number
        int invoice_count
        string status
    }

    invoice_records {
        string stark_invoice_id
        string batch_id
        bigint amount
        string status
        bigint fee_amount
    }

    webhook_event_records {
        string stark_event_id
        string subscription
        string invoice_id
        string log_type
        jsonb raw_payload
        string status
    }

    transfer_records {
        string invoice_id
        string event_id
        string external_id
        bigint gross_amount
        bigint fee_amount
        bigint net_amount
        string status
    }
```

O schema é gerenciado por Flyway e validado pelo Hibernate com `ddl-auto: validate`.

## Proposta de Deploy Cloud

```mermaid
flowchart LR
    GitHub["GitHub"] --> CI["GitHub Actions"]
    CI --> Registry["Container Registry"]
    Registry --> Runtime["Container runtime<br/>ECS, Cloud Run, App Runner ou Kubernetes"]
    Runtime --> DB["PostgreSQL gerenciado"]
    Runtime --> Secrets["Secrets Manager"]
    Runtime --> Logs["Logs e métricas"]
    Stark["Stark Bank"] --> Edge["HTTPS public endpoint"]
    Edge --> Runtime
```

Proposta mínima:

- Gerar imagem Docker da aplicação.
- Publicar em registry privado.
- Usar PostgreSQL gerenciado.
- Armazenar `STARKBANK_PROJECT_ID` e private key em secrets manager.
- Expor endpoint HTTPS estável para webhook.
- Rodar Flyway no startup controlado ou etapa de deploy.
- Adicionar healthcheck e logs estruturados.

## Observabilidade Futura

Melhorias candidatas:

- Spring Boot Actuator com endpoints seguros.
- Métricas para batches, invoices emitidas, eventos por status, Transfers criadas/falhas e latência de chamadas Stark.
- Dashboard Prometheus/Grafana.
- Alertas para falha de webhook, assinatura inválida, ausência prolongada de eventos e falha de Transfer.
- Correlation IDs com `batchId`, `invoiceId`, `eventId` e `externalId`.
- Tracing distribuído se a aplicação for implantada em ambiente com múltiplos serviços.
