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

@Service.NamedByType(Validation.Number.Min.class)
@Service.Singleton
@Weight(Weighted.DEFAULT_WEIGHT - 30)
class NumberMinValidatorProvider implements ConstraintValidatorProvider {
    @Override
    public ConstraintValidator create(TypeName type, Annotation constraintAnnotation) {
        String min = constraintAnnotation.value().orElseThrow();
        BigDecimal minNumber = new BigDecimal(min);

        if (type.equals(TypeNames.STRING) || type.equals(TypeName.create(CharSequence.class))) {
            return new ValidatorFromString(constraintAnnotation, minNumber);
        }
        return new ValidatorFromNumber(constraintAnnotation, minNumber);
    }

    private static final class ValidatorFromString extends BaseValidator {

        private ValidatorFromString(Annotation annotation, BigDecimal minNumber) {
            super(annotation,
                  "%s is smaller than " + minNumber,
                  it -> validate(it, minNumber));
        }

        private static boolean validate(Object value, BigDecimal minNumber) {
            if (!(value instanceof CharSequence chars)) {
                return false;
            }

            try {
                BigDecimal bd = new BigDecimal(chars.toString());
                return bd.compareTo(minNumber) >= 0;
            } catch (Exception e) {
                return false;
            }
        }
    }

    private static final class ValidatorFromNumber extends BaseValidator {

        private ValidatorFromNumber(Annotation annotation, BigDecimal minNumber) {
            super(annotation,
                  "%s is smaller than " + minNumber,
                  it -> validate(it, minNumber));
        }

        private static boolean validate(Object value, BigDecimal minNumber) {
            if (!(value instanceof Number number)) {
                return false;
            }

            BigDecimal asBd = NumberHelper.toBigDecimal(number);
            return asBd.compareTo(minNumber) >= 0;
        }
    }
}
