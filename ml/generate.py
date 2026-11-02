"""Synthetic labelled traffic for training the fraud model.

Simulated customers make ordinary payments through the real API; a share of them are hit by a
fraud pattern. Every transaction goes through the whole pipeline, so fraud_analyses.features holds
exactly what the service saw, and its label (fraud or not, scenario) is written to
fraud.fraud_labels with source SYNTHETIC.

Patterns (labels in brackets):
  normal           [legit] everyday payments: home country, known device, the customer's usual
                           amounts; some customers also make many tiny ones (coffee, transport)
  travel           [legit] a few payments abroad from a known device
  new_phone        [legit] payments from a new device at home
  big_purchase     [legit] one purchase several times the usual amount
  shopping_spree   [legit] a quick series of ordinary purchases
  business         [legit] small-business owners: several large payments in a short time
  account_takeover [fraud] new device, often (not always) abroad, a quick series of purchases a few
                           times the victim's usual amount, in everyday as well as risky categories
  card_testing     [fraud] new device, a burst of small payments in everyday categories
  structuring      [fraud] several payments just below the high-amount threshold
  quiet_fraud      [fraud] one unusually large purchase from home and a known device (hard on purpose)

Legitimate and fraudulent patterns overlap on purpose (large amounts, bursts, new devices, foreign
countries, everyday categories): a model must combine signals instead of keying on one of them.

Realism is limited by what the API lets a client set: the time is the server's, the IP is the
generator's. The scenarios only shape what a client controls: amount, country, category, device.

Run through ml/run.sh generate (inside the compose network).
"""

import asyncio
import csv
import math
import os
import random
import sys
import time
import uuid
from dataclasses import dataclass, field

import httpx
import psycopg

BASE = os.environ.get("BASE_URL", "http://frontend:8080")
CLIENT_ID = os.environ["CLIENT_ID"]
PASSWORD = os.environ["LOADTEST_PASSWORD"]
PREFIX = os.environ.get("USER_PREFIX", "mlgen")
USERS = int(os.environ.get("USERS", "1500"))
DURATION_S = float(os.environ.get("DURATION_MIN", "30")) * 60
FRAUD_USERS = float(os.environ.get("FRAUD_USER_SHARE", "0.12"))
SEED = int(os.environ.get("SEED", "42"))
PG_DSN = os.environ["PG_DSN"]
OUT = os.environ.get("LABELS_CSV", "/ml/data/labels.csv")

TOKEN_URL = f"{BASE}/auth/realms/finguard/protocol/openid-connect/token"
HOME_COUNTRIES = (["DE"] * 40 + ["FR"] * 15 + ["PL"] * 10 + ["NL"] * 10 + ["GB"] * 10 + ["US"] * 10 + ["ES"] * 5)
ABROAD = ["IT", "ES", "PT", "GR", "TR", "AE", "TH", "US", "GB", "FR", "DE"]
FRAUD_COUNTRIES = ["NG", "RO", "UA", "BR", "VN", "RU", "ID", "PH"]
EVERYDAY = ["GROCERY", "RESTAURANTS", "RETAIL", "TRAVEL", "ELECTRONICS"]
RISKY = ["ELECTRONICS", "GAMBLING", "CRYPTO", "RETAIL"]


@dataclass
class Tx:
    amount: float
    country: str
    category: str
    device: str
    fraud: bool
    scenario: str
    gap_s: float = 0.0  # pause before this payment within an episode


@dataclass
class Customer:
    index: int
    rng: random.Random
    home: str = ""
    devices: list = field(default_factory=list)
    mean_amount: float = 0.0
    categories: list = field(default_factory=list)
    tiny_payer: bool = False
    business: bool = False

    @property
    def username(self):
        return f"{PREFIX}-{self.index:04d}@loadtest.local"

    def usual_amount(self):
        return round(max(1.0, self.rng.lognormvariate(math.log(self.mean_amount), 0.6)), 2)

    def new_device(self, tag):
        return f"{PREFIX}-{self.index}-{tag}-{self.rng.randrange(10**6)}"


def make_customer(i, seed):
    rng = random.Random(seed * 100_003 + i)
    c = Customer(i, rng)
    c.home = rng.choice(HOME_COUNTRIES)
    c.devices = [f"{PREFIX}-{i}-dev{k}" for k in range(rng.choice([1, 1, 2]))]
    c.mean_amount = rng.lognormvariate(math.log(60), 0.7)
    # Each customer favours a few categories; a few legitimately gamble.
    weights = [rng.random() for _ in EVERYDAY]
    c.categories = [cat for cat, _ in sorted(zip(EVERYDAY, weights), key=lambda p: -p[1])[:3]]
    if rng.random() < 0.05:
        c.categories.append("GAMBLING")
    c.tiny_payer = rng.random() < 0.3
    c.business = rng.random() < 0.05
    return c


