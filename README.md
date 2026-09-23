# Room Reservation Service

Room Reservation Service is a Spring Boot microservice for Marvel Hospitality that confirms room
reservations across three payment modes and keeps bank-transfer bookings honest over time.

| # | Behaviour | Trigger |
| --- | --- | --- |
| 1 | Confirm a room reservation (cash confirms immediately; credit card is confirmed via credit-card-payment-service; bank transfer is booked pending payment) | `POST /api/v1/reservations` |
| 2 | Look up a reservation | `GET /api/v1/reservations/{reservationId}` |
| 3 | Confirm a pending bank-transfer booking once it is paid | Kafka topic `bank-transfer-payment-update` |
| 4 | Auto-cancel a bank-transfer booking still unpaid 2 days before check-in | Scheduled job, hourly |

## Tech Stack

* **Java 21**
* **Spring Boot 4.1.1** (Spring Framework 7, Jackson 3)
* **PostgreSQL** (reservation state) + **Flyway** (schema)
* **Apache Kafka** (`bank-transfer-payment-update` consumer)

---

## Why I Built It This Way

The three payment branches are the easy part - basically an `if/else`. What actually took thought was
everything that happens *after* a reservation exists: a bank transfer can come in as more than one
payment, Kafka can redeliver the same event, and a booking can sit unpaid while nothing is watching it
until the scheduler happens to run.

So `Reservation` itself owns the state machine (`confirm()`, `cancel()`, `applyBankTransferPayment()`,
`isEligibleForAutoCancellation()`), not the controller or the listener. That way "what counts as fully
paid" only has one answer, no matter whether the API, the Kafka listener or the scheduler is asking.
`ReservationService` is the only thing that touches the repository and the credit card client - the
controller, listener and scheduler are all thin wrappers around it. And there's a `@Version` column on
the reservation row, because the Kafka listener and the cancellation scheduler can actually race each
other: a payment landing right as the scheduler decides a booking is overdue.

---

## Design & Implementation Decisions

The assignment leaves a lot of gaps, so here's what I decided and why.

* **Pricing.** Nothing in the spec says what a room costs, so I didn't overthink it -
  `RoomPricingCalculator` looks up a nightly rate per `RoomSegment` from `application.yaml`
  (`room-reservation.nightly-rates`) and multiplies by the number of nights. Swap in a real pricing
  service later and this is the only class that changes.

* **Reservation ids.** They needed to survive being typed into a bank transfer reference by hand,
  because that's literally where the confirmation comes from - the event's `transactionDescription`
  carries an 8-character id at the end (`"...P4145478"`). `ReservationIdGenerator` produces a letter
  plus 7 digits, skipping `I`, `O` and `0` so nobody misreads them off a screen or a receipt.

* **The broken spec URL.** The OpenAPI spec I was given has a broken `servers.url` for
  credit-card-payment-service (`http//:localhost:9090//host/...` - note the missing colon). I read it
  as `http://localhost:9090/host/credit-card-payment-api` and wired that in as the default,
  configurable via `room-reservation.credit-card-payment.base-url` if it ever needs to point somewhere
  else.

* **Rejected credit card payments.** Taking the assignment at its word - "if credit payment is
  confirmed, then confirm the room else throw an error" - a rejected payment shouldn't leave anything
  behind. So it doesn't: nothing gets written to the database, and the room stays open for the next
  guest instead of showing up cancelled.

* **Double-booking.** Nobody asked for this protection outright, but a reservation system that hands
  the same room to two guests for the same week isn't much of a reservation system.
  `ReservationRepository.existsOverlapping` catches an overlapping room and date range with a `409`
  before pricing runs or the credit card service gets called - no point burning a paid API call on a
  request that's doomed anyway. Cancelling a reservation frees the room back up. That check alone
  doesn't cover two requests landing at the exact same moment, though, so
  `V2__prevent_room_overlap_at_the_database.sql` backs it up with a Postgres exclusion constraint on
  `(room_number, daterange)` - the real guarantee, and `ReservationRepositoryIntegrationTest` proves it
  against an actual database.

