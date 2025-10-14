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

import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeName;
import io.helidon.service.registry.Service;
import io.helidon.validation.Validation;
import io.helidon.validation.spi.ConstraintValidator;
import io.helidon.validation.spi.ConstraintValidatorProvider;

@Service.NamedByType(Validation.String.Length.class)
@Service.Singleton
@Weight(Weighted.DEFAULT_WEIGHT - 30)
class StringLengthValidatorProvider implements ConstraintValidatorProvider {
    @Override
    public ConstraintValidator create(TypeName type, Annotation constraintAnnotation) {
        int minLength = constraintAnnotation.intValue("min").orElse(0);
        int maxLength = constraintAnnotation.intValue("value").orElse(Integer.MAX_VALUE);
        if (minLength == 0) {
            return new StringLengthValidator(constraintAnnotation, maxLength, true);
        }
        if (maxLength == Integer.MAX_VALUE) {
            return new StringLengthValidator(constraintAnnotation, minLength);
        }
        return new StringLengthValidator(constraintAnnotation, minLength, maxLength);
    }

    private static final class StringLengthValidator extends BaseValidator {
        private StringLengthValidator(Annotation annotation, int minLength, int maxLength) {
            super(annotation,
                  "is shorter than " + minLength + " or longer than " + maxLength + " characters",
                  it -> validate(it, minLength, maxLength));
        }

        private StringLengthValidator(Annotation annotation, int minLength) {
            super(annotation,
                  "is shorter than " + minLength + " characters",
                  it -> validate(it, minLength, Integer.MAX_VALUE));
        }

        private StringLengthValidator(Annotation annotation, int maxLength, boolean ignored) {
            super(annotation,
                  "is longer than " + maxLength + " characters",
                  it -> validate(it, 0, maxLength));
        }

        private static boolean validate(Object value, int minLength, int maxLength) {
            if (!(value instanceof CharSequence chars)) {
                return false;
            }
            int len = chars.length();

            return len >= minLength && len <= maxLength;
        }
    }
}
