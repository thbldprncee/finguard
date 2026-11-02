package com.finguard.fraud.ml;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;

/**
 * Loads the ML model and scores raw features. A missing model, a broken model file or a scoring
 * error never breaks the analysis; the score is then absent. Whether the score counts towards the
 * decision is {@link MlRule}'s business.
 */
@Slf4j
@Component
public class MlScorer {

    public record Score(double probability, String modelVersion) {
    }

    private final FeatureSpec spec;
    private final TreeEnsemble model;

    public MlScorer(MlProperties properties, ResourceLoader resources, ObjectMapper json) {
        FeatureSpec loadedSpec = null;
        TreeEnsemble loadedModel = null;
        if (properties.enabled()) {
            String dir = properties.modelDir().endsWith("/") ? properties.modelDir() : properties.modelDir() + "/";
            Resource specFile = resources.getResource(dir + "feature_spec.json");
            Resource modelFile = resources.getResource(dir + "model.json");
            if (specFile.exists() && modelFile.exists()) {
                try (InputStream s = specFile.getInputStream(); InputStream m = modelFile.getInputStream()) {
                    loadedSpec = json.readValue(s, FeatureSpec.class);
                    loadedModel = TreeEnsemble.fromLightGbmDump(json.readTree(m));
                    if (loadedModel.featureCount() != loadedSpec.features().size()) {
                        throw new IllegalStateException("model has " + loadedModel.featureCount()
                                + " features, feature_spec.json " + loadedSpec.features().size());
                    }
                    log.info("ML model {} loaded from {} ({} features), shadow mode",
                            loadedSpec.modelVersion(), dir, loadedSpec.features().size());
                } catch (IOException | RuntimeException e) {
                    log.error("ML model in {} could not be loaded, scoring without it: {}", dir, e.getMessage());
                    loadedSpec = null;
                    loadedModel = null;
                }
            } else {
                log.info("No ML model in {}, scoring with rules only", dir);
            }
        }
        this.spec = loadedSpec;
        this.model = loadedModel;
    }

    /** Whether a model is loaded; an empty score from a loaded model means scoring failed. */
    public boolean available() {
        return model != null;
    }

    public Optional<Score> score(Map<String, Object> features) {
        if (model == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(new Score(model.predict(spec.encode(features)), spec.modelVersion()));
        } catch (RuntimeException e) {
            log.warn("ML scoring failed, analysis continues without it: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
