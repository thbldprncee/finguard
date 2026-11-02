package com.finguard.fraud.ml;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * How raw features ({@link MlFeatures}) become the model's input vector. Written by the training
 * script (ml/train.py) next to the model, so the encoding always matches the model it belongs to;
 * the Python side encodes with the same file.
 *
 * @param modelVersion identifies the model in fraud_analyses.ml_model_version
 * @param features     the input columns, in the model's order
 */
public record FeatureSpec(
        @JsonProperty("model_version") String modelVersion,
        List<Column> features
) {

    /**
     * One input column.
     * <ul>
     *   <li>{@code numeric}: the raw value of {@code source} (missing: 0);</li>
     *   <li>{@code log1p}: log(1 + value), for amounts;</li>
     *   <li>{@code onehot}: 1 if the raw value of {@code source} equals {@code value}, else 0.</li>
     * </ul>
     */
    public record Column(String name, String source, String kind, String value) {
    }

    public double[] encode(Map<String, Object> raw) {
        double[] x = new double[features.size()];
        for (int i = 0; i < x.length; i++) {
            Column c = features.get(i);
            Object v = raw.get(c.source());
            x[i] = switch (c.kind()) {
                case "numeric" -> number(v);
                case "log1p" -> Math.log1p(Math.max(0, number(v)));
                case "onehot" -> v != null && v.toString().equals(c.value()) ? 1.0 : 0.0;
                default -> throw new IllegalStateException("Unknown feature kind " + c.kind() + " for " + c.name());
            };
        }
        return x;
    }

    private static double number(Object v) {
        return v instanceof Number n ? n.doubleValue() : 0.0;
    }
}
