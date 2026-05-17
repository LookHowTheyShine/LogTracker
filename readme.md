have to create topics manually

$ docker exec kafka kafka-topics --bootstrap-server kafka:29092 --create --if-not-exists --topic warehouse.errors.dlq --partitions 2 --replication-factor 1 --config retention.ms=60
4800000
WARNING: Due to limitations in metric names, topics with a period ('.') or underscore ('_') could collide. To avoid issues it is best to use either, but not both.

khwai@LAPTOP-P6QOOSU6 MINGW64 ~/IdeaProjects/MetricOrg (main)
$ docker exec kafka kafka-topics --bootstrap-server kafka:29092 --create --if-not-exists --topic warehouse.errors.dlq --partitions 2 --replication-factor 1 --config retention.ms=604800000
WARNING: Due to limitations in metric names, topics with a period ('.') or underscore ('_') could collide. To avoid issues it is best to use either, but not both.

khwai@LAPTOP-P6QOOSU6 MINGW64 ~/IdeaProjects/MetricOrg (main)
$ docker exec kafka kafka-topics --bootstrap-server kafka:29092 --create --if-not-exists --topic warehouse.alerts.processed --partitions 3 --replication-factor 1 --config retention
.ms=43200000
WARNING: Due to limitations in metric names, topics with a period ('.') or underscore ('_') could collide. To avoid issues it is best to use either, but not both.
Created topic warehouse.alerts.processed.



 ---
The Core Mental Model Shift

In RabbitMQ, producers send to exchanges, exchanges route to queues, consumers drain queues. Messages are deleted once
consumed.

In Kafka, producers write to topics, topics are a log that persists. Messages are NOT deleted when consumed — they sit
there until the retention period expires. Consumers just track their position (called an offset) in the log.

  ---
The Mapping

┌────────────────────────────────┬──────────────────────────┬─────────────────────────────────────────────────────┐
│            RabbitMQ            │          Kafka           │                        Notes                        │
├────────────────────────────────┼──────────────────────────┼─────────────────────────────────────────────────────┤
│ Exchange                       │ Topic                    │ But no routing — topic is the destination directly  │
├────────────────────────────────┼──────────────────────────┼─────────────────────────────────────────────────────┤
│ Routing key / binding          │ Nothing                  │ Kafka has no routing. You publish to a topic by     │
│                                │                          │ name                                                │
├────────────────────────────────┼──────────────────────────┼─────────────────────────────────────────────────────┤
│ Queue                          │ Partition                │ A topic is split into N ordered partitions          │
├────────────────────────────────┼──────────────────────────┼─────────────────────────────────────────────────────┤
│ Consumer                       │ Consumer in a group      │                                                     │
├────────────────────────────────┼──────────────────────────┼─────────────────────────────────────────────────────┤
│ Consumer group (competing      │ Consumer Group           │ Same concept, same name                             │
│ consumers)                     │                          │                                                     │
├────────────────────────────────┼──────────────────────────┼─────────────────────────────────────────────────────┤
│ Single Active Consumer on a    │ One consumer per         │ Kafka guarantees this automatically                 │
│ queue                          │ partition                │                                                     │
├────────────────────────────────┼──────────────────────────┼─────────────────────────────────────────────────────┤
│ Prefetch count                 │ max.poll.records         │ How many messages fetched per poll                  │
├────────────────────────────────┼──────────────────────────┼─────────────────────────────────────────────────────┤
│ Ack                            │ Commit offset            │ Instead of acking a message, you commit how far     │
│                                │                          │ you've read                                         │
├────────────────────────────────┼──────────────────────────┼─────────────────────────────────────────────────────┤
│ Nack / requeue                 │ Don't commit             │ Just don't commit the offset, message will be       │
│                                │                          │ re-delivered                                        │
├────────────────────────────────┼──────────────────────────┼─────────────────────────────────────────────────────┤
│ Dead Letter Exchange           │ DLQ topic                │ We built this — warehouse.errors.dlq                │
├────────────────────────────────┼──────────────────────────┼─────────────────────────────────────────────────────┤
│ Message TTL                    │ retention.ms             │ What we set on each topic                           │
├────────────────────────────────┼──────────────────────────┼─────────────────────────────────────────────────────┤
│ Management UI                  │ No built-in              │ Need AKHQ, Kafka UI, or Conduktor                   │
├────────────────────────────────┼──────────────────────────┼─────────────────────────────────────────────────────┤
│ Shovel                         │ MirrorMaker              │ Cross-cluster replication                           │
├────────────────────────────────┼──────────────────────────┼─────────────────────────────────────────────────────┤
│ Mnesia (internal RabbitMQ DB)  │ Zookeeper                │ Stores cluster metadata                             │
└────────────────────────────────┴──────────────────────────┴─────────────────────────────────────────────────────┘

  ---
