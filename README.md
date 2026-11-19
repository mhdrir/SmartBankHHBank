# SmartBank Payments

A small, production-style **payment integration platform**: payments come in over a REST API or a CSV
file from a partner, are published as events to **Kafka**, orchestrated by a **Camunda** BPMN process
(validation, four-eyes review for high amounts, booking), and the results are published as events again.

Built as a practice project for the stack: **Java 21, Spring Boot 3, Kafka, Camunda 7, PostgreSQL,
JUnit 5 / Mockito / Testcontainers, Docker, GitHub Actions, Terraform (AWS)**.

```
 Client / Partner
   │  POST /api/v1/payments  (JWT + Idempotency-Key)     POST /api/v1/payments/import (CSV)
   ▼
 PaymentController ─► PaymentService ──(1 DB transaction)──► payments + outbox_events
                                                                   │
                                          OutboxRelay (every 1 s)  ▼
                                                     Kafka: payments.requested
                                                                   │
                                     PaymentRequestedListener (group payment-orchestrator)
                                                                   ▼
                                     Camunda process "payment-process"
                       Validate ─► [≥ 10,000 EUR?] ─► Four-eyes review ─► Book ─► Complete
                           └──────────── errors / declined ──────────► Reject
                                                                   │ (outbox)
                                                                   ▼
                                        Kafka: payments.completed / payments.rejected
                                                                   ▼
                                     PaymentResultListener (group notification-service)
```

## Run locally

Requirements: JDK 21, Maven 3.9, Docker.

```bash
docker compose up -d postgres kafka kafka-ui          # infrastructure
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run      # the app
```

| What | URL |
| --- | --- |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Camunda Cockpit / Tasklist | http://localhost:8080/camunda/app/ (demo / demo) |
| Kafka UI | http://localhost:8081 |
| Health | http://localhost:8080/actuator/health |

Try it (see also `requests.http`):

```bash
TOKEN=$(curl -s -X POST localhost:8080/dev/token | sed 's/.*"access_token":"\([^"]*\)".*/\1/')

# small payment -> COMPLETED automatically
curl -i -X POST localhost:8080/api/v1/payments \
  -H "Authorization: Bearer $TOKEN" -H "Idempotency-Key: $(uuidgen)" -H "Content-Type: application/json" \
  -d '{"debtorIban":"DE02120300000000202051","creditorIban":"DE75512108001245126199","amount":100.00,"currency":"EUR"}'

# 15,000 EUR -> PENDING_REVIEW, approve it in Tasklist
# CSV import
curl -X POST localhost:8080/api/v1/payments/import -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: text/csv" --data-binary @samples/payments.csv
```

## Tests

```bash
mvn test      # unit + web slice + BPMN process tests (no Docker needed)
mvn verify    # + PaymentFlowIT end-to-end with PostgreSQL and Kafka in Testcontainers (needs Docker)
```

## Project layout

```
src/main/java/com/smartbank/payments
  api/          REST controllers, DTOs, error handling (RFC 9457 Problem Details), dev token
  application/  use cases: PaymentService, validation, booking, CSV transformer, idempotency hash
  domain/       entities Account and Payment with their business rules
  outbox/       transactional outbox: OutboxEvent, OutboxWriter, OutboxRelay
  messaging/    Kafka topics, events, listeners, idempotent-consumer table
  process/      Camunda JavaDelegates for the BPMN service tasks
  config/       Kafka, Security (JWT), OpenAPI, typed properties
src/main/resources
  bpmn/payment-process.bpmn    the process model (open with Camunda Modeler)
  db/migration/                Flyway SQL migrations
infra/terraform                AWS: ECR, ECS Fargate, RDS, MSK Serverless, Secrets Manager (not applied)
.github/workflows/ci.yml       build, test, Docker image, optional push to ECR via OIDC
```
