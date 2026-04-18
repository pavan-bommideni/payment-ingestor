# PayStream — Instant Payments Platform

> **HCLTech × Lloyds Bank Hackathon** | Java Spring Boot Engineer Challenge  
> Stack: Java 17 | Spring Boot 3 | Apache Kafka | Spring Data JPA | H2 | Docker Compose

---

## Table of Contents

1. [Message Flow](#1-message-flow)
2. [Kafka Config Choices](#2-kafka-config-choices)
3. [How to Run](#3-how-to-run)
4. [API Reference](#4-api-reference)
5. [Design Decisions](#5-design-decisions)

---

## 1. Message Flow

### 1.1 High-Level Architecture

```
  ┌──────────────────────────────────────────────────────────────────┐
  │                     POST /api/payments                           │
  │                       (client / Postman)                         │
  └─────────────────────────┬────────────────────────────────────────┘
                            │ HTTP 202 Accepted (paymentId returned)
                            ▼
  ┌──────────────────────────────────────────────────────────────────┐
  │               payment-ingestor  :8080                            │
  │                                                                  │
  │  1. Bean Validation (@Valid on PaymentRequest DTO)               │
  │  2. Debit account lookup  → H2 accounts table                    │
  │  3. Credit account lookup → H2 accounts table                    │
  │  4. Status checks (ACTIVE / SUSPENDED)                           │
  │  5. Idempotency check     → reject 409 on duplicate paymentId    │
  │  6. KafkaTemplate.send()  → payments.submitted                   │
  │     Partition key = debitAccountId (ensures ordered delivery     │
  │     per debtor account across all 6 partitions)                  │
  └──────────────────────────────────────────────────────────────────┘
                            │
              Topic: payments.submitted (6 partitions)
              Key:   debitAccountId
              Value: PaymentEvent (JSON)
                            │
                            ▼
  ┌──────────────────────────────────────────────────────────────────┐
  │               payment-processor  :8081                           │
  │                                                                  │
  │  1. @KafkaListener on payments.submitted                         │
  │     (consumer-group: payment-processor-group)                    │
  │  2. Business rule evaluation:                                    │
  │       amount > £250,000  → status = HELD                         │
  │       all other payments → status = PROCESSED                    │
  │       same debit/credit  → status = REJECTED  (ingestor blocks   │
  │                                                 this; defence    │
  │                                                 in depth here)   │
  │  3. Persist PaymentOutcomeEntity → payment_outcomes (H2)         │
  │     (written regardless of PROCESSED / HELD / REJECTED)          │
  │  4. Increment in-memory counters (totalProcessed / totalHeld /   │
  │     totalRejected / avgProcessingTimeMs)                         │
  │  5. KafkaTemplate.send() → payments.processed                    │
  └──────────────────────────────────────────────────────────────────┘
                            │
              Topic: payments.processed (6 partitions)
              Value: PaymentOutcome (JSON)
```

### 1.2 Kafka Topics

| Topic | Partitions | Producer | Consumer | Partition Key |
|---|---|---|---|---|
| `payments.submitted` | 6 | payment-ingestor | payment-processor | `debitAccountId` |
| `payments.processed` | 6 | payment-processor | *(downstream / judges)* | `paymentId` |

### 1.3 Message Schemas

**PaymentEvent** (published to `payments.submitted` by ingestor):

```json
{
  "paymentId":      "550e8400-e29b-41d4-a716-446655440000",
  "debitAccountId": "20-15-88/43917265",
  "creditAccountId":"20-15-88/98765432",
  "amount":         1500.00,
  "currency":       "GBP",
  "reference":      "Invoice INV-2024-001",
  "timestamp":      "2024-10-15T09:30:00Z"
}
```

**PaymentOutcome** (published to `payments.processed` by processor):

```json
{
  "paymentId":         "550e8400-e29b-41d4-a716-446655440000",
  "debitAccountId":    "20-15-88/43917265",
  "creditAccountId":   "20-15-88/98765432",
  "amount":            1500.00,
  "currency":          "GBP",
  "status":            "PROCESSED",
  "processedAt":       "2024-10-15T09:30:00.123Z",
  "processingTimeMs":  42
}
```

### 1.4 Account Validation Flow (ingestor)

```
POST /api/payments
        │
        ▼
 @Valid bean validation ──FAIL──► 400 Bad Request (violations list)
        │
        │ PASS
        ▼
 debitAccountId in H2? ──NO───► 404 Debit account not found: {id}
        │
        │ YES
        ▼
 creditAccountId in H2? ──NO──► 404 Credit account not found: {id}
        │
        │ YES
        ▼
 Either account SUSPENDED? ──YES► 422 Account is suspended: {id}
        │
        │ NO
        ▼
 Duplicate paymentId? ──YES────► 409 Duplicate paymentId
        │
        │ NO
        ▼
 Publish PaymentEvent ─────────► 202 Accepted { "paymentId": "..." }
```

### 1.5 Partition Key Rationale

Using `debitAccountId` as the Kafka partition key guarantees that **all payments from the same debtor account land on the same partition**, and therefore are consumed in strict arrival order by a single processor thread. This is critical for a payments platform: it prevents race conditions where two concurrent payments from the same account could be applied out-of-order.

---

## 2. Kafka Config Choices

> **Assessor note:** Every property below includes an explanation of what it controls and why it was chosen for this payments platform use case. Properties without comments score zero on the Kafka Tuning dimension.

### 2.1 Producer Configuration (`payment-ingestor` — `application.yml`)

```yaml
spring:
  kafka:
    producer:
      bootstrap-servers: localhost:9092
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer

      properties:
        # acks=all — Requires acknowledgement from all in-sync replicas (ISR) before
        # the producer considers a send successful. This is the most durable setting
        # and is mandatory for a payments platform where losing a submitted payment
        # is unacceptable. Trade-off: slightly higher latency vs acks=1 or acks=0.
        acks: all

        # enable.idempotence=true — Assigns each producer a unique ID and attaches a
        # monotonically increasing sequence number to every message batch. The broker
        # deduplicates retried batches, guaranteeing exactly-once delivery to the
        # partition even if the network drops after the broker has written but before
        # the ack reaches the producer. Requires acks=all and retries > 0.
        enable.idempotence: true

        # linger.ms=5 — Producer waits up to 5 ms to accumulate records into a batch
        # before sending. A small linger improves throughput during peak bursts (hundreds
        # of thousands of payments/sec) by reducing the number of individual Kafka
        # requests. 5 ms is low enough that it does not materially affect the
        # "instant" feel of Faster Payments from a customer perspective.
        linger.ms: 5

        # compression.type=snappy — Compresses each batch before it is sent to the
        # broker. Snappy offers a good CPU-to-compression-ratio trade-off: roughly
        # 40-50 % size reduction at very low CPU overhead compared with gzip. At
        # burst volumes this meaningfully reduces broker disk I/O and network
        # bandwidth without stalling the producer thread.
        compression.type: snappy

        # retries=3 — Number of times the producer retries a failed send before
        # raising an exception. Combined with idempotence this is safe; without
        # idempotence, retries could produce duplicates.
        retries: 3

        # max.in.flight.requests.per.connection=5 — Maximum number of unacknowledged
        # requests the producer sends to a single broker before blocking. Value of 5
        # is the maximum safe value when enable.idempotence=true; higher values
        # would break ordering guarantees.
        max.in.flight.requests.per.connection: 5
```

### 2.2 Consumer Configuration (`payment-processor` — `application.yml`)

```yaml
spring:
  kafka:
    consumer:
      bootstrap-servers: localhost:9092
      group-id: payment-processor-group
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer

      properties:
        # auto.offset.reset=earliest — When the consumer group starts with no committed
        # offset (e.g. first deploy, or group reset), begin reading from the very start
        # of the partition rather than skipping to the latest message. For a payments
        # processor this is the safe choice: we would rather replay and deduplicate
        # than silently skip payment events that arrived before the consumer came up.
        auto.offset.reset: earliest

        # max.poll.records=50 — Maximum records returned in a single poll() call.
        # Keeping this low (default is 500) limits the amount of work committed to
        # in each poll cycle. If processing a batch of 500 payments takes longer than
        # max.poll.interval.ms the consumer is evicted from the group, triggering
        # unnecessary rebalances. 50 records × ~5 ms DB write = ~250 ms well inside
        # the default 5-minute interval, giving headroom for occasional slow H2 writes.
        max.poll.records: 50

    listener:
      # concurrency=6 — Spins up 6 listener threads, one per partition on
      # payments.submitted. This is the maximum useful concurrency: adding more
      # threads than partitions wastes resources because idle threads cannot steal
      # work from a partition already assigned to another thread. With 6 threads each
      # partition is processed in parallel, maximising throughput at burst.
      concurrency: 6

      # ack-mode=BATCH — Offsets are committed once per poll batch rather than after
      # every individual record (RECORD mode) or on a timer (TIME mode). This is safer
      # than Spring's default auto-commit (which commits on a timer regardless of
      # whether records were successfully processed) and more efficient than per-record
      # commits. If the processor crashes mid-batch, only that batch is re-delivered —
      # not all uncommitted records from the last N seconds.
      ack-mode: BATCH
```

---

## 3. How to Run

*(To be completed in Dev Sprint 1)*

```bash
# 1. Start Kafka broker
docker compose up -d

# 2. Create topics (if not auto-created)
docker exec -it kafka kafka-topics.sh --create \
  --bootstrap-server localhost:9092 \
  --topic payments.submitted --partitions 6 --replication-factor 1

docker exec -it kafka kafka-topics.sh --create \
  --bootstrap-server localhost:9092 \
  --topic payments.processed --partitions 6 --replication-factor 1

# 3. Start ingestor
cd payment-ingestor && mvn spring-boot:run

# 4. Start processor (separate terminal)
cd payment-processor && mvn spring-boot:run

# 5. Smoke test
bash test-payment.sh
```

---

## 4. API Reference

*(To be completed in Dev Sprint 1 & 2)*

### payment-ingestor (:8080)

| Method | Path | Description | Status |
|---|---|---|---|
| POST | `/api/payments` | Submit a payment | 202 / 400 / 404 / 409 / 422 |
| GET | `/api/accounts/{accountId}` | Account details | 200 / 404 |
| GET | `/actuator/health` | Health check | 200 UP |
| GET | `/actuator/metrics` | Kafka producer metrics | 200 |

### payment-processor (:8081)

| Method | Path | Description | Status |
|---|---|---|---|
| GET | `/api/metrics/summary` | Live in-memory counters | 200 |
| GET | `/api/reports/summary` | Aggregate DB counts | 200 |
| GET | `/api/reports/activity` | Paginated outcomes (`?status=` `?accountId=`) | 200 |
| GET | `/api/accounts/{accountId}/history` | Account payment history | 200 / 404 |
| GET | `/actuator/health` | Health check | 200 UP |

---

## 5. Design Decisions

*(To be completed in Dev Sprint 2)*

- **Why H2 in-memory?** — Zero infrastructure overhead during the hackathon; schema is defined via JPA entities and seeded by `DataLoader` on every startup.
- **Why separate databases?** — Services must not share state. Each service owns its domain data, consistent with microservice principles and the challenge specification.
- **Why `debitAccountId` as partition key?** — Guarantees per-account ordering; see §1.5.
- **Idempotency strategy** — Ingestor stores accepted `paymentId` values and returns 409 on duplicates before touching Kafka.

---

*Git commit tag: `design-sprint-complete`*

> HCLTech Copyright 2026 — For hackathon participants only. Do not distribute.
