/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.lang.model.element.ElementKind;

import io.helidon.nima.http.api.Path;
import io.helidon.pico.processor.spi.CustomAnnotationTemplateProducer;
import io.helidon.pico.processor.spi.CustomAnnotationTemplateProducerRequest;
import io.helidon.pico.processor.spi.CustomAnnotationTemplateProducerResponse;
import io.helidon.pico.processor.spi.TemplateHelperTools;
import io.helidon.pico.spi.QualifierAndValue;
import io.helidon.pico.tools.types.TypeName;

/**
 * Annotation processor that generates a service for each class annotated with {@link io.helidon.nima.http.api.Path} annotation.
 * Service provider implementation of a {@link io.helidon.pico.processor.spi.CustomAnnotationTemplateProducer}.
 */
public class HttpEndpointProducer implements CustomAnnotationTemplateProducer {
    /**
     * Default constructor used by the {@link java.util.ServiceLoader}.
     */
    public HttpEndpointProducer() {
    }

    @Override
    public Set<Class<? extends Annotation>> getAnnoTypes() {
        return Set.of(Path.class);
    }

    @Override
    public CustomAnnotationTemplateProducerResponse produce(CustomAnnotationTemplateProducerRequest request,
                                                            TemplateHelperTools tools) {
        // we are only interested in classes, not in methods
        if (request.getElementKind() != ElementKind.CLASS) {
            return null;
        }
        String classname = request.getEnclosingClassType().getClassName() + "_GeneratedService";
        TypeName generatedType = TypeName.create(request.getEnclosingClassType().getPackageName(), classname);
        return tools.produceStandardCodeGenResponse(request,
                                                    generatedType,
                                                    tools.supplyUsingLiteralTemplate(
                                                            loadTemplate("nima", "http-endpoint.java.hbs")),
                                                    it -> addProperties(request, tools, it), null);
    }

    String loadTemplate(String templateProfile, String name) {
        String path = "templates/pico/" + templateProfile + "/" + name;
        try {
            InputStream in = getClass().getClassLoader().getResourceAsStream(path);
            if (in == null) {
                return null;
            }
            try (in) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Map<String, Object> addProperties(CustomAnnotationTemplateProducerRequest request,
                                              TemplateHelperTools tools,
                                              Map<String, Object> currentProperties) {
        Map<String, Object> response = new HashMap<>(currentProperties);

        Set<QualifierAndValue> qualifiers = request.getBasicServiceInfo().getQualifiers();
        for (QualifierAndValue qualifier : qualifiers) {
            if (qualifier.getQualifierTypeName().equals(Path.class.getName())) {
                response.put("http", new HttpDef(qualifier.getValue()));
                break;
            }
        }

        return response;
    }

    /**
     * Needed for template processing.
     * Do not use.
     */
    @Deprecated(since = "1.0.0")
    public static class HttpDef {
        private String path;

        HttpDef(String path) {
            this.path = path;
        }

        /**
         * Needed for template processing.
         * Do not use.
         * @return do not use
         */
        public String getPath() {
            return path;
        }
    }
}
