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

@Service.NamedByType(Validation.Number.Positive.class)
@Service.Singleton
@Weight(Weighted.DEFAULT_WEIGHT - 30)
class NumberPositiveValidatorProvider implements ConstraintValidatorProvider {
    @Override
    public ConstraintValidator create(TypeName type, Annotation constraintAnnotation) {
        if (type.equals(TypeNames.STRING) || type.equals(TypeName.create(CharSequence.class))) {
            return new ValidatorFromString(constraintAnnotation);
        }
        return new ValidatorFromNumber(constraintAnnotation);
    }

    private static final class ValidatorFromString extends BaseValidator {

        private ValidatorFromString(Annotation annotation) {
            super(annotation,
                  "%s is not a positive number",
                  ValidatorFromString::validate);

        }

        private static boolean validate(Object value) {
            if (!(value instanceof CharSequence chars)) {
                return false;
            }
            BigDecimal bd;
            try {
                bd = new BigDecimal(chars.toString());
            } catch (Exception e) {
                return false;
            }

            return bd.signum() > 0;
        }
    }

    private static final class ValidatorFromNumber extends BaseValidator {

        private ValidatorFromNumber(Annotation annotation) {
            super(annotation,
                  "%s is not positive",
                  ValidatorFromNumber::validate);

        }

        private static boolean validate(Object value) {
            if (!(value instanceof Number number)) {
                return false;
            }
            BigDecimal asBd = NumberHelper.toBigDecimal(number);
            return asBd.signum() > 0;
        }
    }
}
