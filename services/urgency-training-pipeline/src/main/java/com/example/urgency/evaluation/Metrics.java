package com.example.urgency.evaluation;

import com.example.urgency.embedding.CachedEmbedding;
import deepnetts.net.FeedForwardNetwork;

import java.util.ArrayList;
import java.util.List;

/**
 * Evaluation metrics for urgency models.
 *
 * Two separate models:
 * 1. Scorer (regression): outputs 0-1 (scale to 0-10 for display), goal low MAE/MSE.
 * 2. Binary classifier: outputs P(critical) (round(score, 0.5) >= 8 is considered critical)
 */
public final class Metrics {

    /** Urgency >= CRITICAL_THRESHOLD is critical (8.0 on 0-10 scale). */
    public static final float CRITICAL_THRESHOLD = 0.799f;
    /** Actual >= APPLIED_THRESHOLD is "acceptable" when predicted critical. */
    public static final float APPLIED_THRESHOLD = 0.699f;

    private Metrics() {}

    public record ScorerResult(
            double mae,
            double mse,
            double derivedRecall,
            double derivedPrecision,
            double derivedPrecisionApplied,
            int derivedCriticalActual,
            int derivedCriticalPredicted,
            int derivedCriticalCorrect,
            int derivedCriticalCorrectApplied
    ) {
        public void printSummary() {
            System.out.println();
            System.out.println("  1. Scorer (regression) – goal: low MAE/MSE");
            System.out.printf("     %-22s %.4f%n", "MAE", mae);
            System.out.printf("     %-22s %.4f%n", "MSE", mse);
            System.out.println("     Derived (round(score,0.5)≥8 → critical):");
            System.out.printf("       %-20s %.4f  (%d/%d critical detected)%n", "Recall", derivedRecall, derivedCriticalCorrect, derivedCriticalActual);
            System.out.printf("       %-20s %.4f  (%d predicted critical, %d correct)%n", "Precision", derivedPrecision, derivedCriticalPredicted, derivedCriticalCorrect);
            System.out.printf("       %-20s %.4f  (%d/%d had actual ≥%.1f)%n", "Precision-applied", derivedPrecisionApplied, derivedCriticalCorrectApplied, derivedCriticalPredicted, APPLIED_THRESHOLD);
        }
    }

    public record BinaryResult(
            double recall,
            double precision,
            double precisionApplied,
            int criticalActualCount,
            int criticalPredictedCount,
            int criticalTruePositives,
            int criticalPredictedCorrectApplied,
            List<Misclassification> falsePositivesHarsh,
            List<Misclassification> falseNegatives
    ) {
        public void printSummary() {
            System.out.println();
            System.out.println("  2. Binary classifier – P(critical) >= 0.8 → critical");
            System.out.printf("     %-22s %.4f  (%d/%d critical detected)%n", "Recall", recall, criticalTruePositives, criticalActualCount);
            System.out.printf("     %-22s %.4f  (%d predicted critical, %d correct)%n", "Precision", precision, criticalPredictedCount, criticalTruePositives);
            System.out.printf("     %-22s %.4f  (%d/%d had actual ≥%.1f)%n", "Precision-applied", precisionApplied, criticalPredictedCorrectApplied, criticalPredictedCount, APPLIED_THRESHOLD);
        }

        public void printMisclassifications() {
            if (!falseNegatives.isEmpty()) {
                System.out.println("\n  Binary classifier: Critical missed (false negatives):");
                for (Misclassification m : falseNegatives) {
                    System.out.printf("    pred %.1f actual %.1f | %s%n", m.pred() * 10, m.actual() * 10, m.text());
                }
            }
            if (!falsePositivesHarsh.isEmpty()) {
                System.out.println("\n  Binary classifier: Critical said but actual < " + (int)(APPLIED_THRESHOLD * 10) + " (harsh false positives):");
                for (Misclassification m : falsePositivesHarsh) {
                    System.out.printf("    pred %.1f actual %.1f | %s%n", m.pred() * 10, m.actual() * 10, m.text());
                }
            }
        }
    }

