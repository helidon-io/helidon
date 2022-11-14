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

package io.helidon.pico.processor;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.pico.processor.spi.CustomAnnotationTemplateProducer;
import io.helidon.pico.processor.spi.CustomAnnotationTemplateProducerRequest;
import io.helidon.pico.processor.spi.CustomAnnotationTemplateProducerResponse;
import io.helidon.pico.processor.spi.TemplateHelperTools;
import io.helidon.pico.processor.spi.impl.DefaultTemplateHelperTools;
import io.helidon.pico.processor.spi.impl.DefaultTemplateProducerRequest;
import io.helidon.pico.processor.spi.impl.DefaultTemplateProducerResponse;
import io.helidon.pico.types.AnnotationAndValue;
import io.helidon.pico.types.DefaultTypeName;
import io.helidon.pico.ServiceInfoBasics;
import io.helidon.pico.types.TypeName;
import io.helidon.pico.types.TypedElementName;
import io.helidon.pico.tools.ToolsException;
import io.helidon.pico.tools.creator.impl.AbstractFilerMsgr;
import io.helidon.pico.tools.creator.impl.CodeGenFiler;
import io.helidon.pico.tools.creator.impl.DefaultActivatorCreator;
import io.helidon.pico.tools.creator.impl.DefaultActivatorCreatorCodeGen;
import io.helidon.pico.tools.processor.TypeTools;
import io.helidon.pico.tools.utils.CommonUtils;

import static io.helidon.pico.tools.processor.TypeTools.isStatic;
import static io.helidon.pico.tools.processor.TypeTools.toAccess;

/**
 * Processor for all {@link io.helidon.pico.processor.spi.CustomAnnotationTemplateProducer}'s.
 */
public class CustomAnnotationProcessor extends BaseAnnotationProcessor<Void> {

    private static final Map<TypeName, Set<CustomAnnotationTemplateProducer>> PRODUCERS_BY_ANNOTATION =
            new ConcurrentHashMap<>();
    private static final Set<Class<? extends Annotation>> ALL_ANNO_TYPES_HANDLED =
            new CopyOnWriteArraySet<>();
    private static final List<CustomAnnotationTemplateProducer> PRODUCERS = initialize();

    protected static List<CustomAnnotationTemplateProducer> initialize() {
        // note: it is important to use this class' CL since maven will not give us the "right" one.
        List<CustomAnnotationTemplateProducer> producers = HelidonServiceLoader.create(ServiceLoader.load(
                        CustomAnnotationTemplateProducer.class, CustomAnnotationProcessor.class.getClassLoader()))
                .asList();
        producers.forEach(producer -> {
            producer.getAnnoTypes().forEach(annoType -> {
                PRODUCERS_BY_ANNOTATION.compute(DefaultTypeName.create(annoType), (k, v) -> {
                    if (Objects.isNull(v)) {
                        v = new LinkedHashSet<>();
                    }
                    v.add(producer);
                    return v;
                });
            });
        });
        producers.forEach(p -> ALL_ANNO_TYPES_HANDLED.addAll(p.getAnnoTypes()));
        return producers;
    }

