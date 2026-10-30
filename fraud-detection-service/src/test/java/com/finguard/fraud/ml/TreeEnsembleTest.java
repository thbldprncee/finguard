package com.finguard.fraud.ml;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class TreeEnsembleTest {

    private final ObjectMapper json = new ObjectMapper();

    // Two trees over 2 features; tree 2 sends a missing (NaN) feature 1 to the right.
    private static final String MODEL = """
            {"objective": "binary sigmoid:1", "max_feature_idx": 1, "tree_info": [
              {"tree_structure": {"split_feature": 0, "threshold": 0.5, "decision_type": "<=",
                 "default_left": true, "missing_type": "None",
                 "left_child": {"leaf_value": -1.0},
                 "right_child": {"split_feature": 1, "threshold": 10, "decision_type": "<=",
                    "default_left": true, "missing_type": "None",
                    "left_child": {"leaf_value": 0.5}, "right_child": {"leaf_value": 2.0}}}},
              {"tree_structure": {"split_feature": 1, "threshold": 3, "decision_type": "<=",
                 "default_left": false, "missing_type": "NaN",
                 "left_child": {"leaf_value": -0.25}, "right_child": {"leaf_value": 0.25}}}
            ]}""";

    private TreeEnsemble model() throws Exception {
        return TreeEnsemble.fromLightGbmDump(json.readTree(MODEL));
    }

    private static double sigmoid(double raw) {
        return 1 / (1 + Math.exp(-raw));
    }

    @Test
    void sumsLeavesOfAllTrees_andAppliesTheSigmoid() throws Exception {
        TreeEnsemble m = model();
        assertThat(m.predict(new double[]{0, 1})).isCloseTo(sigmoid(-1.0 - 0.25), within(1e-12));
        assertThat(m.predict(new double[]{1, 5})).isCloseTo(sigmoid(0.5 + 0.25), within(1e-12));
        assertThat(m.predict(new double[]{1, 20})).isCloseTo(sigmoid(2.0 + 0.25), within(1e-12));
        assertThat(m.predict(new double[]{0.5, 3})).as("<= goes left").isCloseTo(sigmoid(-1.0 - 0.25), within(1e-12));
    }

    @Test
    void missingValue_followsTheDefaultDirection() throws Exception {
        assertThat(model().predict(new double[]{0, Double.NaN})).isCloseTo(sigmoid(-1.0 + 0.25), within(1e-12));
    }

    @Test
    void rejectsAVectorOfTheWrongSize() throws Exception {
        assertThatThrownBy(() -> model().predict(new double[]{1})).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void featureSpec_encodesNumericLogAndOneHot() {
        FeatureSpec spec = new FeatureSpec("test", List.of(
                new FeatureSpec.Column("amount_log", "amount", "log1p", null),
                new FeatureSpec.Column("country=DE", "country", "onehot", "DE"),
                new FeatureSpec.Column("country=FR", "country", "onehot", "FR"),
                new FeatureSpec.Column("velocity", "velocity_tx_in_window", "numeric", null)));

        assertThat(spec.encode(Map.of("amount", 99.0, "country", "DE", "velocity_tx_in_window", 4.0)))
                .containsExactly(Math.log1p(99.0), 1.0, 0.0, 4.0);
        assertThat(spec.encode(Map.of("amount", 1.0, "country", "US")))
                .as("unknown category and missing number are zeros")
                .containsExactly(Math.log1p(1.0), 0.0, 0.0, 0.0);
    }
}
