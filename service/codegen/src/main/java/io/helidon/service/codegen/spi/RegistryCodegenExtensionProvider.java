/*
 * Copyright (c) 2023, 2026 Oracle and/or its affiliates.
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

package io.helidon.service.codegen.spi;

import io.helidon.codegen.spi.CodegenProvider;
import io.helidon.service.codegen.RegistryCodegenContext;

/**
 * A {@link java.util.ServiceLoader} provider interface for extensions of code generators for Helidon Service Registry.
 * The difference between this extension and a general {@link io.helidon.codegen.spi.CodegenExtensionProvider} is that
 * this provider has access to {@link io.helidon.service.codegen.RegistryCodegenContext}.
 */
public interface RegistryCodegenExtensionProvider extends CodegenProvider {
    /**
     * Whether this extension should also process services whose service contracts contain annotations
     * supported by this extension.
     * <p>
     * Service contracts include direct service contracts and contracts provided by factory services. Contract annotations
     * are discovered from nested contract metadata, including annotations on the contract type, methods, method parameters,
     * parameter and return type arguments, and annotations matched through {@link #supportedMetaAnnotations()}.
     * <p>
     * Matching services are passed to the extension through {@link io.helidon.service.codegen.RegistryRoundContext#types()}.
     * <p>
     * Contract annotations do not make the implementation itself appear from
     * {@link io.helidon.service.codegen.RegistryRoundContext#annotatedTypes(io.helidon.common.types.TypeName)} unless the
     * implementation also has that annotation directly or through normal annotation inheritance.
     *
     * @return whether services with supported service-contract annotations should be processed
     */
    default boolean supportsServiceContractAnnotations() {
        return false;
    }

    /**
     * Create a new extension based on the context.
     *
     * @param codegenContext injection code generation context
     * @return a new extension
     */
    RegistryCodegenExtension create(RegistryCodegenContext codegenContext);
}
