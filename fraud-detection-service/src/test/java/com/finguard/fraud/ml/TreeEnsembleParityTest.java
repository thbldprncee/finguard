package com.finguard.fraud.ml;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.assertj.core.api.Assumptions.assumeThat;

/**
 * The shipped model scored by this service must match LightGBM itself. ml/train.py stores raw
 * feature maps of held-out transactions with LightGBM's predictions (ml/parity_vectors.json);
 * here the same maps go through FeatureSpec + TreeEnsemble.
 */
class TreeEnsembleParityTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void shippedModel_matchesLightGbmPredictions() throws Exception {
        InputStream specIn = getClass().getResourceAsStream("/ml/feature_spec.json");
        InputStream modelIn = getClass().getResourceAsStream("/ml/model.json");
        InputStream vectorsIn = getClass().getResourceAsStream("/ml/parity_vectors.json");
        assumeThat(specIn != null && modelIn != null && vectorsIn != null)
                .as("no trained model shipped yet").isTrue();

        FeatureSpec spec = json.readValue(specIn, FeatureSpec.class);
        TreeEnsemble model = TreeEnsemble.fromLightGbmDump(json.readTree(modelIn));
        JsonNode vectors = json.readTree(vectorsIn);
        assertThat(vectors.size()).isGreaterThan(0);

        for (JsonNode v : vectors) {
            @SuppressWarnings("unchecked")
            Map<String, Object> raw = json.convertValue(v.get("features"), Map.class);
            assertThat(model.predict(spec.encode(raw)))
                    .as("transaction %s", v.path("transaction_id").asText())
                    .isCloseTo(v.get("lightgbm").asDouble(), within(1e-9));
        }
    }
}
