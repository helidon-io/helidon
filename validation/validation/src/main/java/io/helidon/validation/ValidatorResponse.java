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

/**
 * A response returned by a
 * {@link io.helidon.validation.spi.ConstraintValidator#check(io.helidon.validation.ValidatorContext, Object)}.
 */
public interface ValidatorResponse {
    /**
     * Create a new valid response.
     *
     * @return a new valid response
     */
    static ValidatorResponse create() {
        return new OkValidatorResponse();
    }

    /**
     * Create a new failed response.
     *
     * @param annotation annotation that triggered the check
     * @param message message describing the failure
     * @param invalidValue the value that triggered the failure
     * @return a new failed response
     */
    static ValidatorResponse create(Annotation annotation, String message, Object invalidValue) {
        return new FailedValidatorResponse(annotation, message, invalidValue);
    }

    /**
     * Whether the response was valid.
     *
     * @return if valid
     */
    boolean valid();

    /**
     * Annotation that triggered the check.
     *
     * @return annotation of the check
     * @throws java.lang.IllegalStateException in case the response is valid
     */
    Annotation annotation();

    /**
     * Error message describing the failure.
     *
     * @return error message
     * @throws java.lang.IllegalStateException in case the response is valid
     */
    String message();

    /**
     * The value that triggered the failure.
     *
     * @return the value
     * @throws java.lang.IllegalStateException in case the response is valid
     */
    Object invalidValue();
}
