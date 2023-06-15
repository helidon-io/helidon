
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
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;

import io.helidon.common.Errors;
import io.helidon.common.Severity;
import io.helidon.common.types.Annotated;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeValues;
import io.helidon.common.types.TypedElementInfo;

import static io.helidon.builder.processor.Types.BLUEPRINT_TYPE;
import static io.helidon.builder.processor.Types.BUILDER_INTERCEPTOR;
import static io.helidon.builder.processor.Types.CONFIGURED_OPTION_TYPE;
import static io.helidon.builder.processor.Types.CONFIGURED_TYPE;
import static io.helidon.builder.processor.Types.IMPLEMENT_TYPE;
import static io.helidon.builder.processor.Types.OBJECT_TYPE_NAME;
import static io.helidon.builder.processor.Types.PROTOTYPE_FACTORY_TYPE;
import static io.helidon.builder.processor.Types.PROTOTYPE_TYPE;
import static io.helidon.builder.processor.Types.STRING_TYPE;
import static io.helidon.builder.processor.Types.VOID_TYPE;

record TypeContext(
        TypeInformation typeInfo,
        BlueprintData blueprintData,
        ConfiguredData configuredData,
        PropertyData propertyData,
        CustomMethods customMethods) {

    private static final Set<String> IGNORED_NAMES = Set.of("build",
                                                            "get",
                                                            "buildPrototype",
                                                            "from");
    private static final String BLUEPRINT = "Blueprint";
    private static final Set<MethodSignature> IGNORED_METHODS = Set.of(
            // equals, hash code and toString
            new MethodSignature(TypeName.create(boolean.class), "equals", List.of(OBJECT_TYPE_NAME)),
            new MethodSignature(TypeName.create(int.class), "hashCode", List.of()),
            new MethodSignature(STRING_TYPE, "toString", List.of())
    );

    @SuppressWarnings("checkstyle:MethodLength") // use a lot of lines for parameter formatting
    static TypeContext create(ProcessingContext processingContext,
                              Elements elementUtils,
                              TypeElement blueprintElement,
                              TypeInfo blueprint) {
        String javadoc = elementUtils.getDocComment(blueprintElement);
        // we need to have Blueprint
        Optional<Annotation> blueprintAnnotationOpt = blueprint.findAnnotation(BLUEPRINT_TYPE);
        Optional<Annotation> configuredAnnoOpt = blueprint.findAnnotation(CONFIGURED_TYPE);
        Optional<Annotation> implementAnnoOpt = blueprint.findAnnotation(IMPLEMENT_TYPE);

        boolean isConfigured = configuredAnnoOpt.isPresent();

        if (blueprintAnnotationOpt.isEmpty()) {
            throw new IllegalStateException("Cannot get @Prototype.Blueprint annotation when processing it for type "
                                                    + blueprint);
        }

        Annotation blueprintAnnotation =
                blueprintAnnotationOpt.orElseGet(() -> Annotation.create(BLUEPRINT_TYPE));
        Annotation configuredAnnotation =
                configuredAnnoOpt.orElseGet(() -> Annotation.create(CONFIGURED_TYPE));
        List<TypeName> prototypeImplements =
                implementAnnoOpt.map(TypeContext::prototypeImplements)
                        .orElseGet(List::of);

        Set<TypeName> extendList = new LinkedHashSet<>();
        Set<TypeName> superPrototypes = new LinkedHashSet<>();
        Set<TypeName> ignoreInterfaces = new LinkedHashSet<>();

        // add my blueprint
        extendList.add(blueprint.typeName());
        /// prototype (marker interface)
        extendList.add(PROTOTYPE_TYPE);

        gatherExtends(blueprint, extendList, superPrototypes, ignoreInterfaces);
        extendList.addAll(prototypeImplements);

        Optional<TypeName> superPrototype;
        if (superPrototypes.isEmpty()) {
            superPrototype = Optional.empty();
        } else {
            // the first prototype we reach is the one we extend, this is "best effort" approach
            // we could traverse the hierarchy more granularly to find the right one or throw
            // if we extend more than one prototype interface on the same level
            superPrototype = Optional.of(superPrototypes.iterator().next());
        }

        boolean beanStyleAccessors = blueprintAnnotation.getValue("beanStyle")
                .map(Boolean::parseBoolean)
                .orElse(false);


        /*
         * Find all valid builder methods
         */
        Errors.Collector errors = Errors.collector();
        List<PrototypeProperty> propertyMethods = new ArrayList<>();
        // default methods discovered on any interface (this one or extended) - these should not become properties
        Set<MethodSignature> ignoredMethods = new HashSet<>(IGNORED_METHODS);
        // all method signatures defined on super prototypes
        Set<MethodSignature> superPrototypeMethods = new HashSet<>();
        gatherBuilderProperties(processingContext,
                                blueprint,
                                errors,
                                propertyMethods,
                                ignoredMethods,
                                ignoreInterfaces,
                                beanStyleAccessors,
                                superPrototypeMethods);
        errors.collect().checkValid();

        /*
         now some properties on the current blueprint may be overriding properties from on of the super prototypes
         in such a case, we must handle it specifically
         - if it has a different default value, update it in the supertype (constructor should call setter with the default
         */
        List<PrototypeProperty> overridingProperties = new ArrayList<>();
        propertyMethods = propertyMethods.stream()
                .filter(it -> {
                    // filter out all properties from super prototypes
                    if (superPrototypeMethods.contains(it.signature())) {
                        overridingProperties.add(it);
                        return false;
                    }
                    return true;
                })
                .toList();

        // filter out duplicates
        Set<MethodSignature> addedSignatures = new HashSet<>();
        propertyMethods = propertyMethods.stream()
                .filter(it -> addedSignatures.add(it.signature()))
                .toList();

        boolean hasOptional = propertyMethods.stream()
                .map(PrototypeProperty::typeHandler)
                .anyMatch(it -> it.declaredType().genericTypeName().equals(Types.OPTIONAL_TYPE));
        boolean hasRequired = propertyMethods.stream()
                .map(PrototypeProperty::configuredOption)
                .anyMatch(PrototypeProperty.ConfiguredOption::required);
        boolean hasNonNulls = propertyMethods.stream()
                .map(PrototypeProperty::configuredOption)
                .anyMatch(PrototypeProperty.ConfiguredOption::validateNotNull);
        boolean prototypePublic = blueprintAnnotation.getValue("isPublic")
                .map(Boolean::parseBoolean)
                .orElse(true);
        // does not make sense to create public builder, if prototype interface is package local
        boolean builderPublic = blueprintAnnotation.getValue("builderPublic")
                .map(Boolean::parseBoolean)
                .orElse(true);
        boolean createFromConfigPublic = blueprintAnnotation.getValue("createFromConfigPublic")
                .map(Boolean::parseBoolean)
                .orElse(true);
        boolean createEmptyPublic = blueprintAnnotation.getValue("createEmptyPublic")
                .map(Boolean::parseBoolean)
                .orElse(true);
        boolean hasProvider = propertyMethods.stream()
                .map(PrototypeProperty::configuredOption)
                .map(PrototypeProperty.ConfiguredOption::provider)
                .filter(it -> it) // filter our falses
                .findFirst()
                .orElse(false);
        Optional<TypeName> builderInterceptor = blueprintAnnotation.getValue("builderInterceptor")
                .filter(Predicate.not(BUILDER_INTERCEPTOR::equals))
                .map(TypeName::create);

        // factory is if the blueprint implements Factory<RuntimeContractType>
        Optional<TypeInfo> factoryInterface = blueprint.interfaceTypeInfo()
                .stream()
                .filter(it -> PROTOTYPE_FACTORY_TYPE.equals(it.typeName().genericTypeName()))
                .findFirst();
        boolean isFactory = factoryInterface.isPresent();
        Optional<TypeName> runtimeObject = factoryInterface.map(it -> it.typeName().typeArguments().get(0));

        boolean isConfigRoot = configuredAnnotation.getValue("root")
                .map(Boolean::parseBoolean)
                .orElse(false);
        String configPrefix = configuredAnnotation.getValue("prefix")
                .orElse("");

        TypeName prototype = generatedTypeName(blueprint);

        TypeName prototypeImpl = TypeName.builder(prototype)
                .className(prototype.className() + "Impl")
                .build();

        TypeName prototypeBuilder = TypeName.builder(prototype)
                .addEnclosingName(prototype.className())
                .className("Builder")
                .build();

        TypeInformation typeInformation = new TypeInformation(blueprint,
                                                              prototype,
                                                              prototypeBuilder,
                                                              prototypeImpl,
                                                              runtimeObject,
                                                              builderInterceptor,
                                                              superPrototype,
                                                              annotationsToGenerate(blueprint));

        String typeArgs = toGeneratedTypeArguments(blueprint.typeName());
        return new TypeContext(
                typeInformation,
                new BlueprintData(
                        prototypePublic,
                        builderPublic,
                        createFromConfigPublic,
                        createEmptyPublic,
                        isFactory,
                        extendList,
                        javadoc,
                        typeArgs),
                new TypeContext.ConfiguredData(isConfigured,
                                               isConfigRoot,
                                               configPrefix),
                new TypeContext.PropertyData(hasOptional,
                                             hasRequired,
                                             hasNonNulls,
                                             hasProvider,
                                             propertyMethods,
                                             overridingProperties),
                CustomMethods.create(processingContext, typeInformation));
    }

    static List<String> annotationsToGenerate(Annotated annotated) {
        return annotated.findAnnotation(Types.PROTOTYPE_ANNOTATED_TYPE)
                .flatMap(Annotation::value)
                .map(annotation -> annotation.split(","))
                .map(List::of)
                .orElseGet(List::of)
                .stream()
                .map(it -> "@" + it)
                .toList();
    }

    private static List<TypeName> prototypeImplements(Annotation annotation) {
        return annotation.value()
                .map(value -> {
                    return Stream.of(value.split(","))
                            .map(TypeName::create)
                            .toList();
                })
                .orElseGet(List::of);
    }

    private static void gatherExtends(TypeInfo typeInfo, Set<TypeName> extendList,
                                      Set<TypeName> superPrototypes,
                                      Set<TypeName> ignoredInterfaces) {
        // if any implemented interface is a @Blueprint, we must extend the target type as well
        // as any implemented interface is already a Prototype, we ignore additional annotations (it is our super type)
        List<TypeInfo> typeInfos = typeInfo.interfaceTypeInfo();
        for (TypeInfo info : typeInfos) {
            if (info.findAnnotation(BLUEPRINT_TYPE).isPresent()) {
                // this is a blueprint, we must implement its built type
                TypeName typeName = info.typeName();
                String className = typeName.className();
                TypeName toExtend = TypeName.builder(typeName)
                        .className(className.substring(0, className.length() - 9))
                        .build();
                extendList.add(toExtend);
                superPrototypes.add(toExtend);
                ignoredInterfaces.add(toExtend);
                ignoredInterfaces.add(typeName);
            }
            boolean gatherAll = true;
            for (TypeInfo implementedInterface : info.interfaceTypeInfo()) {
                if (implementedInterface.typeName().equals(PROTOTYPE_TYPE)) {
                    extendList.add(info.typeName());
                    // this is a prototype itself, ignore additional interfaces
                    gatherAll = false;
                    superPrototypes.add(info.typeName());
                    ignoredInterfaces.add(info.typeName());
                    ignoredInterfaces.add(TypeName.builder(info.typeName())
                                                  .className(info.typeName().className() + "Blueprint")
                                                  .build());
                    // also add all super interfaces of the prototype
                    info.interfaceTypeInfo()
                            .stream()
                            .map(TypeInfo::typeName)
                            .map(TypeName::genericTypeName)
                            .forEach(ignoredInterfaces::add);
                    break;
                }
            }
            if (gatherAll) {
                gatherExtends(info, extendList, superPrototypes, ignoredInterfaces);
            }
        }
    }

    @SuppressWarnings("checkstyle:ParameterNumber") // we need all of them
    private static void gatherBuilderProperties(ProcessingContext processingContext,
                                                TypeInfo typeInfo,
                                                Errors.Collector errors,
                                                List<PrototypeProperty> properties,
                                                Set<MethodSignature> ignoredMethods,
                                                Set<TypeName> ignoreInterfaces,
                                                boolean beanStyleAccessors,
                                                Set<MethodSignature> superPrototypeMethods) {

        // we are only interested in getter methods
        TypeName typeName = typeInfo.typeName();
        properties.addAll(typeInfo.elementInfo().stream()
                                  .filter(TypeInfoPredicates::isMethod)
                                  .filter(Predicate.not(TypeInfoPredicates::isStatic))
                                  .filter(Predicate.not(TypeInfoPredicates::isPrivate))
                                  .filter(it -> {
                                      if (it.modifiers().contains(TypeValues.MODIFIER_DEFAULT)) {
                                          ignoredMethods.add(MethodSignature.create(it));
                                          return false;
                                      }
                                      return true;
                                  })
                                  .filter(TypeInfoPredicates.notIgnoredMethod(ignoredMethods, IGNORED_NAMES))
                                  .filter(it -> {
                                      // if the method is defined on a super prototype, add it to the set
                                      if (ignoreInterfaces.contains(it.enclosingType().get())) {
                                          // collect all methods from super prototypes, so we know how to handle overrides
                                          superPrototypeMethods.add(MethodSignature.create(it));
                                      }
                                      if (ignoreInterfaces.contains(it.enclosingType().get())) {
                                          // if this method is defined on an ignored interface, filter it out
                                          return false;
                                      }
                                      return true;
                                  })
                                  .filter(it -> {
                                      Severity severity = it.hasAnnotation(CONFIGURED_OPTION_TYPE)
                                              ? Severity.FATAL
                                              : Severity.WARN;

                                      // parameters and return type
                                      if (it.typeName().equals(VOID_TYPE)) {
                                          // invalid return type for builder

                                          errors.message("Builder definition methods cannot have void return type "
                                                                 + "(must be getters): "
                                                                 + typeName + "." + it.elementName(),
                                                         severity);
                                          return false;
                                      }
                                      if (it.parameterArguments().size() != 0) {
                                          errors.message("Builder definition methods cannot have "
                                                                 + "parameters (must be getters): "
                                                                 + typeName + "." + it.elementName(),
                                                         severity);
                                          return false;
                                      }

                                      return true;
                                  })
                                  .filter(it -> !it.findAnnotation(CONFIGURED_OPTION_TYPE)
                                          .flatMap(annot -> annot.getValue("notConfigured"))
                                          .map(Boolean::parseBoolean)
                                          .orElse(false))
                                  // filter out Supplier.get()
                                  .filter(it -> !("get".equals(it.elementName()) && "T".equals(it.typeName().className())))
                                  .map(it -> PrototypeProperty.create(processingContext,
                                                                      typeInfo,
                                                                      it,
                                                                      beanStyleAccessors))
                                  .toList());

        // we also need to add info for all implemented interfaces
        List<TypeInfo> interfaces = typeInfo.interfaceTypeInfo();

        for (TypeInfo anInterface : interfaces) {
            gatherBuilderProperties(processingContext,
                                    anInterface,
                                    errors,
                                    properties,
                                    ignoredMethods,
                                    ignoreInterfaces,
                                    beanStyleAccessors,
                                    superPrototypeMethods);
        }
    }

    private static TypeName generatedTypeName(TypeInfo typeInfo) {
        String typeName = typeInfo.typeName().className();
        if (typeName.endsWith(BLUEPRINT)) {
            typeName = typeName.substring(0, typeName.length() - BLUEPRINT.length());
        } else {
            throw new IllegalArgumentException("Blueprint interface name must end with " + BLUEPRINT
                                                       + ", this is invalid type: " + typeInfo.typeName().fqName());
        }

        return TypeName.builder(typeInfo.typeName())
                .enclosingNames(List.of())
                .className(typeName)
                .build();
    }

    private static String toGeneratedTypeArguments(TypeName typeName) {
        if (typeName.typeArguments().isEmpty()) {
            return "";
        }
        String types = typeName.typeArguments()
                .stream()
                .map(TypeName::className)
                .collect(Collectors.joining(", "));
        return "<" + types + ">";
    }

    record TypeInformation(
            TypeInfo blueprintType,
            TypeName prototype,
            TypeName prototypeBuilder,
            TypeName prototypeImpl,
            Optional<TypeName> runtimeObject,
            Optional<TypeName> builderInterceptor,
            Optional<TypeName> superPrototype,
            List<String> annotationsToGenerate) {
        public TypeName prototypeBuilderBase() {
            return TypeName.builder(prototypeBuilder)
                    .className(prototypeBuilder.className() + "Base")
                    .build()
                    .genericTypeName();
        }
    }

    record BlueprintData(
            boolean prototypePublic,
            boolean builderPublic,
            boolean createFromConfigPublic,
            boolean createEmptyPublic,
            boolean isFactory,
            Set<TypeName> extendsList,
            String javadoc,
            String typeArguments) {
    }

    record ConfiguredData(
            boolean configured,
            boolean root,
            String prefix) {
    }

    record PropertyData(
            boolean hasOptional,
            boolean hasRequired,
            boolean hasNonNulls,
            boolean hasProvider,
            List<PrototypeProperty> properties,
            List<PrototypeProperty> overridingProperties) {
    }

    record MethodSignature(TypeName returnType, String name, List<TypeName> arguments) {
        public static MethodSignature create(TypedElementInfo info) {
            return new MethodSignature(info.typeName(),
                                       info.elementName(),
                                       info.parameterArguments().stream()
                                               .map(TypedElementInfo::typeName)
                                               .toList());
        }
    }
}
