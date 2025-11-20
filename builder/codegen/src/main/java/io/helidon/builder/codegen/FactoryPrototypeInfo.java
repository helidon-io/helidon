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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import io.helidon.codegen.CodegenException;
import io.helidon.codegen.ElementInfoPredicates;
import io.helidon.codegen.RoundContext;
import io.helidon.codegen.classmodel.Javadoc;
import io.helidon.common.Errors;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.Annotations;
import io.helidon.common.types.ElementSignature;
import io.helidon.common.types.Modifier;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;

import static io.helidon.builder.codegen.Types.PROTOTYPE_API;
import static io.helidon.builder.codegen.Types.PROTOTYPE_BLUEPRINT;
import static io.helidon.builder.codegen.Types.PROTOTYPE_BUILDER_DECORATOR;
import static io.helidon.builder.codegen.Types.PROTOTYPE_BUILDER_METHOD;
import static io.helidon.builder.codegen.Types.PROTOTYPE_CONFIGURED;
import static io.helidon.builder.codegen.Types.PROTOTYPE_CONSTANT;
import static io.helidon.builder.codegen.Types.PROTOTYPE_CUSTOM_METHODS;
import static io.helidon.builder.codegen.Types.PROTOTYPE_FACTORY;
import static io.helidon.builder.codegen.Types.PROTOTYPE_FACTORY_METHOD_CONFIG;
import static io.helidon.builder.codegen.Types.PROTOTYPE_FACTORY_METHOD_DEPRECATED;
import static io.helidon.builder.codegen.Types.PROTOTYPE_FACTORY_METHOD_PROTOTYPE;
import static io.helidon.builder.codegen.Types.PROTOTYPE_FACTORY_METHOD_RUNTIME_TYPE;
import static io.helidon.builder.codegen.Types.PROTOTYPE_INCLUDE_DEFAULTS;
import static io.helidon.builder.codegen.Types.PROTOTYPE_PROTOTYPE_METHOD;
import static io.helidon.builder.codegen.Types.PROTOTYPE_PROVIDES;

@SuppressWarnings({"deprecation", "removal"})
final class FactoryPrototypeInfo {

    private static final String BLUEPRINT = "Blueprint";

    private FactoryPrototypeInfo() {
    }