* **Kafka idempotency.** Kafka guarantees at-least-once delivery, not exactly-once, so
  `ProcessedPayment` keeps a record of every `paymentId` it's already handled. A redelivered event
  just gets skipped rather than crediting the same payment twice. Bank transfers can also arrive as
  several smaller payments instead of one lump sum, so `applyBankTransferPayment` adds each one to a
  running balance and only confirms once that balance covers the full price.

* **The cancellation window.** "2 days before the reservation start date" I took to mean: once you
  cross that line, an unpaid bank-transfer booking is fair game for cancellation. The scheduler checks
  hourly instead of once a day (`room-reservation.cancellation-check-cron`), so a booking doesn't sit
  overdue for most of a day before anything notices. It also works through candidates in capped
  batches (`CANCELLATION_BATCH_SIZE`) rather than pulling every overdue reservation into memory in one
  go - and it always re-queries page 0, never an incrementing page number, since cancelling a batch
  shrinks the result set and page 0 keeps landing on whatever's left.

* **Malformed Kafka messages.** A message that's malformed - a `transactionDescription` of the wrong
  length, a missing `paymentId`, a zero or negative `amountReceived` - isn't going to start working if
  you just retry it. `KafkaConsumerConfig` treats those as non-retryable, so the listener logs the bad
  message and moves on instead of getting stuck reprocessing it forever.

* **Cash payments.** These get confirmed without any payment record at all. The assignment only says
  cash "must be confirmed immediately" - nothing about collecting money upfront the way bank transfer
  and credit card do - so a cash reservation comes back `CONFIRMED` with `amountReceived` sitting at
  zero, on the assumption the money actually changes hands at the front desk.

## Trade-offs & What I'd Change

Nightly rates are static config, not a real pricing service - fine for what the assignment asks, but
an actual deployment would call out to a rates/inventory system instead.

The cancellation scheduler just scans on a fixed interval instead of scheduling a precise
per-reservation timer. That's simple and plenty for this scale, and the batched query keeps a single
run cheap even with a large backlog - a much larger reservation volume would probably still want a
work queue built around the lead-time cutoff instead of an hourly poll, though.

---

## Getting Started

### Prerequisites

* **JDK 21**
* **Docker** (optional locally, used by Spring Boot Docker Compose support to manage Postgres and Kafka)
* Maven wrapper included (`./mvnw`)

### Running Locally

```bash
./mvnw spring-boot:run
```

Spring Boot detects `docker-compose.yaml`, starts Postgres and a single-node Kafka broker, discovers
their mapped ports itself, and stops both when the app stops. The service starts on `http://localhost:8080`.

To connect to already-running instances instead:

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments="\
  --spring.docker.compose.enabled=false \
  --spring.datasource.url=jdbc:postgresql://localhost:5432/room_reservation \
  --spring.datasource.username=room_reservation --spring.datasource.password=room_reservation \
  --spring.kafka.bootstrap-servers=localhost:9092"
```

### Running with Docker / Jib

```bash
./mvnw jib:dockerBuild
docker compose --profile full up
```

Jib compiles layers directly without a Dockerfile or a Docker daemon at build time. The application
container runs under an unprivileged user (UID 1000).

### Continuous Integration

`.github/workflows/build.yml` runs `./mvnw verify` on every push and pull request. GitHub runners
provide Docker, so the Testcontainers integration test that skips locally without it does run there.
The JaCoCo report is uploaded as a build artifact, and the surefire reports too when the build fails.

### Running Tests

```bash
./mvnw verify
```

67 tests covering domain rules, the service layer, the credit card client, the web layer, the OpenAPI
contract, the Kafka listener, the scheduler, and the repository (against H2, plus a Testcontainers
Postgres suite that runs the real Flyway migrations, including checking the room-overlap exclusion
constraint - it skips automatically if Docker isn't available). JaCoCo writes a coverage report to
`target/site/jacoco/index.html`.

---

## API Examples

Swagger documentation is available at `http://localhost:8080/swagger-ui.html`.

### Confirm a reservation - cash

```bash
curl -X POST http://localhost:8080/api/v1/reservations \
  -H 'Content-Type: application/json' \
  -d '{
        "customerName": "Jane Doe",
        "roomNumber": "204",
        "reservationStartDate": "2026-10-01",
        "reservationEndDate": "2026-10-05",
        "roomSegment": "MEDIUM",
        "paymentMode": "CASH"
      }'
```

