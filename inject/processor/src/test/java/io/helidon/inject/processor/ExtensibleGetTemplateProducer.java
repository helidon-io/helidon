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

package io.helidon.inject.processor;

import java.util.Optional;
import java.util.Set;

import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.inject.tools.CustomAnnotationTemplateRequest;
import io.helidon.inject.tools.CustomAnnotationTemplateResponse;
import io.helidon.inject.tools.GenericTemplateCreatorRequest;
import io.helidon.inject.tools.spi.CustomAnnotationTemplateCreator;

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
        assert (true); // for setting breakpoints in debug
    }

    @Override
    public Set<String> annoTypes() {
        return Set.of("io.helidon.inject.processor.testsubjects.ExtensibleGET");
    }

    @Override
    public Optional<CustomAnnotationTemplateResponse> create(CustomAnnotationTemplateRequest request) {
        TypeInfo enclosingTypeInfo = request.enclosingTypeInfo();
        String classname = enclosingTypeInfo.typeName().className() + "_"
                + request.annoTypeName().className() + "_"
                + request.targetElement().elementName();
        TypeName generatedTypeName = TypeName.builder(enclosingTypeInfo.typeName())
                .className(classname)
                .build();
        GenericTemplateCreatorDefault genericTemplateCreator = new GenericTemplateCreatorDefault(getClass());
        CharSequence template = genericTemplateCreator.supplyFromResources("helidon", "extensible-get.hbs");
        GenericTemplateCreatorRequest genericCreatorRequest = GenericTemplateCreatorRequest.builder()
                .customAnnotationTemplateRequest(request)
                .generatedTypeName(generatedTypeName)
                .template(template)
                .build();
        return genericTemplateCreator.create(genericCreatorRequest);
    }

}
