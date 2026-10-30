package com.finguard.fraud.ml;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Evaluates a LightGBM binary model from its JSON dump ({@code Booster.dump_model()}).
 * <p>
 * A plain tree walk instead of a native runtime: no JNI library (the service image is Alpine/musl),
 * and a few hundred small trees take microseconds. ml/train.py writes test vectors with LightGBM's
 * own predictions, and TreeEnsembleParityTest checks this evaluator against them.
 * <p>
 * Supported: numerical splits ({@code <=}), missing values (None/Zero/NaN with default_left),
 * the binary objective with a sigmoid. Categorical splits are not used: categories are one-hot
 * encoded by {@link FeatureSpec}.
 */
public final class TreeEnsemble {

    private final List<Node> trees;
    private final double sigmoid;
    private final int featureCount;

    private TreeEnsemble(List<Node> trees, double sigmoid, int featureCount) {
        this.trees = trees;
        this.sigmoid = sigmoid;
        this.featureCount = featureCount;
    }

    public static TreeEnsemble fromLightGbmDump(JsonNode dump) {
        String objective = dump.path("objective").asText("");
        if (!objective.startsWith("binary")) {
            throw new IllegalArgumentException("Only binary LightGBM models are supported, got: " + objective);
        }
        double sigmoid = 1.0;
        for (String part : objective.split(" ")) {
            if (part.startsWith("sigmoid:")) sigmoid = Double.parseDouble(part.substring("sigmoid:".length()));
        }
        List<Node> trees = new ArrayList<>();
        for (JsonNode tree : dump.path("tree_info")) {
            trees.add(Node.parse(tree.path("tree_structure")));
        }
        return new TreeEnsemble(List.copyOf(trees), sigmoid, dump.path("max_feature_idx").asInt() + 1);
    }

    public int featureCount() {
        return featureCount;
    }

    /** Fraud probability in [0, 1]. */
    public double predict(double[] x) {
        if (x.length != featureCount) {
            throw new IllegalArgumentException("Model expects " + featureCount + " features, got " + x.length);
        }
        double raw = 0;
        for (Node tree : trees) {
            raw += tree.value(x);
        }
        return 1.0 / (1.0 + Math.exp(-sigmoid * raw));
    }

    private record Node(int feature, double threshold, boolean defaultLeft, String missingType,
                        Node left, Node right, double leafValue) {

        static Node parse(JsonNode n) {
            if (n.has("leaf_value")) {
                return new Node(-1, 0, false, "None", null, null, n.get("leaf_value").asDouble());
            }
            String decision = n.path("decision_type").asText("<=");
            if (!"<=".equals(decision)) {
                throw new IllegalArgumentException("Unsupported split " + decision + " (categorical splits are not supported)");
            }
            return new Node(n.get("split_feature").asInt(), n.get("threshold").asDouble(),
                    n.path("default_left").asBoolean(true), n.path("missing_type").asText("None"),
                    parse(n.get("left_child")), parse(n.get("right_child")), 0);
        }

        double value(double[] x) {
            Node node = this;
            while (node.left != null) {
                double v = x[node.feature];
                boolean missing = switch (node.missingType) {
                    case "NaN" -> Double.isNaN(v);
                    case "Zero" -> Double.isNaN(v) || v == 0.0;
                    default -> false;
                };
                if (missing) {
                    node = node.defaultLeft ? node.left : node.right;
                } else {
                    // LightGBM treats NaN as 0 when the split has no missing handling.
                    node = (Double.isNaN(v) ? 0.0 : v) <= node.threshold ? node.left : node.right;
                }
            }
            return node.leafValue;
        }
    }
}
