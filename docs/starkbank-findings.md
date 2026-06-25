# Achados sobre Stark Bank Sandbox, SDK e API

Este documento reúne achados exploratórios observados durante a implementação e validação. O objetivo é registrar contexto técnico com linguagem cautelosa, sem classificar nada como bug da Stark Bank sem confirmação reproduzível e evidência suficiente.

## Resumo

| Classificação | Achado | Status |
| --- | --- | --- |
| Comportamento observado | Invoices testadas não geraram evento/log `paid` durante a janela observada. | Necessita confirmação em nova janela de teste. |
| Comportamento observado | Invoice manual mascarada `46628325...0944` seguiu `created -> overdue -> expired`. | Registrado em validação. |
| Lacuna de documentação | Interação entre cobrança imediata, `due` e `expiration` pode exigir exemplos mais claros. | Candidato a documentação futura. |
| Ponto de atenção da SDK | Parse de webhook depende de `Settings.user` configurado antes de `Event.parse`. | Isolado no provider; merece atenção em apps concorrentes. |
| Lacuna de documentação | Disponibilidade de fee/payment details pode variar entre evento, Invoice e Invoice Payment. | Tratado com fallback no app. |
| Candidato a melhoria futura | Publicar findings como issue/discussion pública somente com evidência reproduzível. | Não executado nesta entrega. |

## Comportamento Observado: Ausência de Paid

Durante a janela testada:

- Invoices criadas pela aplicação foram emitidas com sucesso.
- Webhooks reais chegaram para `created`, `overdue` e `expired`.
- Eventos consultados na Stark Bank estavam entregues.
- Nenhum evento/log `paid` foi observado.
- Nenhuma Transfer foi criada.

Interpretação cuidadosa:

- Isso descreve o comportamento observado no Sandbox durante uma janela específica.
- Não há evidência suficiente para afirmar bug da Stark Bank.
- A validação end-to-end `paid -> Transfer` permanece pendente de um evento `paid` real.

## Invoice Manual pelo Portal

A Invoice manual mascarada `46628325...0944`, criada pelo portal como cobrança imediata, também passou por:

```text
created -> overdue -> expired
```

Sem `paid` observado.

Classificação: comportamento observado / necessita confirmação.

Próxima verificação sugerida:

- Repetir o teste em nova janela.
- Registrar horários, configuração da Invoice e logs retornados pela SDK.
- Comparar com documentação oficial da Stark Bank sobre pagamento em Sandbox.

## Due, Expiration e Cobrança Imediata

O app atual cria Invoices com:

- `due` aproximadamente 2 horas no futuro.
- `expiration` de 24 horas.

Durante a validação, uma cobrança manual pelo portal também expirou sem `paid`.

Classificação: lacuna de documentação / necessita confirmação.

Ponto a documentar futuramente:

- Como o Sandbox espera que um pagamento seja simulado.
- Se há diferença prática entre cobrança imediata no portal e campos `due`/`expiration` via SDK/API.
- Quais condições disparam `paid` em ambiente Sandbox.

## Settings.user no Parse de Webhook

O código chama `projectProvider.getProject()` antes de `Event.parse(rawBody, digitalSignature)`. Esse provider também configura `Settings.user = project`.

Classificação: ponto de atenção da SDK.

Risco técnico:

- `Settings.user` parece ser estado global da SDK.
- Em apps com múltiplos projetos ou execução altamente concorrente, estado global pode exigir cuidado extra.

Mitigação atual:

- O projeto usa um único Project Sandbox.
- A configuração está isolada em `StarkBankProjectProvider`.
- Testes cobrem o comportamento esperado do parser.

## Fee e Payment Details

O app tenta usar os valores vindos do evento `paid`. Se `amount` ou `fee` não estiverem disponíveis, busca detalhes pela SDK usando Invoice e Invoice Payment.

Classificação: lacuna de documentação / candidato a melhoria futura.

Motivo:

- A disponibilidade de `fee` e detalhes de pagamento pode depender do tipo de evento e do momento em que a consulta é feita.
- O app já possui fallback, mas uma validação com evento `paid` real ainda é necessária.

## Possíveis Issues Futuras

Não abrir issue pública apenas com a evidência atual. Antes disso, coletar:

- Passos mínimos de reprodução.
- IDs de recursos de Sandbox que possam ser compartilhados sem expor segredo.
- Horários e ambiente.
- Payloads redigidos.
- Resultado esperado vs observado.
- Trechos relevantes da documentação oficial.

Possíveis temas, se confirmados:

- Como simular pagamento de Invoice no Sandbox.
- Clareza sobre `due`, `expiration` e cobrança imediata.
- Comportamento esperado de `Event.parse` em relação a `Settings.user`.
- Onde obter `fee` e payment details com consistência após evento `paid`.