Your Specific Setup Mapped

warehouse.errors — 6 partitions

▎ Think of this as an exchange with 6 queues behind it. Your producer publishes to the exchange (topic), Kafka decides
▎  which queue (partition) based on the message key (deviceId). Same deviceId always goes to the same partition — so
▎ events from the same device are always ordered.

warehouse.errors.dlq — 2 partitions

▎ Your dead letter exchange + queue. When alert-processor fails after 3 retries, DlqProducerService manually publishes
▎  there. DlqConsumer reads it — this is your dead letter consumer.

warehouse.alerts.processed — 3 partitions

▎ Currently unused in the flow but reserved for downstream consumers that might want to react to processed alerts.

Consumer group warehouse-alert-processor

▎ Exactly like competing consumers on a queue. You have 6 partitions and concurrency=3, so 3 threads each own 2
▎ partitions. If you ran a second instance of the service, Kafka would rebalance — some partitions would move to the
▎ new instance. This is automatic, like RabbitMQ competing consumers.

  ---
Partitions = "Single Active Consumer" Built-In

In RabbitMQ you had to explicitly set Single Active Consumer to prevent two consumers processing the same message. In
Kafka this is structural — partition 0 is owned by exactly one consumer in a group at a time. No configuration needed.
That's why partition count matters: it's your parallelism ceiling. You have 6 partitions → max 6 parallel consumers
ever.

  ---
Prefetch

In RabbitMQ prefetch=10 meant "give me 10 unacked messages max". In Kafka the equivalent is max.poll.records=50 (which
we set in application.yml). Your consumer polls and gets up to 50 records, processes them, commits the offset, then
polls again.

  ---

  ---
Partitions = "Single Active Consumer" Built-In

In RabbitMQ you had to explicitly set Single Active Consumer to prevent two consumers processing the same message. In Kafka this is structural — partition
0 is owned by exactly one consumer in a group at a time. No configuration needed. That's why partition count matters: it's your parallelism ceiling. You
have 6 partitions → max 6 parallel consumers ever.

  ---
Prefetch

In RabbitMQ prefetch=10 meant "give me 10 unacked messages max". In Kafka the equivalent is max.poll.records=50 (which we set in application.yml). Your
consumer polls and gets up to 50 records, processes them, commits the offset, then polls again.

  ---
Zookeeper

In RabbitMQ, Erlang/Mnesia handles cluster metadata internally — you never think about it. Zookeeper is Kafka's equivalent: it tracks which brokers are
alive, which broker is the leader for which partition, consumer group membership, etc. It's the cluster's brain.

You never interact with Zookeeper directly. It just needs to be running. Newer Kafka (3.x+) is actually replacing Zookeeper with its own internal
consensus (KRaft mode) — the Confluent 7.7 image we're using still uses Zookeeper the old way.

  ---
The Biggest Difference from RabbitMQ

In RabbitMQ, the broker tracks what each consumer has processed (acked vs unacked). In Kafka, the consumer tracks its own position (offset). This means:

- You can replay messages by resetting the offset (impossible in RabbitMQ without shovel tricks)
- Multiple independent consumer groups can each read the same topic at their own pace — like having multiple independent queues bound to the same
  exchange, but each gets every message
- Our MANUAL_IMMEDIATE ack mode in KafkaConfig.java means we commit the offset ourselves only after successful processing — equivalent to manual ack in
  RabbitMQ

✻ Crunched for 37s



● Kafka vs RabbitMQ — The Real Tradeoffs

  ---
Where Kafka Wins

1. High throughput at scale
   Kafka does millions of messages/second on commodity hardware. RabbitMQ starts struggling past ~50k msg/s. If you're ingesting IoT sensor data,
   clickstreams, logs, financial ticks — Kafka.

2. Replay / event sourcing
   Messages persist. Any consumer can go back and re-read from offset 0. RabbitMQ messages are gone once consumed. This is transformative for:
