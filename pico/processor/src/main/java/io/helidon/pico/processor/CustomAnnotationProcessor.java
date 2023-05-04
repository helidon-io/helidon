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
import java.util.Optional;
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

import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNameDefault;
import io.helidon.common.types.TypedElementName;
import io.helidon.pico.api.ServiceInfoBasics;
import io.helidon.pico.tools.AbstractFilerMessager;
import io.helidon.pico.tools.CodeGenFiler;
import io.helidon.pico.tools.CustomAnnotationTemplateRequest;
import io.helidon.pico.tools.CustomAnnotationTemplateRequestDefault;
import io.helidon.pico.tools.CustomAnnotationTemplateResponse;
import io.helidon.pico.tools.CustomAnnotationTemplateResponseDefault;
import io.helidon.pico.tools.ToolsException;
import io.helidon.pico.tools.spi.CustomAnnotationTemplateCreator;

import static io.helidon.pico.processor.ActiveProcessorUtils.MAYBE_ANNOTATIONS_CLAIMED_BY_THIS_PROCESSOR;
import static io.helidon.pico.processor.GeneralProcessorUtils.hasValue;
import static io.helidon.pico.processor.GeneralProcessorUtils.rootStackTraceElementOf;
import static io.helidon.pico.tools.TypeTools.createTypeNameFromElement;
import static io.helidon.pico.tools.TypeTools.createTypedElementNameFromElement;
import static io.helidon.pico.tools.TypeTools.isStatic;
import static io.helidon.pico.tools.TypeTools.toAccess;
import static io.helidon.pico.tools.TypeTools.toFilePath;

/**
 * Processor for all {@link io.helidon.pico.tools.spi.CustomAnnotationTemplateCreator}'s.
 */
public class CustomAnnotationProcessor extends BaseAnnotationProcessor {
    private static final Map<TypeName, Set<CustomAnnotationTemplateCreator>> PRODUCERS_BY_ANNOTATION = new ConcurrentHashMap<>();
    private static final Set<String> ALL_ANNO_TYPES_HANDLED = new CopyOnWriteArraySet<>();
    private static final List<CustomAnnotationTemplateCreator> PRODUCERS = initialize();

