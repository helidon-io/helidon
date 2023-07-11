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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import io.helidon.common.processor.TypeFactory;
import io.helidon.common.types.TypeName;
import io.helidon.inject.tools.CustomAnnotationTemplateRequest;
import io.helidon.inject.tools.CustomAnnotationTemplateResponse;
import io.helidon.inject.tools.GenericTemplateCreator;
import io.helidon.inject.tools.GenericTemplateCreatorRequest;
import io.helidon.inject.tools.Messager;
import io.helidon.inject.tools.TemplateHelper;
import io.helidon.inject.tools.ToolsException;

/**
 * Default implementation for {@link GenericTemplateCreator}.
 */
class GenericTemplateCreatorDefault implements GenericTemplateCreator {
    private final Class<?> generator;
    private final Messager messager;

    /**
     * Constructor.
     *
     * @param generator the class type for the generator
     */
    GenericTemplateCreatorDefault(Class<?> generator) {
        this(generator, new MessagerToLogAdapter(System.getLogger(GenericTemplateCreatorDefault.class.getName())));
    }

    /**
     * Constructor.
     *
     * @param generator the class type for the generator
     * @param messager  the messager and error handler
     */
    GenericTemplateCreatorDefault(Class<?> generator,
                                  Messager messager) {
        this.generator = Objects.requireNonNull(generator);
        this.messager = messager;
    }

    @Override
    public Optional<CustomAnnotationTemplateResponse> create(GenericTemplateCreatorRequest req) {
        Objects.requireNonNull(req);
        if (!TypeFactory.isFqn(req.generatedTypeName())) {
            messager.debug("skipping custom template production for: " + req.generatedTypeName() + " = " + req);
            return Optional.empty();
        }

        TemplateHelper templateHelper = TemplateHelper.create();
        Map<String, Object> substitutions = gatherSubstitutions(req, templateHelper);
        String javaBody = templateHelper.applySubstitutions(req.template(), substitutions, true);
        return Optional.of(CustomAnnotationTemplateResponse.builder()
                                   .request(req.customAnnotationTemplateRequest())
                                   .putGeneratedSourceCode(req.generatedTypeName(), javaBody)
                                   .build());
    }

    /**
     * Returns the template that will rely on a resource lookup of resources/inject/{templateProfile}/{templateName}.
     * Note: This will only work for non-module based usages, and therefore is not recommended for general use.
     *
     * @param templateProfile the template profile to apply (must be exported by the spi provider module; "default" is reserved
     *                        for internal use)
     * @param templateName    the template name
     * @return the generic template resource
     */
    CharSequence supplyFromResources(String templateProfile,
                                     String templateName) {
        TemplateHelper templateHelper = TemplateHelper.create();
        String template = templateHelper.loadTemplate(templateProfile, templateName);
        if (template == null) {
            ToolsException te = new ToolsException("Unable to find template " + templateProfile + "/" + templateName);
            messager.error(te.getMessage(), te);
            throw te;
        }

        return template;
    }

    Map<String, Object> gatherSubstitutions(GenericTemplateCreatorRequest genericRequest,
                                            TemplateHelper templateHelper) {
        CustomAnnotationTemplateRequest req = genericRequest.customAnnotationTemplateRequest();
        TypeName generatedTypeName = genericRequest.generatedTypeName();
        Map<String, Object> substitutions = new HashMap<>();

        TypeName generatorType = TypeName.create(generator);
        TypeName serviceType = req.serviceInfo().serviceTypeName();
        substitutions.put("generatedSticker", templateHelper.generatedStickerFor(generatorType, serviceType, generatedTypeName));
        substitutions.put("annoTypeName", req.annoTypeName());
        substitutions.put("generatedTypeName", generatedTypeName);
        substitutions.put("packageName", generatedTypeName.packageName());
        substitutions.put("className", generatedTypeName.className());
        substitutions.put("enclosingClassTypeName", req.enclosingTypeInfo().typeName());
        substitutions.put("enclosingAnnotations", req.enclosingTypeInfo().annotations());
        substitutions.put("basicServiceInfo", req.serviceInfo());
        substitutions.put("weight", req.serviceInfo().realizedWeight());
        substitutions.put("runLevel", req.serviceInfo().realizedRunLevel());
        substitutions.put("elementKind", req.targetElement().elementTypeKind());
        substitutions.put("elementName", req.targetElement().elementName());
        substitutions.put("elementAnnotations", req.targetElement().annotations());
        substitutions.put("elementEnclosingTypeName", req.targetElement().typeName());
        substitutions.putAll(genericRequest.overrideProperties());
        return substitutions;
    }

}