- Debugging production issues by replaying what actually happened
- New services that need historical data on their first deploy
- Event sourcing architectures

3. Multiple independent consumers on same data
   One topic, ten different consumer groups, each gets every message independently at their own pace. In RabbitMQ you'd need to fan-out to ten separate
   queues and keep them in sync.

4. Stream processing
   Kafka integrates naturally with Kafka Streams, Flink, Spark Structured Streaming. You can do windowed aggregations, joins across streams, stateful
   processing. RabbitMQ is just a transport — no processing model.

5. Long-term storage as the source of truth
   With long retention (days/weeks), Kafka becomes your audit log, your data lake feeder, your CDC (Change Data Capture) backbone. Companies run Kafka with
   30-day retention and treat it as the system of record.

6. Ordering guarantees at scale
   Within a partition, order is guaranteed. You can partition by customerId and guarantee all events for a customer are processed in order, at scale, across
   many consumers.

  ---
Where RabbitMQ Wins

1. Complex routing logic
   Topic exchanges, direct exchanges, fanout, headers, binding keys — RabbitMQ has a proper routing layer. Kafka has none. If you need "send this message to
   queue A if type=payment AND amount>1000, else queue B" — RabbitMQ does this natively, Kafka requires you to build it in application code.

2. Task queues / work distribution
   Job queues where each task must be processed exactly once by exactly one worker, with priorities, delays, and TTLs. RabbitMQ's queue model is
   purpose-built for this. Kafka can do it but it's awkward.

3. Low latency at low volume
   RabbitMQ push-based delivery is faster for single messages. Kafka consumers poll, so there's inherent latency. For real-time chat, live notifications,
   RPC-style request/reply — RabbitMQ.

4. Request/Reply pattern
   RabbitMQ has native RPC support (reply-to queues, correlation IDs). Kafka has no concept of this — you have to build it yourself with two topics.

5. Per-message TTL and priority queues
   RabbitMQ supports message-level TTL, priority queues, delayed messages out of the box. Kafka only has topic-level retention, no per-message expiry, no
   priorities.

6. Operational simplicity
   No Zookeeper (in modern RabbitMQ), built-in management UI, simpler mental model. For a small team shipping a microservices app, RabbitMQ is far easier to
   operate.

7. Transactions / exactly-once at the business level
   RabbitMQ integrates with database transactions more naturally for the outbox pattern in simple setups.

  ---
The Interview Answer

When an interviewer asks "Kafka or RabbitMQ?", they want to hear this framework:

Choose Kafka when:
- Volume is high (100k+ msg/s) or unpredictable
- You need event replay or audit trail
- Multiple systems need the same events (analytics + application + data warehouse simultaneously)
- You're building an event-driven architecture where events are the source of truth
- You need stream processing / real-time aggregations
- You need to decouple producers from consumers long-term (different teams, different deploy cycles)

Choose RabbitMQ when:
- You need complex routing (conditions, headers, topics)
- You're building task/job queues with workers
- You need request/reply or RPC patterns
- Per-message features matter (TTL, priority, delay)
- Team is small and operational simplicity matters
- Latency for individual messages matters more than throughput

  ---
The One-Liner That Impresses Interviewers

▎ "RabbitMQ is a message broker — it moves messages from A to B and forgets them. Kafka is a distributed log — it records what happened and lets anyone
▎ read it at any time. Choose based on whether your problem is task distribution or event streaming."

  ---
Your Project as an Example

Your MetricOrg project is a good Kafka use case because:
- IoT-style sensor events from many devices simultaneously (high volume)
- Multiple consumers could eventually read the same events — alert processor today, ML anomaly detector tomorrow, data warehouse loader the day after
- You need the dedup window and ordered processing per device (partition by deviceId)
- Replay would let you reprocess historical alerts with new business rules

If this were just "worker pool that processes jobs", RabbitMQ would've been simpler.



Now for the deep dives.

  ---
Architecture

