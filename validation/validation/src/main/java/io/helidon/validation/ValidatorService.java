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

import java.util.Optional;

import io.helidon.common.types.TypeName;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceRegistry;
import io.helidon.validation.spi.TypeValidator;

@Service.Singleton
class ValidatorService implements Validator {
    private static final TypeName TYPE_VALIDATOR = TypeName.create(TypeValidator.class);
    private final ServiceRegistry registry;

    @Service.Inject
    ValidatorService(ServiceRegistry registry) {
        this.registry = registry;
    }

    @Override
    public <T> ValidatorResponse validate(Class<T> type, T object) {
        var validator = validator(type);

        return validator.check(new ConstraintValidatorContextImpl(type, object),
                               object);
    }

    @Override
    public <T> ValidatorResponse validate(Class<T> type, T object, String propertyName) {
        return validator(type)
                .check(new ConstraintValidatorContextImpl(type, object),
                       object,
                       propertyName);
    }

    @Override
    public ValidatorResponse validateProperty(Class<?> type, String propertyName, Object value) {
        return validator(type)
                .checkProperty(new ConstraintValidatorContextImpl(type, null),
                               propertyName,
                               value);
    }

    private <T> TypeValidator<T> validator(Class<T> type) {
        TypeName typeName = TypeName.builder(TYPE_VALIDATOR)
                .addTypeArgument(TypeName.create(type))
                .build();

        Optional<TypeValidator<T>> validator = registry.firstNamed(typeName, type.getName());
        return validator.orElseThrow(() -> new ValidationException("No validator for type " + type.getName()));
    }
}
