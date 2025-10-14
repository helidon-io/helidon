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

import java.math.BigDecimal;

import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.service.registry.Service;
import io.helidon.validation.Validation;
import io.helidon.validation.spi.ConstraintValidator;
import io.helidon.validation.spi.ConstraintValidatorProvider;

@Service.NamedByType(Validation.Number.Digits.class)
@Service.Singleton
@Weight(Weighted.DEFAULT_WEIGHT - 30)
class NumberDigitsValidatorProvider implements ConstraintValidatorProvider {
    @Override
    public ConstraintValidator create(TypeName type, Annotation constraintAnnotation) {
        int integer = constraintAnnotation.intValue("integer").orElse(-1);
        int fraction = constraintAnnotation.intValue("fraction").orElse(-1);

        if (type.equals(TypeNames.STRING) || type.equals(TypeName.create(CharSequence.class))) {
            return new ValidatorFromString(constraintAnnotation, integer, fraction);
        }
        // this should be improved eventually to support various types of numbers directly, to avoid
        // guesswork at the time of validation
        return new ValidatorFromNumber(constraintAnnotation, integer, fraction);
    }

    private static String defaultMessage(int integer, int fraction) {
        if (integer == -1 && fraction == -1) {
            return "";
        }
        if (integer == -1) {
            return "%s has invalid number of fraction digits, it should be up to " + fraction;
        }
        if (fraction == -1) {
            return "%s has invalid number of integer digits, it should be up to " + integer;
        }
        return "%s has invalid number of digits, they should be up to " + integer + " (integer digits) and up to "
                + fraction + " (fractional digits)";
    }

    private static boolean validate(BigDecimal value, int integer, int fraction) {

        if (fraction > -1) {
            int length = Math.max(value.scale(), 0);
            if (length > fraction) {
                return false;
            }
        }
        if (integer > -1) {
            int length = value.precision() - value.scale();
            return length <= integer;
        }
        return true;
    }

    private static final class ValidatorFromString extends BaseValidator
            implements ConstraintValidator {

        private ValidatorFromString(Annotation annotation, int integer, int fraction) {
            super(annotation,
                  defaultMessage(integer, fraction),
                  it -> validate(it, integer, fraction));
        }

        private static boolean validate(Object value, int integer, int fraction) {
            if (!(value instanceof CharSequence chars)) {
                return false;
            }

            BigDecimal bd;
            try {
                bd = new BigDecimal(chars.toString());
            } catch (Exception e) {
                return false;
            }

            return NumberDigitsValidatorProvider.validate(bd, integer, fraction);
        }
    }

    private static final class ValidatorFromNumber extends BaseValidator
            implements ConstraintValidator {

        private ValidatorFromNumber(Annotation annotation, int integer, int fraction) {
            super(annotation,
                  defaultMessage(integer, fraction),
                  it -> validate(it, integer, fraction));
        }

        private static boolean validate(Object value, int integer, int fraction) {
            if (!(value instanceof Number number)) {
                return false;
            }

            return NumberDigitsValidatorProvider.validate(NumberHelper.toBigDecimal(number), integer, fraction);
        }
    }
}
