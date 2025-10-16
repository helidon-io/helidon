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
import io.helidon.validation.Check;
import io.helidon.validation.spi.ConstraintValidator;
import io.helidon.validation.spi.ConstraintValidatorProvider;

@Service.NamedByType(Check.Long.Max.class)
@Service.Singleton
@Weight(Weighted.DEFAULT_WEIGHT - 30)
class LongMaxValidatorProvider implements ConstraintValidatorProvider {
    @Override
    public ConstraintValidator create(TypeName type, Annotation constraintAnnotation) {
        long max = constraintAnnotation.longValue().orElse(Long.MAX_VALUE);

        return new BaseValidator(constraintAnnotation,
                                 "%s is more than " + max,
                                 it -> it instanceof Long value && value <= max);
    }
}
