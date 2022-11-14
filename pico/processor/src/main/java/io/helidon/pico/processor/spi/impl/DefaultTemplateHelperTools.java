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

package io.helidon.pico.processor.spi.impl;

import java.io.PrintStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Stack;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.lang.model.element.ElementKind;

import io.helidon.pico.processor.spi.CustomAnnotationTemplateProducerRequest;
import io.helidon.pico.processor.spi.CustomAnnotationTemplateProducerResponse;
import io.helidon.pico.processor.spi.TemplateHelperTools;
import io.helidon.pico.DefaultServiceInfo;
import io.helidon.pico.types.DefaultTypeName;
import io.helidon.pico.types.TypeName;
import io.helidon.pico.types.TypedElementName;
import io.helidon.pico.spi.ext.TypeInterceptor;
import io.helidon.pico.tools.creator.impl.DefaultInterceptorCreator;
import io.helidon.pico.tools.creator.impl.Msgr;
import io.helidon.pico.tools.processor.TypeTools;
import io.helidon.pico.tools.utils.CommonUtils;
import io.helidon.pico.tools.utils.TemplateHelper;

/**
 * Default implementation for {@link io.helidon.pico.processor.spi.TemplateHelperTools}.
 */
public class DefaultTemplateHelperTools implements TemplateHelperTools {

    private final Class<?> generator;
    private final Msgr messager;

    /**
     * ctor.
     *
     * @param generator the class type for the generator.
     */
    public DefaultTemplateHelperTools(Class<?> generator) {
        this(generator, new MessagerToLogAdapter(System.getLogger(DefaultTemplateHelperTools.class.getName())));
    }

    /**
     * ctor.
     *
     * @param generator the class type for the generator.
     * @param messager the msgr and error handler.
     */
    public DefaultTemplateHelperTools(Class<?> generator, Msgr messager) {
        this.generator = Objects.requireNonNull(generator);
        this.messager = messager;
    }

    @Override
    public CustomAnnotationTemplateProducerResponse produceStandardCodeGenResponse(
            CustomAnnotationTemplateProducerRequest req,
            TypeName generatedType,
            Supplier<CharSequence> templateSupplier,
            Function<Map<String, Object>, Map<String, Object>> propertiesFn,
            PrintStream errOut) {
        if (Objects.isNull(req) || !DefaultTypeName.isFQN(generatedType)
                || Objects.isNull(templateSupplier) || Objects.isNull(templateSupplier.get())) {
            messager.log("skipping custom template production for: " + generatedType + " = " + req);
            return null;
        }

        Map<String, Object> substitutions = gatherSubstitutions(req, generatedType, propertiesFn);
        String template = Objects.requireNonNull(templateSupplier.get()).toString();
        messager.debug("applying template: " + template);
        String javaBody = Objects.requireNonNull(TemplateHelper.applySubstitutions(errOut, template, substitutions));
        messager.debug("produced body: " + javaBody);

        return DefaultTemplateProducerResponse.builder(req.getAnnoType()).generateJavaCode(generatedType, javaBody)
                .build();
    }