def episode(c: Customer, kind: str):
    r = c.rng
    home_dev = r.choice(c.devices)
    if kind == "normal":
        if c.tiny_payer and r.random() < 0.4:
            return [Tx(round(r.uniform(1.5, 9), 2), c.home, r.choice(["RESTAURANTS", "RETAIL", "GROCERY"]),
                       home_dev, False, kind)]
        return [Tx(c.usual_amount(), c.home, r.choice(c.categories), home_dev, False, kind)]
    if kind == "travel":
        where = r.choice([x for x in ABROAD if x != c.home])
        return [Tx(c.usual_amount(), where, r.choice(["RESTAURANTS", "TRAVEL", "RETAIL"]), home_dev, False, kind,
                   gap_s=r.uniform(60, 240)) for _ in range(r.randint(1, 3))]
    if kind == "new_phone":
        dev = c.new_device("phone")
        c.devices.append(dev)
        return [Tx(c.usual_amount(), c.home, r.choice(c.categories), dev, False, kind, gap_s=r.uniform(60, 300))
                for _ in range(r.randint(1, 2))]
    if kind == "big_purchase":
        return [Tx(round(c.mean_amount * r.uniform(3, 12), 2), c.home, r.choice(EVERYDAY), home_dev, False, kind)]
    if kind == "shopping_spree":
        return [Tx(round(c.usual_amount() * r.uniform(1, 3), 2), c.home, r.choice(c.categories + ["ELECTRONICS"]),
                   home_dev, False, kind, gap_s=r.uniform(20, 120)) for _ in range(r.randint(3, 6))]
    if kind == "business":
        return [Tx(round(r.uniform(1500, 9500), 2), c.home, r.choice(["RETAIL", "ELECTRONICS", "TRAVEL"]),
                   home_dev, False, kind, gap_s=r.uniform(30, 180)) for _ in range(r.randint(2, 5))]
    if kind == "account_takeover":
        dev = c.new_device("ato")
        where = r.choices([c.home, r.choice(ABROAD), r.choice(FRAUD_COUNTRIES)], [5, 3, 2])[0]
        return [Tx(round(c.mean_amount * r.uniform(2, 10), 2), where, r.choice(EVERYDAY + RISKY), dev, True, kind,
                   gap_s=r.uniform(10, 120)) for _ in range(r.randint(2, 6))]
    if kind == "card_testing":
        dev = c.new_device("ct")
        where = r.choice([c.home, c.home] + FRAUD_COUNTRIES)
        return [Tx(round(r.uniform(1, 15), 2), where, r.choice(["RETAIL", "GROCERY", "RESTAURANTS"]), dev, True,
                   kind, gap_s=r.uniform(3, 40)) for _ in range(r.randint(4, 9))]
    if kind == "structuring":
        dev = home_dev if r.random() < 0.6 else c.new_device("st")
        return [Tx(round(r.uniform(2500, 9900), 2), c.home, r.choice(["RETAIL", "ELECTRONICS", "TRAVEL"]),
                   dev, True, kind, gap_s=r.uniform(20, 120)) for _ in range(r.randint(3, 5))]
    if kind == "quiet_fraud":
        return [Tx(round(c.mean_amount * r.uniform(4, 12), 2), c.home, r.choice(EVERYDAY), home_dev, True, kind)]
    raise ValueError(kind)


def schedule(c: Customer, fraud_user: bool):
    """(start offset in seconds, payments) episodes of one customer, in time order."""
    r = c.rng
    plan = [(r.uniform(0, DURATION_S), "normal") for _ in range(max(1, int(r.gauss(8, 3))))]
    for kind, p in (("travel", 0.10), ("new_phone", 0.08), ("big_purchase", 0.15), ("shopping_spree", 0.12)):
        if r.random() < p:
            plan.append((r.uniform(0, DURATION_S), kind))
    if c.business:
        plan.append((r.uniform(0, DURATION_S), "business"))
    if fraud_user:
        # After some ordinary history, so the account looks established.
        kind = r.choices(["account_takeover", "card_testing", "structuring", "quiet_fraud"], [4, 3, 2, 2])[0]
        plan.append((r.uniform(0.3, 0.9) * DURATION_S, kind))
    plan.sort()
    return [(start, episode(c, kind)) for start, kind in plan]


