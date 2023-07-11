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

package io.helidon.builder.processor;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

import io.helidon.builder.processor.ValidationTask.ValidateConfiguredType;
import io.helidon.common.Errors;
import io.helidon.common.processor.CopyrightHandler;
import io.helidon.common.processor.GeneratedAnnotationHandler;
import io.helidon.common.processor.TypeInfoFactory;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;

import static io.helidon.builder.processor.Types.FACTORY_METHOD;
import static io.helidon.builder.processor.Types.GENERATED;
import static io.helidon.builder.processor.Types.OVERRIDE;
import static io.helidon.builder.processor.Types.PROTOTYPE_BLUEPRINT;
import static io.helidon.builder.processor.Types.RUNTIME_PROTOTYPE;
import static io.helidon.builder.processor.Types.RUNTIME_PROTOTYPE_TYPE;

/**
 * Annotation processor for prototype blueprints.
 * Generates prototype implementation from the blueprint.
 */
public class BlueprintProcessor extends AbstractProcessor {
    private static final String SOURCE_SPACING = "    ";
    private static final TypeName GENERATOR = TypeName.create(BlueprintProcessor.class);
    private final Set<ValidationTask> validationTasks = new LinkedHashSet<>();
    private final Set<Element> runtimeTypes = new HashSet<>();
    private final Set<Element> blueprintTypes = new HashSet<>();

