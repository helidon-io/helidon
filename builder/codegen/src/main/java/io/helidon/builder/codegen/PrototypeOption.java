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

package io.helidon.builder.codegen;

import java.net.URI;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

import io.helidon.codegen.CodegenContext;
import io.helidon.codegen.CodegenException;
import io.helidon.codegen.CodegenValidator;
import io.helidon.codegen.ElementInfoPredicates;
import io.helidon.codegen.RoundContext;
import io.helidon.codegen.classmodel.ContentBuilder;
import io.helidon.codegen.classmodel.Javadoc;
import io.helidon.common.Errors;
import io.helidon.common.Size;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.ElementSignature;
import io.helidon.common.types.Modifier;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;

import static io.helidon.builder.codegen.Types.ARRAY_LIST;
import static io.helidon.builder.codegen.Types.BUILDER_DESCRIPTION;
import static io.helidon.builder.codegen.Types.DEPRECATED;
import static io.helidon.builder.codegen.Types.LINKED_HASH_MAP;
import static io.helidon.builder.codegen.Types.LINKED_HASH_SET;
import static io.helidon.builder.codegen.Types.OPTION_ACCESS;
import static io.helidon.builder.codegen.Types.OPTION_ALLOWED_VALUES;
import static io.helidon.builder.codegen.Types.OPTION_CONFIGURED;
import static io.helidon.builder.codegen.Types.OPTION_DEFAULT;
import static io.helidon.builder.codegen.Types.OPTION_DEFAULT_BOOLEAN;
import static io.helidon.builder.codegen.Types.OPTION_DEFAULT_CODE;
import static io.helidon.builder.codegen.Types.OPTION_DEFAULT_DOUBLE;
import static io.helidon.builder.codegen.Types.OPTION_DEFAULT_INT;
import static io.helidon.builder.codegen.Types.OPTION_DEFAULT_LONG;
import static io.helidon.builder.codegen.Types.OPTION_DEFAULT_METHOD;
import static io.helidon.builder.codegen.Types.OPTION_DEPRECATED;
import static io.helidon.builder.codegen.Types.OPTION_PROVIDER;
import static io.helidon.builder.codegen.Types.OPTION_REQUIRED;
import static io.helidon.builder.codegen.Types.OPTION_SINGULAR;
import static io.helidon.builder.codegen.Types.OPTION_TRAVERSE_CONFIG;
import static io.helidon.builder.codegen.Types.PROTOTYPE_API;
import static io.helidon.builder.codegen.Types.PROTOTYPE_FACTORY;
import static io.helidon.builder.codegen.ValidationTask.doesImplement;
import static io.helidon.codegen.CodegenUtil.capitalize;
import static io.helidon.codegen.ElementInfoPredicates.elementName;
import static io.helidon.common.types.TypeNames.OBJECT;
import static java.util.function.Predicate.not;

// builder option
final class PrototypeOption {
    private static final Set<String> IGNORED_NAMES = Set.of("build",
                                                            "get",
                                                            "buildPrototype");
    private static final Set<ElementSignature> IGNORED_METHODS = Set.of(
            // equals, hash code and toString
            ElementSignature.createMethod(TypeName.create(boolean.class), "equals", List.of(OBJECT)),
            ElementSignature.createMethod(TypeName.create(int.class), "hashCode", List.of()),
            ElementSignature.createMethod(TypeNames.STRING, "toString", List.of())
    );

    // cannot be identifiers - such as field name or method name
    private static final Set<String> RESERVED_WORDS = Set.of(
            "abstract", "assert", "boolean", "break",
            "byte", "case", "catch", "char",
            "class", "const", "continue", "default",
            "do", "double", "else", "enum",
            "extends", "final", "finally", "float",
            "for", "goto", "if", "implements",
            "import", "instanceof", "int", "interface",
            "long", "native", "new", "package",
            "private", "protected", "public", "return",
            "short", "static", "super", "switch",
            "synchronized", "this", "throw", "throws",
            "transient", "try", "void", "volatile",
            "while", "true", "false", "null"
    );

    private PrototypeOption() {
    }

    static void options(CodegenContext ctx,
                        RoundContext roundContext,
                        PrototypeInfo prototypeInfo,
                        List<OptionInfo> newOptions,
                        List<OptionInfo> existingOptions) {

        // if this has a super prototype, we first analyze it to collect all options that are already taken care of
        Set<TypeName> alreadyHandledInterfaces = new HashSet<>();
        alreadyHandledInterfaces.add(TypeNames.SUPPLIER); // we are not interested in this method ever

        List<TypeInfo> typeScope = new ArrayList<>();
        prototypeInfo.superPrototype()
                .ifPresent(superPrototype -> {
                    TypeInfo superPrototypeInfo = roundContext.typeInfo(superPrototype)
                            .or(() -> roundContext.typeInfo(TypeName.builder(superPrototype)
                                                                    .className(superPrototype.className() + "Blueprint")
                                                                    .build()))
                            .get();
                    allRelevantInterfaces(superPrototypeInfo,
                                          typeScope,
                                          alreadyHandledInterfaces);

                    discoverOptions(ctx, roundContext, prototypeInfo, existingOptions, typeScope);
                });
        typeScope.clear();
        allRelevantInterfaces(prototypeInfo.blueprint(),
                              typeScope,
                              alreadyHandledInterfaces);

        // typeScope now contains only interfaces that are not covered by the super prototype
        // these are the option we want to add to the prototype, everything else is inherited
        // there may be duplicates - but we expect that, as you may want to change default, add annotations etc.
        discoverOptions(ctx, roundContext, prototypeInfo, newOptions, typeScope);

    }