    /*
    Creates a prototype information from the blueprint.
    This method analyses the class, not options.
     */
    @SuppressWarnings("removal")
    static PrototypeInfo create(RoundContext ctx, TypeInfo blueprint) {
        Annotation blueprintAnnotation = blueprintAnnotation(blueprint);

        TypeName prototypeType = generatedTypeName(blueprint);
        Javadoc blueprintJavadoc = Javadoc.parse(blueprint.description().orElse(""));
        Predicate<String> defaultMethodsPredicate = defaultMethodsPredicate(blueprint);
        Optional<TypeName> superPrototype = superPrototype(blueprint);

        PrototypeInfo.Builder prototype = PrototypeInfo.builder()
                .blueprint(blueprint)
                .prototypeType(prototypeType)
                .detachBlueprint(blueprintAnnotation.booleanValue("detach").orElse(false))
                .defaultMethodsPredicate(defaultMethodsPredicate)
                .accessModifier(prototypeAccessModifier(blueprintAnnotation))
                .builderAccessModifier(builderAccessModifier(blueprintAnnotation))
                .createEmptyCreate(createEmptyPublic(blueprintAnnotation))
                .recordStyle(recordStyleAccessors(blueprintAnnotation))
                .registrySupport(registrySupport(blueprint))
                .superPrototype(superPrototype)
                .providerProvides(providerProvides(blueprint))
                .javadoc(prototypeJavadoc(blueprint))
                .builderBaseJavadoc(builderBaseJavadoc(blueprintJavadoc, prototypeType))
                .builderJavadoc(builderJavadoc(blueprintJavadoc, prototypeType));

        prototypeExtends(prototype, blueprint, superPrototype);

        builderDecorator(blueprintAnnotation).ifPresent(prototype::builderDecorator);
        configured(blueprint, blueprintAnnotation).ifPresent(prototype::configured);
        runtimeType(blueprint).ifPresent(prototype::runtimeType);

        List<TypedElementInfo> defaultMethodsNotOptions = defaultMethodsNotOptions(blueprint, defaultMethodsPredicate);

        customMethodsTypeInfo(ctx, blueprint).ifPresent(it -> {
            Errors.Collector errors = Errors.collector();

            prototype.constants(constants(it, errors));
            prototype.prototypeMethods(customMethods(
                    it,
                    errors,
                    PROTOTYPE_PROTOTYPE_METHOD,
                    (collector, customMethodsType, customMethod, annotations) ->
                            prototypeMethod(collector, customMethodsType, customMethod, annotations, defaultMethodsNotOptions)));
            prototype.builderMethods(customMethods(it,
                                                   errors,
                                                   PROTOTYPE_BUILDER_METHOD,
                                                   FactoryPrototypeInfo::builderMethod));

            // these methods can only be processed once we know all the options
            prototype.deprecatedFactoryMethods(customMethods(it,
                                                             errors,
                                                             PROTOTYPE_FACTORY_METHOD_DEPRECATED,
                                                             FactoryPrototypeInfo::deprecatedFactory));

            prototype.prototypeFactories(customMethods(it,
                                                       errors,
                                                       PROTOTYPE_FACTORY_METHOD_PROTOTYPE,
                                                       FactoryPrototypeInfo::prototypeFactory));

            prototype.configFactories(customMethods(it,
                                                    errors,
                                                    PROTOTYPE_FACTORY_METHOD_CONFIG,
                                                    FactoryPrototypeInfo::configFactoryMethod));
            prototype.runtimeTypeFactories(customMethods(it,
                                                         errors,
                                                         PROTOTYPE_FACTORY_METHOD_RUNTIME_TYPE,
                                                         FactoryPrototypeInfo::runtimeTypeFactory));

            Errors collected = errors.collect();
            if (collected.hasFatal()) {
                throw new CodegenException("Invalid custom methods or constants: " + collected,
                                           it.originatingElementValue());
            }
        });

        // also add deprecated factory methods from the blueprint itself (as this was originally supported)
        addBlueprintDeprecatedFactoryMethods(prototype, blueprint);

        copyDefaultMethods(blueprint, prototype, defaultMethodsNotOptions);

        return prototype.build();
    }

    private static List<TypedElementInfo> defaultMethodsNotOptions(TypeInfo blueprint,
                                                                   Predicate<String> defaultMethodsPredicate) {
        return blueprint.elementInfo()
                .stream()
                .filter(ElementInfoPredicates::isMethod)
                .filter(it -> it.elementModifiers().contains(Modifier.DEFAULT))
                .filter(Predicate.not(it -> defaultMethodsPredicate.test(it.elementName())))
                .filter(it -> !it.hasAnnotation(TypeName.create(Override.class)))
                .toList();
    }

    @SuppressWarnings({"removal", "unused"})
    private static void addBlueprintDeprecatedFactoryMethods(PrototypeInfo.Builder prototype, TypeInfo blueprint) {
        List<DeprecatedFactoryMethod> deprecatedFactoryMethods = new ArrayList<>(prototype.deprecatedFactoryMethods());

        Errors.Collector errors = Errors.collector();
        deprecatedFactoryMethods.addAll(customMethods(blueprint,
                                                      errors,
                                                      PROTOTYPE_FACTORY_METHOD_DEPRECATED,
                                                      FactoryPrototypeInfo::deprecatedFactory));
        Errors collected = errors.collect();
        if (collected.hasFatal()) {
            throw new CodegenException("Invalid custom methods or constants: " + collected,
                                       blueprint.originatingElementValue());
        }
        prototype.deprecatedFactoryMethods(deprecatedFactoryMethods);
    }

