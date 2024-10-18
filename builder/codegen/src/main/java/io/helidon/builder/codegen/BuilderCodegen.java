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

package io.helidon.builder.codegen;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import io.helidon.builder.codegen.ValidationTask.ValidateConfiguredType;
import io.helidon.codegen.CodegenContext;
import io.helidon.codegen.CodegenEvent;
import io.helidon.codegen.CodegenException;
import io.helidon.codegen.CodegenFiler;
import io.helidon.codegen.CodegenUtil;
import io.helidon.codegen.FilerTextResource;
import io.helidon.codegen.RoundContext;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.classmodel.Javadoc;
import io.helidon.codegen.classmodel.Method;
import io.helidon.codegen.classmodel.TypeArgument;
import io.helidon.codegen.spi.CodegenExtension;
import io.helidon.common.Errors;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;

import static io.helidon.builder.codegen.Types.RUNTIME_PROTOTYPE;

class BuilderCodegen implements CodegenExtension {
    private static final TypeName GENERATOR = TypeName.create(BuilderCodegen.class);

    // all types annotated with prototyped by (for validation)
    private final Set<TypeName> runtimeTypes = new HashSet<>();
    // all blueprint types (for validation)
    private final Set<TypeName> blueprintTypes = new HashSet<>();
    // all types from service loader that should be supported by ServiceRegistry
    private final Set<String> serviceLoaderContracts = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

    private final CodegenContext ctx;

