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

import java.util.function.Consumer;

import io.helidon.builder.api.RuntimeType;
import io.helidon.validation.spi.ConstraintValidator;

/**
 * Context of {@link io.helidon.validation.spi.TypeValidator#check(ValidationContext, Object)}, also used to validate
 * constraints using #check(ConstraintValidator, Object).
 */
@RuntimeType.PrototypedBy(ValidationContextConfig.class)
public interface ValidationContext extends RuntimeType.Api<ValidationContextConfig> {
    /**
     * Create a new fluent api builder for a {@link ValidationContext}.
     *
     * @return a new builder
     */
    static ValidationContextConfig.Builder builder() {
        return ValidationContextConfig.builder();
    }

    /**
     * Create a new validation context from a configuration.
     *
     * @param config configuration to use
     * @return a new validation context
     */
    static ValidationContext create(ValidationContextConfig config) {
        return new ValidationContextImpl(config);
    }

    /**
     * Create a new validation context customizing its configuration.
     *
     * @param builderConsumer consumer to update the builder
     * @return a new validation context
     */
    static ValidationContext create(Consumer<ValidationContextConfig.Builder> builderConsumer) {
        return builder()
                .update(builderConsumer)
                .build();
    }

    /**
     * Create a new validation context for a given root type, where we do not have an instance to serve as root.
     *
     * @param rootType type of the root validation object
     * @return a new validation context
     */
    static ValidationContext create(Class<?> rootType) {
        return builder()
                .rootType(rootType)
                .build();
    }

    /**
     * Create a new validation context for a given root type and object.
     *
     * @param rootType   type of the root validation object
     * @param rootObject instance of the root validation object, note that this may be {@code null}
     * @return a new validation context
     */
    static ValidationContext create(Class<?> rootType, Object rootObject) {
        return builder()
                .rootType(rootType)
                .update(it -> {
                    if (rootObject != null) {
                        it.rootObject(rootObject);
                    }
                })
                .build();
    }

    /**
     * The overall validation response current available on this context.
     * Calling this method will clear the current response.
     * <p>
     * Alternative method to clear the current response is {@link #throwOnFailure()}.
     *
     * @return the response combined from all
     *         {@link #check(io.helidon.validation.spi.ConstraintValidator, Object)}
     *         calls.
     */
    ValidationResponse response();

    /**
     * Throws a Validation exception in case the current response is not valid, returns normally otherwise.
     * Calling this method will clear the current response.
     */
    default void throwOnFailure() {
        var res = response();
        if (res.valid()) {
            return;
        }
        throw res.toException();
    }

    /**
     * Run the provided {@code check} on the provided {@code object}.
     * Adds the validator response to this context's validation response.
     *
     * @param validator the type validator or constraint validator to run
     * @param object    the object to check
     */
    void check(ConstraintValidator validator, Object object);

    /**
     * Enter a new scope.
     * The validation context internally maintains a path used to create constraint violations.
     *
     * @param location the location we are entering
     * @param name     name of the location (i.e. class name for type, method signature for method)
     * @return a new scope, should be used with try with resources to run any checks nested within the scope
     * @see ConstraintViolation#location()
     */
    Scope scope(ConstraintViolation.Location location, String name);

    /**
     * Scope of a validation operation.
     */
    interface Scope extends AutoCloseable {
        @Override
        void close();
    }
}
