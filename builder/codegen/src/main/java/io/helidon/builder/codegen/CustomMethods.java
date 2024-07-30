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
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import io.helidon.codegen.CodegenContext;
import io.helidon.codegen.CodegenException;
import io.helidon.codegen.ElementInfoPredicates;
import io.helidon.codegen.classmodel.ContentBuilder;
import io.helidon.codegen.classmodel.Javadoc;
import io.helidon.common.Errors;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.Modifier;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;

import static io.helidon.builder.codegen.Types.PROTOTYPE_BUILDER_METHOD;
import static io.helidon.builder.codegen.Types.PROTOTYPE_CONSTANT;
import static io.helidon.builder.codegen.Types.PROTOTYPE_CUSTOM_METHODS;
import static io.helidon.builder.codegen.Types.PROTOTYPE_FACTORY_METHOD;
import static io.helidon.builder.codegen.Types.PROTOTYPE_PROTOTYPE_METHOD;

record CustomMethods(List<CustomMethod> factoryMethods,
                     List<CustomMethod> builderMethods,
                     List<CustomMethod> prototypeMethods,
                     List<CustomConstant> customConstants) {

    CustomMethods() {
        this(List.of(), List.of(), List.of(), List.of());
    }

    static CustomMethods create(CodegenContext ctx, TypeContext.TypeInformation typeInformation) {
        Optional<Annotation> annotation = typeInformation.blueprintType().findAnnotation(PROTOTYPE_CUSTOM_METHODS);
        if (annotation.isEmpty()) {
            return new CustomMethods();
        }
        // value is mandatory for this annotation
        String customMethodType = annotation.get().value().orElseThrow();
        // we must get the type info, as otherwise this is an invalid declaration
        TypeInfo customMethodsInfo = ctx.typeInfo(TypeName.create(customMethodType))
                .orElseThrow(() -> new CodegenException("Failed to get type info for a type declared as custom methods type: "
                                                                + customMethodType));

        Errors.Collector errors = Errors.collector();
        List<CustomMethod> factoryMethods = findMethods(typeInformation,
                                                        customMethodsInfo,
                                                        errors,
                                                        PROTOTYPE_FACTORY_METHOD,
                                                        CustomMethods::factoryMethod);
        List<CustomMethod> builderMethods = findMethods(typeInformation,
                                                        customMethodsInfo,
                                                        errors,
                                                        PROTOTYPE_BUILDER_METHOD,
                                                        CustomMethods::builderMethod);
        List<CustomMethod> prototypeMethods = findMethods(typeInformation,
                                                          customMethodsInfo,
                                                          errors,
                                                          PROTOTYPE_PROTOTYPE_METHOD,
                                                          CustomMethods::prototypeMethod);
        List<CustomConstant> customConstants = findConstants(customMethodsInfo,
                                                             errors);

        errors.collect().checkValid();
        return new CustomMethods(factoryMethods, builderMethods, prototypeMethods, customConstants);
    }

    // methods to be part of prototype interface (signature), and implement in both builder and impl
    private static GeneratedMethod prototypeMethod(Errors.Collector errors,
                                                   TypeContext.TypeInformation typeInformation,
                                                   TypeName customMethodsType,
                                                   List<String> annotations,
                                                   Method customMethod) {
        List<Argument> customMethodArgs = customMethod.arguments();
        if (customMethodArgs.isEmpty()) {
            errors.fatal(customMethodsType.fqName(),
                         "Methods annotated with @Prototype.PrototypeMethod must accept the prototype "
                                 + "as the first parameter, but method: " + customMethod.name() + " has no parameters");
        } else if (!correctType(typeInformation.prototype(), customMethodArgs.getFirst().typeName())) {
            errors.fatal(customMethodsType.fqName(),
                         "Methods annotated with @Prototype.PrototypeMethod must accept the prototype "
                                 + "as the first parameter, but method: " + customMethod.name()
                                 + " expected: " + typeInformation.prototypeBuilder().fqName()
                                 + " actual: " + customMethodArgs.getFirst().typeName().fqName());
        }
        List<Argument> generatedArgs = customMethodArgs.subList(1, customMethodArgs.size());
        List<String> argumentNames = new ArrayList<>();
        argumentNames.add("this");
        argumentNames.addAll(generatedArgs.stream()
                                     .map(Argument::name)
                                     .toList());

        Consumer<ContentBuilder<?>> codeGenerator = contentBuilder -> {
            if (!customMethod.returnType().equals(TypeNames.PRIMITIVE_VOID)) {
                contentBuilder.addContent("return ");
            }
            contentBuilder.addContent(customMethodsType.genericTypeName())
                    .addContent(".")
                    .addContent(customMethod.name())
                    .addContent("(")
                    .addContent(String.join(", ", argumentNames))
                    .addContentLine(");");
        };

        return new GeneratedMethod(
                new Method(typeInformation.prototypeBuilder(),
                           customMethod.name(),
                           customMethod.returnType(),
                           generatedArgs,
                           // todo the javadoc may differ (such as when we have an additional parameter for instance methods)
                           customMethod.javadoc()),
                annotations,
                codeGenerator);
    }

    // methods to be part of prototype builder only
    private static GeneratedMethod builderMethod(Errors.Collector errors,
                                                 TypeContext.TypeInformation typeInformation,
                                                 TypeName customMethodsType,
                                                 List<String> annotations,
                                                 Method customMethod) {

        List<Argument> customMethodArgs = customMethod.arguments();
        if (customMethodArgs.isEmpty()) {
            errors.fatal(customMethodsType.fqName(),
                         "Methods annotated with @Prototype.BuilderMethod must accept the prototype builder base "
                                 + "as the first parameter, but method: " + customMethod.name() + " has no parameters");
        } else if (!correctType(typeInformation.prototypeBuilderBase(),
                                customMethodArgs.getFirst().typeName().genericTypeName())) {
            errors.fatal(customMethodsType.fqName(),
                         "Methods annotated with @Prototype.BuilderMethod must accept the prototype builder "
                                 + "base as the first parameter, but method: " + customMethod.name()
                                 + " expected: " + typeInformation.prototypeBuilderBase().fqName()
                                 + " actual: " + customMethodArgs.getFirst().typeName().fqName());
        }

        List<Argument> generatedArgs = customMethodArgs.subList(1, customMethodArgs.size());
        List<String> argumentNames = new ArrayList<>();
        argumentNames.add("this");
        argumentNames.addAll(generatedArgs.stream()
                                     .map(Argument::name)
                                     .toList());

        // return CustomMethodsType.methodName(this, param1, param2)
        Consumer<ContentBuilder<?>> codeGenerator = contentBuilder -> {
            contentBuilder.addContent(customMethodsType.genericTypeName())
                    .addContent(".")
                    .addContent(customMethod.name())
                    .addContent("(")
                    .addContent(String.join(", ", argumentNames))
                    .addContentLine(");")
                    .addContent("return self();");
        };

        return new GeneratedMethod(
                new Method(typeInformation.prototypeBuilder(),
                           customMethod.name(),
                           typeInformation.prototypeBuilder(),
                           generatedArgs,
                           customMethod.javadoc()),
                annotations,
                codeGenerator);
    }

    private static boolean correctType(TypeName knownType, TypeName processingType) {
        // processing type may be for a generated class, which does not contain package information
        if (processingType.packageName().isEmpty()) {
            if (processingType.className().equals("<any>")) {
                // cannot be resolved as this is part of our round, good faith it is a correct parameter
                // this type name is used for types that are part of this round and that have a generic declaration
                // such as BuilderBase<?, ?>, also compilation will fail with a correct exception if the type is wrong
                // it will just fail on the generated class
                return true;
            }
            // the type name is known, but package could not be determined as the type is generated as part of this
            // annotation processing round - if the class name is correct, assume we have the right type
            return knownType.className().equals(processingType.className())
                    && knownType.enclosingNames().equals(processingType.enclosingNames());
        }
        return knownType.equals(processingType);
    }

    // static methods on prototype
    private static GeneratedMethod factoryMethod(Errors.Collector errors,
                                                 TypeContext.TypeInformation typeInformation,
                                                 TypeName customMethodsType,
                                                 List<String> annotations,
                                                 Method customMethod) {

        // if void: CustomMethodsType.methodName(param1, param2)
        // if returns: return CustomMethodsType.methodName(param1, param2)
        Consumer<ContentBuilder<?>> codeGenerator = contentBuilder -> {
            if (!customMethod.returnType().equals(TypeNames.PRIMITIVE_VOID)) {
                contentBuilder.addContent("return ");
            }
            contentBuilder.addContent(customMethodsType.genericTypeName())
                    .addContent(".")
                    .addContent(customMethod.name())
                    .addContent("(")
                    .addContent(customMethod.arguments().stream().map(Argument::name).collect(Collectors.joining(", ")))
                    .addContentLine(");");
        };

        // factory methods are just copied to the generated prototype
        return new GeneratedMethod(new Method(typeInformation.prototype(),
                                              customMethod.name(),
                                              customMethod.returnType(),
                                              customMethod.arguments(),
                                              customMethod.javadoc()),
                                   annotations,
                                   codeGenerator);
    }

    private static List<CustomConstant> findConstants(TypeInfo customMethodsType,
                                                      Errors.Collector errors) {
        return customMethodsType.elementInfo()
                .stream()
                .filter(ElementInfoPredicates::isField)
                .filter(ElementInfoPredicates.hasAnnotation(PROTOTYPE_CONSTANT))
                .map(it -> {
                    if (!it.elementModifiers().contains(Modifier.STATIC)) {
                        errors.fatal(it,
                                     "A field annotated with @Prototype.Constant must be static, final, "
                                             + "and at least package local. Field \"" + it.elementName() + "\" is not static.");
                    }
                    if (!it.elementModifiers().contains(Modifier.FINAL)) {
                        errors.fatal(it,
                                     "A field annotated with @Prototype.Constant must be static, final, "
                                             + "and at least package local. Field \"" + it.elementName() + "\" is not final.");
                    }
                    if (it.accessModifier() == AccessModifier.PRIVATE) {
                        errors.fatal(it,
                                     "A field annotated with @Prototype.Constant must be static, final, "
                                             + "and at least package local. Field \"" + it.elementName() + "\" is private.");
                    }
                    TypeName fieldType = it.typeName();
                    String name = it.elementName();
                    Javadoc javadoc = it.description()
                            .map(Javadoc::parse)
                            .orElseGet(() -> Javadoc.builder()
                                    .add(fieldType.equals(TypeNames.STRING)
                                                 ? "Constant for {@value}."
                                                 : "Code generated constant.")
                                    .build());

                    return new CustomConstant(customMethodsType.typeName(),
                                              fieldType,
                                              name,
                                              javadoc);
                })
                .toList();
    }

    private static List<CustomMethod> findMethods(TypeContext.TypeInformation typeInformation,
                                                  TypeInfo customMethodsType,
                                                  Errors.Collector errors,
                                                  TypeName requiredAnnotation,
                                                  MethodProcessor methodProcessor) {
        // all custom methods must be static
        // parameter and return type validation is to be done by method processor
        return customMethodsType.elementInfo()
                .stream()
                .filter(ElementInfoPredicates::isMethod)
                .filter(ElementInfoPredicates::isStatic)
                .filter(ElementInfoPredicates.hasAnnotation(requiredAnnotation))
                .map(it -> {
                    // return type
                    TypeName returnType = it.typeName();
                    // method name
                    String methodName = it.elementName();
                    // parameters
                    List<Argument> arguments = it.parameterArguments()
                            .stream()
                            .map(arg -> new Argument(arg.elementName(), arg.typeName()))
                            .toList();

                    // javadoc, if present
                    List<String> javadoc = it.description()
                            .map(String::trim)
                            .stream()
                            .filter(Predicate.not(String::isBlank))
                            .findAny()
                            .map(description -> description.split("\n"))
                            .map(List::of)
                            .orElseGet(List::of);

                    // annotations to be added to generated code
                    List<String> annotations = it.findAnnotation(Types.PROTOTYPE_ANNOTATED)
                            .flatMap(Annotation::stringValues)
                            .orElseGet(List::of)
                            .stream()
                            .map(String::trim) // to remove spaces after commas when used
                            .filter(Predicate.not(String::isBlank)) // we do not care about blank values
                            .toList();

                    Method customMethod = new Method(customMethodsType.typeName(), methodName, returnType, arguments, javadoc);

                    return new CustomMethod(customMethod,
                                            methodProcessor.process(errors,
                                                                    typeInformation,
                                                                    customMethodsType.typeName(),
                                                                    annotations,
                                                                    customMethod));
                })
                .toList();
    }

    interface MethodProcessor {
        GeneratedMethod process(Errors.Collector collector,
                                TypeContext.TypeInformation typeInformation,
                                TypeName customMethodsType,
                                List<String> annotations,
                                Method customMethod);
    }

    record CustomMethod(Method declaredMethod,
                        GeneratedMethod generatedMethod) {

    }

    record Method(TypeName declaringType,
                  String name,
                  TypeName returnType,
                  List<Argument> arguments,
                  List<String> javadoc) {

    }

    record GeneratedMethod(Method method,
                           List<String> annotations,
                           Consumer<ContentBuilder<?>> generateCode) {
    }

    record Argument(String name,
                    TypeName typeName) {

    }
}
