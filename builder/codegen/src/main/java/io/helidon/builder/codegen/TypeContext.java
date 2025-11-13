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
import io.helidon.common.types.Modifier;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;

import static io.helidon.builder.codegen.Types.COMMON_CONFIG;
import static io.helidon.builder.codegen.Types.CONFIG;
import static io.helidon.builder.codegen.Types.PROTOTYPE_API;
import static io.helidon.builder.codegen.Types.PROTOTYPE_BLUEPRINT;
import static io.helidon.builder.codegen.Types.PROTOTYPE_BUILDER_DECORATOR;
import static io.helidon.builder.codegen.Types.PROTOTYPE_BUILDER_METHOD;
import static io.helidon.builder.codegen.Types.PROTOTYPE_CONFIGURED;
import static io.helidon.builder.codegen.Types.PROTOTYPE_CONSTANT;
import static io.helidon.builder.codegen.Types.PROTOTYPE_CUSTOM_METHODS;
import static io.helidon.builder.codegen.Types.PROTOTYPE_FACTORY;
import static io.helidon.builder.codegen.Types.PROTOTYPE_FACTORY_METHOD;
import static io.helidon.builder.codegen.Types.PROTOTYPE_INCLUDE_DEFAULTS;
import static io.helidon.builder.codegen.Types.PROTOTYPE_PROTOTYPE_METHOD;
import static io.helidon.builder.codegen.Types.PROTOTYPE_PROVIDES;

final class TypeContext {

    private static final String BLUEPRINT = "Blueprint";

    private TypeContext() {
    }

    /*
    Creates a prototype information from the blueprint.
    This method analyses the class, not options.
     */
    static PrototypeInfo create(RoundContext ctx, TypeInfo blueprint) {
        Annotation blueprintAnnotation = blueprintAnnotation(blueprint);

        TypeName prototypeType = generatedTypeName(blueprint);
        Javadoc blueprintJavadoc = Javadoc.parse(blueprint.description().orElse(""));

        PrototypeInfo.Builder prototype = PrototypeInfo.builder()
                .blueprint(blueprint)
                .prototypeType(prototypeType)
                .detachBlueprint(blueprintAnnotation.booleanValue("detach").orElse(false))
                .defaultMethodsPredicate(defaultMethodsPredicate(blueprint))
                .accessModifier(prototypeAccessModifier(blueprintAnnotation))
                .builderAccessModifier(builderAccessModifier(blueprintAnnotation))
                .createEmptyCreate(createEmptyPublic(blueprintAnnotation))
                .recordStyle(recordStyleAccessors(blueprintAnnotation))
                .registrySupport(registrySupport(blueprint))
                .superPrototype(superPrototype(blueprint))
                .providerProvides(providerProvides(blueprint))
                .javadoc(prototypeJavadoc(blueprint))
                .builderBaseJavadoc(builderBaseJavadoc(blueprintJavadoc, prototypeType))
                .builderJavadoc(builderJavadoc(blueprintJavadoc, prototypeType));

        prototypeExtends(prototype, blueprint);

        builderDecorator(blueprintAnnotation).ifPresent(prototype::builderDecorator);
        configured(blueprint, blueprintAnnotation).ifPresent(prototype::configured);
        runtimeType(blueprint).ifPresent(prototype::runtimeType);

        customMethodsTypeInfo(ctx, blueprint).ifPresent(it -> {
            Errors.Collector errors = Errors.collector();

            prototype.constants(constants(it, errors));
            prototype.prototypeMethods(customMethods(
                    it,
                    errors,
                    PROTOTYPE_PROTOTYPE_METHOD,
                    el -> true,
                    TypeContext::prototypeMethod));
            prototype.prototypeFactoryMethods(customMethods(it,
                                                            errors,
                                                            PROTOTYPE_FACTORY_METHOD,
                                                            TypeContext::isPrototypeFactory,
                                                            TypeContext::prototypeFactory));
            prototype.builderMethods(customMethods(it,
                                                   errors,
                                                   PROTOTYPE_BUILDER_METHOD,
                                                   el -> true,
                                                   TypeContext::builderMethod));
            prototype.factoryMethods(customMethods(it,
                                                   errors,
                                                   PROTOTYPE_FACTORY_METHOD,
                                                   TypeContext::isFactory,
                                                   TypeContext::factoryMethod));

            Errors collected = errors.collect();
            if (collected.hasFatal()) {
                throw new CodegenException("Invalid custom methods or constants: " + collected,
                                           it);
            }
        });

        return prototype.build();
    }

    private static boolean isPrototypeFactory(TypedElementInfo factoryMethod) {
        return !isFactory(factoryMethod);
    }