    private TypeElement blueprintAnnotationType;
    private TypeElement runtimePrototypeAnnotationType;
    private Messager messager;
    private Filer filer;
    private ProcessingEnvironment env;
    private Elements elementUtils;

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(PROTOTYPE_BLUEPRINT,
                      RUNTIME_PROTOTYPE,
                      GENERATED);
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);

        this.elementUtils = processingEnv.getElementUtils();
        this.messager = processingEnv.getMessager();
        this.blueprintAnnotationType = elementUtils.getTypeElement(PROTOTYPE_BLUEPRINT);
        this.runtimePrototypeAnnotationType = elementUtils.getTypeElement(RUNTIME_PROTOTYPE);
        this.filer = processingEnv.getFiler();
        this.env = processingEnv;

        if (blueprintAnnotationType == null || runtimePrototypeAnnotationType == null) {
            throw new IllegalStateException("Bug in BlueprintProcessor code, cannot find required types, probably wrong"
                                                    + " type constants. "
                                                    + PROTOTYPE_BLUEPRINT + " = " + blueprintAnnotationType + ", "
                                                    + RUNTIME_PROTOTYPE + " = " + runtimePrototypeAnnotationType);
        }
    }

    // we need two compiler passes - first to generate all the necessary types
    // second pass to validate everything
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        this.runtimeTypes.addAll(roundEnv.getElementsAnnotatedWith(runtimePrototypeAnnotationType));

        Set<? extends Element> blueprints = roundEnv.getElementsAnnotatedWithAny(blueprintAnnotationType);
        this.blueprintTypes.addAll(blueprints);

        // collect interfaces annotated with supported annotations
        List<TypeElement> blueprintInterfaces = collectInterfaces(blueprints);
        ProcessingContext processingContext = ProcessingContext.create(processingEnv);

        // now process the interfaces
        for (TypeElement blueprint : blueprintInterfaces) {
            try {
                process(blueprint, processingContext);
            } catch (Throwable e) {
                messager.printError("Failed to process @Builder: "
                                            + e.getClass().getName()
                                            + ": " + e.getMessage(),
                                    blueprint);
                throw new IllegalStateException("Failed to code generate builders", e);
            }
        }

        if (roundEnv.processingOver()) {
            // we must collect validation information after all types are generated - so
            // we also listen on @Generated, so there is another round of annotation processing where we have all
            // types nice and ready
            addRuntimeTypesForValidation(this.runtimeTypes);
            addBlueprintsForValidation(processingContext, this.blueprintTypes);

            Errors.Collector collector = Errors.collector();
            for (ValidationTask task : validationTasks) {
                task.validate(collector);
            }
            validationTasks.clear();
            Errors errors = collector.collect();
            if (errors.hasFatal()) {
                for (Errors.ErrorMessage error : errors) {
                    messager.printError(error.toString().replace('\n', ' '));
                }
            }
        }

        return annotations.size() == 1;
    }

    private void process(TypeElement definitionTypeElement, ProcessingContext processingContext) throws IOException {
        TypeInfo typeInfo = TypeInfoFactory.create(env, definitionTypeElement)
                .orElseThrow(() -> new IllegalArgumentException("Could not process " + definitionTypeElement
                                                                        + ", no type info generated"));

        TypeContext typeContext = TypeContext.create(processingContext,
                                                     elementUtils,
                                                     definitionTypeElement,
                                                     typeInfo);

        generatePrototypeWithBuilder(definitionTypeElement,
                                     typeContext);
    }

    private void addBlueprintsForValidation(ProcessingContext processingContext, Set<Element> blueprintElements) {
        for (Element element : blueprintElements) {
            TypeElement typeElement = (TypeElement) element;
            TypeInfo typeInfo = TypeInfoFactory.create(processingEnv, typeElement)
                    .orElse(null);
            if (typeInfo == null) {
                continue;
            }

            validationTasks.add(new ValidationTask.ValidateBlueprint(typeInfo));
            TypeContext typeContext = TypeContext.create(processingContext,
                                                         elementUtils,
                                                         typeElement,
                                                         typeInfo);

            if (typeContext.blueprintData().isFactory()) {
                validationTasks.add(new ValidationTask.ValidateBlueprintExtendsFactory(typeContext.typeInfo().prototype(),
                                                                                       typeInfo,
                                                                                       toTypeInfo(typeInfo,
                                                                                                  typeContext.typeInfo()
                                                                                                          .runtimeObject()
                                                                                                          .get())));
            }
        }
    }

    private void addRuntimeTypesForValidation(Set<? extends Element> runtimeTypes) {
        runtimeTypes.stream()
                .map(TypeElement.class::cast)
                .map(it -> TypeInfoFactory.create(processingEnv, it))
                .flatMap(Optional::stream)
                .forEach(it -> {
                    validationTasks.add(new ValidateConfiguredType(it,
                                                                   annotationTypeValue(it,
                                                                                       RUNTIME_PROTOTYPE_TYPE)));
                });
    }

    private TypeName annotationTypeValue(TypeInfo typeInfo, TypeName annotationType) {
        return typeInfo.findAnnotation(annotationType)
                .flatMap(it -> it.value().map(TypeName::create))
                .orElseThrow(() -> new IllegalArgumentException("Type " + typeInfo.typeName()
                        .fqName() + " has invalid ConfiguredBy annotation"));
    }

    private TypeInfo toTypeInfo(TypeInfo typeInfo, TypeName typeName) {
        TypeElement element = elementUtils.getTypeElement(typeName.genericTypeName().fqName());
        return TypeInfoFactory.create(processingEnv, element)
                .orElseThrow(() -> new IllegalArgumentException("Type " + typeName.fqName() + " is not a valid type for Factory"
                                                                        + " declared on type " + typeInfo.typeName()
                        .fqName()));
    }

    private List<TypeElement> collectInterfaces(Set<? extends Element> builderTypes) {
        List<TypeElement> result = new ArrayList<>();

        // validate that we only have interfaces annotated and collect the type elements
        Errors.Collector errors = Errors.collector();

        for (Element builderType : builderTypes) {
            if (builderType.getKind() != ElementKind.INTERFACE) {
                errors.fatal("@Blueprint can only be defined on an interface, but is defined on: " + builderType);
                messager.printMessage(Diagnostic.Kind.MANDATORY_WARNING,
                                      PROTOTYPE_BLUEPRINT + " can only be defined on an interface",
                                      builderType);
            } else {
                result.add((TypeElement) builderType);
            }
        }

        errors.collect().checkValid();
        return result;
    }

    @SuppressWarnings("checkstyle:MethodLength") // will be fixed when we switch to model
    private void generatePrototypeWithBuilder(TypeElement builderInterface,
                                              TypeContext typeContext) throws IOException {

        TypeContext.BlueprintData blueprintDef = typeContext.blueprintData();
        TypeContext.ConfiguredData configuredData = typeContext.configuredData();
        TypeContext.PropertyData propertyData = typeContext.propertyData();
        TypeContext.TypeInformation typeInformation = typeContext.typeInfo();
        CustomMethods customMethods = typeContext.customMethods();

        TypeInfo typeInfo = typeInformation.blueprintType();
        TypeName prototype = typeContext.typeInfo().prototype();
        String ifaceName = prototype.className();

        JavaFileObject generatedIface = filer.createSourceFile(prototype.name(), builderInterface);
        try (PrintWriter pw = new PrintWriter(generatedIface.openWriter())) {
            // prototype interface (with inner class Builder)
            pw.println(CopyrightHandler.copyright(GENERATOR,
                                                  typeInfo.typeName(),
                                                  prototype));
            pw.print("package ");
            pw.print(typeInfo.typeName().packageName());
            pw.println(";");
            pw.println();
            pw.print("import ");
            pw.print(typeInfo.typeName().genericTypeName().fqName());
            pw.println(";");
            pw.print("import ");
            pw.print(FACTORY_METHOD);
            pw.println(";");
            pw.println("import java.util.Objects;");
            if (propertyData.hasRequired() || propertyData.hasNonNulls()) {
                pw.println("import io.helidon.common.Errors;");
            }
            // TODO add "hasConfigProperty" to propertyData, and remove "hasConfig" method
            if (configuredData.configured() || GenerateAbstractBuilder.hasConfig(propertyData.properties())) {
                pw.println("import io.helidon.common.config.Config;");
            }
            if (propertyData.hasOptional() || configuredData.configured()) {
                pw.println();
                pw.println("import java.util.Optional;");
            }
            pw.println();
            String javadoc = blueprintDef.javadoc();
            if (javadoc == null) {
                javadoc = " Interface generated from definition. Please add javadoc to the definition interface.";
            }
            pw.println("/**");
            for (String javadocLine : javadoc.split("\n")) {
                pw.print(" *");
                pw.println(javadocLine);
            }
            pw.println(" *");
            pw.println(" * @see #builder()");
            if (!propertyData.hasRequired() && blueprintDef.createEmptyPublic() && blueprintDef.builderPublic()) {
                pw.println(" * @see #create()");
            }
            pw.println(" */");
            typeContext.typeInfo()
                    .annotationsToGenerate()
                    .forEach(pw::println);
            pw.println(GeneratedAnnotationHandler.createString(GENERATOR,
                                                               typeInfo.typeName(),
                                                               prototype,
                                                               "1",
                                                               ""));
            if (typeContext.blueprintData().prototypePublic()) {
                pw.print("public ");
            }
            pw.print("interface ");
            pw.print(ifaceName);
            pw.print(blueprintDef.typeArguments());
            pw.print(" extends ");
            pw.print(blueprintDef.extendsList().stream().map(TypeName::fqName).collect(Collectors.joining(", ")));
            pw.println(" {");

            if (blueprintDef.builderPublic()) {
            /*
              static Builder builder()
             */
                pw.print(SOURCE_SPACING);
                pw.println("/**");
                pw.print(SOURCE_SPACING);
                pw.println(" * Create a new fluent API builder to customize configuration.");
                pw.print(SOURCE_SPACING);
                pw.println(" *");
                pw.print(SOURCE_SPACING);
                pw.println(" * @return a new builder");
                pw.print(SOURCE_SPACING);
                pw.println(" */");
                pw.print(SOURCE_SPACING);
                pw.print("static");
                if (!blueprintDef.typeArguments().isEmpty()) {
                    pw.print(" ");
                    pw.print(blueprintDef.typeArguments());
                }
                pw.print(" Builder");
                pw.print(blueprintDef.typeArguments());
                pw.println(" builder() {");
                pw.print(SOURCE_SPACING);
                pw.print(SOURCE_SPACING);
                if (blueprintDef.typeArguments().isEmpty()) {
                    pw.print("return new ");
                    pw.print(ifaceName);
                    pw.println(".Builder();");
                } else {
                    pw.print("return new ");
                    pw.print(ifaceName);
                    pw.println(".Builder<>();");
                }
                pw.print(SOURCE_SPACING);
                pw.println("}");
                pw.println();

            /*
              static Builder builder(T type)
             */
                pw.print(SOURCE_SPACING);
                pw.println("/**");
                pw.print(SOURCE_SPACING);
                pw.println(" * Create a new fluent API builder from an existing instance.");
                pw.print(SOURCE_SPACING);
                pw.println(" *");
                pw.print(SOURCE_SPACING);
                pw.println(" * @param instance an existing instance used as a base for the builder");
                pw.print(SOURCE_SPACING);
                pw.println(" * @return a builder based on an instance");
                pw.print(SOURCE_SPACING);
                pw.println(" */");
                pw.print(SOURCE_SPACING);
                pw.print("static");
                if (!blueprintDef.typeArguments().isEmpty()) {
                    pw.print(" ");
                    pw.print(blueprintDef.typeArguments());
                }
                pw.print(" Builder");
                pw.print(blueprintDef.typeArguments());
                pw.print(" builder(");
                pw.print(ifaceName);
                pw.print(blueprintDef.typeArguments());
                pw.println(" instance) {");
                pw.print(SOURCE_SPACING);
                pw.print(SOURCE_SPACING);
                pw.print("return ");
                pw.print(ifaceName);
                pw.print(".");
                pw.print(blueprintDef.typeArguments());
                pw.println("builder().from(instance);");
                pw.print(SOURCE_SPACING);
                pw.println("}");
                pw.println();
            }

            /*
             static X create(Config config)
             */
            if (blueprintDef.createFromConfigPublic() && configuredData.configured()) {
                pw.print(SOURCE_SPACING);
                pw.println("/**");
                pw.print(SOURCE_SPACING);
                pw.println(" * Create a new instance from configuration.");
                pw.print(SOURCE_SPACING);
                pw.println(" *");
                pw.print(SOURCE_SPACING);
                pw.println(" * @param config used to configure the new instance");
                pw.print(SOURCE_SPACING);
                pw.println(" * @return a new instance configured from configuration");
                pw.print(SOURCE_SPACING);
                pw.println(" */");
                pw.print(SOURCE_SPACING);
                pw.print("static ");
                if (!blueprintDef.typeArguments().isEmpty()) {
                    pw.print(blueprintDef.typeArguments());
                    pw.print(" ");
                }
                pw.print(ifaceName);
                pw.print(blueprintDef.typeArguments());
                pw.println(" create(Config config) {");
                pw.print(SOURCE_SPACING);
                pw.print(SOURCE_SPACING);
                if (blueprintDef.builderPublic()) {
                    pw.print("return ");
                    pw.print(ifaceName);
                    pw.print(".");
                    pw.print(blueprintDef.typeArguments());
                    pw.println("builder().config(config).buildPrototype();");
                } else {
                    if (blueprintDef.typeArguments().isEmpty()) {
                        pw.println("return new Builder().config(config).build();");
                    } else {
                        pw.println("return new Builder()<>.config(config).build();");
                    }
                }
                pw.print(SOURCE_SPACING);
                pw.println("}");
                pw.println();
            }

            if (blueprintDef.createEmptyPublic() && blueprintDef.builderPublic()) {
            /*
            static X create()
             */
                if (!propertyData.hasRequired()) {
                    pw.print(SOURCE_SPACING);
                    pw.println("/**");
                    pw.print(SOURCE_SPACING);
                    pw.println(" * Create a new instance with default values.");
                    pw.print(SOURCE_SPACING);
                    pw.println(" *");
                    pw.print(SOURCE_SPACING);
                    pw.println(" * @return a new instance");
                    pw.print(SOURCE_SPACING);
                    pw.println(" */");
                    pw.print(SOURCE_SPACING);
                    pw.print("static ");
                    if (!blueprintDef.typeArguments().isEmpty()) {
                        pw.print(blueprintDef.typeArguments());
                        pw.print(" ");
                    }
                    pw.print(ifaceName);
                    pw.print(blueprintDef.typeArguments());
                    pw.println(" create() {");
                    pw.print(SOURCE_SPACING);
                    pw.print(SOURCE_SPACING);
                    pw.print(" return ");
                    pw.print(ifaceName);
                    pw.print(".");
                    pw.print(blueprintDef.typeArguments());
                    pw.println("builder().buildPrototype();");
                    pw.print(SOURCE_SPACING);
                    pw.println("}");
                    pw.println();
                }
            }

            for (CustomMethods.CustomMethod customMethod : customMethods.factoryMethods()) {
                // prototype definition - custom static factory methods
                CustomMethods.Method generated = customMethod.generatedMethod().method();
                // static TypeName create(Type type);
                if (!generated.javadoc().isEmpty()) {
                    pw.print(SOURCE_SPACING);
                    pw.println("/**");
                    for (String docLine : generated.javadoc()) {
                        pw.print(SOURCE_SPACING);
                        pw.print(" *");
                        pw.println(docLine);
                    }
                    pw.print(SOURCE_SPACING);
                    pw.println(" */");
                }
                for (String annotation : customMethod.generatedMethod().annotations()) {
                    pw.print(SOURCE_SPACING);
                    pw.print('@');
                    pw.println(annotation);
                }
                pw.print(SOURCE_SPACING);
                pw.print("static ");
                pw.print(generated.returnType().fqName());
                pw.print(" ");
                pw.print(generated.name());
                pw.print("(");
                pw.print(generated.arguments()
                                 .stream()
                                 .map(it -> it.typeName().fqName() + " " + it.name())
                                 .collect(Collectors.joining(", ")));
                pw.println(") {");
                pw.print(SOURCE_SPACING);
                pw.print(SOURCE_SPACING);
                pw.print(customMethod.generatedMethod().callCode());
                pw.println(";");
                pw.print(SOURCE_SPACING);
                pw.println("}");
                pw.println();
            }

            for (CustomMethods.CustomMethod customMethod : customMethods.prototypeMethods()) {
                // prototype definition - custom methods must have a new method defined on this interface, missing on blueprint
                CustomMethods.Method generated = customMethod.generatedMethod().method();

                if (generated.javadoc().isEmpty() && customMethod.generatedMethod().annotations().contains(OVERRIDE)) {
                    // there is no javadoc, and this is overriding a method from super interface, ignore
                    continue;
                }

                // TypeName boxed();
                if (!generated.javadoc().isEmpty()) {
                    Javadoc parsed = Javadoc.parse(generated.javadoc()).removeFirstParam();
                    pw.print(SOURCE_SPACING);
                    pw.println("/**");
                    for (String docLine : parsed.toLines()) {
                        pw.print(SOURCE_SPACING);
                        pw.print(" *");
                        pw.println(docLine);
                    }
                    pw.print(SOURCE_SPACING);
                    pw.println(" */");
                }
                for (String annotation : customMethod.generatedMethod().annotations()) {
                    pw.print(SOURCE_SPACING);
                    pw.print('@');
                    pw.println(annotation);
                }
                pw.print(SOURCE_SPACING);
                pw.print(generated.returnType().fqName());
                pw.print(" ");
                pw.print(generated.name());
                pw.print("(");
                pw.print(generated.arguments()
                                 .stream()
                                 .map(it -> it.typeName().fqName() + " " + it.name())
                                 .collect(Collectors.joining(", ")));
                pw.println(");");
                pw.println();
            }

            /*
            abstract class BuilderBase...
             */
            GenerateAbstractBuilder.generate(pw,
                                             typeInformation.prototype(),
                                             typeInformation.runtimeObject().orElseGet(typeInformation::prototype),
                                             blueprintDef.typeArguments(),
                                             typeContext);
            /*
              class Builder extends Builder Base ...
             */
            GenerateBuilder.generate(pw,
                                     typeInformation.prototype(),
                                     typeInformation.runtimeObject().orElseGet(typeInformation::prototype),
                                     blueprintDef.typeArguments(),
                                     typeContext.blueprintData().isFactory(),
                                     typeContext);

            // end of prototype class
            pw.println("}");
        }
    }

}
