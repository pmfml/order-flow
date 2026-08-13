# OrderFlow — Event Contract

Canonical reference for every message published to Kafka. `ARCHITECTURE.md` §7
covers the *design* (topics, saga flow, compensation); this document is the
*contract* a consumer implementation must satisfy.

The Java representation lives in the `common` module as
`com.pmfml.orderflow.common.events.EventEnvelope`, and the event/topic names in
`com.pmfml.orderflow.common.events.EventTypes`. Both are shared by producers and
consumers so the contract is declared once.

---

## 1. Envelope

Every message on every topic is an envelope. Event-specific data lives under
`payload`; nothing else is ever added at the root.

```json
{
  "eventId": "9ef96733-9bfe-46f2-a80a-c1c4eaf99c93",
  "eventType": "orders.created",
  "tenantId": "tenant-e2e",
  "occurredAt": "2026-08-02T21:48:51.772866Z",
  "payload": { }
}
```

| Field | Type | Notes |
|---|---|---|
| `eventId` | UUID string | Stable identifier used for consumer deduplication. See §3. |
| `eventType` | string | Also the topic name. One of the values in `EventTypes`. |
| `tenantId` | string | Owning tenant. Always present, never inside `payload`. |
| `occurredAt` | ISO-8601 instant, UTC | When the producer *recorded* the event, not when it was published. Republishing does not change it. |
| `payload` | object | Event-specific. Never `null`; an event with no data carries `{}`. |

### Message key

The Kafka record key is the **aggregate id** (for `orders.*`, the order id), not
the `eventId`. This keeps all events for one order on the same partition, so
their relative order is preserved. The `eventId` identifies the message; the key
identifies the thing the message is about.

---

## 2. Topics and naming

Topic name == `eventType` == `<domain>.<event-in-past-tense>`, kebab-case. The
Outbox poller routes an event using its own `eventType`, so there is no separate
topic mapping to keep in sync.

| Topic | Producer | Consumer(s) | Status |
|---|---|---|---|
| `orders.created` | Order Service (Outbox) | Inventory Service | Produced |
| `inventory.reserved` | Inventory Service | Payment Service | Phase 4 |
| `inventory.reservation-failed` | Inventory Service | Order Service | Phase 4 |
| `payment.authorized` | Payment Service | Order Service | Phase 5 |
| `payment.failed` | Payment Service | Order Service | Phase 5 |
| `payment.captured` | Payment Service | Order Service | Phase 5 |
| `orders.cancelled` | Order Service | Inventory Service | Produced |
| `orders.confirmed` | Order Service | audit / future consumers | Produced |

---

## 3. Idempotency (mandatory)

The Outbox poller is **at-least-once**: if the process dies after the Kafka send
but before the row is marked processed, the same row is published again on the
next cycle.

Because `eventId` is the outbox row's primary key rather than a UUID generated at
publish time, **every republication of a row carries the same `eventId`**. That
is the property consumers rely on.

Every Kafka listener must therefore:

1. Read `eventId` from the envelope.
2. Check it against a `processed_events` store with a unique index on `eventId`.
3. Skip the message if already present.
4. Record the `eventId` in the same transaction as the business effect.

Step 4 matters: recording it in a separate transaction reintroduces the dual-write
problem the Outbox pattern exists to remove.

The `processed_events` store is created by each consuming service — a Postgres
table in Order/Payment, a Mongo collection in Inventory — as that service gains
its first listener, not before.

---

## 4. Monetary values

Amounts are serialized as JSON **numbers**, from `BigDecimal`:

```json
{ "totalAmount": 4501.5, "unitPrice": 1500.5 }
```

Consumers **must** bind these to `BigDecimal`, never to `double` or a generic
`Object`. Jackson parses the literal text straight into `BigDecimal` when the
target field is typed as one, which preserves the exact value; going through
`double` reintroduces binary-float drift on values like `0.1`.

Note the deliberate asymmetry with the gRPC contract, where `price` is a `string`
(`common/src/main/proto/inventory.proto`). Protobuf has no decimal type, so a
string is the only lossless option there. JSON numbers are lossless as long as
the consumer binds to a decimal type, so no quoting is needed here.

Trailing zeros are not significant: `4501.50` is serialized as `4501.5`. Format
for display, never compare with string equality.

