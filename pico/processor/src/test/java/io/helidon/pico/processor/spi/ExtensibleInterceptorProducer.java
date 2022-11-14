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

import io.helidon.pico.processor.spi.impl.DefaultTemplateProducerResponse;
import io.helidon.pico.processor.testsubjects.ExtendedHello;
import io.helidon.pico.types.DefaultTypeName;
import io.helidon.pico.types.TypeName;
import io.helidon.pico.testsubjects.hello.World;

import jakarta.inject.Singleton;

public class ExtensibleInterceptorProducer implements CustomAnnotationTemplateProducer {

    static final Class<? extends Annotation> annoType = Singleton.class;
    static final Class<World> interceptedClass1 = World.class;
    static final Class<ExtendedHello> interceptedClass2 = ExtendedHello.class;

    @Override
    public Set<Class<? extends Annotation>> getAnnoTypes() {
        return Collections.singleton(annoType);
    }

    @Override
    public CustomAnnotationTemplateProducerResponse produce(CustomAnnotationTemplateProducerRequest request,
                                                            TemplateHelperTools tools) {
        TypeName generatedType = DefaultTypeName
                .create(request.getEnclosingClassType().packageName(),
                        interceptedClass1.getSimpleName() + "Interceptor");
        CustomAnnotationTemplateProducerResponse res1 = tools
                .produceNamedBasicInterceptorDelegationCodeGenResponse(request, generatedType,
                                                                    interceptedClass1, null, null);

        generatedType = DefaultTypeName
                .create(request.getEnclosingClassType().packageName(),
                        interceptedClass2.getSimpleName() + "Interceptor");
        CustomAnnotationTemplateProducerResponse res2 = tools
                .produceNamedBasicInterceptorDelegationCodeGenResponse(request, generatedType,
                                                                    interceptedClass2, null, null);

        return DefaultTemplateProducerResponse.aggregate(request.getAnnoType(), res1, res2);
    }

}
