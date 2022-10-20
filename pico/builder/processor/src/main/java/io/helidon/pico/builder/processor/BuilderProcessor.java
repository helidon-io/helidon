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

package io.helidon.pico.builder.processor;

import java.io.IOException;
import java.io.Writer;
import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.Weights;
import io.helidon.pico.builder.spi.BuilderCreator;
import io.helidon.pico.builder.spi.TypeAndBody;
import io.helidon.pico.builder.spi.TypeInfo;
import io.helidon.pico.builder.tools.BuilderTypeTools;
import io.helidon.pico.builder.tools.TypeInfoCreator;
import io.helidon.pico.types.AnnotationAndValue;
import io.helidon.pico.types.DefaultTypeName;
import io.helidon.pico.types.TypeName;

/**
 * The processor for handling any annotation having a {@link io.helidon.pico.builder.api.BuilderTrigger}.
 */
public class BuilderProcessor extends AbstractProcessor {

    private static final System.Logger LOGGER = System.getLogger(BuilderProcessor.class.getName());

    private TypeInfoCreator tools;
    private final LinkedHashSet<Element> elementsProcessed = new LinkedHashSet<>();

    private static final Map<TypeName, Set<BuilderCreator>> PRODUCERS_BY_ANNOTATION = new LinkedHashMap<>();
    private static final Set<Class<? extends Annotation>> ALL_ANNO_TYPES_HANDLED = new LinkedHashSet<>();
    private static final List<BuilderCreator> PRODUCERS = initialize();

    /**
     * Ctor.
     */
    public BuilderProcessor() {
    }

    private static List<BuilderCreator> initialize() {
        try {
            // note: it is important to use this class' CL since maven will not give us the "right" one.
            List<BuilderCreator> producers = HelidonServiceLoader
                    .create(ServiceLoader.load(BuilderCreator.class, BuilderCreator.class.getClassLoader()))
                    .asList();
            producers.forEach(producer -> {
                producer.getSupportedAnnotationTypes().forEach(annoType -> {
                    PRODUCERS_BY_ANNOTATION.compute(DefaultTypeName.create(annoType), (k, v) -> {
                        if (Objects.isNull(v)) {
                            v = new LinkedHashSet<>();
                        }
                        v.add(producer);
                        return v;
                    });
                });
            });
            producers.sort(Weights.weightComparator());
            producers.forEach(p -> ALL_ANNO_TYPES_HANDLED.addAll(p.getSupportedAnnotationTypes()));
            return producers;
        } catch (Throwable t) {
            RuntimeException e = new RuntimeException("Failed to initialize", t);
            LOGGER.log(System.Logger.Level.ERROR, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);

        tools = HelidonServiceLoader.create(
                        ServiceLoader.load(TypeInfoCreator.class, TypeInfoCreator.class.getClassLoader()))
                .asList().stream().findFirst().orElse(null);
        if (Objects.isNull(tools)) {
            String msg = "no available " + TypeInfoCreator.class.getSimpleName() + " instances found";
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, msg);
            LOGGER.log(System.Logger.Level.ERROR, msg);
        }
        LOGGER.log(System.Logger.Level.DEBUG, TypeInfoCreator.class.getSimpleName() + ": " + tools);

        if (PRODUCERS.isEmpty()) {
            String msg = "no available " + BuilderCreator.class.getSimpleName() + " instances found";
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, msg);
            LOGGER.log(System.Logger.Level.ERROR, msg);
        }
        LOGGER.log(System.Logger.Level.DEBUG, BuilderCreator.class.getSimpleName() + "s: " + PRODUCERS);
    }

    Set<BuilderCreator> getProducersForType(TypeName annoTypeName) {
        Set<BuilderCreator> set = PRODUCERS_BY_ANNOTATION.get(annoTypeName);
        return Objects.isNull(set) ? null : Collections.unmodifiableSet(set);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return ALL_ANNO_TYPES_HANDLED.stream().map(Class::getName).collect(Collectors.toSet());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver() || Objects.isNull(tools) || PRODUCERS.isEmpty()) {
            elementsProcessed.clear();
            return false;
        }

        try {
            for (Class<? extends Annotation> annoType : ALL_ANNO_TYPES_HANDLED) {
                Set<? extends Element> typesToProcess = roundEnv.getElementsAnnotatedWith(annoType);
                for (Element element : typesToProcess) {
                    if (!elementsProcessed.add(element)) {
                        continue;
                    }

                    try {
                        process(annoType, element);
                    } catch (Throwable e) {
                        throw new RuntimeException("Failed while processing " + element + " with " + annoType, e);
                    }
                }
            }
        } catch (Throwable e) {
            LOGGER.log(System.Logger.Level.ERROR, e.getMessage(), e);
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, e.getMessage());
            throw new RuntimeException(e.getMessage(), e);
        }

        return false;
    }

    /**
     * Process the annotation for the given element.
     *
     * @param annoType  the annotation that triggered processing
     * @param element   the element being processed
     * @throws IOException if unable to write the generated class
     */
    protected void process(Class<? extends Annotation> annoType, Element element) throws IOException {
        AnnotationMirror am = BuilderTypeTools.findAnnotationMirror(annoType.getName(),
                                                                    element.getAnnotationMirrors());
        AnnotationAndValue builderAnnotation = BuilderTypeTools
                .createAnnotationAndValueFromMirror(am, processingEnv.getElementUtils());
        TypeName typeName = BuilderTypeTools.createTypeNameFromElement(element);
        TypeInfo typeInfo = tools.createTypeInfo(builderAnnotation, typeName, (TypeElement) element, processingEnv);
        if (Objects.isNull(typeInfo)) {
            LOGGER.log(System.Logger.Level.WARNING, "Nothing to process, skipping: " + element);
            return;
        }

        Set<BuilderCreator> creators = getProducersForType(DefaultTypeName.create(annoType));
        Optional<TypeAndBody> result = creators.stream()
                .map(it -> it.create(typeInfo, builderAnnotation).orElse(null)).findFirst();
        if (result.isEmpty()
                || Objects.isNull(result.get().typeName())
                || Objects.isNull(result.get().body())) {
            String msg = "Unable to process " + typeName;
            LOGGER.log(System.Logger.Level.WARNING, msg);
            processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING, msg);
        } else {
            codegen(result.get());
        }
    }

    /**
     * Performs the actual code generation of the given type and body model object.
     *
     * @param typeAndBody   the model object
     * @throws IOException if unable to write the generated class
     */
    protected void codegen(TypeAndBody typeAndBody) throws IOException {
        JavaFileObject javaSrc = processingEnv.getFiler().createSourceFile(typeAndBody.typeName().name());
        try (Writer os = javaSrc.openWriter()) {
            os.write(typeAndBody.body());
        }
    }

}
