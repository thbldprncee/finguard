# Fraud model

A LightGBM model scores every transaction next to the rule engine. Its score is stored with the
analysis (`fraud_analyses.ml_score`, `ml_model_version`) and shown to staff in the API and on the
Fraud Alerts page.

Whether it affects the decision is the `ML_SCORE` rule in `fraud_rules`, **disabled by default**
(shadow mode). Enabled through the rules API (`PATCH /api/fraud/rules/{id}`), it triggers when the
fraud probability reaches `finguard.fraud.ml.threshold` (default 0.8) and adds its weight to the
risk score like any other rule; if the loaded model fails to score a transaction, the rule is
FAILED and the usual failure policy escalates the decision. Shadow mode first, so the model can
be compared with the rules on real traffic before it is trusted.

## How it fits together

```
transaction.created ─► RuleEngineService (8 rules) ─► MlRule ─► DecisionMaker ─► decision
                                                        │  MlFeatures (raw features)
                                                        │  FeatureSpec ─► TreeEnsemble ─► ml_score
                                                        │  ML_SCORE enabled? adds a weighted result
                                                        ▼
                  fraud_analyses.features, ml_score ◄─┐
                                                      ├─ ml/train.py ─► model.json + feature_spec.json
                  fraud_labels (ground truth) ────────┘
```

- **Features are logged at scoring time.** `MlFeatures` collects transaction fields, the outcome
  of each rule and numbers the rules compute anyway (transactions in the velocity window,
  below-threshold payments in the structuring window, amount relative to the high-amount
  threshold). They are stored in `fraud_analyses.features`, and the model is trained on exactly
  these stored values, so training and serving cannot drift apart.
- **The encoding ships with the model.** `train.py` writes `feature_spec.json` (raw features →
  input vector) next to `model.json`; `FeatureSpec.java` and `train.py` encode with the same file.
- **Plain-Java inference.** `TreeEnsemble` walks the trees of LightGBM's JSON dump: no native
  runtime (the service image is Alpine), microseconds per transaction.
  `TreeEnsembleParityTest` checks it against LightGBM's own predictions (`parity_vectors.json`).
- **Failure-safe.** No model, a broken model file or a scoring error leaves `ml_score` empty and
  the analysis runs on the rules; with `ML_SCORE` enabled a scoring error makes that rule FAILED.

## Labels

The decision is not a label: a model trained on it only learns to imitate the rules. Ground
truth lives in `fraud.fraud_labels`:

- `ANALYST`: the Fraud/Legit buttons on the Fraud Alerts page, i.e.
  `PUT /api/fraud/analysis/{transactionId}/label` with `{"fraud": true|false, "note": "..."}`
  (ANALYST or ADMIN). This is the feedback loop for real traffic.
- `SYNTHETIC`: written by `ml/generate.py`.

## Synthetic data

`ml/generate.py` simulates customers who pay through the real API. Most payments are ordinary;
some are legitimate but unusual (travel, a new phone, a big purchase, a shopping spree, a small
business paying large invoices, many tiny payments), and 12 % of customers are hit by one fraud
pattern: account takeover, card testing, structuring, or a "quiet" large purchase from a known
device. Legitimate and fraudulent patterns overlap on purpose (amounts, bursts, new devices,
countries, everyday categories), so no single feature gives the answer away. Every transaction is
labelled with its scenario.

Limits: the API does not let a client set the time or the IP, so time-of-day and IP features
carry no signal in synthetic data; and the model learns the patterns the generator encodes. It is
a working pipeline and a baseline, not a model fit for production decisions.

## Commands

The stack must be running (`docker compose up -d`). Everything runs in a container on the compose
network; nothing needs to be installed on the host.

```bash
USERS=1500 USER_PREFIX=mlgen2 DURATION_MIN=30 ml/run.sh generate   # ~15 000 labelled transactions, ~40 min
ml/run.sh train                                 # trains, writes the model and ml/reports/<version>.md
docker compose up -d --build fraud-detection-service   # load the new model
```

Configuration (`fraud-detection-service`): `finguard.fraud.ml.enabled` (default `true`),
`finguard.fraud.ml.model-dir` (default `classpath:ml/`), `finguard.fraud.ml.threshold`
(default `0.8`).

## Next steps

1. Compare `ml_score` with the rules on real traffic (shadow mode) and collect analyst labels.
2. Enable `ML_SCORE` once the comparison justifies it; tune its weight and the threshold.
3. Richer history features (amount relative to the customer's usual, time since last payment,
   distinct countries in 24 h) once real data shows what the rules miss.
