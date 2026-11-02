// FinGuard performance test suite (k6). One script, several profiles (PROFILE=...):
//
//   smoke   5 rps for 30 s: the script, auth and the pipeline work; zero errors expected.
//   load    expected peak traffic (LOAD_RPS, default 100) held for 10 min: are the SLOs met?
//   stress  steps of +STEP_RPS every 2 min from START_RPS up to MAX_RPS: where does it break?
//           Aborted early once the system has clearly failed.
//   spike   50 rps -> SPIKE_RPS within 10 s for 1 min -> back to 50: does it survive a burst
//           and how fast does the backlog drain?
//   soak    SOAK_RPS for SOAK_MIN minutes: leaks, growing tables, degrading latency.
//
// SLOs (thresholds; a breach is reported, the run is not stopped except in stress):
//   POST /api/transactions  error rate < 0.1 %, p99 < 300 ms
//   fraud decision (POST -> status leaves PENDING)  p95 < 3 s
//
// Load is an arrival rate (requests per second), so a slow server shows up as latency and
// dropped iterations instead of silently receiving fewer requests (closed-model trap).
// Started by loadtest/run.command inside the compose network; see loadtest/README.md.

import http from 'k6/http';
import { check, sleep } from 'k6';
import exec from 'k6/execution';
import { Counter, Rate, Trend } from 'k6/metrics';

const BASE = __ENV.BASE_URL || 'http://frontend:8080';
const PROFILE = __ENV.PROFILE || 'smoke';
const USERS = parseInt(__ENV.USERS || '2000', 10);
const PASSWORD = __ENV.LOADTEST_PASSWORD;
const CLIENT_ID = 'finguard-loadtest';
const env = (name, dflt) => parseInt(__ENV[name] || String(dflt), 10);

// Decision latency is sampled at a constant ~2 transactions per second whatever the load,
// so the polling it needs does not grow into a load of its own at high rates.
const TRACKED_PER_SECOND = 2;
const POLL_INTERVAL_S = 0.25;

// --- profiles: steps of { label, rate, rampS, holdS }; "measured" steps get their own row ----
function profileSteps() {
  switch (PROFILE) {
    case 'smoke':
      return [{ label: 'smoke_5rps', rate: 5, rampS: 5, holdS: 30, measured: true }];
    case 'load': {
      const r = env('LOAD_RPS', 100);
      return [
        { label: 'warmup', rate: Math.round(r / 4), rampS: 20, holdS: 20 },
        { label: `load_${r}rps`, rate: r, rampS: 60, holdS: env('LOAD_MIN', 10) * 60, measured: true },
      ];
    }
    case 'stress': {
      const steps = [{ label: 'warmup', rate: 50, rampS: 20, holdS: 20 }];
      for (let r = env('START_RPS', 100); r <= env('MAX_RPS', 600); r += env('STEP_RPS', 50)) {
        steps.push({ label: `step_${r}rps`, rate: r, rampS: 30, holdS: 90, measured: true });
      }
      return steps;
    }
    case 'spike': {
      const s = env('SPIKE_RPS', 400);
      return [
        { label: 'before_50rps', rate: 50, rampS: 20, holdS: 60, measured: true },
        { label: `spike_${s}rps`, rate: s, rampS: 10, holdS: 60, measured: true },
        { label: 'after_50rps', rate: 50, rampS: 10, holdS: 180, measured: true },
      ];
    }
    case 'soak': {
      const r = env('SOAK_RPS', 70);
      const minutes = env('SOAK_MIN', 60);
      const steps = [{ label: 'warmup', rate: Math.round(r / 4), rampS: 20, holdS: 20 }];
      // Split into 10-minute windows to see whether latency drifts over time.
      for (let m = 0; m < minutes; m += 10) {
        steps.push({ label: `soak_${m}to${Math.min(m + 10, minutes)}min`, rate: r, rampS: m === 0 ? 60 : 0,
          holdS: Math.min(10, minutes - m) * 60, measured: true });
      }
      return steps;
    }
    default:
      throw new Error(`unknown PROFILE ${PROFILE}`);
  }
}

