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

package io.helidon.pico.processor.spi.impl;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import io.helidon.pico.processor.spi.CustomAnnotationTemplateProducerResponse;
import io.helidon.pico.types.TypeName;
import io.helidon.pico.types.TypedElementName;
import io.helidon.pico.tools.ToolsException;

/**
 * Default implementation for {@link io.helidon.pico.processor.spi.CustomAnnotationTemplateProducerResponse}.
 */
public class DefaultTemplateProducerResponse implements CustomAnnotationTemplateProducerResponse {

    private final TypeName annoType;
    private final Map<TypeName, String> generatedJavaCode;
    private final Map<TypedElementName, String> generatedResources;

    private DefaultTemplateProducerResponse(TypeName annoType,
                                            Map<TypeName, String> generatedJavaCode,
                                            Map<TypedElementName, String> generatedResources) {
        this.annoType = annoType;
        this.generatedJavaCode = generatedJavaCode;
        this.generatedResources = generatedResources;
    }

    /**
     * Aggregates the responses given to one response.
     *
     * @param annoType the annotation type being processed
     * @param responses the responses to aggregate into one response instance
     * @return the aggregated response
     */
    public static CustomAnnotationTemplateProducerResponse aggregate(TypeName annoType,
                                                                     CustomAnnotationTemplateProducerResponse... responses) {
        DefaultTemplateProducerResponse.Builder response = DefaultTemplateProducerResponse.builder(annoType);
        for (CustomAnnotationTemplateProducerResponse r : responses) {
            response.aggregate(r);
        }
        return response.build();
    }

    @Override
    public TypeName getAnnoType() {
        return annoType;
    }

    @Override
    public Map<TypeName, String> getGeneratedJavaCode() {
        return generatedJavaCode;
    }

    @Override
    public Map<TypedElementName, String> getGeneratedResources() {
        return generatedResources;
    }

    /**
     * Creates builder for the response.
     *
     * @param annoType the annotation type from the request
     * @return the builder instance
     */
    public static Builder builder(TypeName annoType) {
        return new DefaultTemplateProducerResponse.Builder(Objects.requireNonNull(annoType));
    }

    /**
     * Builder.
     */
    public static class Builder implements CustomAnnotationTemplateProducerResponse.Builder {
        private final TypeName annoType;
        private final Map<TypeName, String> generatedJavaCode = new ConcurrentHashMap<>();
        private final Map<TypedElementName, String> generatedResources = new ConcurrentHashMap<>();

        private Builder(TypeName annoType) {
            this.annoType = annoType;
        }

        /**
         * Request to generate source code.
         *
         * @param generatedTypeName the type name for the generated source
         * @param body              the body of the resource
         * @return the fluent builder
         */
        public Builder generateJavaCode(TypeName generatedTypeName, String body) {
            String prev = generatedJavaCode.put(generatedTypeName, Objects.requireNonNull(body));
            if (Objects.nonNull(prev)) {
                throw new ToolsException("already generating code for this type: " + generatedTypeName);
            }
            return this;
        }

        /**
         * Request to generate resource.
         *
         * @param generatedTypeName the package/type/location for the resource
         * @param body the body of the resource
         * @return the fluent builder
         */
        public Builder generateResource(TypedElementName generatedTypeName, String body) {
            String prev = generatedResources.put(generatedTypeName, Objects.requireNonNull(body));
            if (Objects.nonNull(prev)) {
                throw new ToolsException("already generating resource for this type: " + generatedTypeName);
            }
            return this;
        }

        /**
         * Aggregates the given response into this builder instance.
         *
         * @param res the response
         * @return the fluent builder
         */
        public Builder aggregate(CustomAnnotationTemplateProducerResponse res) {
            if (Objects.nonNull(res)) {
                Map<TypeName, String> resGeneratedJavaCode = res.getGeneratedJavaCode();
                if (Objects.nonNull(resGeneratedJavaCode)) {
                    generatedJavaCode.putAll(res.getGeneratedJavaCode());
                }

                Map<TypedElementName, String> resGeneratedResources = res.getGeneratedResources();
                if (Objects.nonNull(resGeneratedResources)) {
                    generatedResources.putAll(res.getGeneratedResources());
                }
            }

            return this;
        }

        /**
         * @return the built instance
         */
        public DefaultTemplateProducerResponse build() {
            return new DefaultTemplateProducerResponse(annoType, generatedJavaCode, generatedResources);
        }
    }

}