    private static void copyDefaultMethods(TypeInfo blueprint,
                                           PrototypeInfo.Builder prototype,
                                           List<TypedElementInfo> defaultMethodsNotOptions) {

        // add all default methods that are not options, and do not have an Override annotation (as that implies that method
        // is inherited from another interface
        if (defaultMethodsNotOptions.isEmpty()) {
            return;
        }
        if (prototype.detachBlueprint()) {
            throw new CodegenException("Default methods are not allowed on detached blueprints",
                                       defaultMethodsNotOptions.getFirst().originatingElementValue());
        }
        for (TypedElementInfo method : defaultMethodsNotOptions) {
            if (isACustomMethod(prototype, method)) {
                // do not create defaults for custom methods
                continue;
            }
            prototype.addPrototypeMethod(m -> m
                    .method(newMethod -> newMethod.from(method)
                            .clearOriginatingElement())
                    .javadoc(Javadoc.parse(method.description().orElse("")))
                    .contentBuilder(content -> {
                                        if (!method.typeName().equals(TypeNames.PRIMITIVE_VOID)) {
                                            content.addContent("return ");
                                        }
                                        content.addContent(blueprint.typeName())
                                                .addContent(".super.")
                                                .addContent(method.elementName())
                                                .addContent("(")
                                                .addContent(method.parameterArguments().stream()
                                                                    .map(TypedElementInfo::elementName)
                                                                    .collect(Collectors.joining(", ")))
                                                .addContentLine(");");
                                    }
                    )
            );
        }
    }

    private static boolean isACustomMethod(PrototypeInfo.Builder prototypeInfo, TypedElementInfo prototypeDefaultMethod) {
        // only methods not overridden using a custom method should be copied over
        ElementSignature signature = prototypeDefaultMethod.signature();
        for (GeneratedMethod prototypeMethod : prototypeInfo.prototypeMethods()) {
            if (prototypeMethod.method().signature().equals(signature)) {
                return true;
            }
        }
        for (var deprecatedMethod : prototypeInfo.deprecatedFactoryMethods()) {
            if (deprecatedMethod.method().signature().equals(signature)) {
                return true;
            }
        }
        return false;
    }

    private static Optional<TypeName> runtimeType(TypeInfo blueprint) {
        Optional<TypeInfo> factoryInterface = blueprint.interfaceTypeInfo()
                .stream()
                .filter(it -> PROTOTYPE_FACTORY.equals(it.typeName().genericTypeName()))
                .findFirst();
        return factoryInterface.map(it -> it.typeName().typeArguments().getFirst());
    }

    private static Javadoc prototypeJavadoc(TypeInfo blueprint) {
        return blueprint.description()
                .map(Javadoc::parse)
                .orElseGet(() -> Javadoc.builder()
                        .add("Interface generated from blueprint {@code " + blueprint.typeName().fqName() + "}. "
                                     + "Please add javadoc to blueprint, as it is currently missing.")
                        .build());
    }

    private static Javadoc builderBaseJavadoc(Javadoc blueprintJavadoc, TypeName prototypeType) {
        return Javadoc.builder()
                .add("Fluent API builder base for {@link " + prototypeType.className() + "}.")
                .update(it -> {
                    // add blueprint type parameters, as these are copied to the builder
                    blueprintJavadoc.parameters()
                            .forEach(it::addParameter);
                })
                .addParameter("<BUILDER>", "type of the builder extending this abstract builder")
                .addParameter("<PROTOTYPE>", "type of the prototype interface that would be built by {@link #buildPrototype()}")
                .update(it -> blueprintJavadoc.genericsTokens()
                        .forEach((key, lines) -> it.addParameter("<" + key + ">", lines)))
                .build();
    }

    private static Javadoc builderJavadoc(Javadoc blueprintJavadoc, TypeName prototypeType) {
        return Javadoc.builder()
                .add("Fluent API builder for {@link " + prototypeType.className() + "}.")
                .update(it -> {
                    // add blueprint type parameters, as these are copied to the builder
                    blueprintJavadoc.parameters()
                            .forEach(it::addParameter);
                })
                .build();
    }

    private static GeneratedMethod builderMethod(Errors.Collector collector,
                                                 TypeName typeName,
                                                 TypedElementInfo referencedMethod,
                                                 List<Annotation> annotations) {
        return GeneratedMethods.createBuilderMethod(typeName, referencedMethod, annotations);
    }

    private static DeprecatedFactoryMethod deprecatedFactory(Errors.Collector collector,
                                                             TypeName typeName,
                                                             TypedElementInfo referencedMethod,
                                                             List<Annotation> annotations) {
        return DeprecatedFactoryMethod.builder()
                .declaringType(typeName)
                .method(referencedMethod)
                .build();
    }

