"""Trains the fraud model on labelled analyses and ships it to fraud-detection-service.

Input: fraud_analyses.features (what the service saw at scoring time) joined with
fraud_labels (ANALYST verdicts and SYNTHETIC labels).

Output:
  fraud-detection-service/src/main/resources/ml/model.json         LightGBM dump, scored by TreeEnsemble
  fraud-detection-service/src/main/resources/ml/feature_spec.json  raw features -> input vector
  fraud-detection-service/src/test/resources/ml/parity_vectors.json  held-out rows + LightGBM scores
  ml/reports/<model_version>.md                                    metrics vs the rule engine

The split is by time (oldest 75 % train, newest 25 % test), like a model trained today and used
tomorrow. The rule engine is evaluated on the same test rows as the baseline.

Run through ml/run.sh train.
"""

import datetime as dt
import json
import math
import os
import sys

import lightgbm as lgb
import numpy as np
import psycopg
from sklearn.metrics import average_precision_score, precision_score, recall_score, roc_auc_score

REPO = os.environ.get("REPO", "/repo")
PG_DSN = os.environ["PG_DSN"]
SERVICE = f"{REPO}/fraud-detection-service/src"
MIN_CATEGORY_COUNT = 20

# Numeric raw features (see MlFeatures.java); categoricals are one-hot encoded below.
NUMERIC = ["has_device", "rules_failed", "amount_to_threshold", "velocity_tx_in_window",
           "structuring_payments_in_window"]
CATEGORICAL = ["country", "merchant_category", "payment_method", "currency"]


def load():
    with psycopg.connect(PG_DSN) as conn, conn.cursor() as cur:
        cur.execute("""
            SELECT a.transaction_id::text, a.features, a.risk_score, a.decision, l.fraud, l.scenario
            FROM fraud.fraud_analyses a JOIN fraud.fraud_labels l USING (transaction_id)
            WHERE a.features IS NOT NULL
            ORDER BY a.analyzed_at""")
        return cur.fetchall()


def build_spec(train_rows, version):
    rule_flags = sorted({k for _, f, *_ in train_rows for k in f if k.startswith("rule_")})
    columns = [{"name": "amount_log1p", "source": "amount", "kind": "log1p", "value": None}]
    columns += [{"name": n, "source": n, "kind": "numeric", "value": None} for n in NUMERIC + rule_flags]
    for cat in CATEGORICAL:
        counts = {}
        for _, f, *_ in train_rows:
            v = f.get(cat)
            if v is not None:
                counts[str(v)] = counts.get(str(v), 0) + 1
        values = sorted(v for v, n in counts.items() if n >= MIN_CATEGORY_COUNT)
        if len(values) > 1:  # a constant column carries no information
            columns += [{"name": f"{cat}={v}", "source": cat, "kind": "onehot", "value": v} for v in values]
    return {"model_version": version, "features": columns}


def encode(spec, raw):
    """Must match FeatureSpec.encode in Java (checked by TreeEnsembleParityTest)."""
    out = []
    for c in spec["features"]:
        v = raw.get(c["source"])
        num = float(v) if isinstance(v, (int, float)) and not isinstance(v, bool) else 0.0
        if c["kind"] == "numeric":
            out.append(num)
        elif c["kind"] == "log1p":
            out.append(math.log1p(max(0.0, num)))
        else:
            out.append(1.0 if v is not None and str(v) == c["value"] else 0.0)
    return out


def at_alert_rate(y, scores, rate):
    """Precision and recall when the top `rate` share of transactions is flagged."""
    k = max(1, int(round(rate * len(scores))))
    flagged = np.zeros(len(scores), dtype=bool)
    flagged[np.argsort(-scores, kind="stable")[:k]] = True
    return precision_score(y, flagged, zero_division=0), recall_score(y, flagged, zero_division=0)