    @Override
    public void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        logger.log(System.Logger.Level.DEBUG, CustomAnnotationTemplateProducer.class.getSimpleName()
                + "s: " + PRODUCERS);
    }

    @Override
    protected Set<Class<? extends Annotation>> getAnnoTypes() {
        return Collections.unmodifiableSet(ALL_ANNO_TYPES_HANDLED);
    }

    Set<CustomAnnotationTemplateProducer> getProducersForType(TypeName annoTypeName) {
        Set<CustomAnnotationTemplateProducer> set = PRODUCERS_BY_ANNOTATION.get(annoTypeName);
        return Objects.isNull(set) ? null : Collections.unmodifiableSet(set);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        this.processed = true;
        this.roundEnv = roundEnv;

        try {
            if (!roundEnv.processingOver()) {
                for (Class<? extends Annotation> annoType : getAnnoTypes()) {
                    Set<? extends Element> typesToProcess = roundEnv.getElementsAnnotatedWith(annoType);
                    doInner(annoType, typesToProcess, roundEnv);
                }
            }

            return MAYBE_ANNOTATIONS_CLAIMED_BY_THIS_PROCESSOR;
        } catch (Throwable t) {
            error(getClass().getSimpleName() + " error during processing; " + t + " @ "
                          + CommonUtils.rootErrorCoordinateOf(t), t);
            // we typically will not even get to this next line since the messager.error() call will trigger things
            // to halt
            throw new ToolsException("error during processing: " + t + " @ "
                                             + CommonUtils.rootErrorCoordinateOf(t), t);
        } finally {
            this.roundEnv = null;
        }
    }

    protected void doInner(Class<? extends Annotation> annoType,
                           Set<? extends Element> typesToProcess,
                           RoundEnvironment roundEnv) {
        if (typesToProcess.isEmpty()) {
            return;
        }

        final TypeName annoTypeName = DefaultTypeName.create(annoType);
        final Collection<CustomAnnotationTemplateProducer> producers = getProducersForType(annoTypeName);
        if (producers.isEmpty()) {
            return;
        }

        for (Element typeToProcess : typesToProcess) {
            try {
                CustomAnnotationTemplateProducerRequest req = toRequest(annoTypeName, typeToProcess, roundEnv);
                if (Objects.isNull(req)) {
                    continue;
                }
                DefaultTemplateProducerResponse.Builder res = DefaultTemplateProducerResponse
                        .builder(req.getAnnoType());
                for (CustomAnnotationTemplateProducer producer : producers) {
                    DefaultTemplateHelperTools tools = new DefaultTemplateHelperTools(producer.getClass(), this);
                    CustomAnnotationTemplateProducerResponse producerResponse = process(producer, req, tools);
                    res.aggregate(producerResponse);
                }
                DefaultTemplateProducerResponse response = res.build();
                if (req.isFilerEnabled()) {
                    doFiler(response);
                }
            } catch (Throwable t) {
                throw new ToolsException("handling " + typesToProcess + t, t);
            }
        }
    }

    protected void doFiler(DefaultTemplateProducerResponse response) {
        final AbstractFilerMsgr filer = AbstractFilerMsgr.createAnnotationBasedFiler(processingEnv, this);
        final CodeGenFiler codegen = new CodeGenFiler(filer);
        response.getGeneratedJavaCode().forEach((typeName, codeBody) -> {
            codegen.codegenJavaFilerOut(typeName, codeBody);
        });
        response.getGeneratedResources().forEach((typedElementName, resourceBody) -> {
            String fileType = typedElementName.getElementName();
            if (Objects.isNull(fileType)) {
                fileType = ".generated";
            }
            codegen.codegenResourceFilerOut(TypeTools.getFilePath(typedElementName.getTypeName(), fileType),
                                            resourceBody, null);
        });
    }

    protected CustomAnnotationTemplateProducerResponse process(CustomAnnotationTemplateProducer producer,
                                                            CustomAnnotationTemplateProducerRequest req,
                                                            TemplateHelperTools tools) {
        if (Objects.isNull(producer)) {
            return null;
        }

        try {
            CustomAnnotationTemplateProducerResponse response = producer.produce(req, tools);
            if (Objects.nonNull(response) && req.isFilerEnabled() && Objects.nonNull(response.getGeneratedJavaCode())) {
                response.getGeneratedJavaCode().entrySet().forEach(entry -> {
                    DefaultTypeName.ensureIsFQN(entry.getKey());
                    if (!AnnotationAndValue.hasNonBlankValue(entry.getValue())) {
                        throw new ToolsException("expected to have valid code for: " + req + " for " + entry);
                    }
                });
            }
            return response;
        } catch (Throwable t) {
            throw new ToolsException("failed in producer: " + producer + "; " + t, t);
        }
    }

    protected CustomAnnotationTemplateProducerRequest toRequest(TypeName annoTypeName,
                                                                Element typeToProcess,
                                                                RoundEnvironment roundEnv) {
        final TypeElement enclosingClassType = toEnclosingClassTypeElement(typeToProcess);
        final TypeName enclosingClassTypeName = TypeTools.createTypeNameFromElement(enclosingClassType);
        final ServiceInfoBasics siInfo = toBasicServiceInfo(enclosingClassTypeName);
        if (Objects.isNull(siInfo)) {
            return null;
        }
        Elements elements = processingEnv.getElementUtils();
        return DefaultTemplateProducerRequest.builder(annoTypeName)
                .isFilerEnabled(true)
                .elementKind(typeToProcess.getKind())
                .elementType(toElementType(typeToProcess))
                .elementName(toName(typeToProcess))
                .returnType(toReturnType(typeToProcess))
                .elementAnnotations(
                        TypeTools.createAnnotationAndValueListFromElement(typeToProcess, elements))
                .elementArgs(toArgs(typeToProcess))
                .elementAccess(toAccess(typeToProcess))
                .isElementStatic(isStatic(typeToProcess))
                .enclosingClassType(enclosingClassTypeName)
                .enclosingClassAnnotations(
                        TypeTools.createAnnotationAndValueListFromElement(enclosingClassType, elements))
                .basicServiceInfo(siInfo)
                .build();
    }

    protected ServiceInfoBasics toBasicServiceInfo(TypeName enclosingClassType) {
        DefaultActivatorCreatorCodeGen codeGen =
                DefaultActivatorCreatorCodeGen.toActivatorCreatorCodeGen(services);
        if (Objects.isNull(codeGen)) {
            return null;
        }
        DefaultActivatorCreator creator = new DefaultActivatorCreator(createCodeGenFiler());
        return creator.toServiceInfo(enclosingClassType, codeGen);
    }

    protected String toName(Element typeToProcess) {
        return typeToProcess.getSimpleName().toString();
    }

    protected List<TypedElementName> toArgs(Element typeToProcess) {
        if (!(typeToProcess instanceof ExecutableElement)) {
            return null;
        }

        Elements elements = processingEnv.getElementUtils();
        List<TypedElementName> result = new ArrayList<>();
        ExecutableElement executableElement = (ExecutableElement) typeToProcess;
        executableElement.getParameters().forEach((v) -> result.add(
                TypeTools.createTypedElementNameFromElement(v, elements)));
        return result;
    }

    protected TypeName toElementType(Element typeToProcess) {
        if (typeToProcess instanceof ExecutableElement) {
            ExecutableElement executableElement = (ExecutableElement) typeToProcess;
            TypeMirror returnType = executableElement.getReturnType();
            return TypeTools.createTypeNameFromMirror(returnType);
        }

        return TypeTools.createTypeNameFromElement(typeToProcess);
    }

    protected TypeName toReturnType(Element typeToProcess) {
        if (!(typeToProcess instanceof ExecutableElement)) {
            return null;
        }

        return toElementType(typeToProcess);
    }

    protected TypeElement toEnclosingClassTypeElement(Element typeToProcess) {
        while (Objects.nonNull(typeToProcess) && !(typeToProcess instanceof TypeElement)) {
            typeToProcess = typeToProcess.getEnclosingElement();
        }
        return Objects.isNull(typeToProcess) ? null : (TypeElement) typeToProcess;
    }

}