    public record Misclassification(String text, float pred, float actual) {}

    public static ScorerResult evaluateScorer(FeedForwardNetwork net, List<CachedEmbedding> cached) {
        double sumAbsError = 0;
        double sumSquaredError = 0;
        int criticalActual = 0;
        int criticalPredicted = 0;
        int criticalPredictedCorrect = 0;
        int criticalPredictedCorrectApplied = 0;

        for (CachedEmbedding c : cached) {
            float[] predArr = net.predict(c.embedding());
            float pred = predArr[0];
            float actual = c.urgency01();
            sumAbsError += Math.abs(pred - actual);
            sumSquaredError += (pred - actual) * (pred - actual);

            boolean predCritical = Math.round(pred * 20) / 20f >= CRITICAL_THRESHOLD;
            boolean actualCritical = actual >= CRITICAL_THRESHOLD;

            if (actualCritical) {
                criticalActual++;
                if (predCritical) criticalPredictedCorrect++;
            }
            if (predCritical) {
                criticalPredicted++;
                if (actual >= APPLIED_THRESHOLD) criticalPredictedCorrectApplied++;
            }
        }

        int n = cached.size();
        double mae = n > 0 ? sumAbsError / n : Double.NaN;
        double mse = n > 0 ? sumSquaredError / n : Double.NaN;
        double recall = criticalActual > 0 ? (double) criticalPredictedCorrect / criticalActual : Double.NaN;
        double precision = criticalPredicted > 0 ? (double) criticalPredictedCorrect / criticalPredicted : Double.NaN;
        double precisionApplied = criticalPredicted > 0 ? (double) criticalPredictedCorrectApplied / criticalPredicted : Double.NaN;

        return new ScorerResult(mae, mse, recall, precision, precisionApplied,
                criticalActual, criticalPredicted, criticalPredictedCorrect, criticalPredictedCorrectApplied);
    }

    public static BinaryResult evaluateBinary(FeedForwardNetwork net, List<CachedEmbedding> cached) {
        int criticalActual = 0;
        int criticalPredicted = 0;
        int criticalPredictedCorrect = 0;
        int criticalPredictedCorrectApplied = 0;
        List<Misclassification> falsePositivesHarsh = new ArrayList<>();
        List<Misclassification> falseNegatives = new ArrayList<>();

        for (CachedEmbedding c : cached) {
            float[] predArr = net.predict(c.embedding());
            float pred = predArr[0];
            float actual = c.urgency01();
            boolean predCritical = pred >= CRITICAL_THRESHOLD;
            boolean actualCritical = actual >= CRITICAL_THRESHOLD;

            if (actualCritical) {
                criticalActual++;
                if (predCritical) {
                    criticalPredictedCorrect++;
                } else {
                    falseNegatives.add(new Misclassification(c.text(), pred, actual));
                }
            }
            if (predCritical) {
                criticalPredicted++;
                if (actual >= APPLIED_THRESHOLD) {
                    criticalPredictedCorrectApplied++;
                } else {
                    falsePositivesHarsh.add(new Misclassification(c.text(), pred, actual));
                }
            }
        }

        double recall = criticalActual > 0 ? (double) criticalPredictedCorrect / criticalActual : Double.NaN;
        double precision = criticalPredicted > 0 ? (double) criticalPredictedCorrect / criticalPredicted : Double.NaN;
        double precisionApplied = criticalPredicted > 0 ? (double) criticalPredictedCorrectApplied / criticalPredicted : Double.NaN;

        return new BinaryResult(recall, precision, precisionApplied,
                criticalActual, criticalPredicted, criticalPredictedCorrect, criticalPredictedCorrectApplied,
                falsePositivesHarsh, falseNegatives);
    }
}