    /*
    Find all interfaces that the provided baseType extends (we assume the baseType is an interface).
     */
    static void allRelevantInterfaces(TypeInfo baseType, List<TypeInfo> result, Set<TypeName> toIgnore) {
        // if the base type extends Prototype.Api, ignore it
        if (validInterface(baseType)) {
            result.add(baseType);
        }

        List<TypeInfo> typeInfos = baseType.interfaceTypeInfo();
        for (TypeInfo typeInfo : typeInfos) {
            if (toIgnore.add(typeInfo.typeName())) {
                allRelevantInterfaces(typeInfo, result, toIgnore);
            }
        }
    }

    private static boolean validInterface(TypeInfo baseType) {
        if (doesImplement(baseType, PROTOTYPE_API)) {
            return false;
        }
        TypeName typeName = baseType.typeName().genericTypeName();
        if (typeName.isSupplier()) {
            return false;
        }
        if (typeName.equals(PROTOTYPE_FACTORY)) {
            return false;
        }
        return true;
    }

    private static void discoverOptions(CodegenContext ctx,
                                        RoundContext roundContext,
                                        PrototypeInfo prototypeInfo,
                                        List<OptionInfo> options,
                                        List<TypeInfo> typeScope) {

        for (TypeInfo typeInfo : typeScope) {
            discoverOptions(ctx, roundContext, prototypeInfo, options, typeInfo);
        }
    }

    private static void discoverOptions(CodegenContext ctx,
                                        RoundContext roundContext,
                                        PrototypeInfo prototypeInfo,
                                        List<OptionInfo> options,
                                        TypeInfo typeInfo) {
        List<TypedElementInfo> candidates = optionCandidates(ctx, prototypeInfo, typeInfo);

        // all candidates are valid!
        for (TypedElementInfo candidate : candidates) {
            options.add(create(roundContext, prototypeInfo, typeInfo, candidate));
        }
    }

    private static List<TypedElementInfo> optionCandidates(CodegenContext ctx,
                                                           PrototypeInfo prototypeInfo,
                                                           TypeInfo typeInfo) {
        var candidates = typeInfo.elementInfo()
                .stream()
                .filter(ElementInfoPredicates::isMethod) // only methods, constants are ignored
                .filter(not(ElementInfoPredicates::isStatic)) // static factory methods are ignored
                .filter(not(ElementInfoPredicates::isPrivate)) // private interface methods are ignored
                .filter(it -> validOptionMethod(prototypeInfo.defaultMethodsPredicate(), it))
                .filter(PrototypeOption::validOptionMethodName)
                .toList();

        // we must validate - any candidate that is void, or has parameters is a bad candidate
        var collector = Errors.collector();
        for (TypedElementInfo candidate : candidates) {
            if (candidate.typeName().equals(TypeNames.PRIMITIVE_VOID)) {
                collector.warn("Builder definition methods cannot have void return type "
                                       + "(must be getters): "
                                       + typeInfo.typeName().fqName() + "." + candidate.elementName());
            }
            if (!candidate.parameterArguments().isEmpty()) {
                collector.warn("Builder definition methods cannot have parameters "
                                       + "(must be getters): "
                                       + typeInfo.typeName().fqName() + "." + candidate.elementName());
            }
        }

        var logger = ctx.logger();
        collector.collect()
                .forEach(errorMessage -> {
                    System.Logger.Level level = switch (errorMessage.getSeverity()) {
                        case FATAL -> System.Logger.Level.ERROR;
                        case WARN -> System.Logger.Level.WARNING;
                        case HINT -> System.Logger.Level.INFO;
                    };
                    logger.log(level, errorMessage.getMessage());
                });

        return candidates;
    }

