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
import java.util.Collections;
import java.util.Set;

import io.helidon.pico.processor.testsubjects.ExtensibleGET;
import io.helidon.pico.types.DefaultTypeName;
import io.helidon.pico.types.TypeName;

// Note: if we uncomment this @Singleton, a compile-time activator will be built!
//@Singleton
public class ExtensibleGetTemplateProducer implements CustomAnnotationTemplateProducer {

    @Override
    public Set<Class<? extends Annotation>> getAnnoTypes() {
        return Collections.singleton(ExtensibleGET.class);
    }

    @Override
    public CustomAnnotationTemplateProducerResponse produce(CustomAnnotationTemplateProducerRequest request,
                                                            TemplateHelperTools tools) {
        String classname = request.getEnclosingClassType().className() + "_"
                + request.getAnnoType().className() + "_"
                + request.getElementName();
        TypeName generatedType = DefaultTypeName.create(request.getEnclosingClassType().packageName(), classname);
        return tools.produceStandardCodeGenResponse(request, generatedType,
                                            tools.supplyFromResources("nima", "extensible-get.hbs"),
                                            null, null);
    }

}
