/**
 * Warehouse Platform — Main Throughput Load Test
 *
 * Target:  250 req/s sustained for 3 minutes (= 45 000 events)
 * Ramp:    0 → 50 req/s (30s warm-up) → 250 req/s (60s ramp) → 250 req/s (3m peak) → 0 (30s cool-down)
 *
 * What it verifies:
 *   - Producer can sustain 250 req/s without saturation
 *   - p99 HTTP latency stays under 1 000 ms at peak
 *   - Error rate stays under 1%
 *   - Alert-processor throughput visible in Prometheus (alerts_processed_total)
 *
 * Run:
 *   k6 run load-test.js
 *   k6 run --env BASE_URL=http://localhost:8081 load-test.js
 *   k6 run --out json=results/load-test.json load-test.js
 */

import http  from 'k6/http';
import { check }              from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

// ── Config ────────────────────────────────────────────────────────────────────

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081';

const DEVICES = Array.from({ length: 100 }, (_, i) =>
    `WH-RACK-${String(i + 1).padStart(3, '0')}`);

const ZONES       = ['ZONE-A', 'ZONE-B', 'ZONE-C', 'ZONE-D', 'ZONE-E'];
const ERROR_CODES = [
    'TEMP_THRESHOLD_EXCEEDED',
    'HUMIDITY_HIGH',
    'DOOR_OPEN',
    'CONVEYOR_STALL',
    'POWER_FAULT',
];
const SEVERITIES = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'];

// ── Custom metrics ────────────────────────────────────────────────────────────

const errorRate    = new Rate('warehouse_error_rate');
const eventLatency = new Trend('warehouse_event_latency_ms', true);
const published    = new Counter('warehouse_events_published');

// ── Scenario ──────────────────────────────────────────────────────────────────

export const options = {
    scenarios: {
        peak_throughput: {
            executor:        'ramping-arrival-rate',
            startRate:       0,
            timeUnit:        '1s',
            preAllocatedVUs: 50,
            maxVUs:          500,
            stages: [
                { target: 50,  duration: '30s' },  // warm-up
                { target: 250, duration: '60s' },  // ramp to 250 req/s
                { target: 250, duration: '3m'  },  // sustain peak (45 000 events)
                { target: 0,   duration: '30s' },  // cool-down
            ],
        },
    },
    thresholds: {
        // HTTP-level thresholds (producer endpoint)
        http_req_duration:          ['p(50)<200', 'p(95)<500', 'p(99)<1000'],
        http_req_failed:            ['rate<0.01'],
        // Custom thresholds
        warehouse_error_rate:       ['rate<0.01'],
        warehouse_event_latency_ms: ['p(99)<1000'],
    },
};

// ── Default function (runs per VU iteration) ──────────────────────────────────

export default function () {
    const event = buildRandomEvent();

    const start = Date.now();
    const res   = http.post(
        `${BASE_URL}/api/events`,
        JSON.stringify(event),
        { headers: { 'Content-Type': 'application/json' }, timeout: '10s' }
    );
    const elapsed = Date.now() - start;

    const ok = check(res, {
        'status 202':  (r) => r.status === 202,
        'has eventId': (r) => {
            try { return Boolean(JSON.parse(r.body).eventId); }
            catch (_) { return false; }
        },
    });

    errorRate.add(!ok);
    eventLatency.add(elapsed);
    if (ok) published.add(1);
}

// ── Summary ───────────────────────────────────────────────────────────────────

export function handleSummary(data) {
    const metrics = data.metrics;

    const fmt = (key, stat) => {
        const m = metrics[key];
        return m ? `${(m.values[stat] || 0).toFixed(2)}` : 'n/a';
    };

    console.log('\n╔══════════════════════════════════════════════════════╗');
    console.log('║       WAREHOUSE LOAD TEST — SUMMARY                  ║');
    console.log('╠══════════════════════════════════════════════════════╣');
    console.log(`║  Target rate  : 250 req/s  │  Peak duration: 3 min   ║`);
    console.log(`║  Events sent  : ${String(fmt('warehouse_events_published', 'count')).padEnd(36)}║`);
    console.log(`║  Error rate   : ${String(fmt('warehouse_error_rate', 'rate') + '%').padEnd(36)}║`);
    console.log('╠══════════════════════════════════════════════════════╣');
    console.log(`║  HTTP latency (ms)                                    ║`);
    console.log(`║    p50  : ${String(fmt('http_req_duration', 'p(50)')).padEnd(43)}║`);
    console.log(`║    p95  : ${String(fmt('http_req_duration', 'p(95)')).padEnd(43)}║`);
    console.log(`║    p99  : ${String(fmt('http_req_duration', 'p(99)')).padEnd(43)}║`);
    console.log('╠══════════════════════════════════════════════════════╣');
    console.log('║  Post-run checks (alert-processor metrics):           ║');
    console.log('║    curl http://localhost:8082/actuator/prometheus \\   ║');
    console.log('║      | grep alerts_processed                          ║');
    console.log('╚══════════════════════════════════════════════════════╝\n');

    return {
        'results/load-test-summary.json': JSON.stringify(data, null, 2),
    };
}

// ── Helpers ───────────────────────────────────────────────────────────────────

function randomItem(arr) {
    return arr[Math.floor(Math.random() * arr.length)];
}

function buildRandomEvent() {
    return {
        deviceId:      randomItem(DEVICES),
        warehouseZone: randomItem(ZONES),
        errorCode:     randomItem(ERROR_CODES),
        severity:      randomItem(SEVERITIES),
        payload: {
            temperature: parseFloat((20 + Math.random() * 20).toFixed(1)),
            threshold:   35.0,
            humidity:    parseFloat((40 + Math.random() * 40).toFixed(1)),
        },
    };
}
