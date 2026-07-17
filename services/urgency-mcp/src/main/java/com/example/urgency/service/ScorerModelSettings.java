package com.example.urgency.service;

import java.nio.file.Path;
import java.util.Objects;

import com.example.urgency.validation.RequiredText;

record ScorerModelSettings(String modelName, Path modelLocation) {

    private static final String MODEL_NAME_LABEL = "scorer model name";
    private static final String MODEL_LOCATION_LABEL = "scorer model location";

    ScorerModelSettings {
        modelName = new RequiredText(MODEL_NAME_LABEL).require(modelName);
        modelLocation = Objects.requireNonNull(modelLocation, MODEL_LOCATION_LABEL).toAbsolutePath().normalize();
    }

    Path scorerModelPath() {
        return modelLocation.resolve(modelName).normalize();
    }
}
