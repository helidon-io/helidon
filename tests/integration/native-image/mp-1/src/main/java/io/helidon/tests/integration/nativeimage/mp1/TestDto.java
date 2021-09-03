/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
