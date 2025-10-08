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

import java.util.List;

/**
 * Response from a {@link io.helidon.validation.spi.ConstraintValidator} or {@link io.helidon.validation.spi.TypeValidator}.
 * Responses are created from {@link ValidationContext#response()} for successful validations,
 * and from {@link ValidationContext#response(io.helidon.common.types.Annotation, String, Object)}
 * for validation failures.
 */
public interface ValidatorResponse {

    /**
     * True if this is a failed validation response.
     *
     * @return {@code true} if this is a failed validation response, {@code false} otherwise
     */
    boolean failed();

    /**
     * Message describing the validation failure(s).
     *
     * @return message describing the validation failure(s)
     */
    String message();

    /**
     * All violations of this response.
     *
     * @return list of violations
     */
    List<ConstraintViolation> violations();

    /**
     * Merge with another response.
     *
     * @param other response to merge with
     * @return a validator response that contains all violations of both responses, and if either was failed, the result is failed
     */
    ValidatorResponse merge(ValidatorResponse other);

    /**
     * Convert this response to a {@link ValidationException}.
     *
     * @return a new exception with all violations from this response
     * @throws IllegalStateException if this response is not failed
     */
    ValidationException toException();
}
