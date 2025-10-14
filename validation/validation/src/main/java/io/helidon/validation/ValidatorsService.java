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

package io.helidon.validation;

import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeNames;
import io.helidon.service.registry.Service;
import io.helidon.validation.spi.ConstraintValidator;
import io.helidon.validation.spi.ConstraintValidatorProvider;

@Service.Singleton
class ValidatorsService {
    private final ConstraintValidator notNullValidator;

    ValidatorsService(@Service.NamedByType(Validation.NotNull.class) ConstraintValidatorProvider notNullProvider) {
        this.notNullValidator = notNullProvider.create(TypeNames.OBJECT,
                                                       Annotation.create(Validation.NotNull.class));
    }

    void validateNotNull(ValidationContext ctx, Object value) {
        ctx.check(notNullValidator, value);
    }
}
