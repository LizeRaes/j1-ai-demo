package com.example.urgency.evaluation;

import com.example.urgency.embedding.DJLEmbeddingGenerator;
import com.example.urgency.model.Ticket;
import deepnetts.data.MLDataItem;
import deepnetts.net.FeedForwardNetwork;
import deepnetts.net.NeuralNetwork;

import javax.visrec.ml.data.DataSet;
import java.util.ArrayList;
import java.util.List;

/**
 * Evaluation metrics for urgency model.
 * - Critical recall: recall for urgency >= 0.8 (critical events that must be detected)
 * - Critical precision: of those predicted critical, how many were actually critical
 * - MAE: mean absolute error (regression metric)
 */
public final class Metrics {

    /** Urgency >= this threshold is considered critical. */
    public static final float CRITICAL_THRESHOLD = 0.8f;
    /** Actual >= this is "acceptable" when predicted critical (0.7 scored 0.8 is not bad). */
    public static final float APPLIED_THRESHOLD = 0.7f;

    private Metrics() {}

    /**
     * Compute metrics by iterating dataset, predicting each sample.
     */
    public static EvaluationResult evaluate(NeuralNetwork net, DataSet<? extends MLDataItem> dataSet) {
        FeedForwardNetwork ffn = (FeedForwardNetwork) net;

        double sumAbsError = 0;
        int criticalActual = 0;
        int criticalPredicted = 0;
        int criticalPredictedCorrect = 0;

        int n = dataSet.size();
        for (int i = 0; i < n; i++) {
            MLDataItem item = dataSet.get(i);
            float[] input = item.getInput().getValues();
            float[] targetArr = item.getTargetOutput().getValues();
            float actual = targetArr.length > 1 ? targetArr[1] : targetArr[0];  // [1-u,u] or [u]

            float[] predArr = ffn.predict(input);
            float pred = predArr.length > 1 ? predArr[1] : predArr[0];  // urgency = P(critical)

            sumAbsError += Math.abs(pred - actual);

            boolean actualCritical = actual >= CRITICAL_THRESHOLD;
            boolean predCritical = pred >= CRITICAL_THRESHOLD;
            if (actualCritical) {
                criticalActual++;
                if (predCritical) criticalPredictedCorrect++;
            }
            if (predCritical) criticalPredicted++;
        }

        double mae = n > 0 ? sumAbsError / n : Double.NaN;
        double criticalRecall = criticalActual > 0
                ? (double) criticalPredictedCorrect / criticalActual
                : Double.NaN;
        double criticalPrecision = criticalPredicted > 0
                ? (double) criticalPredictedCorrect / criticalPredicted
                : Double.NaN;

        return new EvaluationResult(mae, criticalRecall, criticalPrecision, Double.NaN,
                criticalActual, criticalPredicted, criticalPredictedCorrect, 0,
                List.of(), List.of());
    }