const STEPS = profileSteps();
const stages = [];
const timeline = []; // { until (s since scenario start), label, rate }
let t = 0;
for (const s of STEPS) {
  if (s.rampS > 0) {
    stages.push({ target: s.rate, duration: `${s.rampS}s` });
    t += s.rampS;
    timeline.push({ until: t, label: 'ramp', rate: s.rate });
  }
  stages.push({ target: s.rate, duration: `${s.holdS}s` });
  t += s.holdS;
  timeline.push({ until: t, label: s.label, rate: s.rate });
}
stages.push({ target: 0, duration: '5s' });
timeline.push({ until: Infinity, label: 'rampdown', rate: 1 });
const MEASURED = STEPS.filter((s) => s.measured);

function currentStep() {
  const elapsed = (Date.now() - exec.scenario.startTime) / 1000;
  return timeline.find((s) => elapsed < s.until);
}

// --- options & SLOs ---------------------------------------------------------------------
const thresholds = {
  'http_req_failed{name:create}': ['rate<0.001'],
  'http_req_duration{name:create}': ['p(99)<300'],
  decision_latency: ['p(95)<3000'],
};
for (const s of MEASURED) {
  // Empty thresholds make k6 report these sub-metrics per step in the summary.
  thresholds[`http_req_duration{name:create,step:${s.label}}`] = [];
  thresholds[`http_reqs{name:create,step:${s.label}}`] = [];
  thresholds[`http_req_failed{name:create,step:${s.label}}`] = [];
  thresholds[`decision_latency{step:${s.label}}`] = [];
  thresholds[`decision_timeouts{step:${s.label}}`] = [];
}
if (PROFILE === 'stress') {
  // Stop once the system has clearly failed: no point in hammering a dead service for minutes.
  thresholds.dropped_iterations = [{ threshold: 'count<3000', abortOnFail: true, delayAbortEval: '30s' }];
  thresholds['http_req_failed{name:create}'] = [
    'rate<0.001',
    { threshold: 'rate<0.05', abortOnFail: true, delayAbortEval: '60s' },
  ];
}

export const options = {
  setupTimeout: '10m',
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max', 'count'],
  scenarios: {
    traffic: {
      executor: 'ramping-arrival-rate',
      startRate: 1,
      timeUnit: '1s',
      preAllocatedVUs: 100,
      // One VU = one user, as in production: users never share a session. With more VUs than
      // users, several VUs held copies of the same refresh token; the realm rotates refresh
      // tokens (revokeRefreshToken), so all but the first refresh failed with invalid_token
      // and fell back to password logins. When every VU is busy, k6 reports dropped
      // iterations, which is the saturation signal we want.
      maxVUs: USERS,
      stages,
      gracefulStop: '70s',
    },
  },
  thresholds,
};

const decisionLatency = new Trend('decision_latency', true);
const decisionTimeouts = new Counter('decision_timeouts');
const loginFailures = new Counter('login_failures');
const createStatus = new Counter('create_status');

// --- auth -------------------------------------------------------------------------------
const TOKEN_URL = `${BASE}/auth/realms/finguard/protocol/openid-connect/token`;

function username(i) {
  return `loadtest-${String(i + 1).padStart(4, '0')}@loadtest.local`;
}

function passwordLogin(i) {
  return {
    method: 'POST', url: TOKEN_URL,
    body: { grant_type: 'password', client_id: CLIENT_ID, username: username(i), password: PASSWORD },
    params: { tags: { name: 'login' }, timeout: '30s' },
  };
}

function session(res) {
  if (res.status !== 200) return null;
  // Refresh a bit before the 5-minute expiry; the jitter spreads refreshes over time instead
  // of every VU hitting Keycloak in the same second (password hashing is CPU-heavy by design).
  return {
    token: res.json('access_token'),
    refresh: res.json('refresh_token'),
    refreshAt: Date.now() + (180 + Math.random() * 60) * 1000,
  };
}

