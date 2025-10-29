/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.validation;

import io.helidon.common.types.Annotation;

class FailedValidatorResponse implements ValidatorResponse {
    private final Annotation annotation;
    private final String message;
    private final Object invalidValue;

    FailedValidatorResponse(Annotation annotation, String message, Object invalidValue) {
        this.annotation = annotation;
        this.message = message;
        this.invalidValue = invalidValue;
    }

    @Override
    public boolean valid() {
        return false;
    }

    @Override
    public Annotation annotation() {
        return annotation;
    }

    @Override
    public String message() {
        return message;
    }

    @Override
    public Object invalidValue() {
        return invalidValue;
    }
}
