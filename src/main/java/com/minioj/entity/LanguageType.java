package com.minioj.entity;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum LanguageType {
    JAVA, CPP;

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static LanguageType fromJson(String value) {
        return valueOf(value);
    }
}
