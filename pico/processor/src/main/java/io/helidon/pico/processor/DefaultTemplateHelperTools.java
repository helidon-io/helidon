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

import io.helidon.pico.tools.CommonUtils;
import io.helidon.pico.tools.CustomAnnotationTemplateRequest;
import io.helidon.pico.tools.CustomAnnotationTemplateResponse;
import io.helidon.pico.tools.DefaultCustomAnnotationTemplateResponse;
import io.helidon.pico.tools.Msgr;
import io.helidon.pico.tools.TemplateHelper;
import io.helidon.pico.tools.TemplateHelperTools;
import io.helidon.pico.tools.ToolsException;
import io.helidon.pico.types.DefaultTypeName;
import io.helidon.pico.types.TypeName;

/**
 * Default implementation for {@link io.helidon.pico.tools.TemplateHelperTools}.
 */
class DefaultTemplateHelperTools implements TemplateHelperTools {

    private final Class<?> generator;
    private final Msgr messager;

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
            Msgr messager) {
        this.generator = Objects.requireNonNull(generator);
        this.messager = messager;
    }

    @Override
    public Optional<CustomAnnotationTemplateResponse> produceStandardCodeGenResponse(
            CustomAnnotationTemplateRequest req,
            TypeName generatedType,
            Supplier<CharSequence> templateSupplier,
            Optional<Function<Map<String, Object>, Map<String, Object>>> propertiesFn) {
        Objects.requireNonNull(req);
        if (!DefaultTypeName.isFQN(generatedType)
                || (templateSupplier == null)
                || (templateSupplier.get() == null)) {
            messager.log("skipping custom template production for: " + generatedType + " = " + req);
            return Optional.empty();
        }

        TemplateHelper templateHelper = TemplateHelper.create();

        Map<String, Object> substitutions = gatherSubstitutions(req, templateHelper, generatedType, propertiesFn.orElse(null));
        String template = Objects.requireNonNull(templateSupplier.get()).toString();
        messager.debug("applying template: " + template);

        String javaBody = Objects.requireNonNull(templateHelper.applySubstitutions(template, substitutions, true));
        messager.debug("produced body: " + javaBody);

        return Optional.of(DefaultCustomAnnotationTemplateResponse.builder()
                                   .request(req)
                                   .addGeneratedSourceCode(generatedType, javaBody)
                                   .build());
    }

//    @Override
//    public CustomAnnotationTemplateResponse produceNamedBasicInterceptorDelegationCodeGenResponse(
//            CustomAnnotationTemplateRequest req,
//            TypeName generatedType,
//            Class<?> contractIntercepted,
//            Function<Map<String, Object>, Map<String, Object>> propertiesFn,
//            PrintStream errOut) {
//        if (Objects.isNull(req) || !DefaultTypeName.isFQN(generatedType) || Objects.isNull(contractIntercepted)
//                || Objects.isNull(req.getBasicServiceInfo())
//                || !contractIntercepted.isInterface() || ElementKind.CLASS != req.getElementKind()
//                || !req.getBasicServiceInfo().getContractsImplemented().contains(contractIntercepted.getName())
//                || req.getBasicServiceInfo().getContractsImplemented().contains(TypeInterceptor.class.getName())) {
//            messager.log("skipping custom template production for: " + generatedType + " = " + req);
//            return null;
//        }
//
//        String template = TemplateHelper.safeLoadTemplate("basic-interceptor.hbs");
//        String methodTemplate = TemplateHelper.safeLoadTemplate("basic-interceptor-method.hbs");
//        String voidMethodTemplate = TemplateHelper.safeLoadTemplate("basic-interceptor-void-method.hbs");
//
//        Map<String, Object> substitutions = gatherSubstitutions(req, generatedType, propertiesFn);
//        substitutions.put("interceptedTypeName", DefaultTypeName.create(contractIntercepted));
//        substitutions.put("weight", DefaultInterceptorCreator.interceptorWeight(req.getBasicServiceInfo().weight()));
//
//        List<String> methodDecls = new ArrayList<>();
//        Stack<Class<?>> stack = new Stack<>();
//        stack.push(contractIntercepted);
//        while (!stack.isEmpty()) {
//            Class<?> cn = stack.pop();
//            if (cn == Object.class) {
//                continue;
//            }
//
//            for (Method m : cn.getDeclaredMethods()) {
//                List<TypedElementName> elemArgs = TypeTools.createTypedElementNameListFromMethodArgs(m);
//                List<String> elemArgsNoTypes = elemArgs.stream().map(TypedElementName::getElementName)
//                        .collect(Collectors.toList());
//                List<String> elemThrows = Arrays.stream(m.getExceptionTypes())
//                        .map(Class::getName).collect(Collectors.toList());
//                Map<String, Object> methodSubstitutions = new HashMap<>(substitutions);
//                methodSubstitutions.put("elementName", m.getName());
//                methodSubstitutions.put("elementAnnotations",
//                                        TypeTools.createAnnotationAndValueListFromAnnotations(m.getAnnotations()));
//                methodSubstitutions.put("elementEnclosingTypeName", DefaultTypeName.create(m.getReturnType()));
//                methodSubstitutions.put("elementArgs", elemArgs);
//                methodSubstitutions.put("elementArgs-declaration", CommonUtils.toString(elemArgs));
//                if (!elemArgsNoTypes.isEmpty()) {
//                    methodSubstitutions.put("elementArgs-declaration-notypes", CommonUtils.toString(elemArgsNoTypes));
//                }
//                methodSubstitutions.put("elementThrows", elemThrows);
//                methodSubstitutions.put("elementThrows-declaration",
//                                        (m.getExceptionTypes().length > 0)
//                                                ? "throws " + CommonUtils.toString(elemThrows) : null);
//                String javaBody = Objects.requireNonNull(TemplateHelper
//                                                                 .applySubstitutions(errOut,
//                                                                                     (void.class == m.getReturnType())
//                                                                                             ? voidMethodTemplate
//                                                                                             : methodTemplate,
//                                                                                     methodSubstitutions));
//                methodDecls.add(javaBody);
//            }
//            stack.addAll(Arrays.asList(cn.getInterfaces()));
//        }
//        substitutions.put("interceptedMethods", methodDecls);
//
//        messager.log("applying template: " + template);
//        String javaBody = Objects.requireNonNull(TemplateHelper.applySubstitutions(errOut, template, substitutions));
//        messager.debug("produced body: " + javaBody);
//
//        return DefaultTemplateProducerResponse.builder(req.getAnnoType()).generateJavaCode(generatedType, javaBody)
//                .build();
//    }

    @Override
    public Supplier<CharSequence> supplyFromResources(
            String templateProfile,
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
    public Supplier<CharSequence> supplyUsingLiteralTemplate(
            CharSequence template) {
        return () -> template;
    }

    Map<String, Object> gatherSubstitutions(
            CustomAnnotationTemplateRequest req,
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
        substitutions.put("elementKind", req.targetElement().elementKind());
        substitutions.put("elementName", req.targetElement().elementName());
        substitutions.put("elementAnnotations", req.targetElement().annotations());
        substitutions.put("elementEnclosingTypeName", req.targetElement().typeName());
        substitutions.put("elementArgs", req.targetElementArgs());
        substitutions.put("elementArgs-declaration", CommonUtils.toString(req.targetElementArgs()));

        if (propertiesFn != null) {
            substitutions = Objects.requireNonNull(propertiesFn.apply(substitutions));
        }
        return substitutions;
    }

}