def main():
    rows = load()
    n_fraud = sum(1 for r in rows if r[4])
    print(f"{len(rows)} labelled analyses, {n_fraud} fraud")
    if len(rows) < 1000 or n_fraud < 50:
        print("not enough labelled data (need >= 1000 rows and >= 50 fraud); run ml/run.sh generate first")
        return 1

    cut = int(len(rows) * 0.75)
    train_rows, test_rows = rows[:cut], rows[cut:]
    version = "lgbm-" + dt.datetime.now(dt.timezone.utc).strftime("%Y%m%d-%H%M")
    spec = build_spec(train_rows, version)

    def matrix(part):
        return (np.array([encode(spec, r[1]) for r in part], dtype=np.float64),
                np.array([1 if r[4] else 0 for r in part]))

    x_train, y_train = matrix(train_rows)
    x_test, y_test = matrix(test_rows)
    names = [c["name"] for c in spec["features"]]

    # Last 15 % of the training period for early stopping.
    val = int(len(x_train) * 0.85)
    params = {"objective": "binary", "learning_rate": 0.05, "num_leaves": 15, "min_data_in_leaf": 30,
              "feature_fraction": 0.9, "bagging_fraction": 0.9, "bagging_freq": 1, "lambda_l2": 1.0,
              "is_unbalance": True, "seed": 7, "deterministic": True, "verbose": -1,
              # Early stopping on PR-AUC: with is_unbalance the log loss starts rising after a few
              # trees (the reweighted probabilities drift), which stopped training at 3 trees.
              "metric": "average_precision", "first_metric_only": True}
    booster = lgb.train(params, lgb.Dataset(x_train[:val], y_train[:val], feature_name=names),
                        num_boost_round=600,
                        valid_sets=[lgb.Dataset(x_train[val:], y_train[val:], feature_name=names)],
                        callbacks=[lgb.early_stopping(80, verbose=False)])
    p_test = booster.predict(x_test, num_iteration=booster.best_iteration)

    # Ship exactly the trees used for p_test.
    dump = booster.dump_model(num_iteration=booster.best_iteration)
    os.makedirs(f"{SERVICE}/main/resources/ml", exist_ok=True)
    os.makedirs(f"{SERVICE}/test/resources/ml", exist_ok=True)
    with open(f"{SERVICE}/main/resources/ml/model.json", "w") as f:
        json.dump(dump, f)
    with open(f"{SERVICE}/main/resources/ml/feature_spec.json", "w") as f:
        json.dump(spec, f, indent=2)
    sample = list(range(0, len(test_rows), max(1, len(test_rows) // 300)))
    with open(f"{SERVICE}/test/resources/ml/parity_vectors.json", "w") as f:
        json.dump([{"transaction_id": test_rows[i][0], "features": test_rows[i][1], "lightgbm": float(p_test[i])}
                   for i in sample], f)

    # --- evaluation against the rule engine on the same test rows ---
    rule_score = np.array([r[2] for r in test_rows], dtype=float)
    rule_alert = np.array([r[3] != "APPROVED" for r in test_rows])
    alert_rate = rule_alert.mean()
    rp, rr = precision_score(y_test, rule_alert, zero_division=0), recall_score(y_test, rule_alert, zero_division=0)
    mp, mr = at_alert_rate(y_test, p_test, alert_rate)

    lines = [f"# Fraud model {version}", "",
             f"Data: {len(rows)} labelled analyses ({n_fraud} fraud); train {len(train_rows)}, test {len(test_rows)} "
             f"(newest 25 %, {int(y_test.sum())} fraud). Trees: {booster.best_iteration}, features: {len(names)}.", "",
             "| | ROC-AUC | PR-AUC | Precision | Recall |", "|---|---|---|---|---|",
             f"| Rules (risk score; alert = REVIEW or BLOCK) | {roc_auc_score(y_test, rule_score):.3f} | "
             f"{average_precision_score(y_test, rule_score):.3f} | {rp:.3f} | {rr:.3f} |",
             f"| Model (same alert rate, {alert_rate:.1%}) | {roc_auc_score(y_test, p_test):.3f} | "
             f"{average_precision_score(y_test, p_test):.3f} | {mp:.3f} | {mr:.3f} |", "",
             "## Recall by scenario (test)", "", "| Scenario | Fraud tx | Rules | Model |", "|---|---|---|---|"]
    k = max(1, int(round(alert_rate * len(p_test))))
    model_alert = np.zeros(len(p_test), dtype=bool)
    model_alert[np.argsort(-p_test, kind="stable")[:k]] = True
    for scenario in sorted({r[5] for r in test_rows if r[4] and r[5]}):
        idx = [i for i, r in enumerate(test_rows) if r[4] and r[5] == scenario]
        lines.append(f"| {scenario} | {len(idx)} | {rule_alert[idx].mean():.2f} | {model_alert[idx].mean():.2f} |")
    lines += ["", "## False alerts on legitimate patterns (test)", "", "| Scenario | Tx | Rules | Model |",
              "|---|---|---|---|"]
    for scenario in sorted({r[5] for r in test_rows if not r[4] and r[5]}):
        idx = [i for i, r in enumerate(test_rows) if not r[4] and r[5] == scenario]
        lines.append(f"| {scenario} | {len(idx)} | {rule_alert[idx].mean():.3f} | {model_alert[idx].mean():.3f} |")
    gain = booster.feature_importance("gain", iteration=booster.best_iteration)
    lines += ["", "## Top features (gain)", ""]
    lines += [f"- {n}: {g:.0f}" for n, g in sorted(zip(names, gain), key=lambda p: -p[1])[:12]]
    report = "\n".join(lines) + "\n"

    os.makedirs(f"{REPO}/ml/reports", exist_ok=True)
    with open(f"{REPO}/ml/reports/{version}.md", "w") as f:
        f.write(report)
    print(report)
    print(f"model {version} written to {SERVICE}/main/resources/ml/")
    return 0


if __name__ == "__main__":
    sys.exit(main())
