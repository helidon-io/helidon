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

package io.helidon.pico.processor.spi;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import io.helidon.builder.processor.spi.TypeInfo;
import io.helidon.common.types.DefaultTypeName;
import io.helidon.common.types.TypeName;
import io.helidon.pico.tools.CustomAnnotationTemplateCreator;
import io.helidon.pico.tools.CustomAnnotationTemplateRequest;
import io.helidon.pico.tools.CustomAnnotationTemplateResponse;
import io.helidon.pico.tools.TemplateHelperTools;

/**
 * For Testing (service loaded).
 *
 * @deprecated
 */
// Note: if we uncomment this @Singleton, a compile-time activator will be built - we want to avoid that here
//@Singleton
public class ExtensibleGetTemplateProducer implements CustomAnnotationTemplateCreator {

    /**
     * For testing.
     */
    public ExtensibleGetTemplateProducer() {
        int debugMe = 1;
    }

    @Override
    public Set<String> annoTypes() {
        return Set.of("io.helidon.pico.processor.testsubjects.ExtensibleGET");
    }

    @Override
    public Optional<CustomAnnotationTemplateResponse> create(
            CustomAnnotationTemplateRequest request) {
        TypeInfo enclosingTypeInfo = request.enclosingTypeInfo();
        String classname = enclosingTypeInfo.typeName().className() + "_"
                + request.annoTypeName().className() + "_"
                + request.targetElement().elementName();
        TypeName generatedType = DefaultTypeName.create(enclosingTypeInfo.typeName().packageName(), classname);
        TemplateHelperTools tools = Objects.requireNonNull(request.templateHelperTools());
        Supplier<CharSequence> resourceSupplier = tools.supplyFromResources("nima", "extensible-get.hbs");
        return tools.produceStandardCodeGenResponse(request, generatedType, resourceSupplier, Function.identity());
    }

}
