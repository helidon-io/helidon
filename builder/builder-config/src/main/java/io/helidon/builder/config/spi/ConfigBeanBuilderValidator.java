/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.builder.config.spi;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import io.helidon.builder.AttributeVisitor;

/**
 * Validates a {@link io.helidon.builder.config.ConfigBean} generated builder type instance bean the builder build() is
 * called and the result is consumed.
 *
 * @param <CBB> the config bean builder type
 */
@FunctionalInterface
public interface ConfigBeanBuilderValidator<CBB> {

    /**
     * Creates a validation round for all the config bean attributes of the provided config bean.
     *
     * @param builder               the config builder instance
     * @param configBeanBuilderType the config bean type
     * @return the validation round that can be used for attribute level validation
     */
    ValidationRound createValidationRound(
            CBB builder,
            Class<CBB> configBeanBuilderType);


    /**
     * The validation issue severity level.
     * @see ConfigBeanBuilderValidator.ValidationIssue#severity()
     */
    enum Severity {

        /**
         * A warning level.
         */
        WARNING,

        /**
         * An error level.
         */
        ERROR

    }


    /**
     * Represents a single round of validation. A single round of validation will iterate over each attributes of the
     * config bean, calling {@link #validate(String, Supplier, Class, java.util.Map)} for each attribute.
     */
    interface ValidationRound extends AttributeVisitor<Object> {

        /**
         * All the issues found.
         *
         * @return all issues found
         */
        List<ValidationIssue> issues();

        /**
         * Returns true if there were any issues found including warnings or errors.
         *
         * @return true if any issues were found
         */
        default boolean hasIssues() {
            return !issues().isEmpty();
        }

        /**
         * Returns true if any errors were found.
         *
         * @return true if any issues were found of type {@link ConfigBeanBuilderValidator.Severity#ERROR}
         */
        default boolean hasErrors() {
            return issues().stream().anyMatch(it -> it.severity() == Severity.ERROR);
        }

        /**
         * Returns true if the validation round is completed.
         *
         * @return true if the validation round is completed
         * @see #finish(boolean)
         */
        boolean isCompleted();

        /**
         * Performs a validation for a single attribute.
         *
         * @param attributeName the attribute name being validated
         * @param valueSupplier the value supplier for the attribute
         * @param meta          the meta attributes for this attribute type
         * @param cbType        the attribute type
         * @return the validation round continuation as a fluent-builder
         */
        ValidationRound validate(
                String attributeName,
                Supplier<?> valueSupplier,
                Class<?> cbType,
                Map<String, Object> meta);

        @Override
        default void visit(
                String attributeName,
                Supplier<Object> valueSupplier,
                Map<String, Object> meta,
                Object userDefinedCtx,
                Class<?> cbType,
                Class<?>... ignored) {
            validate(attributeName, valueSupplier, cbType, meta);
        }

        /**
         * Finishes the validation round, and will optionally throw a runtime exception if any error issues were found.
         *
         * @param throwIfErrors flag to indicate whether an exception should be raised if any errors were found
         * @return the fluent builder for this round
         * @throws java.lang.IllegalStateException if there were any validation errors in the round
         */
        ValidationRound finish(
                boolean throwIfErrors);
    }


    /**
     * Represents an issue that was encountered during
     * {@link ValidationRound#validate(String, java.util.function.Supplier, Class, java.util.Map)}.
     */
    class ValidationIssue {
        private final Severity severity;
        private final String attributeName;
        private final String message;

        /**
         * Constructs a new validation issue.
         *
         * @param severity      the severity
         * @param attributeName the attribute name in question
         * @param message       the message
         */
        public ValidationIssue(
                Severity severity,
                String attributeName,
                String message) {
            this.severity = Objects.requireNonNull(severity);
            this.attributeName = Objects.requireNonNull(attributeName);
            this.message = Objects.requireNonNull(message);
        }

        /**
         * Return the severity.
         *
         * @return the severity
         */
        public Severity severity() {
            return severity;
        }

        /**
         * Returns the attribute name in question.
         *
         * @return the attribute name
         */
        public String attributeName() {
            return attributeName;
        }

        /**
         * Returns the user-friendly message.
         *
         * @return the user-friendly message
         */
        public String message() {
            return message;
        }

        @Override
        public String toString() {
            return message();
        }

    }

}