    BuilderCodegen(CodegenContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void process(RoundContext roundContext) {
        // see need to keep the type names, as some types may not be available, as we are generating them
        runtimeTypes.addAll(roundContext.annotatedTypes(Types.RUNTIME_PROTOTYPED_BY)
                                    .stream()
                                    .map(TypeInfo::typeName)
                                    .toList());
        Collection<TypeInfo> blueprints = roundContext.annotatedTypes(Types.PROTOTYPE_BLUEPRINT);
        blueprintTypes.addAll(blueprints.stream()
                                      .map(TypeInfo::typeName)
                                      .toList());

        List<TypeInfo> blueprintInterfaces = blueprints.stream()
                .filter(it -> it.kind() == ElementKind.INTERFACE)
                .toList();

        for (TypeInfo blueprintInterface : blueprintInterfaces) {
            process(roundContext, blueprintInterface);
        }
    }

    @Override
    public void processingOver(RoundContext roundContext) {
        process(roundContext);

        // now create service.loader
        updateServiceLoaderResource();

        // we must collect validation information after all types are generated - so
        // we also listen on @Generated, so there is another round of annotation processing where we have all
        // types nice and ready
        List<ValidationTask> validationTasks = new ArrayList<>();
        validationTasks.addAll(addRuntimeTypesForValidation(this.runtimeTypes));
        validationTasks.addAll(addBlueprintsForValidation(this.blueprintTypes));

        Errors.Collector collector = Errors.collector();
        for (ValidationTask task : validationTasks) {
            task.validate(collector);
        }

        Errors errors = collector.collect();
        if (errors.hasFatal()) {
            for (Errors.ErrorMessage error : errors) {
                CodegenEvent.Builder builder = CodegenEvent.builder()
                        .message(error.getMessage().replace('\n', ' '))
                        .addObject(error.getSource());

                switch (error.getSeverity()) {
                case FATAL -> builder.level(System.Logger.Level.ERROR);
                case WARN -> builder.level(System.Logger.Level.WARNING);
                case HINT -> builder.level(System.Logger.Level.INFO);
                default -> builder.level(System.Logger.Level.DEBUG);
                }

                ctx.logger().log(builder.build());
            }
        }
    }

    private static void addCreateDefaultMethod(AnnotationDataBlueprint blueprintDef,
                                               TypeContext.PropertyData propertyData,
                                               ClassModel.Builder classModel,
                                               TypeName prototype,
                                               String ifaceName,
                                               String typeArgumentString,
                                               List<TypeArgument> typeArguments) {
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
                            .addContentLine("return " + ifaceName + "." + typeArgumentString + "builder().buildPrototype();");
                    typeArguments.forEach(builder::addGenericArgument);
                });
            }
        }
    }

    private static void addCreateFromConfigMethod(AnnotationDataBlueprint blueprintDef,
                                                  AnnotationDataConfigured configuredData,
                                                  TypeName prototype,
                                                  List<TypeArgument> typeArguments,
                                                  String ifaceName,
                                                  String typeArgumentString,
                                                  ClassModel.Builder classModel) {
        if (blueprintDef.createFromConfigPublic() && configuredData.configured()) {
            Method.Builder method = Method.builder()
                    .name("create")
                    .isStatic(true)
                    .description("Create a new instance from configuration.")
                    .returnType(prototype, "a new instance configured from configuration")
                    .addParameter(paramBuilder -> paramBuilder.type(Types.COMMON_CONFIG)
                            .name("config")
                            .description("used to configure the new instance"));
            typeArguments.forEach(method::addGenericArgument);
            if (blueprintDef.builderPublic()) {
                method.addContentLine("return " + ifaceName + "." + typeArgumentString + "builder().config(config)"
                                              + ".buildPrototype();");
            } else {
                if (typeArguments.isEmpty()) {
                    method.addContentLine("return new Builder().config(config).build();");
                } else {
                    method.addContentLine("return new Builder()<>.config(config).build();");
                }
            }
            classModel.addMethod(method);
        }
    }

    private static void addCopyBuilderMethod(ClassModel.Builder classModel,
                                             TypeName builderTypeName,
                                             TypeName prototype,
                                             List<TypeArgument> typeArguments,
                                             String ifaceName,
                                             String typeArgumentString) {
        classModel.addMethod(builder -> {
            builder.isStatic(true)
                    .name("builder")
                    .description("Create a new fluent API builder from an existing instance.")
                    .returnType(builderTypeName, "a builder based on an instance")
                    .addParameter(paramBuilder -> paramBuilder.type(prototype)
                            .name("instance")
                            .description("an existing instance used as a base for the builder"));
            typeArguments.forEach(builder::addGenericArgument);
            builder.addContentLine("return " + ifaceName + "." + typeArgumentString + "builder().from(instance);");
        });
    }

    private static void addBuilderMethod(ClassModel.Builder classModel,
                                         TypeName builderTypeName,
                                         List<TypeArgument> typeArguments,
                                         String ifaceName) {
        classModel.addMethod(builder -> {
            builder.isStatic(true)
                    .name("builder")
                    .description("Create a new fluent API builder to customize configuration.")
                    .returnType(builderTypeName, "a new builder");
            typeArguments.forEach(builder::addGenericArgument);
            if (typeArguments.isEmpty()) {
                builder.addContentLine("return new " + ifaceName + ".Builder();");
            } else {
                builder.addContentLine("return new " + ifaceName + ".Builder<>();");
            }
        });
    }

    private static void generateCustomConstants(CustomMethods customMethods, ClassModel.Builder classModel) {
        for (CustomConstant customConstant : customMethods.customConstants()) {
            classModel.addField(constant -> constant
                    .type(customConstant.fieldType())
                    .name(customConstant.name())
                    .javadoc(customConstant.javadoc())
                    .addContent(customConstant.declaringType())
                    .addContent(".")
                    .addContent(customConstant.name()));
        }
    }

    private static void generateCustomMethods(ClassModel.Builder classModel,
                                              TypeName builderTypeName,
                                              TypeName prototype,
                                              CustomMethods customMethods) {
        for (CustomMethods.CustomMethod customMethod : customMethods.factoryMethods()) {
            TypeName typeName = customMethod.declaredMethod().returnType();
            // there is a chance the typeName does not have a package (if "forward referenced"),
            // in that case compare just by classname (leap of faith...)
            if (typeName.packageName().isBlank()) {
                String className = typeName.className();
                if (!(
                        className.equals(prototype.className())
                                || className.equals(builderTypeName.className()))) {
                    // based on class names
                    continue;
                }
            } else if (!(typeName.equals(prototype) || typeName.equals(builderTypeName))) {
                // we only generate custom factory methods if they return prototype or builder
                continue;
            }

            // prototype definition - custom static factory methods
            // static TypeName create(Type type);
            CustomMethods.Method generated = customMethod.generatedMethod().method();
            Method.Builder method = Method.builder()
                    .name(generated.name())
                    .javadoc(Javadoc.parse(generated.javadoc()))
                    .isStatic(true)
                    .returnType(generated.returnType());
            customMethod.generatedMethod().generateCode().accept(method);

            for (String annotation : customMethod.generatedMethod().annotations()) {
                method.addAnnotation(io.helidon.codegen.classmodel.Annotation.parse(annotation));
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
            if (generated.javadoc().isEmpty()
                    && customMethod.generatedMethod()
                    .annotations()
                    .contains(Override.class.getName())) {
                // there is no javadoc, and this is overriding a method from super interface, ignore
                continue;
            }

            // TypeName boxed();
            Method.Builder method = Method.builder()
                    .name(generated.name())
                    .javadoc(Javadoc.parse(generated.javadoc()))
                    .returnType(generated.returnType());
            for (String annotation : customMethod.generatedMethod().annotations()) {
                method.addAnnotation(io.helidon.codegen.classmodel.Annotation.parse(annotation));
            }
            for (CustomMethods.Argument argument : generated.arguments()) {
                method.addParameter(param -> param.name(argument.name())
                        .type(argument.typeName()));
            }
            classModel.addMethod(method);
        }
    }

    private void updateServiceLoaderResource() {
        CodegenFiler filer = ctx.filer();
        FilerTextResource serviceLoaderResource = filer.textResource("META-INF/helidon/service.loader");
        List<String> lines = new ArrayList<>(serviceLoaderResource.lines());
        if (lines.isEmpty()) {
            lines.add("# List of service contracts we want to support either from service registry, or from service loader");
        }
        boolean modified = false;
        for (String serviceLoaderContract : this.serviceLoaderContracts) {
            if (!lines.contains(serviceLoaderContract)) {
                modified = true;
                lines.add(serviceLoaderContract);
            }
        }

        if (modified) {
            serviceLoaderResource.lines(lines);
            serviceLoaderResource.write();
        }
    }

    private void process(RoundContext roundContext, TypeInfo blueprint) {
        TypeContext typeContext = TypeContext.create(ctx, blueprint);
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

        // prototype interface (with inner class Builder)
        ClassModel.Builder classModel = ClassModel.builder()
                .type(prototype)
                .classType(ElementKind.INTERFACE)
                .copyright(CodegenUtil.copyright(GENERATOR,
                                                 typeInfo.typeName(),
                                                 prototype));

        String javadocString = blueprintDef.javadoc();
        List<TypeArgument> typeArguments = new ArrayList<>();
        Javadoc javadoc;
        if (javadocString == null) {
            javadoc = Javadoc.parse("Interface generated from definition. Please add javadoc to the "
                                            + "definition interface.");
        } else {
            javadoc = Javadoc.parse(blueprintDef.javadoc());
        }
        classModel.javadoc(javadoc);

        typeGenericArguments.forEach(arg -> {
            TypeArgument.Builder tokenBuilder = TypeArgument.builder()
                    .token(arg.className());
            if (!arg.upperBounds().isEmpty()) {
                arg.upperBounds().forEach(tokenBuilder::addBound);
            }
            if (javadoc.genericsTokens().containsKey(arg.className())) {
                tokenBuilder.description(javadoc.genericsTokens().get(arg.className()));
            }
            typeArguments.add(tokenBuilder.build());
        });

        List<TypeName> typeArgumentNames = typeArguments.stream()
                .map(it -> TypeName.createFromGenericDeclaration(it.className()))
                .collect(Collectors.toList());
        typeArguments.forEach(classModel::addGenericArgument);

        if (blueprintDef.builderPublic()) {
            classModel.addJavadocTag("see", "#builder()");
        }
        if (!propertyData.hasRequired() && blueprintDef.createEmptyPublic() && blueprintDef.builderPublic()) {
            classModel.addJavadocTag("see", "#create()");
        }

        typeContext.typeInfo()
                .annotationsToGenerate()
                .forEach(annotation -> classModel.addAnnotation(io.helidon.codegen.classmodel.Annotation.parse(annotation)));

        classModel.addAnnotation(CodegenUtil.generatedAnnotation(GENERATOR,
                                                                 typeInfo.typeName(),
                                                                 prototype,
                                                                 "1",
                                                                 ""));

        if (typeContext.blueprintData().prototypePublic()) {
            classModel.accessModifier(AccessModifier.PUBLIC);
        } else {
            classModel.accessModifier(AccessModifier.PACKAGE_PRIVATE);
        }
        blueprintDef.extendsList()
                .forEach(classModel::addInterface);

        generateCustomConstants(customMethods, classModel);

        TypeName builderTypeName = TypeName.builder()
                .from(TypeName.create(prototype.fqName() + ".Builder"))
                .typeArguments(prototype.typeArguments())
                .build();

        // static Builder builder()
        addBuilderMethod(classModel, builderTypeName, typeArguments, ifaceName);

        // static Builder builder(T instance)
        addCopyBuilderMethod(classModel, builderTypeName, prototype, typeArguments, ifaceName, typeArgumentString);

        // static T create(Config config)
        addCreateFromConfigMethod(blueprintDef,
                                  configuredData,
                                  prototype,
                                  typeArguments,
                                  ifaceName,
                                  typeArgumentString,
                                  classModel);

        // static X create()
        addCreateDefaultMethod(blueprintDef, propertyData, classModel, prototype, ifaceName, typeArgumentString, typeArguments);

        generateCustomMethods(classModel, builderTypeName, prototype, customMethods);

        // abstract class BuilderBase...
        GenerateAbstractBuilder.generate(classModel,
                                         typeInformation.prototype(),
                                         typeInformation.runtimeObject().orElseGet(typeInformation::prototype),
                                         typeArguments,
                                         typeArgumentNames,
                                         typeContext);
        // class Builder extends BuilderBase ...
        GenerateBuilder.generate(classModel,
                                 typeInformation.prototype(),
                                 typeInformation.runtimeObject().orElseGet(typeInformation::prototype),
                                 typeArguments,
                                 typeArgumentNames,
                                 typeContext.blueprintData().isFactory(),
                                 typeContext);

        roundContext.addGeneratedType(prototype,
                                      classModel,
                                      blueprint.typeName(),
                                      blueprint.originatingElementValue());

        if (typeContext.typeInfo().supportsServiceRegistry() && typeContext.propertyData().hasProvider()) {
            for (PrototypeProperty property : typeContext.propertyData().properties()) {
                if (property.configuredOption().provider()) {
                    this.serviceLoaderContracts.add(property.configuredOption().providerType().genericTypeName().fqName());
                }
            }
        }
    }

    private Collection<? extends ValidationTask> addBlueprintsForValidation(Set<TypeName> blueprints) {
        List<ValidationTask> result = new ArrayList<>();

        for (TypeName blueprintType : blueprints) {
            TypeInfo blueprint = ctx.typeInfo(blueprintType)
                    .orElseThrow(() -> new CodegenException("Could not get TypeInfo for " + blueprintType.fqName()));
            result.add(new ValidationTask.ValidateBlueprint(blueprint));
            TypeContext typeContext = TypeContext.create(ctx, blueprint);

            if (typeContext.blueprintData().isFactory()) {
                result.add(new ValidationTask.ValidateBlueprintExtendsFactory(typeContext.typeInfo().prototype(),
                                                                              blueprint,
                                                                              toTypeInfo(blueprint,
                                                                                         typeContext.typeInfo()
                                                                                                 .runtimeObject()
                                                                                                 .get())));
            }
        }

        return result;
    }

    private TypeInfo toTypeInfo(TypeInfo typeInfo, TypeName typeName) {
        return ctx.typeInfo(typeName.genericTypeName())
                .orElseThrow(() -> new IllegalArgumentException("Type " + typeName.fqName() + " is not a valid type for Factory"
                                                                        + " declared on type " + typeInfo.typeName()
                        .fqName()));
    }

    private List<? extends ValidationTask> addRuntimeTypesForValidation(Set<TypeName> runtimeTypes) {
        return runtimeTypes.stream()
                .map(ctx::typeInfo)
                .flatMap(Optional::stream)
                .map(it -> new ValidateConfiguredType(it,
                                                      annotationTypeValue(it, RUNTIME_PROTOTYPE)))
                .toList();
    }

    private TypeName annotationTypeValue(TypeInfo typeInfo, TypeName annotationType) {
        return typeInfo.findAnnotation(annotationType)
                .flatMap(Annotation::typeValue)
                .orElseThrow(() -> new IllegalArgumentException("Type " + typeInfo.typeName()
                        .fqName() + " has invalid ConfiguredBy annotation"));
    }

    private String createTypeArgumentString(List<TypeName> typeArguments) {
        if (!typeArguments.isEmpty()) {
            String arguments = typeArguments.stream()
                    .map(TypeName::className)
                    .collect(Collectors.joining(", "));
            return "<" + arguments + ">";
        }
        return "";
    }
}
