
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

package io.helidon.builder.codegen;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import io.helidon.codegen.CodegenContext;
import io.helidon.codegen.ElementInfoPredicates;
import io.helidon.common.Errors;
import io.helidon.common.Severity;
import io.helidon.common.types.Annotated;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.Modifier;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;

import static io.helidon.builder.codegen.Types.PROTOTYPE_BUILDER_DECORATOR;
import static io.helidon.builder.codegen.Types.PROTOTYPE_FACTORY;
import static io.helidon.common.types.TypeNames.OBJECT;
import static io.helidon.common.types.TypeNames.OPTIONAL;

record TypeContext(
        TypeInformation typeInfo,
        AnnotationDataBlueprint blueprintData,
        AnnotationDataConfigured configuredData,
        PropertyData propertyData,
        CustomMethods customMethods) {

    private static final Set<String> IGNORED_NAMES = Set.of("build",
                                                            "get",
                                                            "buildPrototype");
    private static final String BLUEPRINT = "Blueprint";
    private static final Set<MethodSignature> IGNORED_METHODS = Set.of(
            // equals, hash code and toString
            new MethodSignature(TypeName.create(boolean.class), "equals", List.of(OBJECT)),
            new MethodSignature(TypeName.create(int.class), "hashCode", List.of()),
            new MethodSignature(TypeNames.STRING, "toString", List.of())
    );

    @SuppressWarnings("checkstyle:MethodLength") // use a lot of lines for parameter formatting
    static TypeContext create(CodegenContext ctx, TypeInfo blueprint) {
        String javadoc = blueprint.description().orElse(null);
        // we need to have Blueprint
        Optional<Annotation> blueprintAnnotationOpt = blueprint.findAnnotation(Types.PROTOTYPE_BLUEPRINT);
        Optional<Annotation> implementAnnoOpt = blueprint.findAnnotation(Types.PROTOTYPE_IMPLEMENT);


        if (blueprintAnnotationOpt.isEmpty()) {
            throw new IllegalStateException("Cannot get @Prototype.Blueprint annotation when processing it for type "
                                                    + blueprint);
        }

        Annotation blueprintAnnotation =
                blueprintAnnotationOpt.orElseGet(() -> Annotation.create(Types.PROTOTYPE_BLUEPRINT));
        List<TypeName> prototypeImplements =
                implementAnnoOpt.map(TypeContext::prototypeImplements)
                        .orElseGet(List::of);

        Set<TypeName> extendList = new LinkedHashSet<>();
        Set<TypeName> superPrototypes = new LinkedHashSet<>();
        Set<TypeName> ignoreInterfaces = new LinkedHashSet<>();

        // add my blueprint
        extendList.add(blueprint.typeName());
        /// prototype (marker interface)
        extendList.add(Types.PROTOTYPE_API);

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
        gatherBuilderProperties(ctx,
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
                .anyMatch(it -> it.declaredType().genericTypeName().equals(OPTIONAL));
        boolean hasRequired = propertyMethods.stream()
                .map(PrototypeProperty::configuredOption)
                .anyMatch(AnnotationDataOption::required);
        boolean hasNonNulls = propertyMethods.stream()
                .map(PrototypeProperty::configuredOption)
                .anyMatch(AnnotationDataOption::validateNotNull);
        boolean hasAllowedValues = propertyMethods.stream()
                .map(PrototypeProperty::configuredOption)
                .anyMatch(AnnotationDataOption::hasAllowedValues);
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
                .map(AnnotationDataOption::provider)
                .filter(it -> it) // filter our falses
                .findFirst()
                .orElse(false);
        Optional<TypeName> decorator = blueprintAnnotation.getValue("decorator")
                .map(TypeName::create)
                .filter(Predicate.not(PROTOTYPE_BUILDER_DECORATOR::equals));

        // factory is if the blueprint implements Factory<RuntimeContractType>
        Optional<TypeInfo> factoryInterface = blueprint.interfaceTypeInfo()
                .stream()
                .filter(it -> PROTOTYPE_FACTORY.equals(it.typeName().genericTypeName()))
                .findFirst();
        boolean isFactory = factoryInterface.isPresent();
        Optional<TypeName> runtimeObject = factoryInterface.map(it -> it.typeName().typeArguments().getFirst());

        AnnotationDataConfigured configured = AnnotationDataConfigured.create(blueprint);

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
                                                              decorator,
                                                              superPrototype,
                                                              annotationsToGenerate(blueprint));

        return new TypeContext(
                typeInformation,
                new AnnotationDataBlueprint(
                        prototypePublic,
                        builderPublic,
                        createFromConfigPublic,
                        createEmptyPublic,
                        isFactory,
                        extendList,
                        javadoc,
                        blueprint.typeName().typeArguments()),
                configured,
                new PropertyData(hasOptional,
                                             hasRequired,
                                             hasNonNulls,
                                             hasProvider,
                                             hasAllowedValues,
                                             propertyMethods,
                                             overridingProperties),
                CustomMethods.create(ctx, typeInformation));
    }

    static List<String> annotationsToGenerate(Annotated annotated) {
        return annotated.findAnnotation(Types.PROTOTYPE_ANNOTATED)
                .flatMap(Annotation::stringValues)
                .stream()
                .flatMap(List::stream)
                .toList();
    }

    private static List<TypeName> prototypeImplements(Annotation annotation) {
        return annotation.stringValues()
                .stream()
                .flatMap(List::stream)
                .map(TypeName::create)
                .toList();
    }

    private static void gatherExtends(TypeInfo typeInfo, Set<TypeName> extendList,
                                      Set<TypeName> superPrototypes,
                                      Set<TypeName> ignoredInterfaces) {
        // if any implemented interface is a @Blueprint, we must extend the target type as well
        // as any implemented interface is already a Prototype, we ignore additional annotations (it is our super type)
        List<TypeInfo> typeInfos = typeInfo.interfaceTypeInfo();
        for (TypeInfo info : typeInfos) {
            if (info.findAnnotation(Types.PROTOTYPE_BLUEPRINT).isPresent()) {
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
                if (implementedInterface.typeName().equals(Types.PROTOTYPE_API)) {
                    extendList.add(info.typeName());
                    // this is a prototype itself, ignore additional interfaces
                    gatherAll = false;
                    superPrototypes.add(info.typeName());

                    // we need to ignore ANY interface implemented by "info" and its super interfaces
                    if (ignoredInterfaces.add(info.typeName().genericTypeName())) {
                        ignoredInterfaces.add(TypeName.builder(info.typeName())
                                                      .className(info.typeName().className() + "Blueprint")
                                                      .build());
                        ignoreAllInterfaces(ignoredInterfaces, info);
                    }
                    break;
                }
            }
            if (gatherAll) {
                gatherExtends(info, extendList, superPrototypes, ignoredInterfaces);
            }
        }
    }

    private static void ignoreAllInterfaces(Set<TypeName> ignoredInterfaces, TypeInfo info) {
        // also add all super interfaces of the prototype
        List<TypeInfo> superIfaces = info.interfaceTypeInfo();

        for (TypeInfo superIface : superIfaces) {
            if (ignoredInterfaces.add(superIface.typeName().genericTypeName())) {
                ignoreAllInterfaces(ignoredInterfaces, superIface);
            }
        }
    }

    @SuppressWarnings("checkstyle:ParameterNumber") // we need all of them
    private static void gatherBuilderProperties(CodegenContext ctx,
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
                                  .filter(ElementInfoPredicates::isMethod)
                                  .filter(Predicate.not(ElementInfoPredicates::isStatic))
                                  .filter(Predicate.not(ElementInfoPredicates::isPrivate))
                                  .filter(it -> {
                                      if (it.elementModifiers().contains(Modifier.DEFAULT)) {
                                          ignoredMethods.add(MethodSignature.create(it));
                                          return false;
                                      }
                                      return true;
                                  })
                                  .filter(it -> {
                                      if (IGNORED_NAMES.contains(it.elementName())) {
                                          return false;
                                      }
                                      return !ignoredMethods.contains(MethodSignature.create(it));
                                  })
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
                                      Severity severity = Severity.WARN;

                                      // parameters and return type
                                      if (it.typeName().equals(TypeNames.PRIMITIVE_VOID)) {
                                          // invalid return type for builder

                                          errors.message("Builder definition methods cannot have void return type "
                                                                 + "(must be getters): "
                                                                 + typeName + "." + it.elementName(),
                                                         severity);
                                          return false;
                                      }
                                      if (!it.parameterArguments().isEmpty()) {
                                          errors.message("Builder definition methods cannot have "
                                                                 + "parameters (must be getters): "
                                                                 + typeName + "." + it.elementName(),
                                                         severity);
                                          return false;
                                      }

                                      return true;
                                  })
                                  // filter out Supplier.get()
                                  .filter(it -> !("get".equals(it.elementName()) && "T".equals(it.typeName().className())))
                                  .map(it -> PrototypeProperty.create(ctx,
                                                                      typeInfo,
                                                                      it,
                                                                      beanStyleAccessors))
                                  .toList());

        // we also need to add info for all implemented interfaces
        List<TypeInfo> interfaces = typeInfo.interfaceTypeInfo();

        for (TypeInfo anInterface : interfaces) {
            gatherBuilderProperties(ctx,
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

    record TypeInformation(
            TypeInfo blueprintType,
            TypeName prototype,
            TypeName prototypeBuilder,
            TypeName prototypeImpl,
            Optional<TypeName> runtimeObject,
            Optional<TypeName> decorator,
            Optional<TypeName> superPrototype,
            List<String> annotationsToGenerate) {
        public TypeName prototypeBuilderBase() {
            return TypeName.builder(prototypeBuilder)
                    .className(prototypeBuilder.className() + "Base")
                    .build()
                    .genericTypeName();
        }
    }

    record PropertyData(
            boolean hasOptional,
            boolean hasRequired,
            boolean hasNonNulls,
            boolean hasProvider,
            boolean hasAllowedValues,
            List<PrototypeProperty> properties,
            List<PrototypeProperty> overridingProperties) {
    }

}
