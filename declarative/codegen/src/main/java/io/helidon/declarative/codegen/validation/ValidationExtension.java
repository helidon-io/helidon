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

package io.helidon.declarative.codegen.validation;

import java.util.Collection;

import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.service.codegen.RegistryCodegenContext;
import io.helidon.service.codegen.RegistryRoundContext;
import io.helidon.service.codegen.spi.RegistryCodegenExtension;

import static io.helidon.declarative.codegen.validation.ValidationTypes.VALIDATION_CONSTRAINT;

class ValidationExtension implements RegistryCodegenExtension {
    private static final TypeName GENERATOR = TypeName.create(ValidationExtension.class);

    private final RegistryCodegenContext ctx;

    ValidationExtension(RegistryCodegenContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void process(RegistryRoundContext roundContext) {
        // we want both, as we must not add interceptors to types that are marked as validated
        // also constraints are valid (outside validated types) only on interceptable services
        Collection<TypeInfo> validated = roundContext.annotatedTypes(ValidationTypes.VALIDATION_VALIDATED);
        Collection<TypeName> constraintAnnotations = roundContext.annotatedAnnotations(VALIDATION_CONSTRAINT);

        var validatedGenerator = new ValidatedTypeGenerator(roundContext, constraintAnnotations);
        for (TypeInfo validate : validated) {
            validatedGenerator.process(validate);
        }

        var interceptorGenerator = new InterceptorGenerator(roundContext, constraintAnnotations);
        for (TypeInfo type : roundContext.types()) {
            interceptorGenerator.process(type);
        }
    }
}