---

## 5. Event payloads

### `orders.created`

Produced by Order Service when an order is persisted. Line items are included so
the Inventory Service can reserve stock without calling back.

```json
{
  "eventId": "9ef96733-9bfe-46f2-a80a-c1c4eaf99c93",
  "eventType": "orders.created",
  "tenantId": "tenant-e2e",
  "occurredAt": "2026-08-02T21:48:51.772866Z",
  "payload": {
    "orderId": "a387b12e-9bef-450d-b20a-ef370c07aa57",
    "status": "PENDING",
    "totalAmount": 4501.5,
    "items": [
      {
        "productId": "prod-e2e",
        "quantity": 3,
        "unitPrice": 1500.5
      }
    ]
  }
}
```

| Field | Type | Notes |
|---|---|---|
| `orderId` | UUID string | Same value as the Kafka message key. |
| `status` | string | `OrderStatus` at the time of the event. Always `PENDING` here. |
| `totalAmount` | number | Sum of `quantity * unitPrice`. See §4. |
| `items[].productId` | string | Catalog product id. |
| `items[].quantity` | integer | Always `> 0`. |
| `items[].unitPrice` | number | Price captured from the catalog at order time, not the client's. |

`items[].productName` is intentionally **not** published: it is a display-only
snapshot held by Order Service and no consumer needs it.

### `orders.confirmed`

Produced by Order Service when a payment is authorized, finalizing the happy path of the Saga.

```json
{
  "eventId": "9ef96733-9bfe-46f2-a80a-c1c4eaf99c93",
  "eventType": "orders.confirmed",
  "tenantId": "tenant-e2e",
  "occurredAt": "2026-08-02T21:49:51.772866Z",
  "payload": {
    "orderId": "a387b12e-9bef-450d-b20a-ef370c07aa57",
    "status": "CONFIRMED"
  }
}
```

| Field | Type | Notes |
|---|---|---|
| `orderId` | UUID string | Same value as the Kafka message key. |
| `status` | string | Always `CONFIRMED`. |

### `orders.cancelled`

Produced by Order Service as a compensating transaction when `inventory.reservation-failed` or `payment.failed` is received.

```json
{
  "eventId": "9ef96733-9bfe-46f2-a80a-c1c4eaf99c93",
  "eventType": "orders.cancelled",
  "tenantId": "tenant-e2e",
  "occurredAt": "2026-08-02T21:49:51.772866Z",
  "payload": {
    "orderId": "a387b12e-9bef-450d-b20a-ef370c07aa57",
    "status": "CANCELLED"
  }
}
```

| Field | Type | Notes |
|---|---|---|
| `orderId` | UUID string | Same value as the Kafka message key. |
| `status` | string | Always `CANCELLED`. |

### `payment.captured`

Produced by Payment Service when the AWS Lambda webhook confirms that the external
provider (Stripe) has successfully captured the payment. This is the final step
in the payment lifecycle.

```json
{
  "eventId": "a0cc1b4e-3c2f-4e81-a6b2-0f9a8c7d5e3a",
  "eventType": "payment.captured",
  "tenantId": "tenant-e2e",
  "occurredAt": "2026-08-12T15:30:00.000Z",
  "payload": {
    "orderId": "a387b12e-9bef-450d-b20a-ef370c07aa57",
    "paymentIntentId": "pi_3QhFKJL2A..."
  }
}
```

| Field | Type | Notes |
|---|---|---|
| `orderId` | UUID string | Same value as the Kafka message key. |
| `paymentIntentId` | string | Stripe PaymentIntent ID. May be null if the provider doesn't return one. |

### Remaining events

`inventory.*` and other `payment.*` events (`payment.authorized`, `payment.failed`) are
specified in the phase that introduces them (see §2). Their envelopes follow §1
unchanged.

---

## 6. Evolution rules

- **Adding** an optional field to a payload is backward compatible. Do it freely.
- **Removing or renaming** a field, or narrowing a type, is breaking. Publish a
  new `eventType` instead and retire the old one once no consumer reads it.
- Consumers must ignore unknown fields rather than fail on them.
- The envelope itself is frozen. New cross-cutting metadata (for example the
  correlation id arriving in Phase 8) is added here as an envelope field and
  rolled out to all producers together.
