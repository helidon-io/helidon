/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.json.codegen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.helidon.codegen.CodegenContext;
import io.helidon.codegen.CodegenException;
import io.helidon.codegen.ElementInfoPredicates;
import io.helidon.codegen.classmodel.TypeArgument;
import io.helidon.common.AccessorStyle;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.ElementSignature;
import io.helidon.common.types.Modifier;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;

import static io.helidon.common.types.TypeNames.OBJECT;
import static io.helidon.common.types.TypeNames.PRIMITIVE_BOOLEAN;
import static io.helidon.common.types.TypeNames.PRIMITIVE_INT;
import static io.helidon.common.types.TypeNames.PRIMITIVE_VOID;
import static io.helidon.common.types.TypeNames.STRING;
import static java.util.function.Predicate.not;

record ConvertedTypeInfo(TypeName converterType,
                         TypeName originalType,
                         TypeName wildcardsGenerics,
                         TypeName objectsGenerics,
                         boolean nullable,
                         boolean failOnUnknown,
                         Map<String, JsonProperty> jsonProperties,
                         Comparator<String> orderedProperties,
                         CreatorInfo creatorInfo,
                         Optional<BuilderInfo> builderInfo, Map<TypeName, Integer> genericParamsWithIndexes) {

    private static final Set<ElementSignature> IGNORED_METHODS = Set.of(
            // equals, hash code and toString
            ElementSignature.createMethod(PRIMITIVE_BOOLEAN, "equals", List.of(OBJECT)),
            ElementSignature.createMethod(PRIMITIVE_INT, "hashCode", List.of()),
            ElementSignature.createMethod(STRING, "toString", List.of())
    );

    private static final Set<TypeName> IGNORED_BUILDER_METHOD_PARAM_TYPES = Set.of(
            TypeNames.CONSUMER,
            TypeNames.OPTIONAL,
            TypeNames.SUPPLIER
    );

    private static final Map<String, Comparator<String>> PROPERTY_ORDER = Map.of(
            "ALPHABETICAL", Comparator.naturalOrder(),
            "REVERSE_ALPHABETICAL", Comparator.reverseOrder());

    public static ConvertedTypeInfo create(TypeInfo typeInfo, CodegenContext ctx) {
        String classNameWithEnclosingNames = typeInfo.typeName().classNameWithEnclosingNames();
        String replacedDot = classNameWithEnclosingNames.replace(".", "_");
        String nameBase = typeInfo.typeName().fqName().replace(classNameWithEnclosingNames, replacedDot);
        TypeName converterTypeName = TypeName.create(nameBase + "__GeneratedConverter");
        AccessorStyle recordAccessors = typeInfo.annotation(JsonTypes.JSON_ENTITY)
                .enumValue("accessorStyle", AccessorStyle.class)
                .orElse(AccessorStyle.AUTO);
        if (typeInfo.kind() == ElementKind.RECORD && recordAccessors.equals(AccessorStyle.AUTO)) {
            recordAccessors = AccessorStyle.RECORD;
        }
        boolean nullable = obtainClassAnnotationFromHierarchy(JsonTypes.JSON_SERIALIZE_NULLS, typeInfo)
                .flatMap(Annotation::booleanValue)
                .orElse(JsonCodegenOptions.CODEGEN_JSON_NULL.value(ctx.options()));
        boolean failOnUnknown = obtainClassAnnotationFromHierarchy(JsonTypes.JSON_FAIL_ON_UNKNOWN, typeInfo)
                .flatMap(Annotation::booleanValue)
                .orElse(JsonCodegenOptions.CODEGEN_JSON_UNKNOWN.value(ctx.options()));
        String orderStrategy = obtainClassAnnotationFromHierarchy(JsonTypes.JSON_PROPERTY_ORDER, typeInfo)
                .flatMap(Annotation::stringValue)
                .orElse(JsonCodegenOptions.CODEGEN_JSON_ORDER.value(ctx.options()));
        Map<String, JsonProperty.Builder> properties = new LinkedHashMap<>();
        Map<String, Map<String, TypeName>> resolvedGenerics = new LinkedHashMap<>();
        Map<TypeName, Integer> genericParamsWithIndexes = obtainGenericParamsWithIndexes(typeInfo);
        discoverAllPossibleGenerics(typeInfo, resolvedGenerics, Map.of());
        discoverFields(properties, typeInfo, nullable, resolvedGenerics);
        discoverGetAndSetMethods(properties, typeInfo, recordAccessors, resolvedGenerics);
        Optional<Annotation> builderAnnotation = typeInfo.findAnnotation(JsonTypes.JSON_BUILDER_INFO);
        Optional<BuilderInfo> builderInfo = builderAnnotation.flatMap(Annotation::stringValue)
                .map(TypeName::create)
                .flatMap(ctx::typeInfo)
                .flatMap(it -> processBuilderInfoFromClass(it,
                                                           typeInfo,
                                                           null,
                                                           builderAnnotation,
                                                           properties,
                                                           resolvedGenerics))
                .or(() -> processBuilderInfo(typeInfo, properties, ctx, resolvedGenerics));
        CreatorInfo creatorInfo = discoverCreator(properties, typeInfo, resolvedGenerics);
        Map<String, JsonProperty> jsonProperties = finalizeJsonProperties(properties);
        Comparator<String> orderComparator = PROPERTY_ORDER.getOrDefault(orderStrategy, (o1, o2) -> 0);
        return new ConvertedTypeInfo(converterTypeName,
                                     typeInfo.typeName(),
                                     transformGenerics(typeInfo.typeName(), TypeArgument.create("?")),
                                     transformGenerics(typeInfo.typeName(), TypeArgument.create(OBJECT)),
                                     nullable,
                                     failOnUnknown,
                                     jsonProperties,
                                     orderComparator,
                                     creatorInfo,
                                     builderInfo,
                                     genericParamsWithIndexes);
    }

    private static Map<TypeName, Integer> obtainGenericParamsWithIndexes(TypeInfo typeInfo) {
        //We need to find all the generic parameters of the currently created type. No super types needed.
        //This is needed for later converter generation, so we can determine a proper index to get the runtime type from
        List<TypeName> typeArguments = typeInfo.typeName().typeArguments();
        Map<TypeName, Integer> argumentsWithIndexes = new HashMap<>();
        for (int i = 0; i < typeArguments.size(); i++) {
            argumentsWithIndexes.put(typeArguments.get(i), i);
        }
        return argumentsWithIndexes;
    }

    private static void discoverAllPossibleGenerics(TypeInfo typeInfo,
                                                    Map<String, Map<String, TypeName>> resolvedGenerics,
                                                    Map<String, TypeName> childGenerics) {
        Map<String, TypeName> typeGenerics = new HashMap<>();
        TypeName typeName = typeInfo.typeName();
        for (int i = 0; i < typeName.typeParameters().size(); i++) {
            String parameterName = typeName.typeParameters().get(i);
            TypeName typeValue = typeName.typeArguments().get(i);
            if (typeValue.generic()) {
                //We will try to resolve it from the child actual parameters
                typeValue = childGenerics.getOrDefault(typeValue.toString(), typeValue);
            }
            typeGenerics.put(parameterName, typeValue);
        }
        resolvedGenerics.put(typeName.fqName(), typeGenerics);

        Optional<TypeInfo> superTypeInfo = typeInfo.superTypeInfo();
        superTypeInfo.ifPresent(info -> discoverAllPossibleGenerics(info, resolvedGenerics, typeGenerics));
    }

    static boolean needsResolving(TypeName typeName) {
        for (TypeName typeArgument : typeName.typeArguments()) {
            if (needsResolving(typeArgument)) {
                return true;
            }
        }
        return typeName.generic();
    }

    private static Optional<BuilderInfo> processBuilderInfo(TypeInfo createdTypeInfo,
                                                            Map<String, JsonProperty.Builder> properties,
                                                            CodegenContext ctx,
                                                            Map<String, Map<String, TypeName>> resolvedGenerics) {
        Optional<TypedElementInfo> builderMethod = findHelidonBuilderMethod(createdTypeInfo, ctx);
        if (builderMethod.isEmpty()) {
            return Optional.empty();
        }
        TypeName builderTypeName = builderMethod.get().typeName();
        TypeInfo builderTypeInfo = ctx.typeInfo(builderTypeName)
                .orElseThrow(() -> new CodegenException("Could not find builder type: " + builderTypeName, createdTypeInfo));
        discoverAllPossibleGenerics(builderTypeInfo, resolvedGenerics, Map.of());
        return processBuilderInfoFromClass(builderTypeInfo,
                                           createdTypeInfo,
                                           builderMethod.get().elementName(),
                                           Optional.empty(),
                                           properties,
                                           resolvedGenerics);
    }

    private static Optional<BuilderInfo> processBuilderInfoFromClass(TypeInfo builderTypeInfo,
                                                                     TypeInfo createdTypeInfo,
                                                                     String builderMethodName,
                                                                     Optional<Annotation> builderAnnotation,
                                                                     Map<String, JsonProperty.Builder> properties,
                                                                     Map<String, Map<String, TypeName>> resolvedGenerics) {
        String builderMethodPrefix = builderAnnotation.flatMap(it -> it.stringValue("methodPrefix"))
                .orElse("");
        String buildMethod = builderAnnotation.flatMap(it -> it.stringValue("buildMethod"))
                .orElse("build");

        if (!checkBuildMethod(builderTypeInfo, createdTypeInfo, buildMethod)) {
            throw new CodegenException("Build method with the name \"" + buildMethod
                                               + "\" does not exist or does not return: " + createdTypeInfo.typeName().fqName(),
                                       createdTypeInfo);
        }

        processBuilderMethods(builderTypeInfo, builderTypeInfo, properties, resolvedGenerics, builderMethodPrefix);

        return Optional.of(new BuilderInfo(builderTypeInfo.typeName(),
                                           Optional.ofNullable(builderMethodName),
                                           buildMethod));
    }

    private static void processBuilderMethods(TypeInfo builderTypeInfo, //The builder we are using
                                              TypeInfo currentTypeInfo, //Currently processed type info
                                              Map<String, JsonProperty.Builder> properties,
                                              Map<String, Map<String, TypeName>> resolvedGenerics,
                                              String builderMethodPrefix) {
        currentTypeInfo.superTypeInfo()
                .ifPresent(superType -> processBuilderMethods(builderTypeInfo,
                                                              superType,
                                                              properties,
                                                              resolvedGenerics,
                                                              builderMethodPrefix));

        for (TypeInfo interf : currentTypeInfo.interfaceTypeInfo()) {
            processBuilderMethods(builderTypeInfo, interf, properties, resolvedGenerics, builderMethodPrefix);
        }

        // Find all builder methods (withXXX methods)
        List<TypedElementInfo> builderMethods = currentTypeInfo.elementInfo()
                .stream()
                .filter(ElementInfoPredicates::isMethod)
                .filter(not(ElementInfoPredicates::isPrivate))
                .filter(not(ElementInfoPredicates::isStatic))
                .filter(method -> !method.hasAnnotation(JsonTypes.JSON_IGNORE))
                .filter(method -> method.elementName().startsWith(builderMethodPrefix))
                .filter(it -> resolveGenerics(it.typeName(), currentTypeInfo, resolvedGenerics)
                        .equals(builderTypeInfo.typeName()))
                .filter(it -> it.parameterArguments().size() == 1)
                .filter(it -> !it.elementName().equals("from")) //To ignore "from" methods of the generated Builder
                .filter(it -> !it.elementName().equals("config")) //To ignore "configure" methods of the generated Builder
                .filter(it -> !IGNORED_BUILDER_METHOD_PARAM_TYPES.contains(it.typeName().genericTypeName()))
                .filter(not(ConvertedTypeInfo::isIgnored))
                .toList();

        // Configure properties with builder methods
        Set<String> builderProperties = new HashSet<>();
        for (TypedElementInfo method : builderMethods) {
            String methodName = method.elementName();
            String propertyName = methodToFieldName(builderMethodPrefix, methodName);

            if (builderProperties.contains(method.elementName())) {
                // Multiple builder methods found for a property; ignoring duplicates.
                continue;
            }

            TypedElementInfo parameter = method.parameterArguments().getFirst();

            JsonProperty.Builder jsonPropertyBuilder = properties.computeIfAbsent(propertyName,
                                                                                  name -> JsonProperty.builder());

            jsonPropertyBuilder.usedInBuilder(true)
                    .setterName(methodName)
                    .deserializationNameIfNotSet(propertyName)
                    .deserializationType(resolveGenerics(parameter.typeName(), builderTypeInfo, resolvedGenerics))
                    .deserializationName(obtainStringFromAnnotation(parameter, JsonTypes.JSON_PROPERTY))
                    .deserializer(obtainTypeNameFromAnnotation(parameter, JsonTypes.JSON_CONVERTER))
                    .deserializer(obtainTypeNameFromAnnotation(parameter, JsonTypes.JSON_DESERIALIZER));

            builderProperties.add(propertyName);
        }
    }

    private static boolean checkBuildMethod(TypeInfo builderTypeInfo, TypeInfo originalTypeInfo, String buildMethodName) {
        return builderTypeInfo.elementInfo()
                .stream()
                .filter(it -> it.elementName().equals(buildMethodName))
                .filter(ElementInfoPredicates::isMethod)
                .filter(not(ElementInfoPredicates::isPrivate))
                .filter(not(ElementInfoPredicates::isStatic))
                .filter(ElementInfoPredicates::hasNoArgs)
                .anyMatch(it -> it.typeName().equals(originalTypeInfo.typeName()));
    }

    private static Optional<TypedElementInfo> findHelidonBuilderMethod(TypeInfo typeInfo, CodegenContext ctx) {
        return typeInfo.elementInfo()
                .stream()
                .filter(it -> it.elementName().equals("builder"))
                .filter(ElementInfoPredicates::isMethod)
                .filter(not(ElementInfoPredicates::isPrivate))
                .filter(ElementInfoPredicates::isStatic)
                .filter(not(ConvertedTypeInfo::isIgnored))
                .filter(ElementInfoPredicates::hasNoArgs)
                .filter(it -> ctx.typeInfo(it.typeName())
                        .map(info -> info.findInHierarchy(JsonTypes.BUILDER_TYPE).isPresent())
                        .orElse(false))
                .findFirst();
    }

    private static void discoverFields(Map<String, JsonProperty.Builder> properties,
                                       TypeInfo typeInfo,
                                       boolean nullable,
                                       Map<String, Map<String, TypeName>> resolvedGenerics) {
        typeInfo.superTypeInfo()
                .ifPresent(superType -> discoverFields(properties, superType, nullable, resolvedGenerics));

        List<TypedElementInfo> fields = typeInfo.elementInfo()
                .stream()
                .filter(ElementInfoPredicates::isField)
                .filter(not(ElementInfoPredicates::isStatic))
                .toList();

        for (TypedElementInfo field : fields) {
            String fieldName = field.elementName();
            TypeName fieldType = resolveGenerics(field.typeName(), typeInfo, resolvedGenerics);
            JsonProperty.Builder builder = JsonProperty.builder()
                    .fieldName(fieldName)
                    .deserializationName(fieldName)
                    .serializationName(fieldName)
                    .deserializationType(fieldType)
                    .serializationType(fieldType)
                    .directFieldRead(field.accessModifier() != AccessModifier.PRIVATE)
                    .directFieldWrite(field.accessModifier() != AccessModifier.PRIVATE
                                              && !field.elementModifiers().contains(Modifier.FINAL))
                    .nullable(nullable);

            obtainStringFromAnnotation(field, JsonTypes.JSON_PROPERTY)
                    .ifPresent(value -> builder.serializationName(value).deserializationName(value));
            obtainTypeNameFromAnnotation(field, JsonTypes.JSON_CONVERTER)
                    .ifPresent(value -> builder.serializer(value).deserializer(value));
            obtainTypeNameFromAnnotation(field, JsonTypes.JSON_SERIALIZER).ifPresent(builder::serializer);
            obtainTypeNameFromAnnotation(field, JsonTypes.JSON_DESERIALIZER).ifPresent(builder::deserializer);
            obtainBooleanFromAnnotation(field, JsonTypes.JSON_IGNORE).ifPresent(builder::fieldIgnored);
            field.findAnnotation(JsonTypes.JSON_REQUIRED).ifPresent(annotation -> builder.required(true));
            obtainBooleanFromAnnotation(field, JsonTypes.JSON_SERIALIZE_NULLS).ifPresent(builder::nullable);
            if (field.elementModifiers().contains(Modifier.TRANSIENT)) {
                builder.fieldIgnored(true);
            }
            properties.put(fieldName, builder);
        }
    }

    private static AccessorStyle discoverGetAndSetMethods(Map<String, JsonProperty.Builder> properties,
                                                          TypeInfo typeInfo,
                                                          AccessorStyle accessorStyle,
                                                          Map<String, Map<String, TypeName>> resolvedGenerics) {
        AccessorStyle detectedAccessorStyle = typeInfo.superTypeInfo()
                .map(superType -> discoverGetAndSetMethods(properties, superType, accessorStyle, resolvedGenerics))
                .orElse(accessorStyle);

        for (TypeInfo interf : typeInfo.interfaceTypeInfo()) {
            discoverGetAndSetMethods(properties, interf, detectedAccessorStyle, resolvedGenerics);
        }

        List<TypedElementInfo> methods = List.of();
        if (detectedAccessorStyle.equals(AccessorStyle.AUTO) || detectedAccessorStyle.equals(AccessorStyle.BEAN)) {
            methods = obtainAllAccessors(typeInfo, AccessorStyle.BEAN);
        }
        if (methods.isEmpty()) {
            if (detectedAccessorStyle.equals(AccessorStyle.AUTO) || detectedAccessorStyle.equals(AccessorStyle.RECORD)) {
                methods = obtainAllAccessors(typeInfo, AccessorStyle.RECORD);
                detectedAccessorStyle = AccessorStyle.RECORD;
            }
        } else {
            detectedAccessorStyle = AccessorStyle.BEAN;
        }

        for (TypedElementInfo method : methods) {
            String methodName = method.elementName();
            if (isGetter(method, detectedAccessorStyle)) {
                String prefix = detectedAccessorStyle == AccessorStyle.RECORD
                        ? ""
                        : (method.typeName().equals(PRIMITIVE_BOOLEAN) ? "is" : "get");
                String propertyName = methodToFieldName(prefix, methodName);
                JsonProperty.Builder property = properties.computeIfAbsent(propertyName, name -> JsonProperty.builder())
                        .getterName(methodName)
                        .serializationNameIfNotSet(propertyName)
                        .serializationType(resolveGenerics(method.typeName(), typeInfo, resolvedGenerics))
                        .serializationName(obtainStringFromAnnotation(method, JsonTypes.JSON_PROPERTY))
                        .serializer(obtainTypeNameFromAnnotation(method, JsonTypes.JSON_CONVERTER));
                obtainBooleanFromAnnotation(method, JsonTypes.JSON_IGNORE).ifPresent(property::getterIgnored);
                obtainBooleanFromAnnotation(method, JsonTypes.JSON_SERIALIZE_NULLS).ifPresent(property::nullable);
                method.findAnnotation(JsonTypes.JSON_REQUIRED).ifPresent(annotation -> property.required(true));
            } else if (typeInfo.kind() != ElementKind.RECORD && isSetter(method, detectedAccessorStyle)) {
                //bean style setter in regular classes
                String prefix = detectedAccessorStyle == AccessorStyle.RECORD ? "" : "set";
                String propertyName = methodToFieldName(prefix, methodName);
                TypeName parameterType = method.parameterArguments().getFirst().typeName();
                JsonProperty.Builder property = properties.computeIfAbsent(propertyName, name -> JsonProperty.builder())
                        .setterName(methodName)
                        .deserializationNameIfNotSet(propertyName)
                        .deserializationType(resolveGenerics(parameterType, typeInfo, resolvedGenerics))
                        .deserializationName(obtainStringFromAnnotation(method, JsonTypes.JSON_PROPERTY))
                        .deserializer(obtainTypeNameFromAnnotation(method, JsonTypes.JSON_CONVERTER));
                obtainBooleanFromAnnotation(method, JsonTypes.JSON_IGNORE).ifPresent(property::setterIgnored);
            }
            //Not valid getter or setter
        }
        return detectedAccessorStyle;
    }

    private static List<TypedElementInfo> obtainAllAccessors(TypeInfo typeInfo, AccessorStyle accessorStyle) {
        return typeInfo.elementInfo()
                .stream()
                .filter(ElementInfoPredicates::isMethod)
                .filter(not(ElementInfoPredicates::isPrivate))
                .filter(not(ElementInfoPredicates::isStatic))
                .filter(not(ConvertedTypeInfo::isIgnored))
                .filter(it -> isGetter(it, accessorStyle) || isSetter(it, accessorStyle))
                .toList();
    }

    private static CreatorInfo discoverCreator(Map<String, JsonProperty.Builder> properties,
                                               TypeInfo typeInfo,
                                               Map<String, Map<String, TypeName>> resolvedGenerics) {
        List<TypedElementInfo> creators = typeInfo.elementInfo()
                .stream()
                .filter(info -> info.hasAnnotation(JsonTypes.JSON_CREATOR))
                .toList();
        if (creators.isEmpty()) {
            if (typeInfo.kind() == ElementKind.RECORD) {
                //Handle record constructor as creator if none is explicitly selected
                creators = typeInfo.elementInfo()
                        .stream()
                        .filter(ElementInfoPredicates::isConstructor)
                        .toList();
                if (creators.size() > 1) {
                    throw new CodegenException("Only one record constructor is allowed. "
                                                       + "If multiple is needed, one has to be annotated with "
                                                       + JsonTypes.JSON_CREATOR, typeInfo);
                }
            } else {
                return new CreatorInfo(null, "", List.of());
            }
        } else if (creators.size() > 1) {
            throw new CodegenException("Only one Creator is allowed to be set", typeInfo);
        }
        TypedElementInfo creator = creators.getFirst();
        ElementKind creatorKind = creator.kind();
        if (creatorKind == ElementKind.METHOD && !creator.elementModifiers().contains(Modifier.STATIC)) {
            throw new CodegenException("Creator has to be either on constructor or static method", typeInfo);
        } else if (creator.accessModifier() == AccessModifier.PRIVATE) {
            throw new CodegenException("Creator has to be non-private", typeInfo);
        }
        String creatorMethod = creator.elementName();
        List<String> parameterNames = new ArrayList<>();
        for (TypedElementInfo parameter : creator.parameterArguments()) {
            String parameterName = parameter.elementName();
            parameterNames.add(parameterName);
            properties.computeIfAbsent(parameterName, name -> JsonProperty.builder())
                    .usedInCreator(true)
                    .deserializationName(parameterName)
                    .deserializationType(resolveGenerics(parameter.typeName(), typeInfo, resolvedGenerics))
                    .deserializationName(obtainStringFromAnnotation(parameter, JsonTypes.JSON_PROPERTY))
                    .deserializer(obtainTypeNameFromAnnotation(parameter, JsonTypes.JSON_CONVERTER))
                    .deserializer(obtainTypeNameFromAnnotation(parameter, JsonTypes.JSON_DESERIALIZER));
        }
        return new CreatorInfo(creatorKind, creatorMethod, parameterNames);
    }

    private static TypeName resolveGenerics(TypeName elementTypeName,
                                            TypeInfo typeInfo,
                                            Map<String, Map<String, TypeName>> resolvedGenerics) {
        if (needsResolving(elementTypeName)) {
            if (elementTypeName.generic()) {
                var typeGenerics = resolvedGenerics.getOrDefault(typeInfo.typeName().fqName(), Map.of());
                return typeGenerics.getOrDefault(elementTypeName.name(), elementTypeName);
            }
            TypeName.Builder builder = TypeName.builder()
                    .from(elementTypeName)
                    .typeArguments(List.of());
            elementTypeName.typeArguments().forEach(
                    arg -> builder.addTypeArgument(resolveGenerics(arg, typeInfo, resolvedGenerics)));
            return builder.build();
        }
        return elementTypeName;
    }

    private static boolean isGetter(TypedElementInfo typedElementInfo, AccessorStyle accessorStyle) {
        if (accessorStyle == AccessorStyle.RECORD) {
            return typedElementInfo.parameterArguments().isEmpty()
                    && !typedElementInfo.typeName().equals(PRIMITIVE_VOID);
        }
        String methodName = typedElementInfo.elementName();
        int length = -1;
        if (methodName.startsWith("get")) {
            length = 3;
        } else if (methodName.startsWith("is")) {
            length = 2;
        }
        return length > -1
                && methodName.length() > length
                && Character.isUpperCase(methodName.charAt(length))
                && typedElementInfo.parameterArguments().isEmpty()
                && !typedElementInfo.typeName().equals(PRIMITIVE_VOID);
    }

    private static boolean isSetter(TypedElementInfo typedElementInfo, AccessorStyle accessorStyle) {
        if (accessorStyle == AccessorStyle.RECORD) {
            return typedElementInfo.parameterArguments().size() == 1
                    && typedElementInfo.typeName().equals(PRIMITIVE_VOID);
        }
        String methodName = typedElementInfo.elementName();
        return methodName.startsWith("set")
                && methodName.length() > 3
                && Character.isUpperCase(methodName.charAt(3))
                && typedElementInfo.parameterArguments().size() == 1
                && typedElementInfo.typeName().equals(PRIMITIVE_VOID);
    }

    private static String methodToFieldName(String prefix, String methodName) {
        if (methodName.startsWith(prefix)) {
            String str = methodName.substring(prefix.length());
            if (str.isEmpty()) {
                return methodName;
            } else if (str.length() == 1) {
                return str.toLowerCase();
            } else {
                return Character.toLowerCase(str.charAt(0)) + str.substring(1);
            }
        }
        return methodName;
    }

    private static boolean isIgnored(TypedElementInfo elementInfo) {
        return IGNORED_METHODS.contains(elementInfo.signature());
    }

    private static Optional<String> obtainStringFromAnnotation(TypedElementInfo elementInfo, TypeName annotationType) {
        return elementInfo.findAnnotation(annotationType)
                .flatMap(Annotation::stringValue);
    }

    private static Optional<TypeName> obtainTypeNameFromAnnotation(TypedElementInfo elementInfo, TypeName annotationType) {
        return elementInfo.findAnnotation(annotationType)
                .flatMap(Annotation::typeValue);
    }

    private static Optional<Boolean> obtainBooleanFromAnnotation(TypedElementInfo elementInfo, TypeName annotationType) {
        return elementInfo.findAnnotation(annotationType)
                .flatMap(Annotation::booleanValue);
    }

    private static Map<String, JsonProperty> finalizeJsonProperties(Map<String, JsonProperty.Builder> properties) {
        Map<String, JsonProperty> finalProperties = new LinkedHashMap<>(properties.size());
        for (Map.Entry<String, JsonProperty.Builder> entry : properties.entrySet()) {
            JsonProperty.Builder builder = entry.getValue();
            if (!builder.directFieldRead()              // Field cant be read from
                    && !builder.directFieldWrite()      // Field cant be written to
                    && builder.setterName().isEmpty()   // Has no setter
                    && builder.getterName().isEmpty()   // Has no getter
                    && !builder.usedInBuilder()         // Is not used in the builder
                    && !builder.usedInCreator()) {      // Is not used in the creator
                //Ignore
                continue;
            }
            finalProperties.put(entry.getKey(), builder.build());
        }
        return finalProperties;
    }

    private static Optional<Annotation> obtainClassAnnotationFromHierarchy(TypeName annotationType, TypeInfo currentType) {
        if (currentType.hasAnnotation(annotationType)) {
            return currentType.findAnnotation(annotationType);
        }
        return currentType.superTypeInfo()
                .flatMap(superType -> obtainClassAnnotationFromHierarchy(annotationType, superType))
                .or(() -> {
                    for (TypeInfo interf : currentType.interfaceTypeInfo()) {
                        Optional<Annotation> annotation = obtainClassAnnotationFromHierarchy(annotationType, interf);
                        if (annotation.isPresent()) {
                            return annotation;
                        }
                    }
                    return Optional.empty();
                });
    }

    private static TypeName transformGenerics(TypeName typeName, TypeArgument transformTo) {
        TypeName.Builder builder = TypeName.builder(typeName).clearTypeArguments();
        for (int i = 0; i < typeName.typeArguments().size(); i++) {
            builder.addTypeArgument(transformTo);
        }
        return builder.build();
    }

}
