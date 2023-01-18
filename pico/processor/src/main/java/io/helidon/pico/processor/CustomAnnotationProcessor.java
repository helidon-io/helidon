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

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;

import io.helidon.builder.processor.spi.TypeInfo;
import io.helidon.builder.processor.spi.TypeInfoCreatorProvider;
import io.helidon.common.HelidonServiceLoader;
import io.helidon.pico.ServiceInfoBasics;
import io.helidon.pico.tools.AbstractFilerMsgr;
import io.helidon.pico.tools.ActivatorCreatorCodeGen;
import io.helidon.pico.tools.CodeGenFiler;
import io.helidon.pico.tools.CommonUtils;
import io.helidon.pico.tools.CustomAnnotationTemplateCreator;
import io.helidon.pico.tools.CustomAnnotationTemplateRequest;
import io.helidon.pico.tools.CustomAnnotationTemplateResponse;
import io.helidon.pico.tools.DefaultActivatorCreator;
import io.helidon.pico.tools.DefaultCustomAnnotationTemplateRequest;
import io.helidon.pico.tools.DefaultCustomAnnotationTemplateResponse;
import io.helidon.pico.tools.ToolsException;
import io.helidon.pico.types.DefaultTypeName;
import io.helidon.pico.types.TypeName;
import io.helidon.pico.types.TypedElementName;

import static io.helidon.pico.tools.TypeTools.createTypeNameFromElement;
import static io.helidon.pico.tools.TypeTools.createTypedElementNameFromElement;
import static io.helidon.pico.tools.TypeTools.isStatic;
import static io.helidon.pico.tools.TypeTools.toAccess;
import static io.helidon.pico.tools.TypeTools.toFilePath;

/**
 * Processor for all {@link io.helidon.pico.tools.CustomAnnotationTemplateCreator}'s.
 */
public class CustomAnnotationProcessor extends BaseAnnotationProcessor<Void> {
    private static final Map<TypeName, Set<CustomAnnotationTemplateCreator>> PRODUCERS_BY_ANNOTATION = new ConcurrentHashMap<>();
    private static final Set<Class<? extends Annotation>> ALL_ANNO_TYPES_HANDLED = new CopyOnWriteArraySet<>();
    private static final List<CustomAnnotationTemplateCreator> PRODUCERS = initialize();

    static List<CustomAnnotationTemplateCreator> initialize() {
        // note: it is important to use this class' CL since maven will not give us the "right" one.
        List<CustomAnnotationTemplateCreator> producers = HelidonServiceLoader.create(ServiceLoader.load(
                        CustomAnnotationTemplateCreator.class, CustomAnnotationProcessor.class.getClassLoader())).asList();
        producers.forEach(producer -> {
            producer.annoTypes().forEach(annoType -> {
                PRODUCERS_BY_ANNOTATION.compute(DefaultTypeName.create(annoType), (k, v) -> {
                    if (v == null) {
                        v = new LinkedHashSet<>();
                    }
                    v.add(producer);
                    return v;
                });
            });
        });
        producers.forEach(p -> ALL_ANNO_TYPES_HANDLED.addAll(p.annoTypes()));
        return producers;
    }

    @Override
    public void init(
            ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        logger().log(System.Logger.Level.DEBUG, CustomAnnotationTemplateCreator.class.getSimpleName() + "s: " + PRODUCERS);
    }

    @Override
    protected Set<Class<? extends Annotation>> annoTypes() {
        return Set.copyOf(ALL_ANNO_TYPES_HANDLED);
    }

    Set<CustomAnnotationTemplateCreator> producersForType(
            TypeName annoTypeName) {
        Set<CustomAnnotationTemplateCreator> set = PRODUCERS_BY_ANNOTATION.get(annoTypeName);
        return (set == null) ? null : Collections.unmodifiableSet(set);
    }

    @Override
    public boolean process(
            Set<? extends TypeElement> annotations,
            RoundEnvironment roundEnv) {
        this.processed = true;
        this.roundEnv = roundEnv;

        try {
            if (!roundEnv.processingOver()) {
                for (Class<? extends Annotation> annoType : annoTypes()) {
                    Set<? extends Element> typesToProcess = roundEnv.getElementsAnnotatedWith(annoType);
                    doInner(annoType, typesToProcess, roundEnv);
                }
            }

            return MAYBE_ANNOTATIONS_CLAIMED_BY_THIS_PROCESSOR;
        } catch (Throwable t) {
            error(getClass().getSimpleName() + " error during processing; " + t + " @ "
                          + CommonUtils.rootStackTraceElementOf(t), t);
            // we typically will not even get to this next line since the messager.error() call will trigger things to halt
            throw new ToolsException("error during processing: " + t + " @ "
                                             + CommonUtils.rootStackTraceElementOf(t), t);
        } finally {
            this.roundEnv = null;
        }
    }

