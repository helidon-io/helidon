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

import java.time.Clock;
import java.util.Objects;

import io.helidon.common.types.Annotation;

/**
 * Context of {@link io.helidon.validation.spi.ConstraintValidator#check(ValidationContext, Object)}
 * and {@link io.helidon.validation.spi.TypeValidator#check(ValidationContext, Object)} and similar methods.
 */
public interface ValidationContext {
    /**
     * Create a new validation context for a given root type, where we do not have an instance to serve as root.
     *
     * @param rootType type of the root validation object
     * @return a new validation context
     */
    static ValidationContext create(Class<?> rootType) {
        Objects.requireNonNull(rootType, "rootType is null");
        return new ConstraintValidatorContextImpl(rootType, null);
    }

    /**
     * Create a new validation context for a given root type and object.
     *
     * @param rootType   type of the root validation object
     * @param rootObject instance of the root validation object
     * @return a new validation context
     */
    static ValidationContext create(Class<?> rootType, Object rootObject) {
        Objects.requireNonNull(rootType, "rootType is null");
        Objects.requireNonNull(rootObject, "rootObject is null");

        return new ConstraintValidatorContextImpl(rootType, rootObject);
    }

    /**
     * Create a new validation context for a given root type and object, and with the specified clock.
     *
     * @param rootType   type of the root validation object
     * @param rootObject instance of the root validation object
     * @param clock      clock to use for validation of calendar constraints
     * @return a new validation context
     */
    static ValidationContext create(Class<?> rootType, Object rootObject, Clock clock) {
        Objects.requireNonNull(rootType, "rootType is null");
        Objects.requireNonNull(rootObject, "rootObject is null");
        Objects.requireNonNull(clock, "clock is null");

        return new ConstraintValidatorContextImpl(rootType, rootObject, clock);
    }

    /**
     * Create a new failed validation response with the current path.
     *
     * @param annotation   annotation that caused this failure (a constraint annotation)
     * @param message      message describing the failure
     * @param invalidValue the value that was not valid, may be {@code null}!
     * @return a new failed validation response
     */
    ValidatorResponse response(Annotation annotation, String message, Object invalidValue);

    /**
     * Create a new success response.
     *
     * @return a new success response
     */
    ValidatorResponse response();

    /*
    TODO: can we hide enter/leave from the user
     */

    /**
     * Enter a new location.
     * The validation context internally maintains a path used to create constraint violations.
     *
     * @param location the location we are entering
     * @param name     name of the location (i.e. class name for type, method signature for method)
     */
    void enter(ConstraintViolation.Location location, String name);

    /**
     * Leave the current location.
     */
    void leave();

    /**
     * Clock to use for validation of calendar constraints.
     *
     * @return the configured clock, or the system clock if none was configured
     */
    Clock clock();
}