    private static OptionInfo create(RoundContext roundContext,
                                     PrototypeInfo prototypeInfo,
                                     TypeInfo typeInfo,
                                     TypedElementInfo candidate) {
        boolean recordStyle = prototypeInfo.recordStyle();

        OptionInfo.Builder option = OptionInfo.builder();
        TypeName type = propertyTypeName(candidate);
        String maybeReservedName = propertyName(candidate.elementName(),
                                                type.boxed().equals(TypeNames.BOXED_BOOLEAN),
                                                recordStyle);

        String getterName = candidate.elementName();
        String setterName = setterName(maybeReservedName, recordStyle);
        String name = optionName(maybeReservedName);
        boolean sameGeneric = candidate.hasAnnotation(Types.OPTION_SAME_GENERIC);
        boolean confidential = candidate.hasAnnotation(Types.OPTION_CONFIDENTIAL);

        Optional<Annotation> redundantAnnotation = candidate.findAnnotation(Types.OPTION_REDUNDANT);
        boolean toStringValue = !redundantAnnotation.flatMap(it -> it.getValue("stringValue"))
                .map(Boolean::parseBoolean)
                .orElse(false);
        boolean equality = !redundantAnnotation.flatMap(it -> it.getValue("equality"))
                .map(Boolean::parseBoolean)
                .orElse(false);
        boolean registryService = candidate.hasAnnotation(Types.OPTION_REGISTRY_SERVICE);
        var qualifiers = candidate.annotations()
                .stream()
                .filter(a -> a.hasMetaAnnotation(Types.SERVICE_QUALIFIER))
                .toList();

        option.blueprintMethod(candidate)
                .name(name)
                .declaredType(type)
                .includeInEqualsAndHashCode(equality)
                .includeInToString(toStringValue)
                .confidential(confidential)
                .registryService(registryService)
                .sameGeneric(sameGeneric)
                .qualifiers(qualifiers);

        optionBuilder(roundContext, type).ifPresent(option::builderInfo);

        candidate.findAnnotation(Types.OPTION_DECORATOR)
                .flatMap(Annotation::typeValue)
                .ifPresent(option::decorator);

        var accessModifier = candidate.findAnnotation(OPTION_ACCESS)
                .flatMap(Annotation::stringValue)
                .map(it -> it.isBlank() ? AccessModifier.PACKAGE_PRIVATE : AccessModifier.valueOf(it))
                .orElse(prototypeInfo.builderAccessModifier());
        option.accessModifier(accessModifier);

        // allowedValues
        if (candidate.hasAnnotation(OPTION_ALLOWED_VALUES)) {
            Annotation annotation = candidate.annotation(OPTION_ALLOWED_VALUES);
            annotation.annotationValues()
                    .orElseGet(List::of)
                    .stream()
                    .forEach(it -> {
                        String value = it.stringValue().orElseThrow();
                        String description = it.stringValue("description").orElseThrow();
                        option.addAllowedValue(av -> av.value(value)
                                .description(description));
                    });
        }

        Javadoc javadoc = optionJavadoc(candidate, name);
        // configured
        addConfiguredOptionData(option, candidate, type, name);
        // deprecation
        addDeprecatedOptionData(option, candidate, javadoc);
        if (option.deprecation().isPresent()) {
            javadoc = updateWithDeprecation(javadoc, option.deprecation().get());
        }
        // provider
        addProviderOptionData(option, candidate);
        // default value
        addDefaultValue(option, type, typeInfo.typeName(), candidate);
        // required
        isOptionRequired(option, candidate, type);

        // now we can build the option info fully
        setters(option, javadoc, getterName, type, name, setterName);

        // singular
        addSingularOptionData(prototypeInfo, option, candidate, type, name, javadoc, getterName);

        addGetter(prototypeInfo, option, candidate);
        addBuilderGetter(prototypeInfo, option, candidate);
        addImplGetter(prototypeInfo, option, candidate);

        return option.build();
    }

    private static Optional<OptionBuilder> optionBuilder(RoundContext roundContext, TypeName type) {
        /*
        the type of interest - T below
        Map<K, T>
        Supplier<T>
        Optional<T>
        List<T>
        Set<T>
        T
         */
        TypeName actualType;
        if (type.isOptional() || type.isSupplier() || type.isSet() || type.isList()) {
            actualType = type.typeArguments().getFirst();
        } else if (type.isMap()) {
            actualType = type.typeArguments().get(1);
        } else {
            actualType = type;
        }

        /*
        We have a type, i.e. Option - now we are interested in
        Option.builder() // method name
        Option.Builder // builder class
        Option.Builder.build() // build method
         */
        if (actualType.equals(OBJECT)
                || actualType.unboxed().primitive()
                || actualType.generic()
                || actualType.array()
                || actualType.equals(TypeNames.STRING)) {
            // cannot build these for sure
            return Optional.empty();
        }

        // let's try to find the type and discover everything
        var typeInfo = roundContext.typeInfo(actualType);
        if (typeInfo.isPresent()) {
            // find builder method or builder constructor
            return optionBuilder(roundContext, actualType, typeInfo.get());
        } else {
            // assume this is a type built as part of this codegen round and is a prototype
            return optionBuilderGuessed(actualType);
        }
    }

    private static Optional<OptionBuilder> optionBuilder(RoundContext ctx,
                                                         TypeName actualType,
                                                         TypeInfo actualTypeInfo) {
        return actualTypeInfo.elementInfo()
                .stream()
                .filter(ElementInfoPredicates::isMethod)
                .filter(ElementInfoPredicates::isStatic)
                .filter(elementName("builder"))
                .filter(ElementInfoPredicates::hasNoArgs)
                // Has to have public no-param build method returning right type
                .filter(it -> ctx.typeInfo(it.typeName()).stream()
                        .flatMap(builderTypeInfo -> builderTypeInfo.elementInfo().stream())
                        .filter(ElementInfoPredicates::isMethod)
                        .filter(ElementInfoPredicates::isPublic)
                        .filter(ElementInfoPredicates::hasNoArgs)
                        .filter(m -> m.typeName().equals(actualType)
                                || (actualType.packageName().isBlank() && m.typeName().className().equals(actualType.className())))
                        .anyMatch(elementName("build")))
                .findFirst()
                .map(it -> OptionBuilder.builder()
                        .buildMethodName("build")
                        .builderMethodName(it.elementName())
                        .builderType(it.typeName())
                        .build());
    }

