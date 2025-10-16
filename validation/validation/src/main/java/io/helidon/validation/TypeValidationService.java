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

import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.common.types.TypeName;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceRegistry;
import io.helidon.validation.spi.TypeValidator;

@Service.Singleton
@Weight(Weighted.DEFAULT_WEIGHT - 10)
class TypeValidationService implements TypeValidation {
    private static final TypeName TYPE_VALIDATOR = TypeName.create(TypeValidator.class);
    private final ServiceRegistry registry;

    @Service.Inject
    TypeValidationService(ServiceRegistry registry) {
        this.registry = registry;
    }

    @Override
    public <T> ValidationResponse validate(Class<T> type, T object) {
        var validator = validator(type);

        var ctx = ValidationContext.create(type, object);
        validator.check(ctx, object);
        return ctx.response();
    }

    @Override
    public <T> void check(Class<T> type, T object) {
        var validator = validator(type);

        var ctx = ValidationContext.create(type, object);
        validator.check(ctx, object);
        ctx.throwOnFailure();
    }

    @Override
    public <T> ValidationResponse validate(Class<T> type, T object, String propertyName) {
        var ctx = ValidationContext.create(type, object);
        validator(type)
                .check(ctx,
                       object,
                       propertyName);

        return ctx.response();
    }

    @Override
    public <T> void check(Class<T> type, T object, String propertyName) {
        var ctx = ValidationContext.create(type, object);
        validator(type)
                .check(ctx,
                       object,
                       propertyName);
        ctx.throwOnFailure();
    }

    @Override
    public ValidationResponse validateProperty(Class<?> type, String propertyName, Object value) {
        var ctx = ValidationContext.create(type);
        validator(type)
                .checkProperty(ctx,
                               propertyName,
                               value);

        return ctx.response();
    }

    @Override
    public void checkProperty(Class<?> type, String propertyName, Object value) {
        var ctx = ValidationContext.create(type);
        validator(type)
                .checkProperty(ctx,
                               propertyName,
                               value);

        ctx.throwOnFailure();
    }

    private <T> TypeValidator<T> validator(Class<T> type) {
        TypeName typeName = TypeName.builder(TYPE_VALIDATOR)
                .addTypeArgument(TypeName.create(type))
                .build();

        Optional<TypeValidator<T>> validator = registry.firstNamed(typeName, type.getName());
        return validator.orElseThrow(() -> new IllegalArgumentException("No validator for type " + type.getName()));
    }
}
