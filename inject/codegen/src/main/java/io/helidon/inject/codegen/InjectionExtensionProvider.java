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

package io.helidon.inject.codegen;

import java.util.Set;

import io.helidon.codegen.Option;
import io.helidon.common.types.TypeName;
import io.helidon.inject.codegen.spi.InjectCodegenExtension;
import io.helidon.inject.codegen.spi.InjectCodegenExtensionProvider;

/**
 * A {@link java.util.ServiceLoader} provider implementation that adds code generation for Helidon Inject.
 * This extension creates service descriptors, and intercepted types.
 */
public class InjectionExtensionProvider implements InjectCodegenExtensionProvider {
    /**
     * Required default constructor for {@link java.util.ServiceLoader}.
     *
     * @deprecated only for {@link java.util.ServiceLoader}
     */
    @Deprecated
    public InjectionExtensionProvider() {
        super();
    }

    @Override
    public Set<Option<?>> supportedOptions() {
        return Set.of(InjectOptions.AUTO_ADD_NON_CONTRACT_INTERFACES,
                      InjectOptions.INTERCEPTION_STRATEGY,
                      InjectOptions.SCOPE_META_ANNOTATIONS);
    }

    @Override
    public Set<TypeName> supportedAnnotations() {
        return Set.of(InjectCodegenTypes.INJECTION_SINGLETON,
                      InjectCodegenTypes.INJECTION_PRE_DESTROY,
                      InjectCodegenTypes.INJECTION_POST_CONSTRUCT,
                      InjectCodegenTypes.INJECTION_INJECT,
                      InjectCodegenTypes.INJECTION_SERVICE,
                      InjectCodegenTypes.INJECTION_REQUEST_SCOPE);
    }

    @Override
    public InjectCodegenExtension create(InjectionCodegenContext codegenContext) {
        return new InjectionExtension(codegenContext);
    }
}