    private static Optional<OptionBuilder> optionBuilderGuessed(TypeName actualType) {
        TypeName builderType = TypeName.builder(actualType)
                .className("Builder")
                .addEnclosingName(actualType.className())
                .build();
        return Optional.of(OptionBuilder.builder()
                                   .builderMethodName("builder")
                                   .builderType(builderType)
                                   .buildMethodName("build")
                                   .build());
    }

    private static void addGetter(PrototypeInfo prototypeInfo, OptionInfo.Builder option, TypedElementInfo candidate) {
        // TODO not used for now
        TypedElementInfo builderGetter = TypedElementInfo.builder()
                .accessModifier(prototypeInfo.builderAccessModifier())
                .typeName(candidate.typeName()) // this must be an option for required types and types without defaults
                .elementName(candidate.elementName())
                .kind(ElementKind.METHOD)
                .description(candidate.description().orElse(""))
                .build();
        option.getter(builderGetter);
    }

    private static void addImplGetter(PrototypeInfo prototypeInfo, OptionInfo.Builder option, TypedElementInfo candidate) {
        // TODO not used for now
        TypedElementInfo builderGetter = TypedElementInfo.builder()
                .accessModifier(prototypeInfo.builderAccessModifier())
                .typeName(candidate.typeName()) // this must be an option for required types and types without defaults
                .elementName(candidate.elementName())
                .kind(ElementKind.METHOD)
                .description(candidate.description().orElse(""))
                .build();
        option.implGetter(builderGetter);
    }

    private static void addBuilderGetter(PrototypeInfo prototypeInfo, OptionInfo.Builder option, TypedElementInfo candidate) {
        // TODO not used for now
        TypedElementInfo builderGetter = TypedElementInfo.builder()
                .accessModifier(prototypeInfo.builderAccessModifier())
                .typeName(candidate.typeName()) // this must be an option for required types and types without defaults
                .elementName(candidate.elementName())
                .kind(ElementKind.METHOD)
                .description(candidate.description().orElse(""))
                .build();
        option.builderGetter(builderGetter);
    }

    private static void addSingularOptionData(PrototypeInfo prototypeInfo,
                                              OptionInfo.Builder option,
                                              TypedElementInfo element,
                                              TypeName returnType,
                                              String name,
                                              Javadoc getterJavadoc,
                                              String getterName) {
        if (element.hasAnnotation(OPTION_SINGULAR)) {
            var singularAnnot = element.annotation(OPTION_SINGULAR);
            String singularName = singularAnnot.value()
                    .filter(not(String::isBlank))
                    .orElseGet(() -> singularName(name));
            boolean singularAddPrefix = singularAnnot.booleanValue("withPrefix")
                    .orElse(true);

            String singularSetterName;
            if (singularAddPrefix) {
                String prefix = returnType.isMap() ? "put" : "add";
                singularSetterName = prefix + capitalize(singularName);
            } else {
                singularSetterName = singularName;
            }

            List<TypedElementInfo> parameterArguments = singularParameters(element, propertyTypeName(element), singularName);

            TypedElementInfo singularSetter = TypedElementInfo.builder()
                    .accessModifier(prototypeInfo.builderAccessModifier())
                    .typeName(TypeName.createFromGenericDeclaration("BUILDER"))
                    .elementName(singularSetterName)
                    .kind(ElementKind.METHOD)
                    .parameterArguments(parameterArguments)
                    .description(String.join("\n",
                                             singularSetterJavadoc(getterJavadoc, singularName, getterName).toString()))
                    .build();

            option.singular(singular -> singular
                    .name(singularName)
                    .setter(singularSetterName));
        }
    }

    private static String optionName(String maybeReservedName) {
        if (RESERVED_WORDS.contains(maybeReservedName)) {
            return "the" + capitalize(maybeReservedName);
        } else {
            return maybeReservedName;
        }
    }

    private static void setters(OptionInfo.Builder option,
                                Javadoc getterJavadoc,
                                String getterName,
                                TypeName optionType,
                                String optionName,
                                String setterName) {

        // the following methods are not configurable and will be generated as we see fit
        // for lists and sets - add `addValues(List<> values)` as well (always)
        // and add(Consumer<Builder>) for builders
        // and set(Consumer<Builder>)
        TypedElementInfo declaredSetter = TypedElementInfo.builder()
                .accessModifier(option.accessModifier())
                .typeName(TypeName.createFromGenericDeclaration("BUILDER"))
                .elementName(setterName)
                .kind(ElementKind.METHOD)
                .addParameterArgument(param -> param.elementName(optionName)
                        .typeName(toSetterType(optionType))
                        .kind(ElementKind.PARAMETER))
                .description(String.join("\n", setterJavadoc(getterJavadoc, optionName, getterName).toString()))
                .build();

        if (optionType.isOptional()) {
            option.setterForOptional(declaredSetter);
            option.setter(TypedElementInfo.builder()
                                  .accessModifier(option.accessModifier())
                                  .typeName(TypeName.createFromGenericDeclaration("BUILDER"))
                                  .elementName(setterName)
                                  .kind(ElementKind.METHOD)
                                  .addParameterArgument(param -> param.elementName(optionName)
                                          .typeName(optionType.typeArguments().getFirst())
                                          .kind(ElementKind.PARAMETER))
                                  .description(String.join("\n",
                                                           setterJavadoc(getterJavadoc, optionName, getterName).toString()))
                                  .build());
        } else {
            option.setter(declaredSetter);
        }
    }

