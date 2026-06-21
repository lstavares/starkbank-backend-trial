# Validation Notes

## Validated

- Invoice creation through Stark Bank Java SDK.
- Webhook endpoint receiving Stark Bank invoice events.
- Webhook signature parsing through the SDK.
- Idempotent persistence of invoice events.
- Correctly skipping non-paid invoice events such as `created`, `overdue`, and `expired`.
- Transfer flow implemented and ready to run when a paid invoice event is received.

## Pending External Sandbox Validation

The full `paid invoice event -> transfer` flow is pending because, during the observed Sandbox validation window, the created invoices did not transition to `paid`.

Observed Sandbox behavior:

- App-created invoices received `created` events.
- Short-due invoices received `created` and `overdue` events.
- A portal-created immediate invoice with 1 hour expiration received `created`, `overdue`, and `expired` events.
- No `paid` invoice log/event was observed.
- All queried Stark Bank invoice events were delivered successfully.

Once the Sandbox generates a paid invoice event, the application should process the event and create the corresponding transfer.