    private static GeneratedMethod prototypeFactory(Errors.Collector collector,
                                                    TypeName typeName,
                                                    TypedElementInfo referencedMethod,
                                                    List<Annotation> annotations) {
        return GeneratedMethods.createFactoryMethod(typeName, referencedMethod, annotations);
    }

    private static RuntimeTypeInfo runtimeTypeFactory(Errors.Collector collector,
                                                      TypeName typeName,
                                                      TypedElementInfo referencedMethod,
                                                      List<Annotation> annotations) {

        String methodName = referencedMethod.elementName();
        if (referencedMethod.typeName().unboxed().equals(TypeNames.PRIMITIVE_VOID)) {
            collector.fatal("@runtimeTypeFactoryMethods must not be void, but method "
                                    + typeName.fqName() + "." + methodName + " is");
        }

        TypeName runtimeType = referencedMethod.typeName();
        TypeName prototypeType = paramType(collector, typeName, referencedMethod, "Runtime type");

        var methodBuilder = FactoryMethod.builder()
                .declaringType(typeName)
                .methodName(methodName)
                .returnType(runtimeType)
                .parameterType(prototypeType);

        Annotation annotation = referencedMethod.annotation(PROTOTYPE_FACTORY_METHOD_RUNTIME_TYPE);
        annotation.value()
                .filter(Predicate.not(String::isBlank))
                .ifPresent(methodBuilder::optionName);

        var builder = OptionBuilder.builder()
                .builderMethodType(prototypeType)
                .builderType(Utils.prototypeBuilderType(prototypeType));
        /*
        First guess from the parameter type, then use annotation to override
         */
        annotation.stringValue("builderMethodName")
                .filter(Predicate.not("builder"::equals))
                .ifPresent(builder::builderMethodName);
        annotation.stringValue("buildMethodName")
                .filter(Predicate.not("build"::equals))
                .ifPresent(builder::buildMethodName);
        annotation.typeValue("builderType")
                .filter(Predicate.not(PROTOTYPE_FACTORY_METHOD_RUNTIME_TYPE::equals))
                .ifPresent(builder::builderType);
        annotation.typeValue("builderMethodType")
                .filter(Predicate.not(PROTOTYPE_FACTORY_METHOD_RUNTIME_TYPE::equals))
                .ifPresent(builder::builderType);

        return RuntimeTypeInfo.builder()
                .factoryMethod(methodBuilder)
                .optionBuilder(builder)
                .build();
    }

    private static FactoryMethod configFactoryMethod(Errors.Collector collector,
                                                     TypeName typeName,
                                                     TypedElementInfo referencedMethod,
                                                     List<Annotation> annotations) {

        var builder = FactoryMethod.builder()
                .declaringType(typeName)
                .returnType(referencedMethod.typeName())
                .methodName(referencedMethod.elementName())
                .parameterType(paramType(collector, typeName, referencedMethod, "Config"));

        referencedMethod.annotation(PROTOTYPE_FACTORY_METHOD_CONFIG)
                .stringValue()
                .filter(Predicate.not(String::isBlank))
                .ifPresent(builder::optionName);

        return builder.build();
    }

    private static TypeName paramType(Errors.Collector collector,
                                      TypeName typeName,
                                      TypedElementInfo referencedMethod,
                                      String factoryType) {
        if (referencedMethod.parameterArguments().size() != 1) {
            collector.fatal(factoryType + " must have exactly one parameter, but method "
                                    + typeName.fqName() + "." + referencedMethod.elementName() + " has "
                                    + referencedMethod.parameterArguments().size());

        }
        return referencedMethod.parameterArguments().getFirst().typeName();
    }