    /**
     * Evaluate with tickets (has text) to compute metrics and collect false positives/negatives.
     */
    public static EvaluationResult evaluateWithTickets(FeedForwardNetwork net, List<Ticket> tickets,
                                                       DJLEmbeddingGenerator embeddingGen) {
        double sumAbsError = 0;
        int criticalActual = 0;
        int criticalPredicted = 0;
        int criticalPredictedCorrect = 0;
        int criticalPredictedCorrectApplied = 0;  // pred critical AND actual >= 0.7
        List<Misclassification> falsePositivesHarsh = new ArrayList<>();  // pred critical AND actual < 0.7
        List<Misclassification> falseNegatives = new ArrayList<>();  // critical missed

        for (Ticket t : tickets) {
            float[] emb = embeddingGen.embed(t.text());
            float[] predArr = net.predict(emb);
            float pred = predArr.length > 1 ? predArr[1] : predArr[0];
            float actual = (float) (t.urgency() > 1.0 ? t.urgency() / 10.0 : t.urgency());

            sumAbsError += Math.abs(pred - actual);

            boolean actualCritical = actual >= CRITICAL_THRESHOLD;
            boolean predCritical = pred >= CRITICAL_THRESHOLD;
            if (actualCritical) {
                criticalActual++;
                if (predCritical) {
                    criticalPredictedCorrect++;
                } else {
                    falseNegatives.add(new Misclassification(t.text(), pred, actual));
                }
            }
            if (predCritical) {
                criticalPredicted++;
                if (actual >= APPLIED_THRESHOLD) {
                    criticalPredictedCorrectApplied++;
                } else {
                    falsePositivesHarsh.add(new Misclassification(t.text(), pred, actual));
                }
            }
        }

        int n = tickets.size();
        double mae = n > 0 ? sumAbsError / n : Double.NaN;
        double criticalRecall = criticalActual > 0
                ? (double) criticalPredictedCorrect / criticalActual
                : Double.NaN;
        double criticalPrecision = criticalPredicted > 0
                ? (double) criticalPredictedCorrect / criticalPredicted
                : Double.NaN;
        double precisionApplied = criticalPredicted > 0
                ? (double) criticalPredictedCorrectApplied / criticalPredicted
                : Double.NaN;

        return new EvaluationResult(mae, criticalRecall, criticalPrecision, precisionApplied,
                criticalActual, criticalPredicted, criticalPredictedCorrect, criticalPredictedCorrectApplied,
                falsePositivesHarsh, falseNegatives);
    }

    public record Misclassification(String text, float pred, float actual) {
        float pred10() { return pred * 10; }
        float actual10() { return actual * 10; }
    }

    public record EvaluationResult(
            double mae,
            double criticalRecall,
            double criticalPrecision,
            double precisionApplied,
            int criticalActualCount,
            int criticalPredictedCount,
            int criticalTruePositives,
            int criticalPredictedCorrectApplied,
            List<Misclassification> falsePositivesHarsh,
            List<Misclassification> falseNegatives
    ) {
        public String summary() {
            return String.format(
                    "MAE: %.4f | Critical Recall (>=%.1f): %.4f (%d/%d detected) | Precision: %.4f | Precision-applied (≥%.1f): %.4f (%d/%d)",
                    mae, CRITICAL_THRESHOLD, criticalRecall, criticalTruePositives, criticalActualCount,
                    criticalPrecision, APPLIED_THRESHOLD, precisionApplied, criticalPredictedCorrectApplied, criticalPredictedCount
            );
        }

        public void printSummary() {
            System.out.println();
            System.out.println("Validation results");
            System.out.println("────────────────────────────────────────");
            System.out.printf("  %-24s %.4f%n", "MAE", mae);
            System.out.printf("  %-24s %.4f  (%d/%d critical detected)%n", "Critical Recall (≥" + CRITICAL_THRESHOLD + ")", criticalRecall, criticalTruePositives, criticalActualCount);
            System.out.printf("  %-24s %.4f  (%d predicted critical, %d correct)%n", "Precision", criticalPrecision, criticalPredictedCount, criticalTruePositives);
            System.out.printf("  %-24s %.4f  (%d/%d had actual ≥%.1f)%n", "Precision-applied", precisionApplied, criticalPredictedCorrectApplied, criticalPredictedCount, APPLIED_THRESHOLD);
            System.out.println("────────────────────────────────────────");
        }

        public void printMisclassifications() {
            if (!falseNegatives.isEmpty()) {
                System.out.println("\n  Critical missed (false negatives):");
                for (Misclassification m : falseNegatives) {
                    System.out.printf("    pred %.1f actual %.1f | %s%n", m.pred10(), m.actual10(), m.text());
                }
            }
            if (!falsePositivesHarsh.isEmpty()) {
                System.out.println("\n  Critical said but actual < " + (int)(APPLIED_THRESHOLD * 10) + " (harsh false positives):");
                for (Misclassification m : falsePositivesHarsh) {
                    System.out.printf("    pred %.1f actual %.1f | %s%n", m.pred10(), m.actual10(), m.text());
                }
            }
        }
    }
}
