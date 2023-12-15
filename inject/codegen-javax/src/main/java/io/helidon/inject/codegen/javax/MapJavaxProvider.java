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
import io.helidon.inject.codegen.InjectCodegenTypes;

/**
 * A {@link java.util.ServiceLoader} provider implementation of {@link io.helidon.codegen.spi.AnnotationMapperProvider}.
 * This providers adds mapping from known annotations in {@code javax} packages to Helidon Inject specific annotations.
 */
public class MapJavaxProvider implements AnnotationMapperProvider {
    private static final Set<TypeName> TYPES = Set.of(JavaxTypes.INJECT_SINGLETON,
                                                      JavaxTypes.INJECT_QUALIFIER,
                                                      JavaxTypes.INJECT_INJECT,
                                                      JavaxTypes.INJECT_SCOPE,
                                                      JavaxTypes.INJECT_NAMED,
                                                      JavaxTypes.INJECT_POST_CONSTRUCT,
                                                      JavaxTypes.INJECT_PRE_DESTROY);

    private static final Map<TypeName, Annotation> DIRECTLY_MAPPED = Map.of(
            JavaxTypes.INJECT_SINGLETON, Annotation.create(InjectCodegenTypes.INJECTION_SINGLETON),
            JavaxTypes.INJECT_QUALIFIER, Annotation.create(InjectCodegenTypes.INJECTION_QUALIFIER),
            JavaxTypes.INJECT_INJECT, Annotation.create(InjectCodegenTypes.INJECTION_INJECT),
            JavaxTypes.INJECT_POST_CONSTRUCT, Annotation.create(InjectCodegenTypes.INJECTION_POST_CONSTRUCT),
            JavaxTypes.INJECT_PRE_DESTROY, Annotation.create(InjectCodegenTypes.INJECTION_PRE_DESTROY)
    );

    /**
     * Required default constructor.
     *
     * @deprecated required by {@link java.util.ServiceLoader}
     */
    @Deprecated
    public MapJavaxProvider() {
    }

    @Override
    public Set<TypeName> supportedAnnotations() {
        return TYPES;
    }

    @Override
    public AnnotationMapper create(CodegenOptions options) {
        return new JavaxAnnotationMapper();
    }

    private static class JavaxAnnotationMapper implements AnnotationMapper {
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
            if (JavaxTypes.INJECT_SCOPE.equals(typeName)) {
                return Set.of();
            }
            // named is mapped to our named
            if (JavaxTypes.INJECT_NAMED.equals(typeName)) {
                return Set.of(Annotation.create(InjectCodegenTypes.INJECTION_NAMED, original.value().orElse("")));
            }

            return Set.of(original);
        }
    }
}
