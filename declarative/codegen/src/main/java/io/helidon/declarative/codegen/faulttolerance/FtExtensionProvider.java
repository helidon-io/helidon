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

package io.helidon.declarative.codegen.faulttolerance;

import java.util.Set;

import io.helidon.common.types.TypeName;
import io.helidon.service.codegen.RegistryCodegenContext;
import io.helidon.service.codegen.spi.RegistryCodegenExtension;
import io.helidon.service.codegen.spi.RegistryCodegenExtensionProvider;

/**
 * Java {@link java.util.ServiceLoader} provider implementation for
 * {@link io.helidon.service.codegen.spi.RegistryCodegenExtensionProvider} that generates required
 * services to handle declarative Fault tolerance.
 */
public class FtExtensionProvider implements RegistryCodegenExtensionProvider {

    /**
     * Default constructor.
     *
     * @deprecated required by Java {@link java.util.ServiceLoader}
     */
    @Deprecated
    public FtExtensionProvider() {
    }

    @Override
    public Set<TypeName> supportedAnnotations() {
        return Set.of(FtTypes.FALLBACK_ANNOTATION,
                      FtTypes.RETRY_ANNOTATION,
                      FtTypes.CIRCUIT_BREAKER_ANNOTATION,
                      FtTypes.BULKHEAD_ANNOTATION,
                      FtTypes.TIMEOUT_ANNOTATION,
                      FtTypes.ASYNC_ANNOTATION);
    }

    @Override
    public RegistryCodegenExtension create(RegistryCodegenContext codegenContext) {
        return new FtExtension(codegenContext);
    }
}
