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

import java.lang.annotation.Annotation;
import java.util.Set;

/**
 * Instances of this are found via the service loader during compilation time and called by the
 * {@link io.helidon.pico.processor.CustomAnnotationProcessor}. It should be noted, however, that this contract may
 * in fact be called multiple times, as the processing naturally happens across multiple iterations.
 */
public interface CustomAnnotationTemplateProducer {

    /**
     * These are the set of annotation types that will trigger a call this producer.
     *
     * @return the supported annotation types for this producer
     */
    Set<Class<? extends Annotation>> getAnnoTypes();

    /**
     * The implementor should return null if the request should not be handled.
     *
     * @param request the request
     * @param tools tools that might be helpful in producing the response
     *
     * @return the response that will describe what template to produce, or null to skip
     */
    CustomAnnotationTemplateProducerResponse produce(CustomAnnotationTemplateProducerRequest request,
                                                     TemplateHelperTools tools);

}
