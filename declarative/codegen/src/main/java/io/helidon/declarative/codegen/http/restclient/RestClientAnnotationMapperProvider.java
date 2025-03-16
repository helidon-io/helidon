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

package io.helidon.declarative.codegen.http.restclient;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import io.helidon.codegen.CodegenContext;
import io.helidon.codegen.CodegenOptions;
import io.helidon.codegen.spi.AnnotationMapper;
import io.helidon.codegen.spi.AnnotationMapperProvider;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeName;

import static io.helidon.service.codegen.ServiceCodegenTypes.SERVICE_ANNOTATION_CONTRACT;

/**
 * Annotation mapper that makes each client API a contract.
 */
public class RestClientAnnotationMapperProvider implements AnnotationMapperProvider {
    /**
     * Public constructor required by {@link java.util.ServiceLoader}.
     */
    public RestClientAnnotationMapperProvider() {
    }

    @Override
    public AnnotationMapper create(CodegenOptions options) {
        return new RestClientAnnotationMapper();
    }

    @Override
    public Set<TypeName> supportedAnnotations() {
        return Set.of(RestClientTypes.REST_CLIENT_ENDPOINT);
    }

    private static class RestClientAnnotationMapper implements AnnotationMapper {
        private static final Annotation CONTRACT_ANNOTATION = Annotation.create(SERVICE_ANNOTATION_CONTRACT);

        @Override
        public boolean supportsAnnotation(Annotation annotation) {
            return annotation.typeName().equals(RestClientTypes.REST_CLIENT_ENDPOINT);
        }

        @Override
        public Collection<Annotation> mapAnnotation(CodegenContext ctx, Annotation original, ElementKind elementKind) {
            return List.of(original, CONTRACT_ANNOTATION);
        }
    }
}