    private static List<TypedElementInfo> singularParameters(TypedElementInfo candidate,
                                                             TypeName optionType,
                                                             String singularName) {
        if (optionType.isSet() || optionType.isList()) {
            TypeName typeName = optionType.typeArguments().getFirst();
            return List.of(TypedElementInfo.builder()
                                   .kind(ElementKind.PARAMETER)
                                   .elementName(singularName)
                                   .typeName(toSetterType(typeName))
                                   .build());
        }
        if (optionType.isMap()) {
            return List.of(TypedElementInfo.builder()
                                   .kind(ElementKind.PARAMETER)
                                   .elementName("key")
                                   .typeName(toSetterType(optionType.typeArguments().get(0)))
                                   .build(),
                           TypedElementInfo.builder()
                                   .kind(ElementKind.PARAMETER)
                                   .elementName(singularName)
                                   .typeName(toSetterType(optionType.typeArguments().get(1)))
                                   .build());
        }
        throw new CodegenException("@Option.Singular annotation on invalid option type, only Set, List, and Map are supported",
                                   candidate);
    }

    private static TypeName toSetterType(TypeName optionType) {
        if (optionType.isOptional() || optionType.isSet() || optionType.isList()) {
            TypeName argument = optionType.typeArguments().getFirst();
            if (argument.unboxed().primitive() || argument.equals(TypeNames.STRING)) {
                return optionType;
            }
            TypeName typeArgument = TypeName.builder()
                    .className("?")
                    .generic(true)
                    .addUpperBound(argument)
                    .build();
            return TypeName.builder(optionType)
                    .typeArguments(List.of(typeArgument))
                    .build();
        }
        return optionType;
    }

    private static boolean validOptionMethodName(TypedElementInfo element) {
        if (IGNORED_NAMES.contains(element.elementName())) {
            return false;
        }
        return !IGNORED_METHODS.contains(element.signature());
    }

    private static boolean validOptionMethod(Predicate<String> defaultMethods, TypedElementInfo element) {
        if (element.elementModifiers().contains(Modifier.DEFAULT)) {
            // default methods are OK only if allowed by the blueprint
            if (element.typeName().equals(TypeNames.PRIMITIVE_BOOLEAN)) {
                // void default methods can never be options
                return false;
            }
            if (element.parameterArguments().isEmpty()) {
                return defaultMethods.test(element.elementName());
            }
            // default methods with parameters can never be options
            return false;
        }
        // abstract methods are always OK
        return true;
    }

    private static void addDefaultValue(OptionInfo.Builder option,
                                        TypeName returnType,
                                        TypeName enclosingType,
                                        TypedElementInfo element) {
        // type is either single value, list/set, or map
        OptionType optionType = OptionType.create(returnType);
        TypeName realType = realType(returnType);

        if (element.hasAnnotation(OPTION_DEFAULT)) {
            List<String> defaults = element.annotation(OPTION_DEFAULT)
                    .stringValues()
                    .orElseGet(List::of);

            option.defaultValue(optionType.toDefaultString(enclosingType, element, realType, defaults));
            return;
        }
        if (element.hasAnnotation(OPTION_DEFAULT_INT)) {
            List<Integer> defaults = element.annotation(OPTION_DEFAULT_INT)
                    .intValues()
                    .orElseGet(List::of);
            option.defaultValue(optionType.toDefault(enclosingType, element, realType, defaults));
            return;
        }
        if (element.hasAnnotation(OPTION_DEFAULT_LONG)) {
            List<Long> defaults = element.annotation(OPTION_DEFAULT_LONG)
                    .longValues()
                    .orElseGet(List::of);
            option.defaultValue(optionType.toDefaultLongs(enclosingType, element, realType, defaults));
            return;
        }
        if (element.hasAnnotation(OPTION_DEFAULT_DOUBLE)) {
            List<Double> defaults = element.annotation(OPTION_DEFAULT_DOUBLE)
                    .doubleValues()
                    .orElseGet(List::of);
            option.defaultValue(optionType.toDefaultDoubles(enclosingType, element, realType, defaults));
            return;
        }
        if (element.hasAnnotation(OPTION_DEFAULT_BOOLEAN)) {
            List<Boolean> defaults = element.annotation(OPTION_DEFAULT_BOOLEAN)
                    .booleanValues()
                    .orElseGet(List::of);
            option.defaultValue(optionType.toDefault(enclosingType, element, realType, defaults));
            return;
        }
        if (element.hasAnnotation(OPTION_DEFAULT_METHOD)) {
            Annotation annotation = element.annotation(OPTION_DEFAULT_METHOD);
            TypeName type = annotation
                    .typeValue()
                    .filter(not(OPTION_DEFAULT_METHOD::equals))
                    .orElseGet(element::typeName);
            String name = annotation.stringValue().orElseThrow();

            option.defaultValue(it -> it.addContent(type.genericTypeName())
                    .addContent(".")
                    .addContent(name)
                    .addContent("()"));
            return;
        }
        if (element.hasAnnotation(OPTION_DEFAULT_CODE)) {
            var defaultCode = element.annotation(OPTION_DEFAULT_CODE).stringValue().orElseThrow();
            option.defaultValue(it -> it.addContent(defaultCode));
        }
    }

