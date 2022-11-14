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

package io.helidon.pico.test.extended.resources.producer;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.Set;

import io.helidon.pico.processor.spi.CustomAnnotationTemplateProducer;
import io.helidon.pico.processor.spi.CustomAnnotationTemplateProducerRequest;
import io.helidon.pico.processor.spi.CustomAnnotationTemplateProducerResponse;
import io.helidon.pico.processor.spi.TemplateHelperTools;
import io.helidon.pico.types.DefaultTypeName;
import io.helidon.pico.types.DefaultTypedElementName;
import io.helidon.pico.types.TypeName;
import io.helidon.pico.test.extended.resources.MyCompileTimeInheritableTestQualifier;
import io.helidon.pico.test.utils.JsonUtils;

public class MyCompileTimeProducer implements CustomAnnotationTemplateProducer {

    @Override
    public Set<Class<? extends Annotation>> getAnnoTypes() {
        return Collections.singleton(MyCompileTimeInheritableTestQualifier.class);
    }

    @Override
    public CustomAnnotationTemplateProducerResponse produce(CustomAnnotationTemplateProducerRequest request,
                                                            TemplateHelperTools tools) {
        String value = JsonUtils.prettyPrintJson(request);
        TypeName typeName = DefaultTypeName.create("produced.resource.for",
                                                               request.getAnnoType().className()
                                + "_" + request.getEnclosingClassType().className()
                                + "_" + request.getElementName());
        return CustomAnnotationTemplateProducerResponse.builder(request)
                .generateResource(DefaultTypedElementName.create(typeName, ".json"), value)
                .build();
    }

}