┌─────────────────────────────────────────────────────────────────┐
│                      warehouse-producer :8081                    │
│                                                                 │
│  POST /api/events                                               │
│  ──────────────                                                 │
│  EventController  ──(@Valid)──►  EventProducerService           │
│                                   enrich()  →  uuid + timestamp │
│                                   KafkaTemplate.send()          │
│                                     key = deviceId              │
│                                     async → 202 immediately     │
└───────────────────────────┬─────────────────────────────────────┘
│ warehouse.errors (6 partitions)
│ deviceId key → same device always
│ same partition → ordering guaranteed
▼
┌─────────────────────────────────────────────────────────────────┐
│                  warehouse-alert-processor :8082                 │
│                                                                 │
│  WarehouseErrorEventConsumer                                    │
│    @KafkaListener  MANUAL_IMMEDIATE ack  concurrency=3          │
│    │                                                            │
│    ▼                                                            │
│  AlertProcessorService (no AOP annotations — pure orchestrator) │
│    │                                                            │
│    ├─► DeduplicationService                                     │
│    │     Redis SET NX  key=warehouse:dedup:{deviceId}:{errCode} │
│    │     TTL=60s  →  atomic, no race between 3 consumer threads │
│    │     duplicate? → DEDUPED metric → return (consumer acks)   │
│    │                                                            │
│    ├─► RateLimitingService                                      │
│    │     Bucket4j token bucket in Redis                         │
│    │     key=warehouse:ratelimit:{deviceId}                     │
│    │     10 tokens/min per device  →  bucket.tryConsume(1)      │
│    │     no token? → RATE_LIMITED metric → return (acks)        │
│    │                                                            │
│    └─► RetryablePersistService  (@Retryable)                    │
│          └─► AlertPersistenceService  (@Transactional)          │
│                persist Alert + resolveIncident → PostgreSQL      │
│                success → SAVED metric                            │
│                3 failures → @Recover → DlqProducerService        │
│                                                                 │
│  DlqConsumer                                                    │
│    @KafkaListener  warehouse.errors.dlq  concurrency=2          │
│    → persist as FAILED → ack                                    │
└───────────────────────────┬─────────────────────────────────────┘
│ reads same alerts/incidents tables
▼
┌─────────────────────────────────────────────────────────────────┐
│                   warehouse-dashboard :8083                      │
│                                                                 │
│  AlertView + IncidentView  (@Immutable JPA,  hikari read-only)  │
│  RedisStatsService  →  reads warehouse:stats:*                  │
│  ConsumerLagService →  AdminClient offset query, 5s cache       │
│  AlertStatsService  →  aggregates all three sources             │
│  DashboardController  GET / (Thymeleaf)  GET /api/stats (JSON)  │
│  dashboard.html auto-refreshes every 5s via JS fetch            │
└─────────────────────────────────────────────────────────────────┘

  ---
Kafka Nuances in This Project

Why 6 partitions for warehouse.errors?
Partitions = maximum parallelism. With concurrency=3, three consumer threads run. Six partitions means each thread owns two partitions. You could scale to
6 threads later without changing the topic. More than 6 consumer threads would leave some idle (no partition to own).

Why deviceId as the message key?
Kafka routes messages with the same key to the same partition. All events from WH-RACK-001 always land on partition 2 (for example). That means one
consumer thread owns all events for that device, so you get natural per-device ordering without needing distributed locks.

Why MANUAL_IMMEDIATE ack mode?
In RabbitMQ terms: manual ack. The consumer only commits the Kafka offset after ack.acknowledge() is called — which only happens after process() succeeds.
If processing throws, the method exits without acking, and Kafka will redeliver that message to the same consumer (or another one after rebalance). This
is equivalent to a rejected message going back to the queue in Rabbit.

MANUAL_IMMEDIATE specifically means the ack is sent to the broker immediately when ack.acknowledge() is called, rather than batched. Important for
low-latency offset commits.

Why DLQ has only 2 partitions?
DLQ traffic is a small fraction of normal traffic (only events that fail 3 DB retries). Two partitions is enough. DlqConsumer sets concurrency=2 to match
exactly.

setUseTypeHeaders(false) — when warehouse-producer serializes a WarehouseErrorEvent to JSON, Spring Kafka by default adds a __TypeId__ header with the
class name. But k6 sends raw JSON with no headers. Without this flag, the consumer's JsonDeserializer looks for the __TypeId__ header, can't find it, and
throws a type-resolution error. Setting false makes it use the declared type parameter (WarehouseErrorEvent.class) instead of the header.

  ---
Redis Nuances

Two separate Redis connections

