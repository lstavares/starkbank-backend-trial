# Troubleshooting

Este guia reúne problemas comuns ao rodar o Stark Bank Backend Trial localmente e ao validar a integração com o Sandbox.

## Credenciais Stark Bank

Sintomas comuns:

- Erro ao criar Invoices.
- Erro ao fazer parse de webhook.
- Mensagens mencionando `Access-Id`.
- Mensagens de configuração da SDK.

Verifique:

- `STARKBANK_ENVIRONMENT=sandbox`.
- `STARKBANK_PROJECT_ID` preenchido com o Project ID correto.
- Private key compatível com o projeto.
- Apenas uma das alternativas de chave precisa estar configurada: `STARKBANK_PRIVATE_KEY_PATH` ou `STARKBANK_PRIVATE_KEY`.
- A private key não deve ter espaços extras antes/depois do conteúdo.

## STARKBANK_PROJECT_ID

`STARKBANK_PROJECT_ID` deve identificar o projeto Stark Bank usado no Sandbox. Se o valor estiver vazio, incorreto ou não combinar com a private key, chamadas à SDK podem falhar.

O app valida esse campo antes de usar a Stark Bank SDK e retorna erro de configuração se ele estiver ausente.

## Private Key Path

Use `STARKBANK_PRIVATE_KEY_PATH` para apontar para a private key local:

```bash
export STARKBANK_PRIVATE_KEY_PATH=path/to/private-key-file
```

Cuidados:

- Não commitar arquivos de chave privada.
- Não usar caminho absoluto em documentação compartilhada.
- Confirmar que o processo Java tem permissão para ler o arquivo.
- Confirmar que o arquivo não está vazio.

## Erro de Access-Id

Um erro mencionando `Access-Id` normalmente indica que a SDK não conseguiu autenticar o projeto corretamente.

Possíveis causas:

- `STARKBANK_PROJECT_ID` incorreto.
- Private key de outro projeto.
- `STARKBANK_ENVIRONMENT` diferente do ambiente da credencial.
- Conteúdo da private key quebrado ao copiar para variável inline.

## Description Maior que 20 Caracteres

A criação de Invoice usa `Invoice.Description`. Se a Stark Bank API rejeitar descrições longas, revise o par `key/value` enviado pela aplicação.

No código atual, o valor usado é curto: `service = Trial invoice`.

Se esse problema reaparecer:

- Conferir a mensagem exata retornada pela SDK/API.
- Conferir se algum teste manual alterou a descrição.
- Registrar como lacuna de documentação ou validação de limite, sem assumir bug da API.

## ngrok URL Mudou

O ngrok gratuito costuma gerar nova URL ao reiniciar o túnel.

Sintomas:

- Nenhum webhook chega à aplicação.
- A Stark Bank mostra eventos sem entrega recente para a URL esperada.
- Logs locais não mostram chamadas em `/webhooks/starkbank`.

Correção:

1. Rodar `ngrok http 8080`.
2. Copiar a nova URL HTTPS.
3. Atualizar o webhook no Sandbox para `https://your-domain.example/webhooks/starkbank`.
4. Emitir nova Invoice ou aguardar novo evento.

## Webhook Sem Digital-Signature

O endpoint rejeita requests sem `Digital-Signature`:

```text
400 Digital-Signature header is required.
```

Isso é esperado. Webhooks reais da Stark Bank devem incluir a assinatura. Requests manuais em `.http` ou `curl` servem apenas para exercitar o endpoint e não simulam uma assinatura válida.

## Sandbox Sem Evento Paid

Durante a validação real, o Sandbox gerou `created`, `overdue` e `expired`, mas não gerou `paid`.

Como analisar:

- Consulte `GET /admin/webhook-events`.
- Consulte os logs/eventos da Invoice pela Stark Bank.
- Verifique se os eventos consultados estão com `isDelivered=true`.
- Confirme se há registros `log_type=paid` localmente.
- Confirme se `GET /admin/transfers` permanece vazio.

Ausência de `paid` não prova falha da aplicação. O fluxo `paid -> Transfer` depende de um evento `paid` real.

## Como Consultar Invoice Logs

Pelo lado da aplicação, use:

```bash
curl http://localhost:8080/admin/webhook-events
```

No banco:

```bash
docker compose exec -T postgres psql -U starkbank -d starkbank_trial -c "select stark_event_id, invoice_id, invoice_log_id, log_type, status, received_at from webhook_event_records order by received_at desc limit 20;"
```

Pelo lado da Stark Bank, consulte os eventos/logs pela SDK ou pelo portal Sandbox, conforme a ferramenta disponível no momento da validação.

## Diferenciar Falha do App vs Comportamento Externo

Use esta ordem:

1. O app está de pé? `GET /health`.
2. O ngrok aponta para a porta correta?
3. O webhook no Sandbox usa a URL atual?
4. O request chegou ao app?
5. O app retornou HTTP 200?
6. O evento aparece em `webhook_event_records`?
7. O evento é `paid`?
8. Há Transfer em `transfer_records`?
9. A Stark mostra `isDelivered=true` para o evento consultado?

Interpretação:

- Chegou ao app, HTTP 200, evento não pago e sem Transfer: comportamento esperado.
- Stark não entrega para a URL atual: provável configuração de webhook/túnel.
- App retorna 400 por assinatura: provável request manual ou assinatura inválida.
- Evento `paid` chegou e Transfer falhou: investigar `transfer_records.error_message` e configuração de destino.

## PostgreSQL

Se a aplicação não inicia:

- Confirme que o container está em execução.
- Rode `docker compose config` para validar o Compose.
- Confirme porta `5432` livre.
- Confirme `DATABASE_URL`, `DATABASE_USERNAME` e `DATABASE_PASSWORD`.

Comando útil:

```bash
docker compose ps
```

## Scheduler

Se batches agendados não aparecem:

- Verifique `INVOICE_SCHEDULER_ENABLED`.
- Verifique `INVOICE_INTERVAL_HOURS`.
- Verifique se `INVOICE_MAX_BATCHES` já foi atingido.
- Consulte `GET /admin/invoice-batches`.

O endpoint manual `POST /admin/invoices/issue-now` não depende do limite de batches agendados.
