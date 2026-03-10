# Urgency Training Pipeline

Trains two DeepNetts MLP models from ticket JSON: a **regression scorer** (urgency 0–10) and a **binary classifier** (critical vs non-critical).

[Train](#train) | [Predict](#predict) | [Models](#models) | [Embeddings](#embeddings) | [Output](#output-preview)

## Train

```bash
# Local embeddings (MiniLM, 384 dim) – default
mvn compile exec:java -Dexec.args="--dataset training/dataset --export training/export/model.dnet"

# OpenAI embeddings (1536 dim) – requires OPENAI_API_KEY
mvn compile exec:java -Dexec.args="--embedding-provider openai --dataset training/dataset"
```

Outputs:
- Local: `training/export/model-scorer-local.dnet`, `training/export/model-binary-local.dnet`
- OpenAI: `training/export/model-scorer-openai.dnet`, `training/export/model-binary-openai.dnet`

## Predict

Default uses local embeddings:

```bash
mvn compile exec:java@predict -Dexec.args="Stripe refused my payment 3x"
```

OpenAI mode (uses OpenAI embeddings):

```bash
mvn compile exec:java@predict -Dexec.args="--embedding-provider openai Stripe refused my payment 3x"
```

---

## Models

Both are MLPs (feed-forward): `inputDim → 64 → 32 → 1`. Hidden layers: Tanh; output: Sigmoid.

- **Scorer** (regression): MSE loss, predicts urgency 0–1 (scale to 0–10).
- **Binary** (classifier): BCE loss, predicts P(critical); threshold 0.8 → critical. Samples with urgency 7.0 or 7.5 (gray zone) are excluded from binary training but kept in validation, so the model learns a clearer critical/non-critical boundary.

## Embeddings

- **local** (default): sentence-transformers/all-MiniLM-L6-v2 via DJL
- **openai**: text-embedding-3-small via LangChain4j (takes some time to create the first time)

Cached under `training/embeddings/{provider}/`. When embeddings exist already and no changes were made to `training/dataset`, then embeddings are not recalculated.

## Output preview

Data is split 80% train / 20% validation. Samples are shuffled before each run, so no two training runs produce the exact same model.

After training you get validation metrics and sample predictions:

```
  1. Scorer (regression) – goal: low MAE/MSE
     MAE                   0.1234
     MSE                   0.0456
     Derived (round(score,0.5)≥8 → critical):
       Recall               0.95   (X/Y critical detected)
       Precision            0.90   (predicted critical, correct)
       Precision-applied    0.85   (predicted critical had actual ≥7.0)

  2. Binary classifier – P(critical) >= 0.8 → critical
     Recall               0.92   (X/Y critical detected)
     Precision            0.88   (predicted critical, correct)
     Precision-applied    0.82   (predicted critical had actual ≥7.0)

  First 10 validation samples (scorer: 0-10; binary: critical/non-critical):
    score 8.5 [CRITICAL] (actual 9.0) Patient data visible to wrong user...
    score 4.0 (actual 3.5) Can't find the reset password link...
```

- **MAE/MSE**: Scorer error (lower = better).
- **Recall**: Of all actual critical tickets, how many we flagged as critical indeed (goal: high).
- **Precision**: Of all we flagged critical, how many were truly critical (goal: high)
- **Precision-applied**: Same but we accept predicted critical if actual ≥7.0 (we don't find that so bad and rather err on the side of not missing a critical ticket).
