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

package io.helidon.nima.http.processor;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.helidon.common.types.DefaultTypeName;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.pico.tools.CustomAnnotationTemplateRequest;
import io.helidon.pico.tools.CustomAnnotationTemplateResponse;
import io.helidon.pico.tools.DefaultGenericTemplateCreatorRequest;
import io.helidon.pico.tools.GenericTemplateCreatorRequest;
import io.helidon.pico.tools.spi.CustomAnnotationTemplateCreator;

/**
 * Annotation processor that generates a service for each class annotated with {@value #PATH_ANNOTATION} annotation.
 * Service provider implementation of a {@link io.helidon.pico.tools.spi.CustomAnnotationTemplateCreator}.
 */
public class HttpEndpointCreator implements CustomAnnotationTemplateCreator {
    private static final String PATH_ANNOTATION = "io.helidon.common.http.Path";

    /**
     * Default constructor used by the {@link java.util.ServiceLoader}.
     */
    public HttpEndpointCreator() {
    }

    @Override
    public Set<String> annoTypes() {
        return Set.of(PATH_ANNOTATION);
    }

    @Override
    public Optional<CustomAnnotationTemplateResponse> create(CustomAnnotationTemplateRequest request) {
        TypeInfo enclosingType = request.enclosingTypeInfo();
        if (!enclosingType.typeKind().equals(TypeInfo.KIND_CLASS)) {
            // we are only interested in classes, not in methods
            return Optional.empty();
        }

        String classname = enclosingType.typeName().className() + "_GeneratedService";
        TypeName generatedTypeName = DefaultTypeName.create(enclosingType.typeName().packageName(), classname);

        String template = Templates.loadTemplate("nima", "http-endpoint.java.hbs");
        GenericTemplateCreatorRequest genericCreatorRequest = DefaultGenericTemplateCreatorRequest.builder()
                .customAnnotationTemplateRequest(request)
                .template(template)
                .generatedTypeName(generatedTypeName)
                .overrideProperties(addProperties(request))
                .build();
        return request.genericTemplateCreator().create(genericCreatorRequest);
    }

    private Map<String, Object> addProperties(CustomAnnotationTemplateRequest request) {
        Map<String, Object> response = new HashMap<>();

        var annots = request.enclosingTypeInfo().annotations();
        for (var annot : annots) {
            if (annot.typeName().name().equals(PATH_ANNOTATION)) {
                response.put("http", Map.of("path", annot.value().orElse("/")));
                break;
            }
        }

        return response;
    }
}
