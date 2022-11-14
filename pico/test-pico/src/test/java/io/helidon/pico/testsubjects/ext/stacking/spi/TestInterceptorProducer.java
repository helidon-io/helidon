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

package io.helidon.pico.testsubjects.ext.stacking.spi;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.Set;

import io.helidon.pico.processor.spi.CustomAnnotationTemplateProducer;
import io.helidon.pico.processor.spi.CustomAnnotationTemplateProducerRequest;
import io.helidon.pico.processor.spi.CustomAnnotationTemplateProducerResponse;
import io.helidon.pico.processor.spi.TemplateHelperTools;
import io.helidon.pico.types.DefaultTypeName;
import io.helidon.pico.types.TypeName;
import io.helidon.pico.testsubjects.ext.stacking.Intercepted;

import jakarta.inject.Singleton;

@SuppressWarnings("unused")
public class TestInterceptorProducer implements CustomAnnotationTemplateProducer {

    static final Class<? extends Annotation> annoType = Singleton.class;
    static final Class<Intercepted> interceptedClass = Intercepted.class;

    @Override
    public Set<Class<? extends Annotation>> getAnnoTypes() {
        return Collections.singleton(annoType);
    }

    @Override
    public CustomAnnotationTemplateProducerResponse produce(CustomAnnotationTemplateProducerRequest request,
                                                            TemplateHelperTools tools) {
        TypeName generatedType = DefaultTypeName.create(request.getEnclosingClassType().packageName(),
                                    interceptedClass.getSimpleName() + "Interceptor");
        CustomAnnotationTemplateProducerResponse res = tools
                .produceNamedBasicInterceptorDelegationCodeGenResponse(request, generatedType,
                                                                   interceptedClass, null, null);
        return res;
    }

}
