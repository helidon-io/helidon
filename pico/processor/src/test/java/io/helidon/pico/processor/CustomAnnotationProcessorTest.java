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

package io.helidon.pico.processor;

import java.util.List;
import java.util.Set;

import javax.lang.model.element.ElementKind;

import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeValues;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.pico.api.AccessModifier;
import io.helidon.pico.api.ServiceInfo;
import io.helidon.pico.api.ServiceInfoBasics;
import io.helidon.pico.processor.testsubjects.BasicEndpoint;
import io.helidon.pico.processor.testsubjects.ExtensibleGET;
import io.helidon.pico.tools.CustomAnnotationTemplateRequest;
import io.helidon.pico.tools.CustomAnnotationTemplateResponse;
import io.helidon.pico.tools.spi.CustomAnnotationTemplateCreator;

import org.junit.jupiter.api.Test;

import static io.helidon.common.types.TypeName.create;
import static io.helidon.pico.processor.TestUtils.loadStringFromResource;
import static io.helidon.pico.tools.TypeTools.createAnnotationListFromAnnotations;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;

class CustomAnnotationProcessorTest {

    @Test
    @SuppressWarnings("unchecked")
    void annotationSupported() {
        CustomAnnotationProcessor processor = new CustomAnnotationProcessor();
        assertThat(processor.getSupportedAnnotationTypes(),
                   containsInAnyOrder(ExtensibleGET.class.getName()));
    }

    @Test
    void extensibleGET() {
        CustomAnnotationProcessor processor = new CustomAnnotationProcessor();

        List<Annotation> annotations = createAnnotationListFromAnnotations(BasicEndpoint.class.getAnnotations());
        TypeInfo enclosingTypeInfo = TypeInfo.builder()
                .typeKind(TypeValues.KIND_CLASS)
                .typeName(create(BasicEndpoint.class))
                .annotations(annotations)
                .build();
        TypedElementInfo target = TypedElementInfo.builder()
                .typeName(create(String.class))
                .elementTypeKind(ElementKind.METHOD.name())
                .elementName("itWorks")
                .build();
        TypedElementInfo arg1 = TypedElementInfo.builder()
                .typeName(create(String.class))
                .elementName("header")
                .elementTypeKind(TypeValues.KIND_PARAMETER)
                .build();
        ServiceInfoBasics serviceInfo = ServiceInfo.builder()
                .serviceTypeName(BasicEndpoint.class)
                .build();
        GenericTemplateCreatorDefault genericTemplateCreator =
                new GenericTemplateCreatorDefault(ExtensibleGetTemplateProducer.class);
        CustomAnnotationTemplateRequest req = CustomAnnotationTemplateRequest.builder()
                .annoTypeName(create(ExtensibleGET.class))
                .serviceInfo(serviceInfo)
                .targetElement(target)
                .targetElementArgs(List.of(arg1))
                .targetElementAccess(AccessModifier.PUBLIC)
                .enclosingTypeInfo(enclosingTypeInfo)
                .genericTemplateCreator(genericTemplateCreator)
                .build();
        assertThat(req.isFilerEnabled(), is(true));

        Set<CustomAnnotationTemplateCreator> producers = processor.producersForType(req.annoTypeName());
        assertThat(producers.size(), equalTo(1));

        CustomAnnotationTemplateResponse res = processor.process(producers.iterator().next(), req);
        assertThat(res.request().annoTypeName().name(), equalTo(ExtensibleGET.class.getName()));
        TypeName generatedTypeName =
                TypeName.create("io.helidon.pico.processor.testsubjects.BasicEndpoint_ExtensibleGET_itWorks");
        assertThat(res.generatedSourceCode(),
                   hasKey(generatedTypeName));
        assertThat(res.toString(), res.generatedSourceCode().size(),
                   is(1));
        String generatedSource = res.generatedSourceCode().get(generatedTypeName);
        assertThat(generatedSource,
                   equalTo(loadStringFromResource("expected/BasicEndpoint_ExtensibleGET._java_")));
    }

}
