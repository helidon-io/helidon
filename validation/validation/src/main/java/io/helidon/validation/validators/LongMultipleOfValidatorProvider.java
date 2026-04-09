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

import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeName;
import io.helidon.service.registry.Service;
import io.helidon.validation.Validation;
import io.helidon.validation.spi.ConstraintValidator;
import io.helidon.validation.spi.ConstraintValidatorProvider;

@Service.NamedByType(Validation.Long.MultipleOf.class)
@Service.Singleton
@Weight(Weighted.DEFAULT_WEIGHT - 30)
class LongMultipleOfValidatorProvider implements ConstraintValidatorProvider {

    @Override
    public ConstraintValidator create(TypeName type, Annotation constraintAnnotation) {
        long factor = constraintAnnotation.longValue().orElse(0L);

        if (factor <= 0L) {
            throw new IllegalArgumentException("Validation.Long.MultipleOf value must be greater than zero: "
                                                       + factor);
        }

        return new BaseValidator(constraintAnnotation,
                                 "%s is not a multiple of " + factor,
                                 it -> it instanceof Long value && value % factor == 0L);
    }
}
