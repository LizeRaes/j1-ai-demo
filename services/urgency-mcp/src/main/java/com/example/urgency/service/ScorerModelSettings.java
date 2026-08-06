package com.example.urgency.service;

import java.nio.file.Path;
import java.util.Objects;

import com.example.urgency.validation.RequiredText;

record ScorerModelSettings(String modelName, Path modelLocation) {

    private static final String MODEL_NAME_LABEL = "scorer model name";
    private static final String MODEL_LOCATION_LABEL = "scorer model location";

    public static ScorerModelSettings of(String modelName, Path modelLocation) {
        modelName = new RequiredText(MODEL_NAME_LABEL).require(modelName);
        modelLocation = Objects.requireNonNull(modelLocation, MODEL_LOCATION_LABEL).toAbsolutePath().normalize();
        return new ScorerModelSettings(modelName, modelLocation);
    }

    Path scorerModelPath() {
        return modelLocation.resolve(modelName).normalize();
    }
}
