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

package io.helidon.pico.processor.spi;

import java.util.Map;

import io.helidon.pico.processor.spi.impl.DefaultTemplateProducerResponse;
import io.helidon.pico.types.TypeName;
import io.helidon.pico.types.TypedElementName;

/**
 * The response from {@link io.helidon.pico.processor.spi.CustomAnnotationTemplateProducer#produce(CustomAnnotationTemplateProducerRequest, TemplateHelperTools)}.
 */
public interface CustomAnnotationTemplateProducerResponse {

    /**
     * The annotation type that triggered the producer to be called.
     *
     * @return one of the annotation types supported by the associated producer
     */
    TypeName getAnnoType();

    /**
     * Any source that should be code generated.
     *
     * @return map of generated type name to body of the source to be generated
     */
    Map<TypeName, String> getGeneratedJavaCode();

    /**
     * Any generated resources that might be built.
     *
     * @return map of generated type name (which is really just a directory path under resources) to the resource to be generated
     */
    Map<TypedElementName, String> getGeneratedResources();

    /**
     * Creates a builder for {@link io.helidon.pico.processor.spi.CustomAnnotationTemplateProducerResponse}.
     *
     * @param request the request that triggered this response
     * @return the builder for the response
     */
    static Builder builder(CustomAnnotationTemplateProducerRequest request) {
        return DefaultTemplateProducerResponse.builder(request.getAnnoType());
    }

    /**
     * Builder.
     */
    interface Builder {

        /**
         * Any source code that should be generated.
         *
         * @param generatedTypeName the type name for the generated source
         * @param body              the body of the resource
         * @return the fluent builder
         */
        Builder generateJavaCode(TypeName generatedTypeName, String body);

        /**
         * Any resources that should be generated.
         *
         * @param generatedTypeName the package/type/location for the resource
         * @param body the body of the resource
         * @return the fluent builder
         */
        Builder generateResource(TypedElementName generatedTypeName, String body);

        /**
         * @return the built response.
         */
        CustomAnnotationTemplateProducerResponse build();

    }

}
