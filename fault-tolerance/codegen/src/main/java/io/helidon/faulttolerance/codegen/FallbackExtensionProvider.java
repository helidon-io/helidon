/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.faulttolerance.codegen;

import java.util.Set;

import io.helidon.common.types.TypeName;
import io.helidon.service.codegen.ServiceCodegenContext;
import io.helidon.service.codegen.spi.InjectCodegenExtension;
import io.helidon.service.codegen.spi.InjectCodegenExtensionProvider;

/**
 * Java {@link java.util.ServiceLoader} provider implementation for
 * {@link io.helidon.service.codegen.spi.InjectCodegenExtensionProvider} that generates required
 * services to handle declarative Fault tolerance.
 */
public class FallbackExtensionProvider implements InjectCodegenExtensionProvider {
    static final TypeName FALLBACK_ANNOTATION = TypeName.create("io.helidon.faulttolerance.FaultTolerance.Fallback");

    /**
     * Default constructor.
     *
     * @deprecated required by Java {@link java.util.ServiceLoader}
     */
    @Deprecated
    public FallbackExtensionProvider() {
    }

    @Override
    public Set<TypeName> supportedAnnotations() {
        return Set.of(FALLBACK_ANNOTATION);
    }

    @Override
    public InjectCodegenExtension create(ServiceCodegenContext codegenContext) {
        return new FallbackExtension(codegenContext);
    }
}