    private static boolean isFactory(TypedElementInfo factoryMethod) {
        if (factoryMethod.parameterArguments().size() == 1) {
            var type = factoryMethod.parameterArguments()
                    .getFirst()
                    .typeName();
            if (type.equals(CONFIG)) {
                return true;
            }
            if (type.equals(COMMON_CONFIG)) {
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
        return GeneratedBuilderMethod.create(typeName, referencedMethod, annotations);
    }

    private static GeneratedMethod prototypeFactory(Errors.Collector collector,
                                                    TypeName typeName,
                                                    TypedElementInfo referencedMethod,
                                                    List<Annotation> annotations) {
        return GeneratedFactoryMethod.create(typeName, referencedMethod, annotations);
    }

    private static FactoryMethod factoryMethod(Errors.Collector collector,
                                               TypeName typeName,
                                               TypedElementInfo referencedMethod,
                                               List<Annotation> annotations) {

        return new FactoryMethodImpl(typeName, referencedMethod.typeName(), referencedMethod.elementName(),
                                     referencedMethod.parameterArguments()
                                             .stream()
                                             .map(TypeContext::toFactoryMethodParameter)
                                             .toList());
    }

    private static FactoryMethod.Parameter toFactoryMethodParameter(TypedElementInfo elementInfo) {
        return new FactoryMethodParameterImpl(elementInfo.typeName(), elementInfo.elementName());
    }

    private static GeneratedMethod prototypeMethod(Errors.Collector collector,
                                                   TypeName typeName,
                                                   TypedElementInfo referencedMethod,
                                                   List<Annotation> annotations) {

        return GeneratedPrototypeMethod.create(typeName,
                                               referencedMethod,
                                               annotations);
    }

    private static <T> List<? extends T> customMethods(TypeInfo customMethodsType,
                                                       Errors.Collector errors,
                                                       TypeName requiredAnnotation,
                                                       Predicate<TypedElementInfo> predicate,
                                                       CustomMethodProcessor<T> methodProcessor) {
        // all custom methods must be static
        // parameter and return type validation is to be done by method processor
        return customMethodsType.elementInfo()
                .stream()
                .filter(predicate)
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

    private static Optional<TypeInfo> customMethodsTypeInfo(RoundContext ctx, TypeInfo blueprint) {
        return blueprint.findAnnotation(PROTOTYPE_CUSTOM_METHODS)
                .map(it -> customMethodsTypeInfo(ctx, blueprint, it));

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

                    return PrototypeConstantReference.create(customMethodsType.typeName(),
                                                             fieldType,
                                                             name,
                                                             javadoc);
                })
                .toList();
    }

    private static Optional<PrototypeConfigured> configured(TypeInfo blueprint, Annotation blueprintAnnotation) {
        return blueprint.findAnnotation(PROTOTYPE_CONFIGURED)
                .map(it -> TypeContext.configured(blueprintAnnotation, it));
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

    private static void prototypeExtends(PrototypeInfo.Builder prototype, TypeInfo blueprint) {
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

        if (detachBlueprint) {
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
                // other we can add directly, also as this is a set, if you extend both prototype and blueprint it is fine
                prototypeExtends.add(superInterface.typeName());
            }
        }

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
                .map(it -> AccessModifier.PACKAGE_PRIVATE)
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
                    superPrototypes.add(anInterface.typeName());
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

    private static class FactoryMethodParameterImpl implements FactoryMethod.Parameter {
        private final TypeName type;
        private final String name;

        private FactoryMethodParameterImpl(TypeName type, String name) {
            this.type = type;
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public TypeName type() {
            return type;
        }

        @Override
        public String toString() {
            return type.className() + " " + name;
        }
    }

    private static class FactoryMethodImpl implements FactoryMethod {
        private final TypeName declaringType;
        private final TypeName returnType;
        private final String methodName;
        private final List<FactoryMethod.Parameter> parameters;

        private FactoryMethodImpl(TypeName declaringType, TypeName returnType, String methodName, List<Parameter> parameters) {
            this.declaringType = declaringType;
            this.returnType = returnType;
            this.methodName = methodName;
            this.parameters = parameters;
        }

        @Override
        public TypeName declaringType() {
            return declaringType;
        }

        @Override
        public TypeName returnType() {
            return returnType;
        }

        @Override
        public String methodName() {
            return methodName;
        }

        @Override
        public List<Parameter> parameters() {
            return parameters;
        }

        @Override
        public String toString() {
            return declaringType.fqName() + "." + methodName + "(" + parameters + ")";
        }
    }
}