    void doInner(
            Class<? extends Annotation> annoType,
            Set<? extends Element> typesToProcess,
            RoundEnvironment roundEnv) {
        if (typesToProcess.isEmpty()) {
            return;
        }

        final TypeName annoTypeName = DefaultTypeName.create(annoType);
        final Collection<CustomAnnotationTemplateCreator> producers = producersForType(annoTypeName);
        if (producers.isEmpty()) {
            return;
        }

        for (Element typeToProcess : typesToProcess) {
            try {
                DefaultCustomAnnotationTemplateRequest.Builder req = toRequestBuilder(annoTypeName, typeToProcess, roundEnv);
                if (req == null) {
                    continue;
                }

                DefaultCustomAnnotationTemplateResponse.Builder res = DefaultCustomAnnotationTemplateResponse.builder()
                        .request(req);
                for (CustomAnnotationTemplateCreator producer : producers) {
                    req.templateHelperTools(new DefaultTemplateHelperTools(producer.getClass(), this));
                    CustomAnnotationTemplateResponse producerResponse = process(producer, req);
                    res = CustomAnnotationTemplateResponse.aggregate(req, res, producerResponse);
                }
                CustomAnnotationTemplateResponse response = res.build();
                if (req.isFilerEnabled()) {
                    doFiler(response);
                }
            } catch (Throwable t) {
                throw new ToolsException("handling " + typesToProcess + t, t);
            }
        }
    }

    void doFiler(
            CustomAnnotationTemplateResponse response) {
        AbstractFilerMsgr filer = AbstractFilerMsgr.createAnnotationBasedFiler(processingEnv, this);
        CodeGenFiler codegen = new CodeGenFiler(filer);
        response.generatedSourceCode().forEach((typeName, codeBody) -> {
            codegen.codegenJavaFilerOut(typeName, codeBody);
        });
        response.generatedResources().forEach((typedElementName, resourceBody) -> {
            String fileType = typedElementName.elementName();
            if (!hasValue(fileType)) {
                fileType = ".generated";
            }
            codegen.codegenResourceFilerOut(toFilePath(typedElementName.typeName(), fileType),
                                            resourceBody, null);
        });
    }

    CustomAnnotationTemplateResponse process(
            CustomAnnotationTemplateCreator producer,
            CustomAnnotationTemplateRequest req) {
        if (producer == null) {
            return null;
        }

        try {
            CustomAnnotationTemplateResponse res = producer.create(req).orElse(null);
            if (res != null && req.isFilerEnabled() && !res.generatedSourceCode().isEmpty()) {
                res.generatedSourceCode().entrySet().forEach(entry -> {
                    DefaultTypeName.ensureIsFQN(entry.getKey());
                    if (!hasValue(entry.getValue())) {
                        throw new ToolsException("expected to have valid code for: " + req + " for " + entry);
                    }
                });
            }
            return res;
        } catch (Throwable t) {
            throw new ToolsException("failed in producer: " + producer + "; " + t, t);
        }
    }

    DefaultCustomAnnotationTemplateRequest.Builder toRequestBuilder(
            TypeName annoTypeName,
            Element typeToProcess,
            RoundEnvironment roundEnv) {
        TypeElement enclosingClassType = toEnclosingClassTypeElement(typeToProcess);
        TypeName enclosingClassTypeName = createTypeNameFromElement(enclosingClassType).orElse(null);
        if (enclosingClassTypeName == null) {
            return null;
        }

        ServiceInfoBasics siInfo = toBasicServiceInfo(enclosingClassTypeName);
        if (siInfo == null) {
            return null;
        }

        TypeInfoCreatorProvider tools = HelidonServiceLoader.create(
                        ServiceLoader.load(TypeInfoCreatorProvider.class, TypeInfoCreatorProvider.class.getClassLoader()))
                .asList()
                .stream()
                .findFirst()
                .orElse(null);
        TypeInfo enclosingClassTypeInfo = tools.createTypeInfo(annoTypeName, enclosingClassTypeName, (TypeElement) typeToProcess, processingEnv)
                .orElseThrow();
        Elements elements = processingEnv.getElementUtils();
        return DefaultCustomAnnotationTemplateRequest.builder()
                .filerEnabled(true)
                .annoTypeName(annoTypeName)
                .basicServiceInfo(siInfo)
                .targetElement(createTypedElementNameFromElement(typeToProcess, elements))
                .targetElementArgs(toArgs(typeToProcess))
                .targetElementAccess(toAccess(typeToProcess))
                .elementStatic(isStatic(typeToProcess))
                .enclosingTypeInfo(enclosingClassTypeInfo);
    }

    ServiceInfoBasics toBasicServiceInfo(
            TypeName enclosingClassType) {
        ActivatorCreatorCodeGen codeGen =
                DefaultActivatorCreator.createActivatorCreatorCodeGen(services).orElse(null);
        if (codeGen == null) {
            return null;
        }
        return DefaultActivatorCreator.toServiceInfo(enclosingClassType, codeGen);
    }

    List<TypedElementName> toArgs(
            Element typeToProcess) {
        if (!(typeToProcess instanceof ExecutableElement)) {
            return null;
        }

        Elements elements = processingEnv.getElementUtils();
        List<TypedElementName> result = new ArrayList<>();
        ExecutableElement executableElement = (ExecutableElement) typeToProcess;
        executableElement.getParameters().forEach((v) -> result.add(
                createTypedElementNameFromElement(v, elements)));
        return result;
    }

    protected TypeElement toEnclosingClassTypeElement(Element typeToProcess) {
        while (typeToProcess != null && !(typeToProcess instanceof TypeElement)) {
            typeToProcess = typeToProcess.getEnclosingElement();
        }
        return (typeToProcess == null) ? null : (TypeElement) typeToProcess;
    }

}
