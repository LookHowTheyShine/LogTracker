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