```json
{ "reservationId": "P4145478", "reservationStatus": "CONFIRMED" }
```

### Confirm a reservation - credit card

```bash
curl -X POST http://localhost:8080/api/v1/reservations \
  -H 'Content-Type: application/json' \
  -d '{
        "customerName": "Jane Doe",
        "roomNumber": "204",
        "reservationStartDate": "2026-10-01",
        "reservationEndDate": "2026-10-05",
        "roomSegment": "MEDIUM",
        "paymentMode": "CREDIT_CARD",
        "paymentReference": "DL123456789"
      }'
```

Confirmed if credit-card-payment-service reports the reference as `CONFIRMED`; otherwise `422` and no
reservation is created.

### Confirm a reservation - bank transfer

Booked `PENDING_PAYMENT`. It is confirmed once a `bank-transfer-payment-update` event whose
`transactionDescription` ends with this `reservationId` brings the running total to the full price, or
auto-cancelled if that never happens by 2 days before `reservationStartDate`.

### Look up a reservation

```bash
curl http://localhost:8080/api/v1/reservations/P4145478
```

### A `bank-transfer-payment-update` event

```json
{
  "paymentId": "PAY-98213",
  "debtorAccountnumber": "NL91ABNA0417164300",
  "amountReceived": 480,
  "transactionDescription": "1401541457P4145478"
}
```

The last 8 characters (`P4145478`) are the reservation id; the first 10 are an end-to-end id this
service does not otherwise use.

---

## Error Handling & Operations

### Error Responses

All API errors return a standard `application/json` payload (`type`, `title`, `status`, `detail`, `instance`):

```json
{
  "type": "https://example.com/room-reservation-service/errors/room-unavailable",
  "title": "Room unavailable",
  "status": 409,
  "detail": "Room 204 is already reserved between 2026-10-01 and 2026-10-05.",
  "instance": "/api/v1/reservations"
}
```

| HTTP Status | Trigger Condition |
| --- | --- |
| `400 Bad Request` | A missing/blank field, a malformed enum value, or a stay longer than 30 days |
| `404 Not Found` | Unknown reservation id, or an invalid route |
| `405 Method Not Allowed` | Invalid HTTP verb used on a valid route |
| `409 Conflict` | The room is already reserved for an overlapping date range |
| `422 Unprocessable Entity` | Credit card mode, and the referenced payment was not confirmed |
| `502 Bad Gateway` | credit-card-payment-service could not be reached, or kept failing after retries |
| `500 Internal Server Error` | Unexpected internal exception |

### Actuator & OpenAPI Endpoints

* **Health Check:** `http://localhost:8080/actuator/health`
* **Metrics:** `http://localhost:8080/actuator/metrics`
* **OpenAPI Spec:** `http://localhost:8080/v3/api-docs.yaml`

A copy of the OpenAPI spec is versioned at `src/main/resources/static/openapi.yaml`. It's not
hand-maintained - `OpenApiDocumentationTest` fails the build if it drifts from what the running app
actually generates, and it also checks that every operation documents all its failure statuses against
the shared `ApiError` schema, each with a real example. Regenerate it after changing a controller:

```bash
./mvnw spring-boot:run
curl -s localhost:8080/v3/api-docs.yaml -o src/main/resources/static/openapi.yaml
```

---

## Package Structure

```text
web/           ReservationController, ApiExceptionHandler  - REST endpoint
web/dto/       ReservationResponse                          - the API's success response shape
web/mapper/    ReservationMapper                            - entity to response
dto/           ReservationRequest                            - shared by the controller and the service
service/       ReservationService, RoomPricingCalculator     - business logic
service/impl/  ReservationServiceImpl                        - confirm / pay / cancel
entity/        Reservation, ProcessedPayment                  - JPA entities
domain/        PaymentMode, RoomSegment, TransactionDescription, ... - value types shared across layers
repository/    ReservationRepository, ProcessedPaymentRepository
client/        CreditCardPaymentClient + RestCreditCardPaymentClient - credit-card-payment-service
messaging/     BankTransferPaymentUpdateListener              - Kafka consumer
scheduler/     ReservationCancellationScheduler                - auto-cancellation
exception/     ErrorCode + exception types
config/        REST client, Kafka error handling, scheduling, OpenAPI, properties
```
