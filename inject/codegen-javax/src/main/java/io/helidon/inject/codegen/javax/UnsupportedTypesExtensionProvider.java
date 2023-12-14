/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.inject.codegen.javax;

import java.util.Set;

import io.helidon.codegen.Option;
import io.helidon.common.types.TypeName;
import io.helidon.inject.codegen.InjectOptions;
import io.helidon.inject.codegen.InjectionCodegenContext;
import io.helidon.inject.codegen.spi.InjectCodegenExtension;
import io.helidon.inject.codegen.spi.InjectCodegenExtensionProvider;

/**
 * A {@link java.util.ServiceLoader} provider implementation of
 * {@link io.helidon.inject.codegen.spi.InjectCodegenExtensionProvider} that adds support for checking annotations that are
 * recognized, yet not supported.
 * <p>
 * The default behavior is to fail code generation session if such an annotation is encountered.
 */
public class UnsupportedTypesExtensionProvider implements InjectCodegenExtensionProvider {
    private static final Set<TypeName> TYPES = Set.of(
            JavaxTypes.ANNOT_MANAGED_BEAN,
            JavaxTypes.ANNOT_RESOURCE,
            JavaxTypes.ANNOT_RESOURCES,
            CdiTypes.APPLICATION_SCOPED,
            CdiTypes.BEFORE_DESTROYED,
            CdiTypes.CONVERSATION_SCOPED,
            CdiTypes.DEPENDENT,
            CdiTypes.DESTROYED,
            CdiTypes.INITIALIZED,
            CdiTypes.NORMAL_SCOPE,
            CdiTypes.REQUEST_SCOPED,
            CdiTypes.SESSION_SCOPED,
            CdiTypes.ACTIVATE_REQUEST_CONTEXT,
            CdiTypes.OBSERVES,
            CdiTypes.OBSERVES_ASYNC,
            CdiTypes.ALTERNATIVE,
            CdiTypes.DISPOSES,
            CdiTypes.INTERCEPTED,
            CdiTypes.MODEL,
            CdiTypes.PRODUCES,
            CdiTypes.SPECIALIZES,
            CdiTypes.STEREOTYPE,
            CdiTypes.TRANSIENT_REFERENCE,
            CdiTypes.TYPED,
            CdiTypes.VETOED,
            CdiTypes.NONBINDING
    );

    /**
     * Required default constructor.
     *
     * @deprecated required by {@link java.util.ServiceLoader}
     */
    @Deprecated
    public UnsupportedTypesExtensionProvider() {
    }

    @Override
    public Set<Option<?>> supportedOptions() {
        return Set.of(MapApplicationScopedProvider.MAP_APPLICATION_TO_SINGLETON_SCOPE,
                      InjectOptions.IGNORE_UNSUPPORTED_ANNOTATIONS);
    }

    @Override
    public Set<TypeName> supportedAnnotations() {
        return TYPES;
    }

    @Override
    public InjectCodegenExtension create(InjectionCodegenContext codegenContext) {
        return new UnsupportedTypesExtension(codegenContext);
    }
}