function auth(token) {
  return { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' };
}

export function setup() {
  if (!PASSWORD) throw new Error('LOADTEST_PASSWORD is not set');
  const users = [];
  for (let from = 0; from < USERS; from += 25) {
    const idx = [];
    for (let i = from; i < Math.min(from + 25, USERS); i++) idx.push(i);
    const sessions = http.batch(idx.map(passwordLogin)).map((r, k) => {
      const s = session(r);
      if (!s) throw new Error(`login failed for ${username(idx[k])}: HTTP ${r.status} ${r.body}`);
      return s;
    });

    // Right after "docker compose up" the gateway may answer before every service is ready.
    let lists = [];
    for (let attempt = 1; attempt <= 24; attempt++) {
      lists = http.batch(sessions.map((s) => ({
        method: 'GET', url: `${BASE}/api/accounts`, params: { headers: auth(s.token), tags: { name: 'setup' } },
      })));
      if (lists.every((r) => r.status === 200)) break;
      if (attempt === 24) throw new Error(`GET /api/accounts keeps failing: HTTP ${lists.find((r) => r.status !== 200).status}`);
      sleep(5);
    }
    for (let k = 0; k < idx.length; k++) {
      let account = (lists[k].json() || []).find((a) => a.currency === 'USD' && a.status === 'ACTIVE');
      if (!account) {
        const res = http.post(`${BASE}/api/accounts`, JSON.stringify({ currency: 'USD' }),
          { headers: auth(sessions[k].token), tags: { name: 'setup' } });
        if (res.status !== 201) throw new Error(`open account failed: HTTP ${res.status} ${res.body}`);
        account = res.json();
      }
      // No tokens here: setup data is copied into every VU, and 2000 VUs x 2000 sessions
      // took gigabytes. Each VU logs in as its own user on first use, like a real client.
      users.push({ i: idx[k], account: account.accountNumber });
    }
  }
  // Marks the moment the measured traffic starts; run.command uses it to cut Prometheus data.
  console.log(`SCENARIO_START ${Date.now()} profile=${PROFILE} users=${users.length} ` +
    `steps=${MEASURED.map((s) => `${s.label}@${s.rate}`).join(',')}`);
  return { users };
}

// Per-VU session cache.
const sessions = {};

function tokenFor(user) {
  const s = sessions[user.i];
  if (s && Date.now() < s.refreshAt) return s.token;
  // Refresh-token grant first (no password hashing), password login as a fallback and for
  // the VU's first request.
  let next = s ? session(http.post(TOKEN_URL,
    { grant_type: 'refresh_token', client_id: CLIENT_ID, refresh_token: s.refresh },
    { tags: { name: 'login' }, timeout: '30s' })) : null;
  if (!next) {
    const req = passwordLogin(user.i);
    next = session(http.post(req.url, req.body, req.params));
  }
  if (!next) {
    loginFailures.add(1);
    return null;
  }
  sessions[user.i] = next;
  return next.token;
}

// --- traffic ----------------------------------------------------------------------------
const CATEGORIES = ['RETAIL', 'GROCERY', 'RESTAURANTS', 'TRAVEL', 'ELECTRONICS'];

function body(user) {
  const r = Math.random();
  return JSON.stringify({
    sourceAccount: user.account,
    targetAccount: `EXT-${Math.floor(Math.random() * 1e6)}`,
    amount: (10 + Math.random() * 890).toFixed(2),
    currency: 'USD',
    country: r < 0.95 ? 'DE' : 'FR',
    merchantCategory: r < 0.02 ? 'GAMBLING' : CATEGORIES[Math.floor(Math.random() * CATEGORIES.length)],
    paymentMethod: 'CARD',
  });
}

export default function (data) {
  // Each VU acts as its own user (VU n -> user n). Picking a random user per iteration made
  // every VU keep and refresh tokens for all users: hundreds of VUs x 500 users turned token
  // refreshes into a login storm against Keycloak that had nothing to do with the system.
  const user = data.users[exec.vu.idInTest - 1];
  const step = currentStep();
  const token = tokenFor(user);
  if (!token) return;
  const headers = {
    ...auth(token),
    'Idempotency-Key': `lt-${exec.vu.idInTest}-${exec.scenario.iterationInTest}-${Date.now()}`,
    'X-Device-Id': `loadtest-device-${user.i}`,
  };

  const started = Date.now();
  const res = http.post(`${BASE}/api/transactions`, body(user),
    { headers, tags: { name: 'create', step: step.label }, timeout: '10s' });
  createStatus.add(1, { code: String(res.status), step: step.label });
  const ok = check(res, { 'created (201)': (r) => r.status === 201 });
  if (!ok || Math.random() >= TRACKED_PER_SECOND / Math.max(step.rate, 1)) return;

  // Sampled: wait for the fraud decision and measure the end-to-end time.
  const id = res.json('id');
  const deadline = started + 60 * 1000;
  while (Date.now() < deadline) {
    sleep(POLL_INTERVAL_S);
    const t2 = tokenFor(user);
    if (!t2) return;
    const poll = http.get(`${BASE}/api/transactions/${id}`,
      { headers: auth(t2), tags: { name: 'poll', step: step.label }, timeout: '10s' });
    if (poll.status === 200 && poll.json('status') !== 'PENDING') {
      decisionLatency.add(Date.now() - started, { step: step.label });
      return;
    }
  }
  decisionTimeouts.add(1, { step: step.label });
}

// --- report -----------------------------------------------------------------------------
const values = (data, name) => (data.metrics[name] ? data.metrics[name].values : {});
const ms = (v) => (v === undefined || v === null || Number.isNaN(v) ? '-' : `${Math.round(v)} ms`);
const pct = (v) => (v === undefined || v === null || Number.isNaN(v) ? '-' : `${(v * 100).toFixed(2)} %`);

export function handleSummary(data) {
  const rows = MEASURED.map((s) => {
    const d = values(data, `http_req_duration{name:create,step:${s.label}}`);
    const n = values(data, `http_reqs{name:create,step:${s.label}}`);
    const f = values(data, `http_req_failed{name:create,step:${s.label}}`);
    const dl = values(data, `decision_latency{step:${s.label}}`);
    const to = values(data, `decision_timeouts{step:${s.label}}`);
    return {
      step: s.label, target: s.rate, achieved: (n.count || 0) / s.holdS, errors: f.rate,
      p50: d.med, p95: d['p(95)'], p99: d['p(99)'], max: d.max,
      dP50: dl.med, dP95: dl['p(95)'], dMax: dl.max, dSamples: dl.count || 0, dTimeouts: to.count || 0,
    };
  });
  const slo = Object.entries(data.metrics)
    .filter(([name, m]) => m.thresholds && !name.includes('step:'))
    .map(([name, m]) => ({ metric: name, checks: Object.entries(m.thresholds).map(([k, v]) => ({ threshold: k, ok: v.ok })) }));
  const extra = {
    dropped_iterations: values(data, 'dropped_iterations').count || 0,
    login_failures: values(data, 'login_failures').count || 0,
    vus_max: values(data, 'vus_max').max,
    duration_s: Math.round(data.state.testRunDurationMs / 1000),
  };

  const lines = [`FinGuard ${PROFILE} test (${USERS} users)`, ''];
  lines.push('step               | target | achieved | errors   | p50     | p95     | p99     | decision p50 | decision p95 | timeouts');
  lines.push('-------------------|--------|----------|----------|---------|---------|---------|--------------|--------------|---------');
  for (const r of rows) {
    lines.push([r.step.padEnd(18), String(r.target).padStart(6), r.achieved.toFixed(1).padStart(8), pct(r.errors).padStart(8),
      ms(r.p50).padStart(7), ms(r.p95).padStart(7), ms(r.p99).padStart(7), ms(r.dP50).padStart(12), ms(r.dP95).padStart(12),
      String(r.dTimeouts).padStart(8)].join(' | '));
  }
  lines.push('');
  for (const s of slo) lines.push(`SLO ${s.metric}: ${s.checks.map((c) => `${c.threshold} ${c.ok ? 'PASS' : 'FAIL'}`).join(', ')}`);
  lines.push(`dropped iterations: ${extra.dropped_iterations}, login failures: ${extra.login_failures}, max VUs: ${extra.vus_max}, duration: ${extra.duration_s} s`);
  const text = lines.join('\n') + '\n';
  return {
    stdout: text,
    [`/results/${PROFILE}-summary.txt`]: text,
    [`/results/${PROFILE}-steps.json`]: JSON.stringify({ profile: PROFILE, steps: rows, slo, extra }, null, 2),
    [`/results/${PROFILE}-k6-summary.json`]: JSON.stringify(data),
  };
}
