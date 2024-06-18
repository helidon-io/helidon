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

package io.helidon.service.codegen.jakarta;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import io.helidon.codegen.CodegenContext;
import io.helidon.codegen.CodegenOptions;
import io.helidon.codegen.spi.AnnotationMapper;
import io.helidon.codegen.spi.AnnotationMapperProvider;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeName;
import io.helidon.service.codegen.ServiceCodegenTypes;

/**
 * A {@link java.util.ServiceLoader} provider implementation of {@link io.helidon.codegen.spi.AnnotationMapperProvider}.
 * This providers adds mapping from known annotations in {@code jakarta} packages to Helidon Inject specific annotations.
 */
public class MapJakartaProvider implements AnnotationMapperProvider {
    private static final Set<TypeName> TYPES = Set.of(JakartaTypes.INJECT_SINGLETON,
                                                      JakartaTypes.INJECT_QUALIFIER,
                                                      JakartaTypes.INJECT_INJECT,
                                                      JakartaTypes.INJECT_SCOPE,
                                                      JakartaTypes.INJECT_NAMED,
                                                      JakartaTypes.INJECT_POST_CONSTRUCT,
                                                      JakartaTypes.INJECT_PRE_DESTROY);
    private static final Map<TypeName, Annotation> DIRECTLY_MAPPED = Map.of(
            JakartaTypes.INJECT_SINGLETON, Annotation.create(ServiceCodegenTypes.INJECTION_SINGLETON),
            JakartaTypes.INJECT_QUALIFIER, Annotation.create(ServiceCodegenTypes.INJECTION_QUALIFIER),
            JakartaTypes.INJECT_INJECT, Annotation.create(ServiceCodegenTypes.INJECTION_INJECT),
            JakartaTypes.INJECT_POST_CONSTRUCT, Annotation.create(ServiceCodegenTypes.INJECTION_POST_CONSTRUCT),
            JakartaTypes.INJECT_PRE_DESTROY, Annotation.create(ServiceCodegenTypes.INJECTION_PRE_DESTROY)
    );

    /**
     * Required default constructor.
     *
     * @deprecated required by {@link java.util.ServiceLoader}
     */
    @Deprecated
    public MapJakartaProvider() {
    }

    @Override
    public Set<TypeName> supportedAnnotations() {
        return TYPES;
    }

    @Override
    public AnnotationMapper create(CodegenOptions options) {
        return new JakartaAnnotationMapper();
    }

    private static class JakartaAnnotationMapper implements AnnotationMapper {
        @Override
        public boolean supportsAnnotation(Annotation annotation) {
            return TYPES.contains(annotation.typeName());
        }

        @Override
        public Collection<Annotation> mapAnnotation(CodegenContext ctx, Annotation original, ElementKind elementKind) {
            TypeName typeName = original.typeName();
            Annotation annotation = DIRECTLY_MAPPED.get(typeName);
            if (annotation != null) {
                return Set.of(annotation);
            }
            // scope is mapped to nothing
            if (JakartaTypes.INJECT_SCOPE.equals(typeName)) {
                return Set.of();
            }
            // named is mapped to our named
            if (JakartaTypes.INJECT_NAMED.equals(typeName)) {
                return Set.of(Annotation.create(ServiceCodegenTypes.INJECTION_NAMED, original.value().orElse("")));
            }

            return Set.of(original);
        }
    }
}
