package com.example.document.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record DocumentDeleteRequest (@JsonProperty("documentName") String documentName) { }