    private static GeneratedMethod prototypeMethod(Errors.Collector collector,
                                                   TypeName typeName,
                                                   TypedElementInfo referencedMethod,
                                                   List<Annotation> annotations,
                                                   List<TypedElementInfo> defaultMethodsNotOptions) {

        var args = new ArrayList<>(referencedMethod.parameterArguments());
        if (!args.isEmpty()) {
            args.removeFirst();
        }
        // the generated method will be "sans" the first parameter
        TypedElementInfo tei = TypedElementInfo.builder(referencedMethod)
                .parameterArguments(args)
                .build();
        var generatedSignature = tei.signature();

        // if the referenced method is also a default method on the blueprint, we need to add
        // Override annotation, and copy javadoc from it
        for (TypedElementInfo m : defaultMethodsNotOptions) {
            if (m.signature().equals(generatedSignature)) {
                List<Annotation> usedAnnotations = new ArrayList<>(annotations);
                if (Annotations.findFirst(TypeName.create(Override.class), annotations).isEmpty()) {
                    usedAnnotations.add(Annotations.OVERRIDE);
                }
                return GeneratedMethods.createPrototypeMethod(typeName,
                                                              referencedMethod,
                                                              usedAnnotations,
                                                              m);
            }
        }
        return GeneratedMethods.createPrototypeMethod(typeName,
                                                      referencedMethod,
                                                      annotations);
    }

    private static <T> List<? extends T> customMethods(TypeInfo customMethodsType,
                                                       Errors.Collector errors,
                                                       TypeName requiredAnnotation,
                                                       CustomMethodProcessor<T> methodProcessor) {
        // all custom methods must be static
        // parameter and return type validation is to be done by method processor
        return customMethodsType.elementInfo()
                .stream()
                .filter(ElementInfoPredicates::isMethod)
                .filter(ElementInfoPredicates::isStatic)
                .filter(ElementInfoPredicates.hasAnnotation(requiredAnnotation))
                .map(it -> {
                    // annotations to be added to generated code
                    List<Annotation> annotations = it.findAnnotation(Types.PROTOTYPE_ANNOTATED)
                            .flatMap(Annotation::stringValues)
                            .orElseGet(List::of)
                            .stream()
                            .map(String::trim) // to remove spaces after commas when used
                            .filter(Predicate.not(String::isBlank)) // we do not care about blank values
                            .map(io.helidon.codegen.classmodel.Annotation::parse)
                            .map(io.helidon.codegen.classmodel.Annotation::toTypesAnnotation)
                            .toList();

                    return methodProcessor.process(errors,
                                                   customMethodsType.typeName(),
                                                   it,
                                                   annotations);
                })
                .toList();
    }

    private static Optional<TypeInfo> customMethodsTypeInfo(RoundContext ctx,
                                                            TypeInfo blueprint) {
        // first check the blueprint
        var response = blueprint.findAnnotation(PROTOTYPE_CUSTOM_METHODS)
                .map(it -> customMethodsTypeInfo(ctx, blueprint, it));
        if (response.isPresent()) {
            return response;
        }

        // then check all things the blueprint extends
        for (TypeInfo typeInfo : blueprint.interfaceTypeInfo()) {
            response = typeInfo.findAnnotation(PROTOTYPE_CUSTOM_METHODS)
                    .map(it -> customMethodsTypeInfo(ctx, blueprint, it));
            if (response.isPresent()) {
                return response;
            }
        }
        return Optional.empty();
    }

    private static TypeInfo customMethodsTypeInfo(RoundContext ctx, TypeInfo blueprint, Annotation customMethodsAnnotation) {
        // the `value()` is mandatory on this annotation
        TypeName type = customMethodsAnnotation.typeValue()
                .orElseThrow();
        return ctx.typeInfo(type)
                .orElseThrow(() -> new CodegenException("No type found for @Prototype.CustomMethods annotation on "
                                                                + blueprint.typeName() + ", type: " + type.fqName(),
                                                        blueprint));
    }

