# warehouse-load-test

k6 load test scripts for the warehouse alerting platform.

## Prerequisites

```bash
# Install k6 (Windows)
winget install k6 --source winget
# or: choco install k6

# macOS / Linux
brew install k6
```

## Before running — make sure these are up

```
docker compose up -d          # from MetricOrg/ — starts Kafka, Redis, Postgres, Prometheus, Grafana
cd warehouse-producer      && mvn spring-boot:run   # port 8081
cd warehouse-alert-processor && mvn spring-boot:run  # port 8082
```

---

## Scripts

### 1. `load-test.js` — Main throughput test

Ramps to **250 req/s** and sustains for **3 minutes** (~45 000 events total).

```bash
k6 run load-test.js

# Override producer URL (e.g. when running in Docker):
k6 run --env BASE_URL=http://localhost:8081 load-test.js

# Export raw metrics to JSON:
k6 run --out json=results/load-test.json load-test.js
```

**Thresholds (fail the run if breached):**
- HTTP p99 < 1 000 ms
- Error rate < 1%

**After the run, verify alert-processor throughput:**
```bash
curl -s http://localhost:8082/actuator/prometheus | grep alerts_processed_total
```

---

### 2. `dedup-test.js` — Deduplication window validation

Sends the **same deviceId + errorCode** at 2 req/s for 2 minutes.
The alert-processor should SAVE the first event per 60-second window and DEDUP the rest.

```bash
k6 run dedup-test.js

# Use a custom device / error code:
k6 run --env DEVICE_ID=WH-RACK-042 --env ERROR_CODE=TEMP_THRESHOLD_EXCEEDED dedup-test.js
```

**Verify in Redis:**
```bash
docker exec -it redis redis-cli GET "warehouse:stats:deduped"
docker exec -it redis redis-cli TTL "warehouse:dedup:DEDUP-TEST-DEVICE:DEDUP_TEST_ERROR"
```

**Verify in Prometheus:**
```bash
curl -s http://localhost:8082/actuator/prometheus | grep 'alerts_processed_total.*deduped'
```

---

### 3. `rate-limit-test.js` — Rate limit burst validation

Sends **3 rounds of 15 rapid events** from a single device (limit = 10/min).
Events 11–15 in each round should be rate-limited by Bucket4j.
Each round is separated by a 65-second pause for the bucket to refill.

```bash
k6 run rate-limit-test.js
# Total runtime ≈ 3 × (15 events × 50ms) + 2 × 65s ≈ 2 min 13 sec
```

**Expected outcome per round:**
| Events | Status |
|--------|--------|
| 1–10 | `SAVED` (bucket has tokens) |
| 11–15 | `RATE_LIMITED` (bucket exhausted) |

**Verify in Redis:**
```bash
docker exec -it redis redis-cli GET "warehouse:stats:rate_limited"   # expect ~15

# Inspect bucket state (raw Bucket4j bytes — non-human-readable, but key existence confirms the bucket exists):
docker exec -it redis redis-cli EXISTS "warehouse:ratelimit:RATE-LIMIT-DEVICE"
```

**Verify in Prometheus:**
```bash
curl -s http://localhost:8082/actuator/prometheus | grep rate_limit_hits_total
```

---

## Reading Grafana panels during a load test

Open http://localhost:3000 (admin/admin) while `load-test.js` is running:

| Panel | What to watch |
|-------|--------------|
| Events/min throughput | Should reach ~15 000/min at peak |
| p99 processing latency | Target < 45 ms (alert-processor internal) |
| Alert outcome breakdown | Mix of saved/deduped/rate_limited |
| Consumer lag | Should stay near 0 — processor keeps up |
| DLQ event rate | Should be 0 under normal conditions |

---

## Results

k6 writes JSON summaries to `results/` after each run (created automatically).
