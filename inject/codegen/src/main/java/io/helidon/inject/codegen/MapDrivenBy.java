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

package io.helidon.inject.codegen;

import java.util.Collection;
import java.util.Set;

import io.helidon.codegen.CodegenContext;
import io.helidon.codegen.CodegenOptions;
import io.helidon.codegen.spi.AnnotationMapper;
import io.helidon.codegen.spi.AnnotationMapperProvider;
import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeName;

import static io.helidon.inject.codegen.InjectCodegenTypes.INJECTION_DRIVEN_BY;

/**
 * A {@link java.util.ServiceLoader} provider implementation for {@link io.helidon.codegen.spi.AnnotationMapperProvider}
 * that adds service annotation to driven services (as we want to create the instance just once), unless a scope
 * is already defined.
 */
@Weight(Weighted.DEFAULT_WEIGHT - 10)
public class MapDrivenBy implements AnnotationMapperProvider {
    private static final Annotation SERVICE = Annotation.create(InjectCodegenTypes.INJECTION_SERVICE);

    /**
     * Required default constructor.
     *
     * @deprecated required by {@link java.util.ServiceLoader}
     */
    @Deprecated
    public MapDrivenBy() {
    }

    @Override
    public Set<TypeName> supportedAnnotations() {
        return Set.of(INJECTION_DRIVEN_BY);
    }

    @Override
    public AnnotationMapper create(CodegenOptions options) {
        return new ConfigDrivenMapper();
    }

    private static class ConfigDrivenMapper implements AnnotationMapper {
        @Override
        public boolean supportsAnnotation(Annotation annotation) {
            return annotation.typeName().equals(INJECTION_DRIVEN_BY);
        }

        @Override
        public Collection<Annotation> mapAnnotation(CodegenContext ctx, Annotation original, ElementKind elementKind) {
            return Set.of(original, SERVICE);
        }
    }
}