    /**
     * Service loader based constructor.
     *
     * @deprecated this is a Java ServiceLoader implementation and the constructor should not be used directly
     */
    @Deprecated
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
                    PRODUCERS_BY_ANNOTATION.compute(TypeNameDefault.createFromTypeName(annoType), (k, v) -> {
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
                ToolsException te = new ToolsException("Failed to initialize: " + creator, t);
                logger.log(System.Logger.Level.ERROR, te.getMessage(), te);
                throw te;
            }
        });
        return creators;
    }

    @Override
    public void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        logger().log(System.Logger.Level.DEBUG, CustomAnnotationTemplateCreator.class.getSimpleName() + "s: " + PRODUCERS);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations,
                           RoundEnvironment roundEnv) {
        try {
            if (!roundEnv.processingOver()) {
                for (String annoType : getSupportedAnnotationTypes()) {
                    TypeName annoName = TypeNameDefault.createFromTypeName(annoType);
                    Optional<TypeElement> annoElement = toTypeElement(annoName);
                    if (annoElement.isEmpty()) {
                        continue;
                    }
                    Set<? extends Element> typesToProcess = roundEnv.getElementsAnnotatedWith(annoElement.get());
                    doInner(annoName, typesToProcess);
                }
            }

            return MAYBE_ANNOTATIONS_CLAIMED_BY_THIS_PROCESSOR;
        } catch (Throwable t) {
            utils().error(getClass().getSimpleName() + " error during processing; " + t + " @ "
                          + rootStackTraceElementOf(t), t);
            // we typically will not even get to this next line since the messager.error() call will trigger things to halt
            throw new ToolsException("Error while processing: " + t + " @ "
                                             + rootStackTraceElementOf(t), t);
        }
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.copyOf(ALL_ANNO_TYPES_HANDLED);
    }

    Set<CustomAnnotationTemplateCreator> producersForType(TypeName annoTypeName) {
        Set<CustomAnnotationTemplateCreator> set = PRODUCERS_BY_ANNOTATION.get(annoTypeName);
        return (set == null) ? null : Collections.unmodifiableSet(set);
    }

    void doInner(TypeName annoTypeName,
                 Set<? extends Element> typesToProcess) {
        if (typesToProcess.isEmpty()) {
            return;
        }

        Collection<CustomAnnotationTemplateCreator> producers = producersForType(annoTypeName);
        if (producers.isEmpty()) {
            return;
        }

        for (Element typeToProcess : typesToProcess) {
            try {
                CustomAnnotationTemplateRequestDefault.Builder req = toRequestBuilder(annoTypeName, typeToProcess);
                if (req == null) {
                    continue;
                }

                CustomAnnotationTemplateResponseDefault.Builder res = CustomAnnotationTemplateResponseDefault.builder()
                        .request(req);
                for (CustomAnnotationTemplateCreator producer : producers) {
                    req.genericTemplateCreator(new GenericTemplateCreatorDefault(producer.getClass(), utils()));
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
                throw new ToolsException("Error while processing: " + typesToProcess + t, t);
            }
        }
    }

    void doFiler(CustomAnnotationTemplateResponse response) {
        AbstractFilerMessager filer = AbstractFilerMessager.createAnnotationBasedFiler(processingEnv, utils());
        CodeGenFiler codegen = CodeGenFiler.create(filer);
        response.generatedSourceCode().forEach(codegen::codegenJavaFilerOut);
        response.generatedResources().forEach((typedElementName, resourceBody) -> {
            String fileType = typedElementName.elementName();
            if (!hasValue(fileType)) {
                fileType = ".generated";
            }
            codegen.codegenResourceFilerOut(toFilePath(typedElementName.typeName(), fileType), resourceBody);
        });
    }

    CustomAnnotationTemplateResponse process(CustomAnnotationTemplateCreator producer,
                                             CustomAnnotationTemplateRequest req) {
        if (producer == null) {
            return null;
        }

        try {
            CustomAnnotationTemplateResponse res = producer.create(req).orElse(null);
            if (res != null && req.isFilerEnabled() && !res.generatedSourceCode().isEmpty()) {
                res.generatedSourceCode().entrySet().forEach(entry -> {
                    TypeNameDefault.ensureIsFQN(entry.getKey());
                    if (!hasValue(entry.getValue())) {
                        throw new ToolsException("Expected to have valid code for: " + req + " for " + entry);
                    }
                });
            }
            return res;
        } catch (Throwable t) {
            throw new ToolsException("Failed in producer: " + producer + "; " + t, t);
        }
    }

    CustomAnnotationTemplateRequestDefault.Builder toRequestBuilder(TypeName annoTypeName,
                                                                    Element typeToProcess) {
        TypeElement enclosingClassType = toEnclosingClassTypeElement(typeToProcess);
        TypeName enclosingClassTypeName = createTypeNameFromElement(enclosingClassType).orElse(null);
        if (enclosingClassTypeName == null) {
            return null;
        }

        TypeInfo enclosingClassTypeInfo = utils()
                .toTypeInfo(enclosingClassType, enclosingClassType.asType(), (typedElement) -> true)
                .orElseThrow();
        ServiceInfoBasics siInfo = GeneralProcessorUtils.toBasicServiceInfo(enclosingClassTypeInfo);
        if (siInfo == null) {
            return null;
        }

        Elements elements = processingEnv.getElementUtils();
        return CustomAnnotationTemplateRequestDefault.builder()
                .filerEnabled(true)
                .annoTypeName(annoTypeName)
                .serviceInfo(siInfo)
                .targetElement(createTypedElementNameFromElement(typeToProcess, elements).orElseThrow())
                .enclosingTypeInfo(enclosingClassTypeInfo)
                // the following are duplicates that should be removed - get them from the enclosingTypeInfo instead
                // see https://github.com/helidon-io/helidon/issues/6773
                .targetElementArgs(toArgs(typeToProcess))
                .targetElementAccess(toAccess(typeToProcess))
                .elementStatic(isStatic(typeToProcess));
    }

    List<TypedElementName> toArgs(Element typeToProcess) {
        if (!(typeToProcess instanceof ExecutableElement)) {
            return List.of();
        }

        Elements elements = processingEnv.getElementUtils();
        List<TypedElementName> result = new ArrayList<>();
        ExecutableElement executableElement = (ExecutableElement) typeToProcess;
        executableElement.getParameters().forEach(v -> result.add(
                createTypedElementNameFromElement(v, elements).orElseThrow()));
        return result;
    }

    private static TypeElement toEnclosingClassTypeElement(Element typeToProcess) {
        while (typeToProcess != null && !(typeToProcess instanceof TypeElement)) {
            typeToProcess = typeToProcess.getEnclosingElement();
        }
        return (typeToProcess == null) ? null : (TypeElement) typeToProcess;
    }

}