    @Override
    public CustomAnnotationTemplateProducerResponse produceNamedBasicInterceptorDelegationCodeGenResponse(
            CustomAnnotationTemplateProducerRequest req,
            TypeName generatedType,
            Class<?> contractIntercepted,
            Function<Map<String, Object>, Map<String, Object>> propertiesFn,
            PrintStream errOut) {
        if (Objects.isNull(req) || !DefaultTypeName.isFQN(generatedType) || Objects.isNull(contractIntercepted)
                || Objects.isNull(req.getBasicServiceInfo())
                || !contractIntercepted.isInterface() || ElementKind.CLASS != req.getElementKind()
                || !req.getBasicServiceInfo().contractsImplemented().contains(contractIntercepted.getName())
                || req.getBasicServiceInfo().contractsImplemented().contains(TypeInterceptor.class.getName())) {
            messager.log("skipping custom template production for: " + generatedType + " = " + req);
            return null;
        }

        String template = TemplateHelper.safeLoadTemplate("basic-interceptor.hbs");
        String methodTemplate = TemplateHelper.safeLoadTemplate("basic-interceptor-method.hbs");
        String voidMethodTemplate = TemplateHelper.safeLoadTemplate("basic-interceptor-void-method.hbs");

        Map<String, Object> substitutions = gatherSubstitutions(req, generatedType, propertiesFn);
        substitutions.put("interceptedTypeName", DefaultTypeName.create(contractIntercepted));
        substitutions.put("weight", DefaultInterceptorCreator.interceptorWeight(req.getBasicServiceInfo().weight()));

        List<String> methodDecls = new ArrayList<>();
        Stack<Class<?>> stack = new Stack<>();
        stack.push(contractIntercepted);
        while (!stack.isEmpty()) {
            Class<?> cn = stack.pop();
            if (cn == Object.class) {
                continue;
            }

            for (Method m : cn.getDeclaredMethods()) {
                List<TypedElementName> elemArgs = TypeTools.createTypedElementNameListFromMethodArgs(m);
                List<String> elemArgsNoTypes = elemArgs.stream().map(TypedElementName::getElementName)
                        .collect(Collectors.toList());
                List<String> elemThrows = Arrays.stream(m.getExceptionTypes())
                        .map(Class::getName).collect(Collectors.toList());
                Map<String, Object> methodSubstitutions = new HashMap<>(substitutions);
                methodSubstitutions.put("elementName", m.getName());
                methodSubstitutions.put("elementAnnotations",
                                        TypeTools.createAnnotationAndValueListFromAnnotations(m.getAnnotations()));
                methodSubstitutions.put("elementEnclosingTypeName", DefaultTypeName.create(m.getReturnType()));
                methodSubstitutions.put("elementArgs", elemArgs);
                methodSubstitutions.put("elementArgs-declaration", CommonUtils.toString(elemArgs));
                if (!elemArgsNoTypes.isEmpty()) {
                    methodSubstitutions.put("elementArgs-declaration-notypes", CommonUtils.toString(elemArgsNoTypes));
                }
                methodSubstitutions.put("elementThrows", elemThrows);
                methodSubstitutions.put("elementThrows-declaration",
                                        (m.getExceptionTypes().length > 0)
                                                ? "throws " + CommonUtils.toString(elemThrows) : null);
                String javaBody = Objects.requireNonNull(TemplateHelper
                                                                 .applySubstitutions(errOut,
                                                                                     (void.class == m.getReturnType())
                                                                                             ? voidMethodTemplate
                                                                                             : methodTemplate,
                                                                                     methodSubstitutions));
                methodDecls.add(javaBody);
            }
            stack.addAll(Arrays.asList(cn.getInterfaces()));
        }
        substitutions.put("interceptedMethods", methodDecls);

        messager.log("applying template: " + template);
        String javaBody = Objects.requireNonNull(TemplateHelper.applySubstitutions(errOut, template, substitutions));
        messager.debug("produced body: " + javaBody);

        return DefaultTemplateProducerResponse.builder(req.getAnnoType()).generateJavaCode(generatedType, javaBody)
                .build();
    }

    @Override
    public Supplier<CharSequence> supplyFromResources(String templateProfile, String templateName) {
        String template = TemplateHelper.loadTemplate(templateProfile, templateName);
        if (Objects.isNull(template)) {
            messager.warn("unable to find template" + templateProfile + "/" + templateName, null);
            return null;
        }
        return supplyUsingLiteralTemplate(template);
    }

    @Override
    public Supplier<CharSequence> supplyUsingLiteralTemplate(CharSequence template) {
        return () -> template;
    }

    protected Map<String, Object> gatherSubstitutions(CustomAnnotationTemplateProducerRequest req,
                                                      TypeName generatedType,
                                                      Function<Map<String, Object>, Map<String, Object>> propertiesFn) {
        Map<String, Object> substitutions = new HashMap<>();
        substitutions.put("generatedSticker", TemplateHelper.getDefaultGeneratedSticker(generator.getName()));
        substitutions.put("annoTypeName", req.getAnnoType());
        substitutions.put("generatedTypeName", generatedType);
        substitutions.put("packageName", generatedType.packageName());
        substitutions.put("className", generatedType.className());
        substitutions.put("enclosingClassTypeName", req.getEnclosingClassType());
        substitutions.put("enclosingAnnotations", req.getElementAnnotations());
        substitutions.put("basicServiceInfo", req.getBasicServiceInfo());
        substitutions.put("weight", DefaultServiceInfo.weightOf(req.getBasicServiceInfo()));
        substitutions.put("elementAccess", req.getElementAccess());
        substitutions.put("elementIsStatic", req.isElementStatic());
        substitutions.put("elementKind", req.getElementKind());
        substitutions.put("elementName", req.getElementName());
        substitutions.put("elementAnnotations", req.getElementAnnotations());
        substitutions.put("elementEnclosingTypeName", req.getElementType());
        substitutions.put("elementArgs", req.getElementArgs());
        substitutions.put("elementArgs-declaration", CommonUtils.toString(req.getElementArgs()));

        if (Objects.nonNull(propertiesFn)) {
            substitutions = Objects.requireNonNull(propertiesFn.apply(substitutions));
        }
        return substitutions;
    }

}