    private static TypeName realType(TypeName typeName) {
        if (typeName.isOptional() || typeName.isSet() || typeName.isList()) {
            return realType(typeName.typeArguments().getFirst());
        }
        if (typeName.isMap()) {
            if (typeName.typeArguments().size() == 2) {
                return typeName.typeArguments().get(1);
            }
            return TypeNames.STRING;
        }
        return typeName;
    }

    private static void isOptionRequired(OptionInfo.Builder option, TypedElementInfo element, TypeName type) {
        boolean required = false;

        if (element.hasAnnotation(OPTION_REQUIRED)) {
            required = true;
        } else if (type.array()) {
            required = true;
        } else {
            // neither of these types can be set to null in the builder due to how it is generated, so no need to validate
            if (!type.isOptional() && !type.isMap() && !type.isSet() && !type.isList()) {
                required = !type.primitive();
            }
        }
        if (option.defaultValue().isPresent() && required) {
            required = false;
        }
        option.required(required);;
    }

    private static Javadoc singularSetterJavadoc(Javadoc getterJavadoc, String singularName, String getterName) {
        return Javadoc.builder(getterJavadoc)
                .addParameter(singularName, getterJavadoc.returnDescription())
                .returnDescription("updated builder instance")
                .addTag("see", "#" + getterName + "()")
                .build();
    }

    private static Javadoc setterJavadoc(Javadoc getterJavadoc, String name, String getterName) {
        return Javadoc.builder(getterJavadoc)
                .addParameter(name, getterJavadoc.returnDescription())
                .returnDescription("updated builder instance")
                .addTag("see", "#" + getterName + "()")
                .build();
    }

    private static Javadoc updateWithDeprecation(Javadoc javadoc, OptionDeprecation optionDeprecation) {
        if (optionDeprecation.alternative().isPresent()) {
            return Javadoc.builder(javadoc)
                    .deprecation("use {@link #" + optionDeprecation.alternative().get() + "()} instead")
                    .build();
        }
        return Javadoc.builder(javadoc)
                .deprecation(optionDeprecation.message())
                .build();
    }

    private static String singularName(String optionName) {
        if (optionName.endsWith("s")) {
            return optionName.substring(0, optionName.length() - 1);
        }
        return optionName;
    }

    private static void addProviderOptionData(OptionInfo.Builder option, TypedElementInfo element) {
        if (element.hasAnnotation(OPTION_PROVIDER)) {
            Annotation annotation = element.annotation(OPTION_PROVIDER);

            option.provider(provider -> provider
                    .providerType(annotation.typeValue().orElseThrow())
                    .discoverServices(annotation.booleanValue("discoverServices").orElse(true))
            );
        }
    }

    private static void addConfiguredOptionData(OptionInfo.Builder option,
                                                TypedElementInfo element,
                                                TypeName returnType,
                                                String name) {
        if (element.hasAnnotation(OPTION_CONFIGURED)) {
            Annotation annotation = element.annotation(OPTION_CONFIGURED);
            String configKey = annotation.stringValue()
                    .filter(not(String::isBlank))
                    .orElseGet(() -> toConfigKey(name));
            boolean merge = annotation.booleanValue("merge")
                    .orElse(false);
            boolean traverse = element.findAnnotation(OPTION_TRAVERSE_CONFIG)
                    .flatMap(Annotation::booleanValue)
                    .orElseGet(() -> traverseByDefault(returnType));
            option.configured(configured -> configured
                    .configKey(configKey)
                    .merge(merge)
                    .traverse(traverse)
                    .build());
        }
    }

    private static boolean traverseByDefault(TypeName typeName) {
        if (typeName.isMap()) {
            TypeName valueTypeName = typeName.typeArguments().get(1);
            return valueTypeName.equals(TypeNames.STRING)
                    || valueTypeName.unboxed().primitive();
        } else {
            return false;
        }
    }

    /*
    Method name is camel case (such as maxInitialLineLength)
    result is kebab-case (such as max-initial-line-length).
    Note that this same method was created in ConfigUtils in common-config, but since this
    module should not have any dependencies in it a copy was left here as well.
    */
    private static String toConfigKey(String name) {
        StringBuilder result = new StringBuilder();

        char[] chars = name.toCharArray();
        for (char aChar : chars) {
            if (Character.isUpperCase(aChar)) {
                if (result.isEmpty()) {
                    result.append(Character.toLowerCase(aChar));
                } else {
                    result.append('-')
                            .append(Character.toLowerCase(aChar));
                }
            } else {
                result.append(aChar);
            }
        }

        return result.toString();
    }