class Session:
    """One customer's login; refreshes the token before it expires, logs in again if needed."""

    def __init__(self, http, username):
        self.http, self.username = http, username
        self.token, self.refresh, self.expires = None, None, 0.0

    async def bearer(self):
        if self.token and time.monotonic() < self.expires:
            return self.token
        data = None
        if self.refresh:
            r = await self.http.post(TOKEN_URL, data={"grant_type": "refresh_token", "client_id": CLIENT_ID,
                                                      "refresh_token": self.refresh})
            data = r.json() if r.status_code == 200 else None
        if data is None:
            r = await self.http.post(TOKEN_URL, data={"grant_type": "password", "client_id": CLIENT_ID,
                                                      "username": self.username, "password": PASSWORD})
            r.raise_for_status()
            data = r.json()
        self.token, self.refresh = data["access_token"], data.get("refresh_token")
        self.expires = time.monotonic() + data.get("expires_in", 300) - 60
        return self.token


async def account_of(http, session):
    headers = {"Authorization": f"Bearer {await session.bearer()}"}
    r = await http.get(f"{BASE}/api/accounts", headers=headers)
    r.raise_for_status()
    for a in r.json():
        if a.get("currency") == "USD" and a.get("status") == "ACTIVE":
            return a["accountNumber"]
    r = await http.post(f"{BASE}/api/accounts", json={"currency": "USD"}, headers=headers)
    r.raise_for_status()
    return r.json()["accountNumber"]


async def run_customer(http, c, fraud_user, t0, labels, stats):
    plan = schedule(c, fraud_user)
    session, account = Session(http, c.username), None
    for start, payments in plan:
        await asyncio.sleep(max(0.0, t0 + start - time.monotonic()))
        for tx in payments:
            await asyncio.sleep(tx.gap_s)
            try:
                account = account or await account_of(http, session)
                r = await http.post(f"{BASE}/api/transactions", json={
                    "sourceAccount": account, "targetAccount": f"EXT-{c.rng.randrange(10**6)}",
                    "amount": f"{tx.amount:.2f}", "currency": "USD", "country": tx.country,
                    "merchantCategory": tx.category, "paymentMethod": "CARD",
                }, headers={"Authorization": f"Bearer {await session.bearer()}",
                            "Idempotency-Key": str(uuid.uuid4()), "X-Device-Id": tx.device})
                if r.status_code != 201:
                    stats["failed"] += 1
                    if stats["failed"] <= 5:
                        print(f"POST failed: HTTP {r.status_code} {r.text[:200]}", flush=True)
                    continue
                labels.append((r.json()["id"], tx.fraud, tx.scenario))
                stats["fraud" if tx.fraud else "legit"] += 1
            except (httpx.HTTPError, KeyError, ValueError) as e:
                stats["failed"] += 1
                if stats["failed"] <= 5:
                    print(f"request error for {c.username}: {e!r}", flush=True)


async def progress(stats, t0):
    while True:
        await asyncio.sleep(60)
        print(f"  {(time.monotonic() - t0) / 60:5.1f} min: {stats['legit']} legit, {stats['fraud']} fraud, "
              f"{stats['failed']} failed", flush=True)


def store_labels(labels):
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["transaction_id", "fraud", "scenario"])
        w.writerows(labels)
    with psycopg.connect(PG_DSN) as conn, conn.cursor() as cur:
        cur.executemany(
            """INSERT INTO fraud.fraud_labels (transaction_id, fraud, source, scenario)
               VALUES (%s, %s, 'SYNTHETIC', %s)
               ON CONFLICT (transaction_id) DO UPDATE
                 SET fraud = EXCLUDED.fraud, scenario = EXCLUDED.scenario, labeled_at = now()
               WHERE fraud_labels.source = 'SYNTHETIC'""",
            labels)
    print(f"{len(labels)} labels stored in fraud.fraud_labels and {OUT}", flush=True)


async def main():
    rng = random.Random(SEED)
    customers = [make_customer(i, SEED) for i in range(1, USERS + 1)]
    fraud_users = {c.index for c in customers if rng.random() < FRAUD_USERS}
    print(f"{USERS} customers, {len(fraud_users)} of them hit by fraud, {DURATION_S / 60:.0f} minutes", flush=True)

    labels, stats = [], {"legit": 0, "fraud": 0, "failed": 0}
    limits = httpx.Limits(max_connections=200, max_keepalive_connections=200)
    async with httpx.AsyncClient(timeout=30, limits=limits) as http:
        t0 = time.monotonic()
        ticker = asyncio.create_task(progress(stats, t0))
        await asyncio.gather(*(run_customer(http, c, c.index in fraud_users, t0, labels, stats) for c in customers))
        ticker.cancel()
    print(f"done: {stats['legit']} legit, {stats['fraud']} fraud, {stats['failed']} failed", flush=True)
    store_labels(labels)
    return 0 if stats["failed"] < 0.01 * max(1, len(labels)) else 1


if __name__ == "__main__":
    sys.exit(asyncio.run(main()))
