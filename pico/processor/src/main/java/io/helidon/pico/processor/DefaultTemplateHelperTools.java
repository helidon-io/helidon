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
import java.util.function.Function;
import java.util.function.Supplier;

import io.helidon.common.types.DefaultTypeName;
import io.helidon.common.types.TypeName;
import io.helidon.pico.tools.CustomAnnotationTemplateRequest;
import io.helidon.pico.tools.CustomAnnotationTemplateResponse;
import io.helidon.pico.tools.DefaultCustomAnnotationTemplateResponse;
import io.helidon.pico.tools.Messager;
import io.helidon.pico.tools.TemplateHelper;
import io.helidon.pico.tools.TemplateHelperTools;
import io.helidon.pico.tools.ToolsException;

/**
 * Default implementation for {@link io.helidon.pico.tools.TemplateHelperTools}.
 */
class DefaultTemplateHelperTools implements TemplateHelperTools {

    private final Class<?> generator;
    private final Messager messager;

    /**
     * Constructor.
     *
     * @param generator the class type for the generator.
     */
    DefaultTemplateHelperTools(
            Class<?> generator) {
        this(generator, new MessagerToLogAdapter(System.getLogger(DefaultTemplateHelperTools.class.getName())));
    }

    /**
     * Constructor.
     *
     * @param generator the class type for the generator.
     * @param messager the msgr and error handler.
     */
    DefaultTemplateHelperTools(
            Class<?> generator,
            Messager messager) {
        this.generator = Objects.requireNonNull(generator);
        this.messager = messager;
    }

    @Override
    public Optional<CustomAnnotationTemplateResponse> produceStandardCodeGenResponse(
            CustomAnnotationTemplateRequest req,
            TypeName generatedType,
            Supplier<? extends CharSequence> templateSupplier,
            Function<Map<String, Object>, Map<String, Object>> propertiesFn) {
        Objects.requireNonNull(req);
        if (!DefaultTypeName.isFQN(generatedType)
                || (templateSupplier == null)
                || (templateSupplier.get() == null)) {
            messager.log("skipping custom template production for: " + generatedType + " = " + req);
            return Optional.empty();
        }

        TemplateHelper templateHelper = TemplateHelper.create();

        Map<String, Object> substitutions = gatherSubstitutions(req, templateHelper, generatedType, propertiesFn);
        String template = Objects.requireNonNull(templateSupplier.get()).toString();
        messager.debug("applying template: " + template);

        String javaBody = Objects.requireNonNull(templateHelper.applySubstitutions(template, substitutions, true));
        messager.debug("produced body: " + javaBody);

        return Optional.of(DefaultCustomAnnotationTemplateResponse.builder()
                                   .request(req)
                                   .addGeneratedSourceCode(generatedType, javaBody)
                                   .build());
    }

    @Override
    public Supplier<CharSequence> supplyFromResources(String templateProfile,
                                                      String templateName) {
        TemplateHelper templateHelper = TemplateHelper.create();
        String template = templateHelper.loadTemplate(templateProfile, templateName);
        if (template == null) {
            ToolsException te = new ToolsException("unable to find template" + templateProfile + "/" + templateName);
            messager.error(te.getMessage(), te);
            throw te;
        }
        return supplyUsingLiteralTemplate(template);
    }

    @Override
    public Supplier<CharSequence> supplyUsingLiteralTemplate(CharSequence template) {
        return () -> template;
    }

    Map<String, Object> gatherSubstitutions(CustomAnnotationTemplateRequest req,
                                            TemplateHelper templateHelper,
                                            TypeName generatedType,
                                            Function<Map<String, Object>, Map<String, Object>> propertiesFn) {
        Map<String, Object> substitutions = new HashMap<>();
        substitutions.put("generatedSticker", templateHelper.generatedStickerFor(generator.getName()));
        substitutions.put("annoTypeName", req.annoTypeName());
        substitutions.put("generatedTypeName", generatedType);
        substitutions.put("packageName", generatedType.packageName());
        substitutions.put("className", generatedType.className());
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

        if (propertiesFn != null) {
            substitutions = Objects.requireNonNull(propertiesFn.apply(substitutions));
        }
        return substitutions;
    }

}
