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

package io.helidon.validation.validators;

import java.util.function.Predicate;

import io.helidon.common.types.Annotation;
import io.helidon.validation.ValidatorContext;
import io.helidon.validation.ValidatorResponse;
import io.helidon.validation.spi.ConstraintValidator;

class BaseValidator implements ConstraintValidator {
    private final Predicate<Object> check;
    private final String messageFormat;
    private final boolean sendNulls;
    private final Annotation annotation;

    /**
     * Create a validator that will not receive {@code null} values - these are considered valid.
     *
     * @param annotation     annotation to obtain a custom message
     * @param defaultMessage default message to use if no custom message is provided in the annotation
     * @param check          predicate that returns {@code true} if the value is valid, {@code false} otherwise
     */
    protected BaseValidator(Annotation annotation, String defaultMessage, Predicate<Object> check) {
        this(annotation, defaultMessage, check, false);
    }

    /**
     * Create a validator.
     *
     * @param annotation     annotation to obtain a custom message
     * @param defaultMessage default message to use if no custom message is provided in the annotation
     * @param check          predicate that returns {@code true} if the value is valid, {@code false} otherwise
     * @param sendNulls      whether to send {@code null} values to the validator
     */
    protected BaseValidator(Annotation annotation, String defaultMessage, Predicate<Object> check, boolean sendNulls) {
        this.messageFormat = annotation.stringValue("message")
                .filter(Predicate.not(String::isBlank))
                .orElse(defaultMessage);
        this.check = check;
        this.sendNulls = sendNulls;
        this.annotation = annotation;
    }

    @Override
    public ValidatorResponse check(ValidatorContext context, Object value) {
        if (value == null && !sendNulls) {
            return ValidatorResponse.create();
        }

        if (check.test(value)) {
            return ValidatorResponse.create();
        }

        return ValidatorResponse.create(annotation, formatMessage(convertValue(value)), value);
    }

    /**
     * Format the error message.
     *
     * @param parameter the invalid value
     * @return string to use in constraint violation
     */
    protected String formatMessage(Object parameter) {
        return messageFormat.formatted(parameter);
    }

    /**
     * Possibility to convert the value before printing an error message.
     *
     * @param object actual object received
     * @return converted object
     */
    protected Object convertValue(Object object) {
        return object;
    }

    /**
     * The constraint annotation.
     *
     * @return annotation
     */
    protected Annotation annotation() {
        return annotation;
    }
}
