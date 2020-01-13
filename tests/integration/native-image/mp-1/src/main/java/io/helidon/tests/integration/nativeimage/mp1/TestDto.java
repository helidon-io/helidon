/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
 */
package io.helidon.tests.integration.nativeimage.mp1;

import javax.enterprise.context.Dependent;

/**
 * JSON-B data transfer object.
 * Must be registered for reflection (see src/main/resources/META-INF/native-image/....json)
 */
// bean defining annotation, just to auto register for reflection
@Dependent
public class TestDto {
    private String message;

    public TestDto() {
    }

    public TestDto(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public TestDto setMessage(String message) {
        this.message = message;
        return this;
    }

    @Override
    public String toString() {
        return "TestDto: " + message;
    }
}
