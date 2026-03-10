package com.example.urgency.evaluation;

import com.example.urgency.embedding.DJLEmbeddingGenerator;
import com.example.urgency.model.Ticket;
import deepnetts.data.TabularDataSet;
import deepnetts.net.FeedForwardNetwork;

import java.util.List;

public final class Metrics {

    public static final float CRITICAL_THRESHOLD = 0.8f;
    public static final float APPLIED_THRESHOLD = 0.7f;

    /** Stop when MAE < this (0–1 scale). MSE ≈ 0.05 is rough equiv for MAE 0.16. */
    public static final double STOP_MAE_THRESHOLD = 0.16;
    public static final double STOP_MSE_THRESHOLD = 0.05;

    /** Stop when MSE improvement < this (MAE equiv ~0.02). */
    public static final double STOP_MIN_IMPROVEMENT_MSE = 0.001;

    /** Stop when critical recall > this. */
    public static final double STOP_CRITICAL_RECALL = 0.95;

    /** Stop when fp rate (pred≥0.8 & actual<0.7) < this. */
    public static final double STOP_FP_RATE_MAX = 0.05;

    private Metrics() {}

    /** Evaluate on TabularDataSet (for validation during training, no embeddings needed). */
    public static EvaluationResult evaluateFromDataSet(
            FeedForwardNetwork net,
            TabularDataSet<TabularDataSet.Item> dataSet) {

        double sumAbsError = 0;
        double sumSqError = 0;
        int criticalActual = 0;
        int criticalPredicted = 0;
        int criticalCorrect = 0;
        int criticalCorrectApplied = 0;
        int fpCount = 0;  // pred≥0.8 & actual<0.7

        int n = dataSet.size();
        for (int i = 0; i < n; i++) {
            var item = dataSet.get(i);
            float[] input = item.getInput().getValues();
            float actual = item.getTargetOutput().getValues()[0];
            float pred = net.predict(input)[0];

            sumAbsError += Math.abs(pred - actual);
            sumSqError += (pred - actual) * (pred - actual);

            boolean actualCritical = actual >= CRITICAL_THRESHOLD;
            boolean predCritical = pred >= CRITICAL_THRESHOLD;

            if (predCritical && actual < APPLIED_THRESHOLD) fpCount++;
            if (actualCritical) {
                criticalActual++;
                if (predCritical) criticalCorrect++;
            }
            if (predCritical) {
                criticalPredicted++;
                if (actual >= APPLIED_THRESHOLD) criticalCorrectApplied++;
            }
        }

        double mae = n > 0 ? sumAbsError / n : Double.NaN;
        double mse = n > 0 ? sumSqError / n : Double.NaN;
        double criticalRecall = criticalActual > 0 ? (double) criticalCorrect / criticalActual : Double.NaN;
        double criticalPrecision = criticalPredicted > 0 ? (double) criticalCorrect / criticalPredicted : Double.NaN;
        double precisionApplied = criticalPredicted > 0
                ? (double) criticalCorrectApplied / criticalPredicted
                : Double.NaN;
        double fpRate = n > 0 ? (double) fpCount / n : Double.NaN;

        return new EvaluationResult(
                mae,
                mse,
                criticalRecall,
                criticalPrecision,
                precisionApplied,
                fpRate,
                criticalActual,
                criticalPredicted,
                criticalCorrect,
                criticalCorrectApplied
        );
    }

    public static EvaluationResult evaluateWithTickets(
            FeedForwardNetwork net,
            List<Ticket> tickets,
            DJLEmbeddingGenerator embeddingGen) {

        double sumAbsError = 0;
        double sumSqError = 0;
        int criticalActual = 0;
        int criticalPredicted = 0;
        int criticalCorrect = 0;
        int criticalCorrectApplied = 0;
        int fpCount = 0;

        for (Ticket t : tickets) {
            float[] emb = embeddingGen.embed(t.text());
            float pred = net.predict(emb)[0];
            float actual = (float) (t.urgency() / 10.0);

            double err = pred - actual;
            sumAbsError += Math.abs(err);
            sumSqError += err * err;

            boolean actualCritical = actual >= CRITICAL_THRESHOLD;
            boolean predCritical = pred >= CRITICAL_THRESHOLD;

            if (predCritical && actual < APPLIED_THRESHOLD) fpCount++;
            if (actualCritical) {
                criticalActual++;
                if (predCritical) criticalCorrect++;
            }
            if (predCritical) {
                criticalPredicted++;
                if (actual >= APPLIED_THRESHOLD) criticalCorrectApplied++;
            }
        }

        int n = tickets.size();
        double mae = n > 0 ? sumAbsError / n : Double.NaN;
        double mse = n > 0 ? sumSqError / n : Double.NaN;
        double criticalRecall = criticalActual > 0 ? (double) criticalCorrect / criticalActual : Double.NaN;
        double criticalPrecision = criticalPredicted > 0 ? (double) criticalCorrect / criticalPredicted : Double.NaN;
        double precisionApplied = criticalPredicted > 0
                ? (double) criticalCorrectApplied / criticalPredicted
                : Double.NaN;
        double fpRate = n > 0 ? (double) fpCount / n : Double.NaN;

        return new EvaluationResult(
                mae,
                mse,
                criticalRecall,
                criticalPrecision,
                precisionApplied,
                fpRate,
                criticalActual,
                criticalPredicted,
                criticalCorrect,
                criticalCorrectApplied
        );
    }

    public record EvaluationResult(
            double mae,
            double mse,
            double criticalRecall,
            double criticalPrecision,
            double precisionApplied,
            double fpRate,
            int criticalActualCount,
            int criticalPredictedCount,
            int criticalTruePositives,
            int criticalPredictedCorrectApplied
    ) {
        public void printSummary() {
            System.out.println();
            System.out.println("Validation results");
            System.out.println("────────────────────────────────────────");
            System.out.printf("  %-24s %.4f  (MSE %.4f)%n", "MAE", mae, mse);
            System.out.printf("  %-24s %.4f (%d/%d)%n",
                    "Critical recall (≥0.8)",
                    criticalRecall,
                    criticalTruePositives,
                    criticalActualCount);
            System.out.printf("  %-24s %.4f%n", "Precision", criticalPrecision);
            System.out.printf("  %-24s %.4f%n", "Precision-applied", precisionApplied);
            System.out.printf("  %-24s %.4f%n", "FP rate (pred≥0.8 & actual<0.7)", fpRate);
            System.out.println("────────────────────────────────────────");
        }

        public boolean shouldStopTraining() {
            return mse < STOP_MSE_THRESHOLD
                    && criticalRecall >= STOP_CRITICAL_RECALL
                    && fpRate < STOP_FP_RATE_MAX;
        }

        public boolean improvementTooSmall(double prevMse) {
            return prevMse >= 0 && (prevMse - mse) < STOP_MIN_IMPROVEMENT_MSE;
        }
    }
}