┌──────────────────────────────────────────────┬─────────────────────────────────────┬──────────────────────────────────┬────────────────────────────┐
│                  Connection                  │               Used by               │           Key pattern            │         Value type         │
├──────────────────────────────────────────────┼─────────────────────────────────────┼──────────────────────────────────┼────────────────────────────┤
│ StringRedisTemplate (Spring Boot auto)       │ DeduplicationService,               │ warehouse:dedup:*,               │ String                     │
│                                              │ RedisStatsService                   │ warehouse:stats:*                │                            │
├──────────────────────────────────────────────┼─────────────────────────────────────┼──────────────────────────────────┼────────────────────────────┤
│ StatefulRedisConnection<String, byte[]>      │ RateLimitingService via Bucket4j    │ warehouse:ratelimit:*            │ byte[] (Bucket4j binary    │
│ (manual)                                     │                                     │                                  │ format)                    │
└──────────────────────────────────────────────┴─────────────────────────────────────┴──────────────────────────────────┴────────────────────────────┘

Bucket4j serializes its token bucket state as binary. It can't share the String-typed connection. RateLimitConfig calls
connectionFactory.getNativeClient() to grab the already-configured RedisClient (avoiding a second TCP connection pool) then opens a second logical
connection with the right codec.

Deduplication — SET NX
SET warehouse:dedup:WH-001:TEMP_THRESHOLD_EXCEEDED 1 EX 60 NX
NX = only set if Not eXists. Returns null if key already existed (= duplicate). Returns "OK" if it was new. This is atomic at the Redis level — even with
3 concurrent consumer threads, only one wins the SET and the rest see it as a duplicate. No locks needed.

Rate limiting — token bucket
Bucket4j stores the entire bucket state (token count + last refill time) as a single binary blob in Redis. tryConsume(1) does a compare-and-swap loop:
read the blob, check tokens, decrement, write back. If another thread changed it between read and write, it retries. This is why the Lettuce byte[]
connection is needed — CAS operations on serialized state.

refillGreedy(10, Duration.ofMinutes(1)) means tokens trickle back in continuously (not all at once at :00). At 10 tokens/min that's 1 token every 6
seconds. So a device that hits the limit has to wait ~6s for the next token.

Fail-open on Redis unavailability
Both DeduplicationService and RateLimitingService have catch (Exception e) { return false; } — i.e., if Redis is down, events are allowed through instead
of being blocked. This is a deliberate product decision: better to process a duplicate than to drop real alerts in a warehouse monitoring system.

  ---
Transactional + Retry Nuances (The AOP Story)

This is the most subtle part. Here's what goes wrong if you put @Retryable and @Transactional on the same method:

Attempt 1: Spring opens TX1 → DB write fails → TX1 rolled back
Attempt 2: Spring Retry fires BUT it's still inside TX1's proxy
→ the transaction was already rolled back
→ any DB work immediately fails with "transaction is not active"
Attempt 3: Same — rolled back TX, guaranteed failure

Spring AOP uses proxies. When Spring wraps a bean with @Transactional, it creates a proxy that opens/closes a transaction around method calls. When it
wraps with @Retryable, it creates a proxy that retries on exception. If both wrap the same bean, the retry proxy is outside but the transaction proxy is
inside — which means retries happen inside the same transaction context.

The fix is three separate beans:

Consumer calls → AlertProcessorService (no AOP)
└── RetryablePersistService (@Retryable proxy wraps THIS)
└── AlertPersistenceService (@Transactional proxy wraps THIS)

Attempt 1: Retry proxy calls persist() → TX proxy opens TX1 → DB fail → TX1 closed
Attempt 2: Retry proxy calls persist() AGAIN → TX proxy opens TX2 (fresh) → ...
Attempt 3: Same → TX3

Each retry goes through RetryablePersistService → AlertPersistenceService. Because AlertPersistenceService is its own bean with its own @Transactional
proxy, each call to persist() opens a brand-new transaction. That's the fix.

Why @Recover must not rethrow:
Normal failure path:
Kafka delivers message → consumer calls process() → 3 DB retries fail
→ @Recover fires → sends to DLQ → returns normally
→ consumer sees no exception → ack.acknowledge() → offset committed

If @Recover rethrew:
→ exception propagates back to consumer
→ consumer catches it → does NOT ack
→ Kafka redelivers the same message
→ Spring Retry fires again (3 more DB retries + @Recover + rethrow)
→ Kafka redelivers again → infinite loop

The DLQ is the terminal state. Once it lands there, it's done. @Recover returning normally is what signals "handled" to the Kafka consumer.