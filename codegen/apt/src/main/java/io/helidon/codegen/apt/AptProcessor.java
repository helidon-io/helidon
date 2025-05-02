/*
 * Copyright (c) 2023, 2025 Oracle and/or its affiliates.
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

package io.helidon.codegen.apt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;

import io.helidon.codegen.Codegen;
import io.helidon.codegen.CodegenEvent;
import io.helidon.codegen.CodegenException;
import io.helidon.codegen.Option;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.ModuleTypeInfo;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;

import static java.lang.System.Logger.Level.TRACE;
import static java.lang.System.Logger.Level.WARNING;

/**
 * Annotation processor that maps APT types to Helidon types, and invokes {@link io.helidon.codegen.Codegen}.
 */
@SuppressWarnings("removal")
public final class AptProcessor extends AbstractProcessor {
    private static final TypeName GENERATOR = TypeName.create(AptProcessor.class);

    private AptContextImpl ctx;
    private Codegen codegen;

    /**
     * Only for {@link java.util.ServiceLoader}, to be loaded by compiler.
     */
    @Deprecated
    public AptProcessor() {
        super();
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        // we need to support all annotations, to be able to use meta-annotations
        return Set.of("*");
    }

    @Override
    public Set<String> getSupportedOptions() {
        return Codegen.supportedOptions()
                .stream()
                .map(Option::name)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);

        this.ctx = AptContextImpl.create(processingEnv, Codegen.supportedOptions());
        this.codegen = Codegen.create(ctx, GENERATOR);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        this.ctx.resetCache();

        Thread thread = Thread.currentThread();
        ClassLoader previousClassloader = thread.getContextClassLoader();
        thread.setContextClassLoader(AptProcessor.class.getClassLoader());

