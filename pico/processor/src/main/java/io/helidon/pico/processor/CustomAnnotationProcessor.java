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
import io.helidon.common.types.DefaultTypeName;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementName;
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

import static io.helidon.pico.tools.TypeTools.createTypeNameFromElement;
import static io.helidon.pico.tools.TypeTools.createTypedElementNameFromElement;
import static io.helidon.pico.tools.TypeTools.isStatic;
import static io.helidon.pico.tools.TypeTools.toAccess;
import static io.helidon.pico.tools.TypeTools.toFilePath;

/**
 * Processor for all {@link io.helidon.pico.tools.CustomAnnotationTemplateCreator}'s.
 *
 * @deprecated
 */
public class CustomAnnotationProcessor extends BaseAnnotationProcessor<Void> {
    private static final Map<TypeName, Set<CustomAnnotationTemplateCreator>> PRODUCERS_BY_ANNOTATION = new ConcurrentHashMap<>();
    private static final Set<String> ALL_ANNO_TYPES_HANDLED = new CopyOnWriteArraySet<>();
    private static final List<CustomAnnotationTemplateCreator> PRODUCERS = initialize();

    /**
     * Service loader based constructor.
     *
     * @deprecated
     */
    public CustomAnnotationProcessor() {
    }

    static List<CustomAnnotationTemplateCreator> initialize() {
        // note: it is important to use this class' CL since maven will not give us the "right" one.
        List<CustomAnnotationTemplateCreator> creators = HelidonServiceLoader.create(ServiceLoader.load(
                        CustomAnnotationTemplateCreator.class, CustomAnnotationTemplateCreator.class.getClassLoader())).asList();
        creators.forEach(creator -> {
            try {
                Set<String> annoTypes = creator.annoTypes();
                annoTypes.forEach(annoType -> {
                    PRODUCERS_BY_ANNOTATION.compute(DefaultTypeName.createFromTypeName(annoType), (k, v) -> {
                        if (v == null) {
                            v = new LinkedHashSet<>();
                        }
                        v.add(creator);
                        return v;
                    });
                });
                ALL_ANNO_TYPES_HANDLED.addAll(annoTypes);
            } catch (Throwable t) {
                System.Logger logger = System.getLogger(CustomAnnotationProcessor.class.getName());
                ToolsException te = new ToolsException("failed to initialize creator: " + creator, t);
                logger.log(System.Logger.Level.ERROR, te.getMessage(), te);
                throw te;
            }
        });
        return creators;
    }

    @Override
    public void init(
            ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        logger().log(System.Logger.Level.DEBUG, CustomAnnotationTemplateCreator.class.getSimpleName() + "s: " + PRODUCERS);
    }

    @Override
    protected Set<String> annoTypes() {
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
        try {
            if (!roundEnv.processingOver()) {
                for (String annoType : annoTypes()) {
                    TypeName annoName = DefaultTypeName.createFromTypeName(annoType);
                    TypeElement annoElement = toTypeElement(annoName);
                    Set<? extends Element> typesToProcess = roundEnv.getElementsAnnotatedWith(annoElement);
                    doInner(annoName, typesToProcess, roundEnv);
                }
            }

            return MAYBE_ANNOTATIONS_CLAIMED_BY_THIS_PROCESSOR;
        } catch (Throwable t) {
            error(getClass().getSimpleName() + " error during processing; " + t + " @ "
                          + CommonUtils.rootStackTraceElementOf(t), t);
            // we typically will not even get to this next line since the messager.error() call will trigger things to halt
            throw new ToolsException("error during processing: " + t + " @ "
                                             + CommonUtils.rootStackTraceElementOf(t), t);
        }
    }

    void doInner(
            TypeName annoTypeName,
            Set<? extends Element> typesToProcess,
            RoundEnvironment roundEnv) {
        if (typesToProcess.isEmpty()) {
            return;
        }

        Collection<CustomAnnotationTemplateCreator> producers = producersForType(annoTypeName);
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
                    if (producerResponse != null) {
                        res = CustomAnnotationTemplateResponse.aggregate(req, res, producerResponse);
                    }
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
        CodeGenFiler codegen = CodeGenFiler.create(filer);
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
            RoundEnvironment ignoredRoundEnv) {
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
        TypeInfo enclosingClassTypeInfo = tools
                .createTypeInfo(annoTypeName, enclosingClassTypeName, enclosingClassType, processingEnv).orElseThrow();
        Elements elements = processingEnv.getElementUtils();
        return DefaultCustomAnnotationTemplateRequest.builder()
                .filerEnabled(true)
                .annoTypeName(annoTypeName)
                .serviceInfo(siInfo)
                .targetElement(createTypedElementNameFromElement(typeToProcess, elements))
                .targetElementArgs(toArgs(typeToProcess))
                .targetElementAccess(toAccess(typeToProcess))
                .elementStatic(isStatic(typeToProcess))
                .enclosingTypeInfo(enclosingClassTypeInfo);
    }

    ServiceInfoBasics toBasicServiceInfo(
            TypeName enclosingClassType) {
        ActivatorCreatorCodeGen codeGen =
                DefaultActivatorCreator.createActivatorCreatorCodeGen(servicesToProcess()).orElse(null);
        if (codeGen == null) {
            return null;
        }
        return DefaultActivatorCreator.toServiceInfo(enclosingClassType, codeGen);
    }

    List<TypedElementName> toArgs(
            Element typeToProcess) {
        if (!(typeToProcess instanceof ExecutableElement)) {
            return List.of();
        }

        Elements elements = processingEnv.getElementUtils();
        List<TypedElementName> result = new ArrayList<>();
        ExecutableElement executableElement = (ExecutableElement) typeToProcess;
        executableElement.getParameters().forEach((v) -> result.add(
                createTypedElementNameFromElement(v, elements)));
        return result;
    }

    TypeElement toEnclosingClassTypeElement(
            Element typeToProcess) {
        while (typeToProcess != null && !(typeToProcess instanceof TypeElement)) {
            typeToProcess = typeToProcess.getEnclosingElement();
        }
        return (typeToProcess == null) ? null : (TypeElement) typeToProcess;
    }

}