    private static void addDeprecatedOptionData(OptionInfo.Builder option, TypedElementInfo element, Javadoc javadoc) {
        boolean deprecated = false;
        boolean forRemoval = false;
        String since = null;
        String alternative = null;
        List<String> description = javadoc.deprecation();

        if (element.hasAnnotation(DEPRECATED)) {
            deprecated = true;
            Annotation annotation = element.annotation(DEPRECATED);
            forRemoval = annotation.booleanValue("forRemoval").orElse(false);
            since = annotation.stringValue("since").filter(not(String::isBlank)).orElse(null);
        }

        if (element.hasAnnotation(OPTION_DEPRECATED)) {
            deprecated = true;
            // alternative overrides description, and it is a required property
            alternative = element.annotation(OPTION_DEPRECATED)
                    .value()
                    .orElse(null);
            description = null;
        }

        if (!deprecated) {
            return;
        }
        var deprecation = OptionDeprecation.builder()
                .forRemoval(forRemoval);

        if (since != null) {
            deprecation.since(since);
        }
        if (description == null) {
            deprecation.message("This option is deprecated");
        } else {
            deprecation.message(String.join("\n", description));
        }
        if (alternative != null) {
            deprecation.alternative(alternative);
        }

        option.deprecation(deprecation.build());

        io.helidon.common.types.Annotation.Builder deprecatedAnnotation = io.helidon.common.types.Annotation.builder()
                .typeName(DEPRECATED);
        if (since != null) {
            deprecatedAnnotation.putValue("since", since);
        }
        if (forRemoval) {
            deprecatedAnnotation.putValue("forRemoval", true);
        }

        option.addAnnotation(deprecatedAnnotation.build());
    }

    private static Javadoc optionJavadoc(TypedElementInfo element, String optionName) {
        if (element.hasAnnotation(BUILDER_DESCRIPTION)) {
            return Javadoc.parse(element.annotation(BUILDER_DESCRIPTION).stringValue().orElseThrow());
        }
        if (element.description().isPresent()) {
            return Javadoc.parse(element.description().get());
        }
        return Javadoc.builder()
                .addLine("Option " + optionName)
                .returnDescription(optionName)
                .build();
    }

    private static TypeName propertyTypeName(TypedElementInfo element) {
        return element.findAnnotation(Types.OPTION_TYPE)
                .flatMap(Annotation::value)
                .map(TypeName::create)
                .orElseGet(element::typeName);
    }

    private static String setterName(String name, boolean recordStyle) {
        if (recordStyle && !RESERVED_WORDS.contains(name)) {
            return name;
        }

        return "set" + capitalize(name);
    }

    private static String propertyName(String getterName, boolean isBoolean, boolean recordStyle) {
        if (recordStyle) {
            return getterName;
        }

        if (isBoolean) {
            if (getterName.startsWith("is")) {
                return deCapitalize(getterName.substring(2));
            }
        }
        if (getterName.startsWith("get")) {
            return deCapitalize(getterName.substring(3));
        }
        return getterName;
    }

    private static String deCapitalize(String string) {
        if (string.isBlank()) {
            return string;
        }
        return Character.toLowerCase(string.charAt(0)) + string.substring(1);
    }

    private enum OptionType {
        SINGLE,
        LIST,
        SET,
        MAP;

        static OptionType create(TypeName typeName) {
            if (typeName.isOptional()) {
                return create(typeName.typeArguments().getFirst());
            }
            if (typeName.isSet()) {
                return SET;
            }
            if (typeName.isList()) {
                return LIST;
            }
            if (typeName.isMap()) {
                return MAP;
            }
            return SINGLE;
        }

        Consumer<ContentBuilder<?>> toDefaultString(TypeName enclosingType,
                                                    TypedElementInfo element,
                                                    TypeName realType,
                                                    List<String> defaults) {
            return switch (this) {
                case SINGLE -> consumer(enclosingType, element, realType, singleDefault(element, defaults));
                case LIST -> consumer(enclosingType, element, realType, defaults, TypeNames.LIST, ARRAY_LIST);
                case SET -> consumer(enclosingType, element, realType, defaults, TypeNames.SET, LINKED_HASH_SET);
                case MAP -> map(enclosingType, element, realType, defaults);
            };
        }

        Consumer<ContentBuilder<?>> toDefaultLongs(TypeName enclosingType,
                                                   TypedElementInfo element,
                                                   TypeName realType,
                                                   List<Long> defaults) {
            return switch (this) {
                case SINGLE -> consumer(enclosingType, element, realType, singleDefault(element, defaults) + "L");
                case LIST -> consumer(enclosingType, element, realType, longs(defaults), TypeNames.LIST, ARRAY_LIST);
                case SET -> consumer(enclosingType, element, realType, longs(defaults), TypeNames.SET, LINKED_HASH_SET);
                case MAP -> map(enclosingType, element, realType, longs(defaults));
            };
        }

        Consumer<ContentBuilder<?>> toDefault(TypeName enclosingType,
                                              TypedElementInfo element,
                                              TypeName realType,
                                              List<?> defaults) {
            return switch (this) {
                case SINGLE -> consumer(enclosingType, element, realType, String.valueOf(singleDefault(element, defaults)));
                case LIST -> consumer(enclosingType, element, realType, toStrings(defaults), TypeNames.LIST, ARRAY_LIST);
                case SET -> consumer(enclosingType, element, realType, toStrings(defaults), TypeNames.SET, LINKED_HASH_SET);
                case MAP -> map(enclosingType, element, realType, toStrings(defaults));
            };
        }

        Consumer<ContentBuilder<?>> toDefaultDoubles(TypeName enclosingType,
                                                     TypedElementInfo element,
                                                     TypeName realType,
                                                     List<Double> defaults) {
            return switch (this) {
                case SINGLE -> consumer(enclosingType, element, realType, singleDefault(element, defaults) + "D");
                case LIST -> consumer(enclosingType, element, realType, doubles(defaults), TypeNames.LIST, ARRAY_LIST);
                case SET -> consumer(enclosingType, element, realType, doubles(defaults), TypeNames.SET, LINKED_HASH_SET);
                case MAP -> map(enclosingType, element, realType, doubles(defaults));
            };
        }

