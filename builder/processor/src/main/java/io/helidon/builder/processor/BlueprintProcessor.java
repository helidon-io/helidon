/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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
import io.helidon.common.processor.classmodel.Annotation;
import io.helidon.common.processor.classmodel.ClassModel;
import io.helidon.common.processor.classmodel.ClassType;
import io.helidon.common.processor.classmodel.Javadoc;
import io.helidon.common.processor.classmodel.Method;
import io.helidon.common.processor.classmodel.TypeArgument;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;

import static io.helidon.builder.processor.Types.GENERATED;
import static io.helidon.builder.processor.Types.OVERRIDE;
import static io.helidon.builder.processor.Types.PROTOTYPE_BLUEPRINT;
import static io.helidon.builder.processor.Types.RUNTIME_PROTOTYPE;
import static io.helidon.builder.processor.Types.RUNTIME_PROTOTYPE_TYPE;

/**
 * Annotation processor for prototype blueprints.
 * Generates prototype implementation from the blueprint.
 *
 * @deprecated replaced with helidon-builder-codegen in
 *     combination with helidon-codegen-apt
 */
@Deprecated(forRemoval = true, since = "4.1.0")
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

        AnnotationDataBlueprint blueprintDef = typeContext.blueprintData();
        AnnotationDataConfigured configuredData = typeContext.configuredData();
        TypeContext.PropertyData propertyData = typeContext.propertyData();
        TypeContext.TypeInformation typeInformation = typeContext.typeInfo();
        CustomMethods customMethods = typeContext.customMethods();

        TypeInfo typeInfo = typeInformation.blueprintType();
        TypeName prototype = typeContext.typeInfo().prototype();
        String ifaceName = prototype.className();
        List<TypeName> typeGenericArguments = blueprintDef.typeArguments();
        String typeArgumentString = createTypeArgumentString(typeGenericArguments);

        JavaFileObject generatedIface = filer.createSourceFile(prototype.name(), builderInterface);

        // prototype interface (with inner class Builder)
        ClassModel.Builder classModel = ClassModel.builder()
                .type(prototype)
                .classType(ClassType.INTERFACE)
                .copyright(CopyrightHandler.copyright(GENERATOR,
                                                      typeInfo.typeName(),
                                                      prototype));

        String javadocString = blueprintDef.javadoc();
        List<TypeArgument> typeArguments = new ArrayList<>();
        if (javadocString == null) {
            classModel.description("Interface generated from definition. Please add javadoc to the definition interface.");
            typeGenericArguments.forEach(arg -> typeArguments.add(TypeArgument.builder()
                                      .token(arg.className())
                                      .build()));
        } else {
            Javadoc javadoc = Javadoc.parse(blueprintDef.javadoc());
            classModel.javadoc(javadoc);
            typeGenericArguments.forEach(arg -> {
                TypeArgument.Builder tokenBuilder = TypeArgument.builder().token(arg.className());
                if (javadoc.genericsTokens().containsKey(arg.className())) {
                    tokenBuilder.description(javadoc.genericsTokens().get(arg.className()));
                }
                typeArguments.add(tokenBuilder.build());
            });
        }
        typeArguments.forEach(classModel::addGenericArgument);

        if (blueprintDef.builderPublic()) {
            classModel.addJavadocTag("see", "#builder()");
        }
        if (!propertyData.hasRequired() && blueprintDef.createEmptyPublic() && blueprintDef.builderPublic()) {
            classModel.addJavadocTag("see", "#create()");
        }

        typeContext.typeInfo()
                .annotationsToGenerate()
                .forEach(annotation -> classModel.addAnnotation(Annotation.parse(annotation)));

        classModel.addAnnotation(builder -> {
            io.helidon.common.types.Annotation generated = GeneratedAnnotationHandler.create(GENERATOR,
                                                                                             typeInfo.typeName(),
                                                                                             prototype,
                                                                                             "1",
                                                                                             "");
            builder.type(generated.typeName());
            generated.values()
                    .forEach(builder::addParameter);
        });

        if (typeContext.blueprintData().prototypePublic()) {
            classModel.accessModifier(AccessModifier.PUBLIC);
        }
        blueprintDef.extendsList()
                .forEach(classModel::addInterface);

        TypeName builderTypeName = TypeName.builder()
                .from(TypeName.create(prototype.fqName() + ".Builder"))
                .typeArguments(prototype.typeArguments())
                .build();

        /*
          static Builder builder()
         */
        classModel.addMethod(builder -> {
            builder.isStatic(true)
                    .name("builder")
                    .description("Create a new fluent API builder to customize configuration.")
                    .returnType(builderTypeName, "a new builder");
            typeArguments.forEach(builder::addGenericArgument);
            if (typeArguments.isEmpty()) {
                builder.addLine("return new " + ifaceName + ".Builder();");
            } else {
                builder.addLine("return new " + ifaceName + ".Builder<>();");
            }
        });

        /*
          static Builder builder(T instance)
         */
        classModel.addMethod(builder -> {
            builder.isStatic(true)
                    .name("builder")
                    .description("Create a new fluent API builder from an existing instance.")
                    .returnType(builderTypeName, "a builder based on an instance")
                    .addParameter(paramBuilder -> paramBuilder.type(prototype)
                            .name("instance")
                            .description("an existing instance used as a base for the builder"));
            typeArguments.forEach(builder::addGenericArgument);
            builder.addLine("return " + ifaceName + "." + typeArgumentString + "builder().from(instance);");
        });

        /*
          static T create(Config config)
         */
        if (blueprintDef.createFromConfigPublic() && configuredData.configured()) {
            Method.Builder method = Method.builder()
                    .name("create")
                    .isStatic(true)
                    .description("Create a new instance from configuration.")
                    .returnType(prototype, "a new instance configured from configuration")
                    .addParameter(paramBuilder -> paramBuilder.type(Types.CONFIG_TYPE)
                            .name("config")
                            .description("used to configure the new instance"));
            typeArguments.forEach(method::addGenericArgument);
            if (blueprintDef.builderPublic()) {
                method.addLine("return " + ifaceName + "." + typeArgumentString + "builder().config(config).buildPrototype();");
            } else {
                if (typeArguments.isEmpty()) {
                    method.addLine("return new Builder().config(config).build();");
                } else {
                    method.addLine("return new Builder()<>.config(config).build();");
                }
            }
            classModel.addMethod(method);
        }

        if (blueprintDef.createEmptyPublic() && blueprintDef.builderPublic()) {
        /*
          static X create()
         */
            if (!propertyData.hasRequired()) {
                classModel.addMethod(builder -> {
                    builder.isStatic(true)
                            .name("create")
                            .description("Create a new instance with default values.")
                            .returnType(prototype, "a new instance")
                            .addLine("return " + ifaceName + "." + typeArgumentString + "builder().buildPrototype();");
                    typeArguments.forEach(builder::addGenericArgument);
                });
            }
        }

        generateCustomMethods(customMethods, classModel);

        /*
          abstract class BuilderBase...
         */
        GenerateAbstractBuilder.generate(classModel,
                                         typeInformation.prototype(),
                                         typeInformation.runtimeObject().orElseGet(typeInformation::prototype),
                                         typeArguments,
                                         typeContext);
        /*
          class Builder extends BuilderBase ...
         */
        GenerateBuilder.generate(classModel,
                                 typeInformation.prototype(),
                                 typeInformation.runtimeObject().orElseGet(typeInformation::prototype),
                                 typeArguments,
                                 typeContext.blueprintData().isFactory(),
                                 typeContext);

        try (PrintWriter pw = new PrintWriter(generatedIface.openWriter())) {
            classModel.build()
                    .write(pw, SOURCE_SPACING);
        }
    }

    private static void generateCustomMethods(CustomMethods customMethods, ClassModel.Builder classModel) {
        for (CustomMethods.CustomMethod customMethod : customMethods.factoryMethods()) {
            // prototype definition - custom static factory methods
            // static TypeName create(Type type);
            CustomMethods.Method generated = customMethod.generatedMethod().method();
            Method.Builder method = Method.builder()
                    .name(generated.name())
                    .javadoc(Javadoc.parse(generated.javadoc()))
                    .isStatic(true)
                    .returnType(generated.returnType())
                    .addLine(customMethod.generatedMethod().callCode() + ";");
            for (String annotation : customMethod.generatedMethod().annotations()) {
                method.addAnnotation(Annotation.parse(annotation));
            }
            for (CustomMethods.Argument argument : generated.arguments()) {
                method.addParameter(param -> param.name(argument.name())
                        .type(argument.typeName()));
            }
            classModel.addMethod(method);
        }

        for (CustomMethods.CustomMethod customMethod : customMethods.prototypeMethods()) {
            // prototype definition - custom methods must have a new method defined on this interface, missing on blueprint
            CustomMethods.Method generated = customMethod.generatedMethod().method();
            if (generated.javadoc().isEmpty() && customMethod.generatedMethod().annotations().contains(OVERRIDE)) {
                // there is no javadoc, and this is overriding a method from super interface, ignore
                continue;
            }

            // TypeName boxed();
            Method.Builder method = Method.builder()
                    .name(generated.name())
                    .javadoc(Javadoc.parse(generated.javadoc()))
                    .returnType(generated.returnType());
            for (String annotation : customMethod.generatedMethod().annotations()) {
                method.addAnnotation(Annotation.parse(annotation));
            }
            for (CustomMethods.Argument argument : generated.arguments()) {
                method.addParameter(param -> param.name(argument.name())
                        .type(argument.typeName()));
            }
            classModel.addMethod(method);
        }
    }

    static String createTypeArgumentString(List<TypeName> typeArguments) {
        if (!typeArguments.isEmpty()) {
            String arguments = typeArguments.stream()
                    .map(TypeName::className)
                    .collect(Collectors.joining(", "));
            if ("?".equals(arguments)) {
                return "";
            }
            return "<" + arguments + ">";
        }
        return "";
    }

}
