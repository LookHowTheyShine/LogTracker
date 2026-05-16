/**
 * Warehouse Platform — Rate Limit Validation Test
 *
 * Sends bursts of events from a single device to validate Bucket4j enforcement.
 * Rate limit: 10 events per device per minute (greedy refill).
 *
 * Three burst rounds, each separated by a 65s pause (bucket refill period + 5s buffer):
 *
 *   Round 1:  15 rapid events from RATE-LIMIT-DEVICE
 *             → events 1-10:  SAVED  (bucket has tokens)
 *             → events 11-15: RATE_LIMITED (bucket exhausted)
 *
 *   [wait 65s — bucket refills to 10 tokens]
 *
 *   Round 2:  15 rapid events
 *             → same pattern: 10 SAVED, 5 RATE_LIMITED
 *
 *   [wait 65s]
 *
 *   Round 3:  15 rapid events (confirms behaviour is stable)
 *
 * The producer always returns 202 — rate limiting happens inside the
 * alert-processor. Verify the metric/Redis counter after the test.
 *
 * Verify after run:
 *   # Redis rate-limit counter
 *   docker exec -it redis redis-cli GET "warehouse:stats:rate_limited"
 *   # Expected: ~15 (5 per round × 3 rounds)
 *
 *   # Prometheus metric
 *   curl http://localhost:8082/actuator/prometheus | grep rate_limit_hits
 *
 *   # Remaining tokens after a burst (should be 0)
 *   docker exec -it redis redis-cli GET "warehouse:ratelimit:RATE-LIMIT-DEVICE"
 *
 * Run:
 *   k6 run rate-limit-test.js
 *   k6 run --env BASE_URL=http://localhost:8081 rate-limit-test.js
 */

import http  from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081';
const DEVICE   = 'RATE-LIMIT-DEVICE';

const BURST_SIZE   = 15;
const ROUNDS       = 3;
const REFILL_PAUSE = 65; // seconds — slightly more than the 60s bucket refill window

const accepted = new Counter('ratelimit_requests_accepted');
const errors   = new Rate('ratelimit_error_rate');

export const options = {
    scenarios: {
        // Single VU executes all rounds sequentially with pauses between them
        burst_rounds: {
            executor: 'per-vu-iterations',
            vus:        1,
            iterations: 1,   // the default function runs once and controls the loop
        },
    },
    thresholds: {
        http_req_failed:       ['rate<0.01'],
        ratelimit_error_rate:  ['rate<0.01'],
    },
};

const EVENT = JSON.stringify({
    deviceId:      DEVICE,
    warehouseZone: 'ZONE-BURST',
    errorCode:     'RATE_LIMIT_TEST_ERROR',
    severity:      'HIGH',
    payload:       { temperature: 39.0, threshold: 35.0 },
});

const HEADERS = { headers: { 'Content-Type': 'application/json' }, timeout: '10s' };

export default function () {
    for (let round = 1; round <= ROUNDS; round++) {
        console.log(`\n--- Round ${round}/${ROUNDS}: sending ${BURST_SIZE} events from ${DEVICE} ---`);

        for (let i = 1; i <= BURST_SIZE; i++) {
            const res = http.post(`${BASE_URL}/api/events`, EVENT, HEADERS);

            const ok = check(res, { 'status 202': (r) => r.status === 202 });
            errors.add(!ok);
            if (ok) accepted.add(1);

            const expected = i <= 10 ? 'SAVED (expected)' : 'RATE_LIMITED (expected)';
            console.log(`  Event ${String(i).padStart(2, '0')}: HTTP ${res.status} → alert-processor should mark as ${expected}`);

            // Small gap between requests in a burst (not zero — avoids connection pool saturation)
            sleep(0.05); // 50ms between requests within a burst
        }

        if (round < ROUNDS) {
            console.log(`\nPausing ${REFILL_PAUSE}s for Bucket4j to refill (rate limit window resets)...`);
            sleep(REFILL_PAUSE);
        }
    }
}

export function handleSummary(data) {
    const total   = (data.metrics['ratelimit_requests_accepted']?.values?.count || 0).toFixed(0);
    const errRate = ((data.metrics['ratelimit_error_rate']?.values?.rate || 0) * 100).toFixed(2);

    const expectedSaved   = ROUNDS * 10;
    const expectedLimited = ROUNDS * (BURST_SIZE - 10);

    console.log('\n╔══════════════════════════════════════════════════════╗');
    console.log('║         RATE LIMIT TEST — SUMMARY                    ║');
    console.log('╠══════════════════════════════════════════════════════╣');
    console.log(`║  Device        : ${DEVICE.padEnd(34)}║`);
    console.log(`║  Rounds        : ${String(ROUNDS).padEnd(34)}║`);
    console.log(`║  Burst size    : ${String(BURST_SIZE + ' events/round').padEnd(34)}║`);
    console.log(`║  HTTP accepted : ${String(total).padEnd(34)}║`);
    console.log(`║  HTTP errors   : ${String(errRate + '%').padEnd(34)}║`);
    console.log('╠══════════════════════════════════════════════════════╣');
    console.log('║  Expected in alert-processor:                         ║');
    console.log(`║    SAVED        ≈ ${String(expectedSaved + ' (10 per round)').padEnd(33)}║`);
    console.log(`║    RATE_LIMITED ≈ ${String(expectedLimited + ' (5 per round)').padEnd(33)}║`);
    console.log('╠══════════════════════════════════════════════════════╣');
    console.log('║  Verify:                                              ║');
    console.log('║    docker exec -it redis redis-cli \\                 ║');
    console.log('║      GET "warehouse:stats:rate_limited"              ║');
    console.log(`║    Expected value: ~${String(expectedLimited).padEnd(31)}║`);
    console.log('╚══════════════════════════════════════════════════════╝\n');

    return {
        'results/rate-limit-test-summary.json': JSON.stringify(data, null, 2),
    };
}
