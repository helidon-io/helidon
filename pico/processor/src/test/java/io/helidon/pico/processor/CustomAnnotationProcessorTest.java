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

import io.helidon.builder.processor.spi.DefaultTypeInfo;
import io.helidon.builder.processor.spi.TypeInfo;
import io.helidon.pico.DefaultServiceInfo;
import io.helidon.pico.ElementInfo;
import io.helidon.pico.ServiceInfoBasics;
import io.helidon.pico.processor.spi.ExtensibleGetTemplateProducer;
import io.helidon.pico.processor.testsubjects.BasicEndpoint;
import io.helidon.pico.processor.testsubjects.ExtensibleGET;
import io.helidon.pico.tools.CustomAnnotationTemplateCreator;
import io.helidon.pico.tools.CustomAnnotationTemplateRequest;
import io.helidon.pico.tools.CustomAnnotationTemplateResponse;
import io.helidon.pico.tools.DefaultCustomAnnotationTemplateRequest;
import io.helidon.pico.types.AnnotationAndValue;
import io.helidon.pico.types.DefaultTypedElementName;
import io.helidon.pico.types.TypedElementName;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static io.helidon.pico.tools.TypeTools.createAnnotationAndValueListFromAnnotations;
import static io.helidon.pico.types.DefaultTypeName.create;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

class CustomAnnotationProcessorTest {

    @Test
    @SuppressWarnings("unchecked")
    void annotationSupported() {
        CustomAnnotationProcessor processor = new CustomAnnotationProcessor();
        assertThat(processor.annoTypes(),
                   containsInAnyOrder(ExtensibleGET.class));
    }

    @Disabled // TODO: review why this doesn't work in intellij
    @Test
    void extensibleGET() {
        CustomAnnotationProcessor processor = new CustomAnnotationProcessor();

        List<AnnotationAndValue> annotations = createAnnotationAndValueListFromAnnotations(BasicEndpoint.class.getAnnotations());
        TypeInfo enclosingTypeInfo = DefaultTypeInfo.builder()
                .typeName(create(BasicEndpoint.class))
                .annotations(annotations)
                .build();
        TypedElementName target = DefaultTypedElementName.builder()
                .typeName(create(String.class))
                .elementKind(ElementKind.METHOD.name())
                .elementName("itWorks")
                .build();
        TypedElementName arg1 = DefaultTypedElementName.builder()
                .typeName(create(String.class))
                .elementName("header")
                .build();
        ServiceInfoBasics serviceInfo = DefaultServiceInfo.builder();
        DefaultTemplateHelperTools tools = new DefaultTemplateHelperTools(ExtensibleGetTemplateProducer.class);
        CustomAnnotationTemplateRequest req = DefaultCustomAnnotationTemplateRequest.builder()
                .annoTypeName(create(ExtensibleGET.class))
                .serviceInfo(serviceInfo)
                .targetElement(target)
                .targetElementArgs(List.of(arg1))
                .targetElementAccess(ElementInfo.Access.PUBLIC)
                .enclosingTypeInfo(enclosingTypeInfo)
                .templateHelperTools(tools)
                .build();
        assertThat(req.isFilerEnabled(), is(true));

        Set<CustomAnnotationTemplateCreator> producers = processor.producersForType(req.annoTypeName());
        assertThat(producers.size(), equalTo(1));

        CustomAnnotationTemplateResponse res = processor.process(producers.iterator().next(), req);
        assertThat(res.request().annoTypeName().name(), equalTo(ExtensibleGET.class.getName()));
        assertThat(res.generatedSourceCode().toString(),
                   equalTo("{io.helidon.pico.processor.testsubjects.BasicEndpoint_ExtensibleGET_itWorks=package io.helidon"
                             + ".pico.processor.testsubjects;\n"
                             + "\n"
                             + "import io.helidon.common.Weight;\n"
                             + "import io.helidon.common.Weighted;\n"
                             + "\n"
                             + "import jakarta.inject.Inject;\n"
                             + "import jakarta.inject.Named;\n"
                             + "import jakarta.inject.Provider;\n"
                             + "import jakarta.inject.Singleton;\n"
                             + "\n"
                             + "@javax.annotation.processing.Generated(value = \"io.helidon"
                             + ".pico.processor.spi.ExtensibleGetTemplateProducer\", comments = \"version=1\")\n"
                             + "@Singleton\n"
                             + "@Named(\"io.helidon.pico.processor.testsubjects.ExtensibleGET\")\n"
                             + "@Weight(100.0)\n"
                             + "public class BasicEndpoint_ExtensibleGET_itWorks {\n"
                             + "    private final Provider<BasicEndpoint> target;\n"
                             + "\n"
                             + "    @Inject\n"
                             + "    BasicEndpoint_ExtensibleGET_itWorks(Provider<BasicEndpoint> target) {\n"
                             + "        this.target = target;\n"
                             + "    }\n"
                             + "\n"
                             + "    public Provider<BasicEndpoint> getBasicEndpoint() {\n"
                             + "        return target;\n"
                             + "    }\n"
                             + "\n"
                             + "}\n"
                             + "}"));
    }

}
