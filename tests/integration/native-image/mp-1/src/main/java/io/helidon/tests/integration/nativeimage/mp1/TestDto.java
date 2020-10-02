/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
 */
package io.helidon.tests.integration.nativeimage.mp1;

import io.helidon.common.Reflected;

/**
 * JSON-B data transfer object.
 * Registered for reflection using the {@link io.helidon.common.Reflected} annotation.
 */
@Reflected
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
