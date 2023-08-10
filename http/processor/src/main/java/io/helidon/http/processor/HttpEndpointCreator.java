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

package io.helidon.http.processor;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeValues;
import io.helidon.inject.tools.CustomAnnotationTemplateRequest;
import io.helidon.inject.tools.CustomAnnotationTemplateResponse;
import io.helidon.inject.tools.GenericTemplateCreatorRequest;
import io.helidon.inject.tools.spi.CustomAnnotationTemplateCreator;

/**
 * Annotation processor that generates a service for each class annotated with {@value #PATH_ANNOTATION} annotation.
 * Service provider implementation of a {@link CustomAnnotationTemplateCreator}.
 */
public class HttpEndpointCreator extends HttpCreatorBase implements CustomAnnotationTemplateCreator {
    private static final System.Logger LOGGER = System.getLogger(HttpEndpointCreator.class.getName());
    private static final String PATH_ANNOTATION = "io.helidon.http.Endpoint.Path";
    private static final String LISTENER_ANNOTATION = "io.helidon.http.Endpoint.Listener";

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
        if (!enclosingType.typeKind().equals(TypeValues.KIND_CLASS)) {
            // we are only interested in classes, not in methods
            return Optional.empty();
        }

        String classname = className(enclosingType.typeName(), "GeneratedService");
        TypeName generatedTypeName = TypeName.builder()
                .packageName(enclosingType.typeName().packageName())
                .className(classname)
                .build();

        String template = Templates.loadTemplate("helidon", "http-endpoint.java.hbs");
        GenericTemplateCreatorRequest genericCreatorRequest = GenericTemplateCreatorRequest.builder()
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
        Map<String, Object> httpMap = new HashMap<>();
        for (var annot : annots) {
            String annotationName = annot.typeName().resolvedName();
            switch (annotationName) {
            case PATH_ANNOTATION -> httpMap.put("path", annot.value().orElse("/"));
            case LISTENER_ANNOTATION -> {
                httpMap.put("hasListener", true);
                httpMap.put("listenerValue", annot.value().orElse("@default"));
                httpMap.put("listenerRequired", annot.getValue("required").map(Boolean::parseBoolean).orElse(false));
            }
            default -> {
                if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
                    LOGGER.log(System.Logger.Level.TRACE, "Unsupported annotation by this processor: " + annotationName);
                }
            }
            }
        }

        response.put("http", httpMap);

        return response;
    }
}
