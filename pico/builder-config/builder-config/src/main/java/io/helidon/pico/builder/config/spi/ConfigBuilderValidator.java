/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.pico.builder.config.spi;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Responsible for validating config bean builders before they are built / consumed.
 */
public interface ConfigBuilderValidator {

    /**
     * Creates a round of validation against config bean attributes.
     *
     * @param builder           the builder instance
     * @param receiver          the eventual receiver for this builder, or null for new object creation
     * @param configBeanType    the config bean type
     * @return the validation round that can be used for attribute level validation
     */
    ValidationRound createValidationRound(Object builder, Object receiver, Class<?> configBeanType);

    /**
     * The validation issue severity level.
     * @see ConfigBuilderValidator.ValidationIssue#getSeverity()
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
     * Represents a single round of validation. A single round of validation will iterate over all attributes of the
     * config bean, calling {@link #validate(String, Supplier, Class, java.util.Map)} for each attribute.
     */
    interface ValidationRound extends ConfigBeanAttributeVisitor<Object> {
        /**
         * @return all issues found
         */
        List<ValidationIssue> getIssues();

        /**
         * @return true if any issues were found
         */
        default boolean hasIssues() {
            return !getIssues().isEmpty();
        }

        /**
         * @return true if any issues were found of type {@link ConfigBuilderValidator.Severity#ERROR}
         */
        default boolean hasErrors() {
            return getIssues().stream().anyMatch(it -> it.getSeverity() == Severity.ERROR);
        }

        /**
         * @return true if finished was called.
         */
        boolean isFinished();

        /**
         * Performs a validation for a single attribute.
         *
         * @param attributeName the attribute name being validated
         * @param valueSupplier the value supplier for the attribute
         * @param meta          the meta attributes for this attribute type
         * @param cbType        the attribute type
         * @return the fluent builder for this round
         */
        ValidationRound validate(String attributeName, Supplier<?> valueSupplier, Class<?> cbType, Map<String, Object> meta);

        @Override
        default void visit(String attributeName,
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
         */
        ValidationRound finish(boolean throwIfErrors);
    }

    /**
     * Represents an issue that was encountered during
     * {@link ConfigBuilderValidator.ValidationRound#validate(String, java.util.function.Supplier, Class, java.util.Map)}.
     */
    class ValidationIssue {
        final Severity severity;
        final String attributeName;
        final String message;

        public ValidationIssue(Severity severity, String attributeName, String message) {
            this.severity = Objects.requireNonNull(severity);
            this.attributeName = Objects.requireNonNull(attributeName);
            this.message = Objects.requireNonNull(message);
        }

        /**
         * @return the severity
         */
        public Severity getSeverity() {
            return severity;
        }

        /**
         * @return the attribute name
         */
        public String getAttributeName() {
            return attributeName;
        }

        /**
         * @return the user friendly message
         */
        public String getMessage() {
            return message;
        }

        @Override
        public String toString() {
            return getMessage();
        }
    }

}