    private static List<? extends PrototypeConstant> constants(TypeInfo customMethodsType, Errors.Collector errors) {
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

                    return PrototypeConstants.create(customMethodsType.typeName(),
                                                     fieldType,
                                                     name,
                                                     javadoc);
                })
                .toList();
    }

    private static Optional<PrototypeConfigured> configured(TypeInfo blueprint, Annotation blueprintAnnotation) {
        return blueprint.findAnnotation(PROTOTYPE_CONFIGURED)
                .map(it -> FactoryPrototypeInfo.configured(blueprintAnnotation, it));
    }

    private static PrototypeConfigured configured(Annotation blueprintAnnotation, Annotation configuredAnnotation) {
        Optional<String> configKey = configuredAnnotation.stringValue()
                .filter(Predicate.not(String::isBlank));

        var builder = PrototypeConfigured.builder();

        configKey.ifPresent(it -> {
            builder.key(it);
            builder.root(configuredAnnotation.booleanValue("root").orElse(true));
        });

        blueprintAnnotation.booleanValue("createFromConfigPublic")
                .map(it -> it ? AccessModifier.PUBLIC : AccessModifier.PACKAGE_PRIVATE)
                .ifPresent(builder::createAccessModifier);

        return builder.build();
    }

    private static void prototypeExtends(PrototypeInfo.Builder prototype, TypeInfo blueprint, Optional<TypeName> superPrototype) {
        // when detaching blueprint, and all types the blueprint extends except for another blueprint
        // when not detaching blueprint, only add blueprint, API, and interfaces from annotation

        boolean detachBlueprint = prototype.detachBlueprint();

        // gather all directly implemented interfaces + add blueprint, Prototype.Api

        Set<TypeName> prototypeExtends = new LinkedHashSet<>();
        if (!detachBlueprint) {
            prototypeExtends.add(blueprint.typeName());
        }
        prototypeExtends.add(PROTOTYPE_API);

        // add custom implements
        blueprint.findAnnotation(Types.PROTOTYPE_IMPLEMENT)
                .flatMap(Annotation::stringValues)
                .stream()
                .flatMap(List::stream)
                .map(TypeName::create)
                .forEach(prototypeExtends::add);

        // add declared implements
        for (TypeInfo superInterface : blueprint.interfaceTypeInfo()) {
            if (superInterface.hasAnnotation(PROTOTYPE_BLUEPRINT)) {
                TypeName superBlueprint = superInterface.typeName();
                String className = superBlueprint.className();
                TypeName toExtend = TypeName.builder()
                        .packageName(superBlueprint.packageName())
                        // blueprints MUST end with Blueprint suffix
                        .className(className.substring(0, className.length() - 9))
                        .build();
                prototypeExtends.add(toExtend);
                continue;
            }

            if (detachBlueprint) {
                // other we can add directly, also as this is a set, if you extend both prototype and blueprint it is fine
                prototypeExtends.add(superInterface.typeName());
            }
        }
        superPrototype.ifPresent(prototypeExtends::add);

        prototype.superTypes(prototypeExtends);
    }

    private static Set<TypeName> providerProvides(TypeInfo blueprint) {
        return blueprint.findAnnotation(PROTOTYPE_PROVIDES)
                .flatMap(Annotation::typeValues)
                .map(it -> (Set<TypeName>) new LinkedHashSet<>(it))
                .orElseGet(Set::of);
    }

    private static boolean registrySupport(TypeInfo blueprint) {
        return blueprint.findAnnotation(Types.PROTOTYPE_SERVICE_REGISTRY)
                .flatMap(Annotation::booleanValue)
                .orElse(false);
    }

    private static boolean createEmptyPublic(Annotation blueprintAnnotation) {
        return blueprintAnnotation.booleanValue("createEmptyPublic")
                .orElse(true);
    }

    private static AccessModifier builderAccessModifier(Annotation blueprintAnnotation) {
        return blueprintAnnotation.booleanValue("builderPublic")
                .filter(it -> !it)
                .map(it -> AccessModifier.PROTECTED)
                .orElse(AccessModifier.PUBLIC);
    }

    private static AccessModifier prototypeAccessModifier(Annotation blueprintAnnotation) {
        return blueprintAnnotation.booleanValue("isPublic")
                .filter(it -> !it)
                .map(it -> AccessModifier.PACKAGE_PRIVATE)
                .orElse(AccessModifier.PUBLIC);
    }

    private static Optional<TypeName> builderDecorator(Annotation blueprintAnnotation) {
        return blueprintAnnotation.getValue("decorator")
                .map(TypeName::create)
                .filter(Predicate.not(PROTOTYPE_BUILDER_DECORATOR::equals));
    }

    private static Annotation blueprintAnnotation(TypeInfo blueprint) {
        return blueprint.findAnnotation(Types.PROTOTYPE_BLUEPRINT)
                .orElseThrow(() -> new CodegenException("No @Prototype.Blueprint annotation found on " + blueprint.typeName(),
                                                        blueprint));
    }

    private static boolean recordStyleAccessors(Annotation blueprintAnnotation) {
        return !blueprintAnnotation.booleanValue("beanStyle")
                .orElse(false);
    }

    private static Predicate<String> defaultMethodsPredicate(TypeInfo blueprint) {
        if (!blueprint.hasAnnotation(PROTOTYPE_INCLUDE_DEFAULTS)) {
            return it -> false;
        }
        Set<String> methodNames = blueprint.findAnnotation(PROTOTYPE_INCLUDE_DEFAULTS)
                .flatMap(Annotation::stringValues)
                .stream()
                .flatMap(List::stream)
                .collect(Collectors.toSet());

        if (methodNames.isEmpty()) {
            return it -> true;
        }

        return methodNames::contains;
    }

    private static TypeName generatedTypeName(TypeInfo typeInfo) {
        String typeName = typeInfo.typeName().className();
        if (typeName.endsWith(BLUEPRINT)) {
            typeName = typeName.substring(0, typeName.length() - BLUEPRINT.length());
        } else {
            throw new CodegenException("Blueprint interface name must end with " + BLUEPRINT
                                               + ", this is invalid type: " + typeInfo.typeName().fqName(),
                                       typeInfo);
        }

        return TypeName.builder(typeInfo.typeName())
                .enclosingNames(List.of())
                .className(typeName)
                .build();
    }

    private static Optional<TypeName> superPrototype(TypeInfo blueprint) {
        // interfaces we directly implement

        Set<TypeName> processedInterfaces = new HashSet<>();
        Set<TypeName> superPrototypes = new LinkedHashSet<>();
        superPrototype(blueprint, superPrototypes, processedInterfaces);

        return superPrototypes.stream()
                .findFirst();
    }

    private static void superPrototype(TypeInfo inProgress, Set<TypeName> superPrototypes, Set<TypeName> processedInterfaces) {
        List<TypeInfo> superInterfaces = inProgress.interfaceTypeInfo();
        for (TypeInfo superInterface : superInterfaces) {
            // we may implement the same interface through multiple super-interface, ignore it next time
            if (!processedInterfaces.add(superInterface.typeName())) {
                continue;
            }
            if (superInterface.hasAnnotation(PROTOTYPE_BLUEPRINT)) {
                // we have a direct super-interface that is a blueprint
                TypeName superBlueprint = superInterface.typeName();
                String className = superBlueprint.className();
                TypeName toExtend = TypeName.builder()
                        .packageName(superBlueprint.packageName())
                        // blueprints MUST end with Blueprint suffix
                        .className(className.substring(0, className.length() - 9))
                        .build();
                // encountering this again will skip it, also we do not care about any super interfaces of this interface,
                // as those are already covered by the prototype we extend
                processedInterfaces.add(toExtend);
                superPrototypes.add(toExtend);

                if (superPrototypes.size() > 1) {
                    throw new CodegenException("A blueprint extends more than one other blueprint/prototype. "
                                                       + "Multiple inheritance is not supported in Java, so we cannot generate"
                                                       + " a builder extending more than one super-builder.",
                                               inProgress);
                }
                continue;
            }

            boolean found = false;
            for (TypeInfo anInterface : superInterface.interfaceTypeInfo()) {
                // all implemented types of our super type - we look for Prototype.Api, as all prototypes must extend that
                // if found, this is a direct super prototype
                if (anInterface.typeName().equals(PROTOTYPE_API)) {
                    superPrototypes.add(superInterface.typeName());
                    if (superPrototypes.size() > 1) {
                        throw new CodegenException("A blueprint extends more than one other blueprint/prototype. "
                                                           + "Multiple inheritance is not supported in Java, so we cannot "
                                                           + "generate a builder extending more than one super-builder.",
                                                   inProgress);
                    }
                    found = true;
                    break;
                }
            }
            if (found) {
                // this was a prototype, continue with next one
                continue;
            }
            // this is a "random" interface, just go through all the types it extends
            superPrototype(superInterface,
                           superPrototypes,
                           processedInterfaces);
        }
    }

    interface CustomMethodProcessor<T> {
        T process(Errors.Collector collector,
                  TypeName customMethodsType,
                  TypedElementInfo customMethod,
                  List<Annotation> annotations);
    }
}
