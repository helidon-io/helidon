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

package io.helidon.builder.processor;

import java.io.IOException;
import java.io.Writer;
import java.lang.annotation.Annotation;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

import io.helidon.builder.processor.spi.BuilderCreatorProvider;
import io.helidon.builder.processor.spi.TypeAndBody;
import io.helidon.builder.processor.spi.TypeInfo;
import io.helidon.builder.processor.spi.TypeInfoCreatorProvider;
import io.helidon.builder.processor.tools.BuilderTypeTools;
import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.Weights;
import io.helidon.pico.types.AnnotationAndValue;
import io.helidon.pico.types.DefaultTypeName;
import io.helidon.pico.types.TypeName;

/**
 * The processor for handling any annotation having a {@link io.helidon.builder.BuilderTrigger}.
 */
public class BuilderProcessor extends AbstractProcessor {
    private static final System.Logger LOGGER = System.getLogger(BuilderProcessor.class.getName());
    private static final Map<TypeName, Set<BuilderCreatorProvider>> PRODUCERS_BY_ANNOTATION = new LinkedHashMap<>();
    private static final Set<Class<? extends Annotation>> ALL_ANNO_TYPES_HANDLED = new LinkedHashSet<>();
    private static final List<BuilderCreatorProvider> PRODUCERS = initialize();

    private final LinkedHashSet<Element> elementsProcessed = new LinkedHashSet<>();

    private TypeInfoCreatorProvider tools;
    private Elements elementUtils;

    /**
     * Default constructor.
     */
    // note: this needs to remain public since it will be resolved via service loader ...
    public BuilderProcessor() {
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return ALL_ANNO_TYPES_HANDLED.stream().map(Class::getName).collect(Collectors.toSet());
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);

        this.elementUtils = processingEnv.getElementUtils();
        this.tools = HelidonServiceLoader.create(
                        ServiceLoader.load(TypeInfoCreatorProvider.class, TypeInfoCreatorProvider.class.getClassLoader()))
                .asList()
                .stream()
                .findFirst()
                .orElse(null);

        if (tools == null) {
            String msg = "no available " + TypeInfoCreatorProvider.class.getSimpleName() + " instances found";
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, msg);
            throw new IllegalStateException(msg);
        }
        LOGGER.log(System.Logger.Level.DEBUG, TypeInfoCreatorProvider.class.getSimpleName() + ": " + tools);

        if (PRODUCERS.isEmpty()) {
            String msg = "no available " + BuilderCreatorProvider.class.getSimpleName() + " instances found";
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, msg);
            throw new IllegalStateException(msg);
        }
        LOGGER.log(System.Logger.Level.DEBUG, BuilderCreatorProvider.class.getSimpleName() + "s: " + PRODUCERS);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations,
                           RoundEnvironment roundEnv) {
        if (roundEnv.processingOver() || tools == null || PRODUCERS.isEmpty()) {
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
                        throw new IllegalStateException("Failed while processing " + element + " with " + annoType, e);
                    }
                }
            }

            return false;
        } catch (Throwable e) {
            LOGGER.log(System.Logger.Level.ERROR, e.getMessage(), e);
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, e.getMessage());
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    /**
     * Process the annotation for the given element.
     *
     * @param annoType the annotation that triggered processing
     * @param element  the element being processed
     * @throws IOException if unable to write the generated class
     */
    protected void process(Class<? extends Annotation> annoType,
                           Element element) throws IOException {
        AnnotationMirror am = BuilderTypeTools.findAnnotationMirror(annoType.getName(),
                                                                    element.getAnnotationMirrors())
                .orElseThrow(() -> new IllegalArgumentException("Cannot find annotation mirror for " + annoType
                                                                        + " on " + element));

        AnnotationAndValue builderAnnotation = BuilderTypeTools
                .createAnnotationAndValueFromMirror(am, elementUtils).get();
        TypeName typeName = BuilderTypeTools.createTypeNameFromElement(element).orElse(null);
        Optional<TypeInfo> typeInfo = tools.createTypeInfo(builderAnnotation, typeName, (TypeElement) element, processingEnv);
        if (typeInfo.isEmpty()) {
            String msg = "Nothing to process, skipping: " + element;
            LOGGER.log(System.Logger.Level.WARNING, msg);
            processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING, msg);
            return;
        }

        Set<BuilderCreatorProvider> creators = getProducersForType(DefaultTypeName.create(annoType));
        Optional<List<TypeAndBody>> result = creators.stream()
                .map(it -> it.create(typeInfo.get(), builderAnnotation))
                .filter(it -> !it.isEmpty())
                .findFirst();
        if (result.isEmpty()) {
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
     * @param codegens the model objects to be code generated
     * @throws IOException if unable to write the generated class
     */
    protected void codegen(List<TypeAndBody> codegens) throws IOException {
        for (TypeAndBody typeAndBody : codegens) {
            JavaFileObject javaSrc = processingEnv.getFiler().createSourceFile(typeAndBody.typeName().name());
            try (Writer os = javaSrc.openWriter()) {
                os.write(typeAndBody.body());
            }
        }
    }

    private static List<BuilderCreatorProvider> initialize() {
        try {
            // note: it is important to use this class' CL since maven will not give us the "right" one.
            List<BuilderCreatorProvider> producers = HelidonServiceLoader
                    .create(ServiceLoader.load(BuilderCreatorProvider.class, BuilderCreatorProvider.class.getClassLoader()))
                    .asList();
            producers.forEach(producer -> {
                producer.supportedAnnotationTypes().forEach(annoType -> {
                    PRODUCERS_BY_ANNOTATION.computeIfAbsent(DefaultTypeName.create(annoType), it -> new LinkedHashSet<>())
                            .add(producer);
                });
            });
            producers.sort(Weights.weightComparator());
            producers.forEach(p -> ALL_ANNO_TYPES_HANDLED.addAll(p.supportedAnnotationTypes()));
            return producers;
        } catch (Throwable t) {
            RuntimeException e = new RuntimeException("Failed to initialize", t);
            LOGGER.log(System.Logger.Level.ERROR, e.getMessage(), e);
            throw e;
        }
    }

    private Set<BuilderCreatorProvider> getProducersForType(TypeName annoTypeName) {
        Set<BuilderCreatorProvider> set = PRODUCERS_BY_ANNOTATION.get(annoTypeName);
        return set == null ? Set.of() : Set.copyOf(set);
    }

}
