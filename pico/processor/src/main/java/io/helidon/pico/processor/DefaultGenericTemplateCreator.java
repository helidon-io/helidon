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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import io.helidon.common.types.DefaultTypeName;
import io.helidon.common.types.TypeName;
import io.helidon.pico.tools.CustomAnnotationTemplateRequest;
import io.helidon.pico.tools.CustomAnnotationTemplateResponse;
import io.helidon.pico.tools.DefaultCustomAnnotationTemplateResponse;
import io.helidon.pico.tools.GenericTemplateCreator;
import io.helidon.pico.tools.GenericTemplateCreatorRequest;
import io.helidon.pico.tools.Messager;
import io.helidon.pico.tools.TemplateHelper;
import io.helidon.pico.tools.ToolsException;

/**
 * Default implementation for {@link GenericTemplateCreator}.
 */
class DefaultGenericTemplateCreator implements GenericTemplateCreator {
    private final Class<?> generator;
    private final Messager messager;

    /**
     * Constructor.
     *
     * @param generator the class type for the generator
     */
    DefaultGenericTemplateCreator(Class<?> generator) {
        this(generator, new MessagerToLogAdapter(System.getLogger(DefaultGenericTemplateCreator.class.getName())));
    }

    /**
     * Constructor.
     *
     * @param generator the class type for the generator
     * @param messager  the messager and error handler
     */
    DefaultGenericTemplateCreator(Class<?> generator,
                                  Messager messager) {
        this.generator = Objects.requireNonNull(generator);
        this.messager = messager;
    }

    @Override
    public Optional<CustomAnnotationTemplateResponse> create(GenericTemplateCreatorRequest req) {
        Objects.requireNonNull(req);
        if (!DefaultTypeName.isFQN(req.generatedTypeName())) {
            messager.debug("skipping custom template production for: " + req.generatedTypeName() + " = " + req);
            return Optional.empty();
        }

        TemplateHelper templateHelper = TemplateHelper.create();
        Map<String, Object> substitutions = gatherSubstitutions(req, templateHelper);
        String javaBody = templateHelper.applySubstitutions(req.template(), substitutions, true);
        return Optional.of(DefaultCustomAnnotationTemplateResponse.builder()
                                   .request(req.customAnnotationTemplateRequest())
                                   .addGeneratedSourceCode(req.generatedTypeName(), javaBody)
                                   .build());
    }

    /**
     * Returns the template that will rely on a resource lookup of resources/pico/{templateProfile}/{templateName}.
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
            ToolsException te = new ToolsException("unable to find template" + templateProfile + "/" + templateName);
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
        substitutions.put("generatedSticker", templateHelper.generatedStickerFor(generator.getName()));
        substitutions.put("annoTypeName", req.annoTypeName());
        substitutions.put("generatedTypeName", generatedTypeName);
        substitutions.put("packageName", generatedTypeName.packageName());
        substitutions.put("className", generatedTypeName.className());
        substitutions.put("enclosingClassTypeName", req.enclosingTypeInfo().typeName());
        substitutions.put("enclosingAnnotations", req.enclosingTypeInfo().annotations());
        substitutions.put("basicServiceInfo", req.serviceInfo());
        substitutions.put("weight", req.serviceInfo().realizedWeight());
        substitutions.put("runLevel", req.serviceInfo().realizedRunLevel());
        substitutions.put("elementAccess", req.targetElementAccess());
        substitutions.put("elementIsStatic", req.isElementStatic());
        substitutions.put("elementKind", req.targetElement().elementTypeKind());
        substitutions.put("elementName", req.targetElement().elementName());
        substitutions.put("elementAnnotations", req.targetElement().annotations());
        substitutions.put("elementEnclosingTypeName", req.targetElement().typeName());
        substitutions.put("elementArgs", req.targetElementArgs());
        substitutions.put("elementArgs-declaration", Utils.toString(req.targetElementArgs()));
        substitutions.putAll(genericRequest.overrideProperties());
        return substitutions;
    }

}
