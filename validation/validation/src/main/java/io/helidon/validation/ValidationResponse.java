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
 * Response from a validation context after processing all checks.
 */
public interface ValidationResponse {
    /**
     * True if this is a valid response.
     *
     * @return {@code true} if this is a valid response, {@code false} otherwise
     */
    boolean valid();

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
     * Convert this response to a {@link ValidationException}.
     *
     * @return a new exception with all violations from this response
     * @throws IllegalStateException if this response is not failed
     */
    ValidationException toException();
}
