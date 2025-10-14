/*
 * Copyright (c) 2024, 2025 Oracle and/or its affiliates.
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

import java.util.Set;

import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.common.types.TypeName;
import io.helidon.service.codegen.RegistryCodegenContext;
import io.helidon.service.codegen.spi.RegistryCodegenExtension;
import io.helidon.service.codegen.spi.RegistryCodegenExtensionProvider;

import static io.helidon.declarative.codegen.validation.ValidationTypes.VALIDATION_CONSTRAINT;
import static io.helidon.declarative.codegen.validation.ValidationTypes.VALIDATION_VALID;
import static io.helidon.declarative.codegen.validation.ValidationTypes.VALIDATION_VALIDATED;

/**
 * Java {@link java.util.ServiceLoader} provider implementation for
 * {@link io.helidon.service.codegen.spi.RegistryCodegenExtensionProvider} that generates required
 * services to handle declarative validation.
 */
@Weight(Weighted.DEFAULT_WEIGHT - 30)
public class ValidationExtensionProvider implements RegistryCodegenExtensionProvider {
    /**
     * Default constructor.
     *
     * @deprecated required by Java {@link java.util.ServiceLoader}
     */
    @Deprecated
    public ValidationExtensionProvider() {
    }

    @Override
    public Set<TypeName> supportedAnnotations() {
        return Set.of(VALIDATION_VALIDATED,
                      VALIDATION_VALID);
    }

    @Override
    public Set<TypeName> supportedMetaAnnotations() {
        return Set.of(VALIDATION_CONSTRAINT);
    }

    @Override
    public RegistryCodegenExtension create(RegistryCodegenContext codegenContext) {
        return new ValidationExtension(codegenContext);
    }
}