        private static Consumer<ContentBuilder<?>> map(TypeName enclosingType,
                                                       TypedElementInfo element,
                                                       TypeName typeName,
                                                       List<String> defaultValues) {
            if (defaultValues.size() % 2 != 0) {
                throw new CodegenException("Default value for a map does not have even number of entries:"
                                                   + defaultValues,
                                           element);
            }
            return content -> {
                content.addContent("new ")
                        .addContent(LINKED_HASH_MAP)
                        .addContent("<>(")
                        .addContent(TypeNames.MAP)
                        .addContent(".of(");

                for (int i = 1; i < defaultValues.size(); i = i + 2) {
                    // key must be a string
                    content.addContentLiteral(defaultValues.get(i - 1))
                            .addContent(", ");
                    consumer(enclosingType, element, typeName, defaultValues.get(i)).accept(content);
                    if (i != defaultValues.size() - 2) {
                        content.addContentLine(", ");
                    }
                    if (i == 1) {
                        content.increaseContentPadding()
                                .increaseContentPadding();
                    }
                }

                content.addContent("))")
                        .decreaseContentPadding()
                        .decreaseContentPadding();
            };
        }

        private static Consumer<ContentBuilder<?>> consumer(TypeName enclosingType,
                                                            TypedElementInfo element,
                                                            TypeName typeName,
                                                            List<String> defaultValues,
                                                            TypeName collectionType,
                                                            TypeName collectionImplType) {
            return content -> {
                content.addContent("new ")
                        .addContent(collectionImplType)
                        .addContent("<>(")
                        .addContent(collectionType)
                        .addContent(".of(");

                for (int i = 0; i < defaultValues.size(); i++) {
                    consumer(enclosingType, element, typeName, defaultValues.get(i)).accept(content);
                    if (i != defaultValues.size() - 1) {
                        content.addContent(", ");
                    }
                }

                content.addContent("))");
            };
        }

        private static Consumer<ContentBuilder<?>> consumer(TypeName enclosingType,
                                                            TypedElementInfo element,
                                                            TypeName typeName,
                                                            String defaultValue) {
            if (TypeNames.STRING.equals(typeName)) {
                return content -> content.addContent("\"")
                        .addContent(defaultValue)
                        .addContent("\"");
            }
            if (TypeNames.SIZE.equals(typeName)) {
                CodegenValidator.validateSize(enclosingType, element, OPTION_DEFAULT, "value", defaultValue);
                return content -> content.addContent(Size.class)
                        .addContent(".parse(\"")
                        .addContent(defaultValue)
                        .addContent("\")");
            }
            if (TypeNames.DURATION.equals(typeName)) {
                CodegenValidator.validateDuration(enclosingType, element, OPTION_DEFAULT, "value", defaultValue);
                return content -> content.addContent(Duration.class)
                        .addContent(".parse(\"")
                        .addContent(defaultValue)
                        .addContent("\")");
            }
            if (Types.CHAR_ARRAY.equals(typeName)) {
                return content -> content.addContent("\"")
                        .addContent(defaultValue)
                        .addContent("\".toCharArray()");
            }
            if (Types.PATH.equals(typeName)) {
                return content -> content.addContent(Paths.class)
                        .addContent(".get(\"")
                        .addContent(defaultValue)
                        .addContent("\")");
            }
            if (Types.URI.equals(typeName)) {
                CodegenValidator.validateUri(enclosingType, element, OPTION_DEFAULT, "value", defaultValue);
                return content -> content.addContent(URI.class)
                        .addContent(".create(\"")
                        .addContent(defaultValue)
                        .addContent("\")");
            }
            if (typeName.primitive()) {
                if (typeName.fqName().equals("char")) {
                    return content -> content.addContent("'")
                            .addContent(defaultValue)
                            .addContent("'");
                }
                return content -> content.addContent(defaultValue);
            }
            if (typeName.name().startsWith("java.")) {
                return content -> content.addContent(defaultValue);
            }
            // should be an enum
            return content -> content.addContent(typeName.genericTypeName())
                    .addContent(".")
                    .addContent(defaultValue);
        }

        private List<String> longs(List<Long> defaults) {
            return defaults.stream()
                    .map(it -> it + "L")
                    .toList();
        }

        private List<String> doubles(List<Double> defaults) {
            return defaults.stream()
                    .map(it -> it + "D")
                    .toList();
        }

        private List<String> toStrings(List<?> defaults) {
            return defaults.stream()
                    .map(String::valueOf)
                    .toList();
        }

        private <T> T singleDefault(TypedElementInfo element, List<T> defaultValues) {
            if (defaultValues.isEmpty()) {
                throw new CodegenException("Default values configured for " + name() + " are empty, one value is expected.",
                                           element);
            }
            if (defaultValues.size() > 1) {
                throw new CodegenException("Default values configured for " + name() + " contain more than one value,"
                                                   + " exactly one value is expected.",
                                           element);
            }
            return defaultValues.getFirst();
        }
    }
}
