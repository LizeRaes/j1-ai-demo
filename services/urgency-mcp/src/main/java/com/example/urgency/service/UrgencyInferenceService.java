package com.example.urgency.service;

import java.util.Objects;

import com.example.urgency.config.RuntimeConfig;
import com.example.urgency.validation.RequiredText;
import io.helidon.config.Config;

public class UrgencyInferenceService implements UrgencyScorer {

    private static final String COMPLAINT_LABEL = "complaint";
    private static final String ENGINE_LABEL = "urgency inference engine";
    private static final Config CONFIG = Config.create();
    private static final RuntimeConfig RUNTIME_CONFIG = new RuntimeConfig(CONFIG);

    private final UrgencyInferenceEngine engine;

    public UrgencyInferenceService() {
        this(defaultEngine());
    }

    UrgencyInferenceService(UrgencyInferenceEngine engine) {
        this.engine = Objects.requireNonNull(engine, ENGINE_LABEL);
    }

    @Override
    public double score(String complaint) {
        String requiredComplaint = new RequiredText(COMPLAINT_LABEL).require(complaint);
        return engine.score(requiredComplaint);
    }

    private static UrgencyInferenceEngine defaultEngine() {
        UrgencyInferenceConfiguration configuration = UrgencyInferenceConfiguration.from(RUNTIME_CONFIG);
        return new UrgencyInferenceEngineFactory().create(configuration, CONFIG);
    }
}
