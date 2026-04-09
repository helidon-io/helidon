/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

import java.util.Locale;

import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.service.registry.Service;
import io.helidon.validation.Validation;
import io.helidon.validation.ValidationException;
import io.helidon.validation.spi.ConstraintValidator;
import io.helidon.validation.spi.ConstraintValidatorProvider;

import static io.helidon.validation.validators.IntegerMaxValidatorProvider.charToString;

@Service.NamedByType(Validation.Integer.MultipleOf.class)
@Service.Singleton
@Weight(Weighted.DEFAULT_WEIGHT - 30)
class IntegerMultipleOfValidatorProvider implements ConstraintValidatorProvider {

    @Override
    public ConstraintValidator create(TypeName type, Annotation constraintAnnotation) {
        int factor = constraintAnnotation.intValue().orElse(0);

        if (factor <= 0) {
            throw new IllegalArgumentException("Validation.Integer.MultipleOf value must be greater than zero: "
                                                       + factor);
        }

        if (type.equals(TypeNames.PRIMITIVE_INT) || type.equals(TypeNames.BOXED_INT)) {
            return new BaseValidator(constraintAnnotation,
                                     "%d is not a multiple of " + factor,
                                     it -> it instanceof Integer value && value % factor == 0);
        }

        if (type.equals(TypeNames.PRIMITIVE_LONG) || type.equals(TypeNames.BOXED_LONG)) {
            return new BaseValidator(constraintAnnotation,
                                     "%d is not a multiple of " + factor,
                                     it -> it instanceof Long value && value % factor == 0);
        }

        if (type.equals(TypeNames.PRIMITIVE_SHORT) || type.equals(TypeNames.BOXED_SHORT)) {
            return new BaseValidator(constraintAnnotation,
                                     "%d is not a multiple of " + factor,
                                     it -> it instanceof Short value && value % factor == 0);
        }

        if (type.equals(TypeNames.PRIMITIVE_BYTE) || type.equals(TypeNames.BOXED_BYTE)) {
            return new BaseValidator(constraintAnnotation,
                                     "0x%1$02x is not a multiple of 0x"
                                             + Integer.toHexString(factor).toUpperCase(Locale.ROOT),
                                     it -> it instanceof Byte value && (value & 0xFF) % factor == 0);
        }

        if (type.equals(TypeNames.PRIMITIVE_CHAR) || type.equals(TypeNames.BOXED_CHAR)) {
            return new CharValidator(constraintAnnotation, factor);
        }

        throw new ValidationException("Invalid type of Integer.MultipleOf: " + type.fqName());
    }

    private static class CharValidator extends BaseValidator {

        private CharValidator(Annotation constraintAnnotation, int factor) {
            super(constraintAnnotation,
                  "'%1$c' (%1$d) is not a multiple of " + charToString(factor),
                  it -> it instanceof Character value && value % factor == 0);
        }

        @Override
        protected Object convertValue(Object object) {
            if (object instanceof Character c) {
                return (int) c;
            }
            return object;
        }
    }
}
