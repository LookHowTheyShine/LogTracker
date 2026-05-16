/**
 * Warehouse Platform — Deduplication Validation Test
 *
 * Sends the same deviceId + errorCode repeatedly to the producer.
 * The alert-processor should SAVE the first event and DEDUP all subsequent
 * ones within the 60-second window.
 *
 * Timeline:
 *   0s         → first event sent → alert-processor: SAVED, Redis key set (TTL 60s)
 *   1s … 59s   → events 2-N sent  → alert-processor: DEDUPED (Redis key exists)
 *   ~60s       → Redis key expires (dedup window resets)
 *   61s        → next event       → alert-processor: SAVED again
 *   62s … 90s  → DEDUPED again
 *
 * Verify after run:
 *   # Redis dedup counter
 *   docker exec -it redis redis-cli GET "warehouse:stats:deduped"
 *
 *   # Prometheus metric
 *   curl http://localhost:8082/actuator/prometheus | grep 'alerts_processed_total.*deduped'
 *
 *   # Alert-processor log should show:
 *   #   "Suppressed duplicate: dedupKey=DEDUP-TEST-DEVICE:DEDUP_TEST_ERROR ttlRemaining=Xs"
 *
 * Run:
 *   k6 run dedup-test.js
 *   k6 run --env BASE_URL=http://localhost:8081 dedup-test.js
 */

import http  from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate } from 'k6/metrics';

const BASE_URL    = __ENV.BASE_URL    || 'http://localhost:8081';
const DEVICE_ID   = __ENV.DEVICE_ID  || 'DEDUP-TEST-DEVICE';
const ERROR_CODE  = __ENV.ERROR_CODE || 'DEDUP_TEST_ERROR';

const accepted = new Counter('dedup_accepted');
const errors   = new Rate('dedup_error_rate');

export const options = {
    scenarios: {
        dedup_window: {
            executor:  'constant-arrival-rate',
            rate:      2,          // 2 req/s — slow enough to see individual events in logs
            timeUnit:  '1s',
            duration:  '2m',       // 2 minutes → spans 2 full 60s dedup windows
            preAllocatedVUs: 2,
            maxVUs:          5,
        },
    },
    thresholds: {
        http_req_failed:  ['rate<0.01'],
        dedup_error_rate: ['rate<0.01'],
    },
};

// Fixed event — same deviceId + errorCode every iteration
const FIXED_EVENT = JSON.stringify({
    deviceId:      DEVICE_ID,
    warehouseZone: 'ZONE-DEDUP',
    errorCode:     ERROR_CODE,
    severity:      'HIGH',
    payload: { temperature: 38.5, threshold: 35.0 },
});

export default function () {
    const res = http.post(
        `${BASE_URL}/api/events`,
        FIXED_EVENT,
        { headers: { 'Content-Type': 'application/json' }, timeout: '10s' }
    );

    const ok = check(res, {
        'status 202': (r) => r.status === 202,
    });

    errors.add(!ok);
    if (ok) accepted.add(1);
}

export function handleSummary(data) {
    const total    = (data.metrics['dedup_accepted']?.values?.count  || 0).toFixed(0);
    const errRate  = ((data.metrics['dedup_error_rate']?.values?.rate || 0) * 100).toFixed(2);

    console.log('\n╔══════════════════════════════════════════════════════╗');
    console.log('║         DEDUPLICATION TEST — SUMMARY                 ║');
    console.log('╠══════════════════════════════════════════════════════╣');
    console.log(`║  Device ID     : ${DEVICE_ID.padEnd(34)}║`);
    console.log(`║  Error code    : ${ERROR_CODE.padEnd(34)}║`);
    console.log(`║  Requests sent : ${String(total).padEnd(34)}║`);
    console.log(`║  HTTP errors   : ${String(errRate + '%').padEnd(34)}║`);
    console.log('╠══════════════════════════════════════════════════════╣');
    console.log('║  Expected in alert-processor:                         ║');
    console.log('║    SAVED  ≈ 2  (one per 60s window)                  ║');
    console.log(`║    DEDUPED ≈ ${String(Number(total) - 2).padEnd(38)}║`);
    console.log('╠══════════════════════════════════════════════════════╣');
    console.log('║  Verify:                                              ║');
    console.log('║    docker exec -it redis redis-cli \\                 ║');
    console.log('║      GET "warehouse:stats:deduped"                   ║');
    console.log('╚══════════════════════════════════════════════════════╝\n');

    return {
        'results/dedup-test-summary.json': JSON.stringify(data, null, 2),
    };
}
