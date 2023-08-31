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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import io.helidon.common.Errors;
import io.helidon.common.processor.ElementInfoPredicates;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;

import static io.helidon.builder.processor.Types.BUILDER_CUSTOM_METHOD_TYPE;
import static io.helidon.builder.processor.Types.CUSTOM_METHODS_TYPE;
import static io.helidon.builder.processor.Types.FACTORY_METHOD_TYPE;
import static io.helidon.builder.processor.Types.PROTOTYPE_CUSTOM_METHOD_TYPE;
import static io.helidon.builder.processor.Types.VOID_TYPE;
import static io.helidon.common.processor.classmodel.ClassModel.TYPE_TOKEN;

record CustomMethods(List<CustomMethod> factoryMethods,
                     List<CustomMethod> builderMethods,
                     List<CustomMethod> prototypeMethods) {

    CustomMethods() {
        this(List.of(), List.of(), List.of());
    }

    static CustomMethods create(ProcessingContext ctx, TypeContext.TypeInformation typeInformation) {
        Optional<Annotation> annotation = typeInformation.blueprintType().findAnnotation(CUSTOM_METHODS_TYPE);
        if (annotation.isEmpty()) {
            return new CustomMethods();
        }
        // value is mandatory for this annotation
        String customMethodType = annotation.get().value().orElseThrow();
        // we must get the type info, as otherwise this is an invalid declaration
        TypeInfo customMethodsInfo = ctx.toTypeInfo(TypeName.create(customMethodType))
                .orElseThrow();

        Errors.Collector errors = Errors.collector();
        List<CustomMethod> factoryMethods = findMethods(typeInformation,
                                                        customMethodsInfo,
                                                        errors,
                                                        FACTORY_METHOD_TYPE,
                                                        CustomMethods::factoryMethod);
        List<CustomMethod> builderMethods = findMethods(typeInformation,
                                                        customMethodsInfo,
                                                        errors,
                                                        BUILDER_CUSTOM_METHOD_TYPE,
                                                        CustomMethods::builderMethod);
        List<CustomMethod> prototypeMethods = findMethods(typeInformation,
                                                          customMethodsInfo,
                                                          errors,
                                                          PROTOTYPE_CUSTOM_METHOD_TYPE,
                                                          CustomMethods::prototypeMethod);

        errors.collect().checkValid();
        return new CustomMethods(factoryMethods, builderMethods, prototypeMethods);
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
        } else if (!correctType(typeInformation.prototype(), customMethodArgs.get(0).typeName())) {
            errors.fatal(customMethodsType.fqName(),
                         "Methods annotated with @Prototype.PrototypeMethod must accept the prototype "
                                 + "as the first parameter, but method: " + customMethod.name()
                                 + " expected: " + typeInformation.prototypeBuilder().fqName()
                                 + " actual: " + customMethodArgs.get(0).typeName().fqName());
        }
        List<Argument> generatedArgs = customMethodArgs.subList(1, customMethodArgs.size());
        List<String> argumentNames = new ArrayList<>();
        argumentNames.add("this");
        argumentNames.addAll(generatedArgs.stream()
                                     .map(Argument::name)
                                     .toList());

        // return CustomMethodsType.methodName(this, param1, param2)
        String generatedCall =
                VOID_TYPE.equals(customMethod.returnType) ? "" : "return "
                        + TYPE_TOKEN
                        + customMethodsType.genericTypeName().fqName()
                        + TYPE_TOKEN
                        + "."
                        + customMethod.name()
                        + "("
                        + String.join(", ", argumentNames)
                        + ")";

        return new GeneratedMethod(
                new Method(typeInformation.prototypeBuilder(),
                           customMethod.name(),
                           customMethod.returnType(),
                           generatedArgs,
                           // todo the javadoc may differ (such as when we have an additional parameter for instance methods)
                           customMethod.javadoc()),
                annotations,
                generatedCall);
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
        } else if (!correctType(typeInformation.prototypeBuilderBase(), customMethodArgs.get(0).typeName().genericTypeName())) {
            errors.fatal(customMethodsType.fqName(),
                         "Methods annotated with @Prototype.BuilderMethod must accept the prototype builder "
                                 + "base as the first parameter, but method: " + customMethod.name()
                                 + " expected: " + typeInformation.prototypeBuilderBase().fqName()
                                 + " actual: " + customMethodArgs.get(0).typeName().fqName());
        }

        List<Argument> generatedArgs = customMethodArgs.subList(1, customMethodArgs.size());
        List<String> argumentNames = new ArrayList<>();
        argumentNames.add("this");
        argumentNames.addAll(generatedArgs.stream()
                                     .map(Argument::name)
                                     .toList());

        // return CustomMethodsType.methodName(this, param1, param2)
        String generatedCall = TYPE_TOKEN
                + customMethodsType.genericTypeName().fqName()
                + TYPE_TOKEN
                + "."
                + customMethod.name()
                + "("
                + String.join(", ", argumentNames)
                + ");"
                + "\nreturn self()";

        return new GeneratedMethod(
                new Method(typeInformation.prototypeBuilder(),
                           customMethod.name(),
                           typeInformation.prototypeBuilder(),
                           generatedArgs,
                           customMethod.javadoc()),
                annotations,
                generatedCall);
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
        String generatedCall =
                VOID_TYPE.equals(customMethod.returnType) ? "" : "return "
                        + TYPE_TOKEN
                        + customMethodsType.genericTypeName().fqName()
                        + TYPE_TOKEN
                        + "."
                        + customMethod.name()
                        + "("
                        + customMethod.arguments().stream().map(Argument::name).collect(Collectors.joining(", "))
                        + ")";

        // factory methods are just copied to the generated prototype
        return new GeneratedMethod(new Method(typeInformation.prototype(),
                                              customMethod.name(),
                                              customMethod.returnType(),
                                              customMethod.arguments(),
                                              customMethod.javadoc()),
                                   annotations,
                                   generatedCall);
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
                    List<String> annotations = it.findAnnotation(Types.PROTOTYPE_ANNOTATED_TYPE)
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
                           String callCode) {

    }

    record Argument(String name,
                    TypeName typeName) {

    }
}
