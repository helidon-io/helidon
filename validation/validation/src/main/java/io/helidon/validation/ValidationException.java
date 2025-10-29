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
import java.util.Objects;

/**
 * This exception is thrown for validation failures.
 * <p>
 * It may contain a list of {@link ConstraintViolation}s.
 */
public class ValidationException extends RuntimeException {
    /**
     * List of validation constraints.
     */
    private final List<ConstraintViolation> violations;

    /**
     * Create a new exception with a descriptive message.
     *
     * @param message the error message
     */
    public ValidationException(String message) {
        this(message, List.of());
    }

    /**
     * Create a new exception with a descriptive message and a list of violations.
     *
     * @param message    the error message
     * @param violations constraint violations that caused this exception
     */
    public ValidationException(String message, List<ConstraintViolation> violations) {
        super(Objects.requireNonNull(message));
        this.violations = List.copyOf(Objects.requireNonNull(violations));
    }

    /**
     * List of constraint violations that caused this exception, never {@code null}.
     *
     * @return list of violations
     */
    public List<ConstraintViolation> violations() {
        return violations;
    }
}
