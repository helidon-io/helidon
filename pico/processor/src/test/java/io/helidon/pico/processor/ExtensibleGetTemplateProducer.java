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

import java.util.Optional;
import java.util.Set;

import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNameDefault;
import io.helidon.pico.tools.CustomAnnotationTemplateRequest;
import io.helidon.pico.tools.CustomAnnotationTemplateResponse;
import io.helidon.pico.tools.GenericTemplateCreatorRequest;
import io.helidon.pico.tools.GenericTemplateCreatorRequestDefault;
import io.helidon.pico.tools.spi.CustomAnnotationTemplateCreator;

/**
 * For Testing (service loaded).
 */
// Note: if we uncomment this @Singleton, a compile-time activator will be built - we want to avoid that here
//@Singleton
public class ExtensibleGetTemplateProducer implements CustomAnnotationTemplateCreator {

    /**
     * Service loader based constructor.
     *
     * @deprecated this is a Java ServiceLoader implementation and the constructor should not be used directly
     */
    @Deprecated
    public ExtensibleGetTemplateProducer() {
        assert(true); // for setting breakpoints in debug
    }

    @Override
    public Set<String> annoTypes() {
        return Set.of("io.helidon.pico.processor.testsubjects.ExtensibleGET");
    }

    @Override
    public Optional<CustomAnnotationTemplateResponse> create(CustomAnnotationTemplateRequest request) {
        TypeInfo enclosingTypeInfo = request.enclosingTypeInfo();
        String classname = enclosingTypeInfo.typeName().className() + "_"
                + request.annoTypeName().className() + "_"
                + request.targetElement().elementName();
        TypeName generatedTypeName = TypeNameDefault.create(enclosingTypeInfo.typeName().packageName(), classname);
        DefaultGenericTemplateCreator genericTemplateCreator = new DefaultGenericTemplateCreator(getClass());
        CharSequence template = genericTemplateCreator.supplyFromResources("nima", "extensible-get.hbs");
        GenericTemplateCreatorRequest genericCreatorRequest = GenericTemplateCreatorRequestDefault.builder()
                .customAnnotationTemplateRequest(request)
                .generatedTypeName(generatedTypeName)
                .template(template)
                .build();
        return genericTemplateCreator.create(genericCreatorRequest);
    }

}
