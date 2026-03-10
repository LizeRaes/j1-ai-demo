# Urgency Training Pipeline

CLI pipeline for training an urgency classifier: **DJL** (sentence-transformers/all-MiniLM-L6-v2) for 384-dim embeddings + **DeepNetts** MLP for binary classification.

## Architecture

- **Embedding**: sentence-transformers/all-MiniLM-L6-v2 (384 dim) via DJL PyTorch
- **MLP**: 384 → 64 (ReLU) → 16 (ReLU) → 1 (Sigmoid)
- **Output**: `model.bin` (DeepNetts serialized format)

## Run from CLI

**With bundled demo data** (loads all `*.json` under `training/dataset/`):

```bash
mvn exec:java -Dexec.args="--demo-data"
```

Or with an explicit directory/file path:

```bash
mvn exec:java -Dexec.args="--demo-data training/dataset"
```

**With custom tickets.json**:

```bash
mvn exec:java -Dexec.args="--dataset training/dataset/tickets.json --export training/export/model.bin"
```

## Predict (test the model)

```bash
mvn exec:java -Dexec.mainClass="com.example.urgency.Predict" -Dexec.args="Stripe refused my payment 3x"
```

With custom model path:

```bash
mvn exec:java -Dexec.mainClass="com.example.urgency.Predict" -Dexec.args="--model training/export/model.bin Your complaint here"
```

Output: `Urgency: 0.7234 (72%)` and `Critical (>= 0.8)` if applicable.

## Directory layout

```
training/
├── dataset/
│   └── tickets.json       # Placeholder demo data
├── embedding/
│   └── DJLEmbeddingGenerator.java   (in src/main/java/.../embedding/)
├── training/
│   └── UrgencyTrainer.java         (in src/main/java/.../training/)
├── evaluation/
│   └── Metrics.java                (in src/main/java/.../evaluation/)
└── export/
    └── model.bin                   # Produced by training
```


## Metrics

- **Critical recall**: Recall for urgency ≥ 0.8 (critical events that must be detected)
- **MAE**: Mean absolute error between predicted and actual urgency

Or after `mvn package`:

```bash
java -cp "target/urgency-training-pipeline-1.0.0-SNAPSHOT.jar:target/lib/*" com.example.urgency.Main
```

## ONNX export (training)

Training exports weights to JSON, then runs `weights_to_onnx.py` to produce `model.onnx`. One-time setup:

```bash
cd services/urgency-training-pipeline
./training/export/setup_venv.sh
```

## Placeholders

- **Demo data**: `tickets.json` has 3 placeholder tickets; replace when real data arrives
- **Embedding model**: DJL pulls `all-MiniLM-L6-v2` from Hugging Face on first run (requires network)
- **MLP hyperparams**: epochs, learning rate, etc. are placeholders; tune for your data
- **Metrics**: basic validation loss/acc; precision/recall/F1 stubs for later

## Dependencies

- **DJL** (api, pytorch-engine, tokenizers) – text embeddings
- **DeepNetts** (deepnetts-core) – MLP training
- **Jackson** – JSON parsing for tickets
