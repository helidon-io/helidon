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

package io.helidon.service.codegen.javax;

import java.util.Collection;
import java.util.Set;

import io.helidon.codegen.CodegenContext;
import io.helidon.codegen.CodegenOptions;
import io.helidon.codegen.Option;
import io.helidon.codegen.spi.AnnotationMapper;
import io.helidon.codegen.spi.AnnotationMapperProvider;
import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeName;

import static io.helidon.service.codegen.ServiceCodegenTypes.INJECTION_SINGLETON;

/**
 * A {@link java.util.ServiceLoader} provider implementation of an annotation mapper that maps CDI application scoped beans
 * to Singleton services.
 */
@Weight(Weighted.DEFAULT_WEIGHT - 10) // lower weight than JavaxAnnotationMapper
public class MapApplicationScopedProvider implements AnnotationMapperProvider {
    /**
     * Identify whether any application scopes (from ee) is translated to {@code Singleton}.
     */
    static final Option<Boolean> MAP_APPLICATION_TO_SINGLETON_SCOPE
            = Option.create("helidon.inject.mapApplicationToSingletonScope",
                            "Should we map application scoped beans from javax CDI to Singleton services?",
                            false);
    private static final Annotation SINGLETON = Annotation.create(INJECTION_SINGLETON);

    /**
     * Required default constructor.
     *
     * @deprecated required by {@link java.util.ServiceLoader}
     */
    @Deprecated
    public MapApplicationScopedProvider() {
    }

    @Override
    public Set<Option<?>> supportedOptions() {
        return Set.of(MAP_APPLICATION_TO_SINGLETON_SCOPE);
    }

    @Override
    public Set<TypeName> supportedAnnotations() {
        return Set.of(CdiTypes.APPLICATION_SCOPED);
    }

    @Override
    public AnnotationMapper create(CodegenOptions options) {
        return new ApplicationScopedMapper(MAP_APPLICATION_TO_SINGLETON_SCOPE.value(options));
    }

    private static class ApplicationScopedMapper implements AnnotationMapper {
        private final boolean enabled;

        private ApplicationScopedMapper(boolean enabled) {
            this.enabled = enabled;
        }

        @Override
        public boolean supportsAnnotation(Annotation annotation) {
            return enabled && annotation.typeName().equals(CdiTypes.APPLICATION_SCOPED);
        }

        @Override
        public Collection<Annotation> mapAnnotation(CodegenContext ctx, Annotation original, ElementKind elementKind) {
            return Set.of(SINGLETON);
        }
    }
}
