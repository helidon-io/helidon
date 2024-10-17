/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

package io.helidon.service.codegen;

import java.util.Set;

import io.helidon.codegen.Option;
import io.helidon.common.types.TypeName;
import io.helidon.service.codegen.spi.RegistryCodegenExtension;
import io.helidon.service.codegen.spi.RegistryCodegenExtensionProvider;

/**
 * A {@link java.util.ServiceLoader} provider implementation that adds code generation for Helidon Service Registry.
 * This extension creates service descriptors.
 */
public class ServiceExtensionProvider implements RegistryCodegenExtensionProvider {
    /**
     * Required default constructor for {@link java.util.ServiceLoader}.
     *
     * @deprecated only for {@link java.util.ServiceLoader}
     */
    @Deprecated
    public ServiceExtensionProvider() {
        super();
    }

    @Override
    public Set<Option<?>> supportedOptions() {
        return Set.of(ServiceOptions.AUTO_ADD_NON_CONTRACT_INTERFACES);
    }

    @Override
    public Set<TypeName> supportedAnnotations() {
        return Set.of(ServiceCodegenTypes.SERVICE_ANNOTATION_PROVIDER);
    }

    @Override
    public RegistryCodegenExtension create(RegistryCodegenContext codegenContext) {
        return new ServiceExtension(codegenContext);
    }
}