        // we want everything to execute in the classloader of this type, so service loaders
        // use the classpath of the annotation processor, and not some "random" classloader, such as a maven one
        try {
            return doProcess(annotations, roundEnv);
        } catch (CodegenException e) {
            Object originatingElement = e.originatingElement()
                    .orElse(null);
            if (originatingElement instanceof Element element) {
                processingEnv.getMessager().printError(e.getMessage(), element);
            } else if (originatingElement instanceof TypeName typeName) {
                processingEnv.getMessager().printError(e.getMessage() + ", source: " + typeName.fqName());
            } else {
                if (originatingElement != null) {
                    processingEnv.getMessager().printError(e.getMessage() + ", source: " + originatingElement);
                }
            }
            throw e;
        } finally {
            thread.setContextClassLoader(previousClassloader);
        }
    }

    private boolean doProcess(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        ctx.logger().log(TRACE, "Process annotations: " + annotations + ", processing over: " + roundEnv.processingOver());

        if (roundEnv.processingOver()) {
            codegen.processingOver();
            return annotations.isEmpty();
        }

        Set<UsedAnnotation> usedAnnotations = usedAnnotations(annotations);

        if (usedAnnotations.isEmpty()) {
            // no annotations, no types, still call the codegen, maybe it has something to do
            codegen.process(List.of(), List.of());
            return annotations.isEmpty();
        }

        List<TypeInfo> allTypes = new ArrayList<>();
        List<ModuleTypeInfo> allModules = new ArrayList<>();

        discoverTypes(usedAnnotations, roundEnv, allTypes, allModules);
        codegen.process(allTypes, allModules);

        return usedAnnotations.stream()
                .map(UsedAnnotation::annotationElement)
                .collect(Collectors.toSet())
                .equals(annotations);
    }

    private Set<UsedAnnotation> usedAnnotations(Set<? extends TypeElement> annotations) {
        var typePredicate = typePredicate(codegen.supportedAnnotations(), codegen.supportedAnnotationPackagePrefixes());
        var metaPredicate = typePredicate(codegen.supportedMetaAnnotations(), Set.of());

        Set<UsedAnnotation> result = new HashSet<>();

        for (TypeElement annotation : annotations) {
            TypeName typeName = TypeName.create(annotation.getQualifiedName().toString());

            Set<TypeName> supportedAnnotations = new HashSet<>();
            // first check direct support (through exact type or prefix)
            if (typePredicate.test(typeName)) {
                supportedAnnotations.add(typeName);
            }
            /*
            find meta annotations that are supported:
            - annotation that annotates the current annotation
            */
            addSupportedAnnotations(metaPredicate, supportedAnnotations, typeName);

            // and add all the annotations
            if (!supportedAnnotations.isEmpty()) {
                result.add(new UsedAnnotation(typeName, annotation, supportedAnnotations));
            }
        }

        return result;
    }

    private Predicate<TypeName> typePredicate(Set<TypeName> typeNames, Set<String> prefixes) {
        return typeName -> {
            if (typeNames.contains(typeName)) {
                return true;
            }

            String packagePrefix = typeName.packageName() + ".";
            for (String prefix : prefixes) {
                if (packagePrefix.startsWith(prefix)) {
                    return true;
                }
            }
            return false;
        };
    }

    private void addSupportedAnnotations(Predicate<TypeName> typeNamePredicate,
                                         Set<TypeName> supportedAnnotations,
                                         TypeName annotationType) {
        Optional<TypeInfo> foundInfo = AptTypeInfoFactory.create(ctx, annotationType);
        if (foundInfo.isPresent()) {
            TypeInfo annotationInfo = foundInfo.get();
            List<Annotation> annotations = annotationInfo.annotations();
            for (Annotation annotation : annotations) {
                TypeName typeName = annotation.typeName();
                if (typeNamePredicate.test(typeName)) {
                    if (supportedAnnotations.add(typeName)) {
                        addSupportedAnnotations(typeNamePredicate, supportedAnnotations, typeName);
                    }
                }
            }
        }
    }

    private void discoverTypes(Set<UsedAnnotation> annotations,
                                         RoundEnvironment roundEnv,
                                         List<TypeInfo> allTypes,
                                         List<ModuleTypeInfo> allModules) {
        // we must discover all types that should be handled, create TypeInfo and only then check if these should be processed
        // as we may replace annotations, elements, and whole types.

        // first collect all types (group by type name, so we do not have duplicity)
        Map<TypeName, TypeElement> types = new HashMap<>();
        Map<String, ModuleElement> modules = new HashMap<>();

        for (UsedAnnotation annotation : annotations) {
            TypeElement annotationElement = annotation.annotationElement();
            Set<? extends Element> elementsAnnotatedWith = roundEnv.getElementsAnnotatedWith(annotationElement);
            for (Element element : elementsAnnotatedWith) {
                ElementKind kind = element.getKind();
                switch (kind) {
                case ENUM, INTERFACE, CLASS, ANNOTATION_TYPE, RECORD -> addType(types, element, element, annotationElement);
                case ENUM_CONSTANT, CONSTRUCTOR, METHOD, FIELD, STATIC_INIT, INSTANCE_INIT, RECORD_COMPONENT ->
                        addType(types, element.getEnclosingElement(), element, annotationElement);
                case PARAMETER -> addType(types, element.getEnclosingElement().getEnclosingElement(), element, annotationElement);
                case MODULE -> addModule(modules, element);
                default -> ctx.logger().log(TRACE, "Ignoring annotated element, not supported: " + element + ", kind: " + kind);
                }
            }
        }

        types.values()
                .stream()
                .flatMap(element -> {
                    Optional<TypeInfo> typeInfo = AptTypeInfoFactory.create(ctx, element);

                    if (typeInfo.isEmpty()) {
                        ctx.logger().log(CodegenEvent.builder()
                                                 .level(WARNING)
                                                 .message("Could not create TypeInfo for annotated type.")
                                                 .addObject(element)
                                                 .build());
                    }
                    return typeInfo.stream();
                })
                .forEach(allTypes::add);

        modules.values()
                .stream()
                .map(element -> AptModuleFactory.create(ctx, element))
                .forEach(allModules::add);
    }

    private void addModule(Map<String, ModuleElement> modules, Element element) {
        ModuleElement me = (ModuleElement) element;
        modules.putIfAbsent(me.getQualifiedName().toString(), me);
    }

    private void addType(Map<TypeName, TypeElement> types,
                         Element typeElement,
                         Element processedElement,
                         TypeElement annotation) {
        Optional<TypeName> typeName = AptTypeFactory.createTypeName(typeElement);
        if (typeName.isPresent()) {
            types.putIfAbsent(typeName.get(), (TypeElement) typeElement);
        } else {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.MANDATORY_WARNING,
                                                     "Could not create TypeName for annotated type."
                                                             + " Annotation: " + annotation,
                                                     processedElement);
        }
    }

    /**
     * Annotation that annotates a processed type and that must be processed.
     *
     * @param annotationType       annotation on processed type
     * @param annotationElement    element of the annotation
     * @param supportedAnnotations annotations that are supported (either the actual annotation, or meta-annotations)
     */
    private record UsedAnnotation(TypeName annotationType,
                                  TypeElement annotationElement,
                                  Set<TypeName> supportedAnnotations) {
    }
}
