/*
 * Copyright (c) 2023, 2026 Oracle and/or its affiliates.
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

package io.helidon.config.metadata.codegen;

import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import io.helidon.codegen.CodegenContext;
import io.helidon.codegen.CodegenException;
import io.helidon.codegen.CodegenLogger;
import io.helidon.codegen.JavadocReader;
import io.helidon.codegen.JavadocWriter;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.config.metadata.model.CmModel.CmAllowedValue;
import io.helidon.config.metadata.model.CmModel.CmOption;
import io.helidon.config.metadata.model.CmModel.CmType;

import static io.helidon.codegen.ElementInfoPredicates.hasNoArgs;
import static io.helidon.codegen.ElementInfoPredicates.isMethod;
import static io.helidon.codegen.ElementInfoPredicates.isPrivate;
import static io.helidon.codegen.ElementInfoPredicates.isStatic;
import static io.helidon.codegen.ElementInfoPredicates.isVoid;
import static java.util.function.Predicate.not;

/**
 * Type handler.
 */
class TypeHandler {

    private static final String UNCONFIGURED_OPTION = "io.helidon.config.metadata.ConfiguredOption.UNCONFIGURED";

    private final Map<TypeInfo, Set<TypeInfo>> configuredAncestors = new HashMap<>();
    private final Map<TypeInfo, CmType> models = new HashMap<>();
    private final TypeResolver resolver;
    private final CodegenLogger logger;
    private final CodegenContext ctx;

    TypeHandler(CodegenContext ctx) {
        this.ctx = ctx;
        this.logger = ctx.logger();
        this.resolver = new TypeResolver(ctx);
    }

    /**
     * Get the config schemas for the processed types.
     *
     * @return schemas
     */
    Map<TypeInfo, CmType> models() {
        return models;
    }

    /**
     * Process an annotated type.
     *
     * @param annotatedTypeInfo annotated type info
     */
    void handle(TypeInfo annotatedTypeInfo) {
        var model = models.get(annotatedTypeInfo);
        if (model == null) {
            var annot = annotatedTypeInfo.annotation(ConfigMetadataTypes.CONFIGURED);
            var targetTypeInfo = targetType(annot, annotatedTypeInfo);
            model = type(annot, annotatedTypeInfo, targetTypeInfo);
            models.put(annotatedTypeInfo, model);
        }
    }

    private TypeInfo targetType(Annotation annot, TypeInfo annotatedTypeInfo) {
        boolean ignoreBuilderMethod = annot.booleanValue("ignoreBuildMethod").orElse(false);
        if (!ignoreBuilderMethod) {
            var targetTypeInfo = targetType(annotatedTypeInfo);
            if (resolver.isSubtype(annotatedTypeInfo, ConfigMetadataTypes.PROTOTYPE_API)) {
                return targetTypeInfo;
            } else {
                var annotatedTypePackage = annotatedTypeInfo.typeName().packageName();
                var targetTypePackage = targetTypeInfo.typeName().packageName();
                if (targetTypePackage.equals(annotatedTypePackage)) {
                    return targetTypeInfo;
                }
            }
        }
        return annotatedTypeInfo;
    }

    private TypeInfo targetType(TypeInfo annotatedTypeInfo) {
        for (var typeInfo : resolver.typeHierarchy(annotatedTypeInfo)) {
            var enclosingTypeName = typeInfo.typeName();
            for (var e : typeInfo.elementInfo()) {
                if (isMethod(e) && !isStatic(e) && hasNoArgs(e) && !isVoid(e) && !isPrivate(e)
                    && "build".equals(e.elementName())) {

                    var returnTypeName = e.typeName();
                    var targetTypeInfo = ctx.typeInfo(returnTypeName).orElse(null);
                    if (targetTypeInfo == null) {
                        targetTypeInfo = resolver.resolveTypeParameter(returnTypeName, enclosingTypeName);
                    }
                    if (targetTypeInfo != null) {
                        return targetTypeInfo;
                    }
                }
            }
        }
        return annotatedTypeInfo;
    }

    private CmType type(Annotation annot, TypeInfo annotatedTypeInfo, TypeInfo targetTypeInfo) {
        var typeName = targetTypeInfo.typeName().fqName();
        var description = description(annot, targetTypeInfo, annotatedTypeInfo);
        boolean standalone = annot.booleanValue("root").orElse(false);
        var inherits = inheritedTypes(annotatedTypeInfo);
        var provides = providedTypes(annot);
        var prefix = annot.stringValue("prefix")
                .filter(not(String::isBlank))
                .orElse(null);
        var options = options(annot, annotatedTypeInfo);
        if (standalone && (prefix == null)) {
            throw new CodegenException("Standalone type does not have a prefix", annotatedTypeInfo, annot);
        }
        if (!provides.isEmpty() && prefix == null) {
            throw new CodegenException("Provider type does not have a prefix", annotatedTypeInfo, annot);
        }
        return CmType.builder()
                .type(typeName)
                .description(description)
                .standalone(standalone)
                .inherits(inherits)
                .options(options)
                .provides(provides)
                .prefix(prefix)
                .build();
    }

    private List<CmOption> options(Annotation annot, TypeInfo typeInfo) {
        var options = new TreeSet<CmOption>();
        var optionLiterals = annot.annotationValues("options").orElseGet(List::of);
        for (var a : optionLiterals) {
            if (a.booleanValue("configured").orElse(true)) {
                options.add(option(a, typeInfo, typeInfo));
            }
        }
        var typeName = typeInfo.typeName().genericTypeName();
        for (var e : typeInfo.elementInfo()) {
            if (isMethod(e)) {
                // filter only methods declared on this type
                var enclosingType = e.enclosingType().orElseThrow();
                var enclosingTypeName = enclosingType.genericTypeName();
                if (typeName.equals(enclosingTypeName)) {
                    if (e.hasAnnotation(ConfigMetadataTypes.OPTIONS)) {
                        var annots = e.annotation(ConfigMetadataTypes.OPTIONS).annotationValues().orElseGet(List::of);
                        for (var a : annots) {
                            if (a.booleanValue("configured").orElse(true)) {
                                options.add(option(a, typeInfo, e));
                            }
                        }
                    } else if (e.hasAnnotation(ConfigMetadataTypes.OPTION)) {
                        var a = e.annotation(ConfigMetadataTypes.OPTION);
                        if (a.booleanValue("configured").orElse(true)) {
                            options.add(option(e.annotation(ConfigMetadataTypes.OPTION), typeInfo, e));
                        }
                    }
                }
            }
        }
        return new ArrayList<>(options);
    }

    private CmOption option(Annotation annot, TypeInfo enclosingTypeInfo, Object originElement) {
        var data = optionTypeData(annot, originElement);
        var typeName = optionTypeName(data);
        var typeInfo = optionTypeInfo(typeName, data.originElements);
        boolean merge = annot.booleanValue("mergeWithParent").orElse(false);
        if (merge) {
            if (typeName.equals(ConfigMetadataTypes.STRING) || typeName.unboxed().primitive()) {
                logger.log(Level.WARNING, "Invalid merge option type: " + typeName.fqName(), data.originElements);
            } else {
                return CmOption.builder()
                        .type(typeName.fqName())
                        .merge(true)
                        .build();
            }
        }
        var key = optionKey(annot, originElement);
        var type = typeName.genericTypeName().fqName();
        var kind = optionKind(annot, originElement);
        var description = description(annot, enclosingTypeInfo, originElement);
        var allowedValues = allowedValues(annot, typeInfo, originElement);
        boolean required = annot.booleanValue("required").orElse(false);
        boolean experimental = annot.booleanValue("experimental").orElse(false);
        boolean deprecated = annot.booleanValue("deprecated").orElse(false);
        boolean provider = annot.booleanValue("provider").orElse(false);
        var defaultValue = optionDefaultValue(annot, typeName, data.originElements);
        if (defaultValue != null && required) {
            throw new CodegenException("Required option cannot have a default value", originElement, annot);
        }

        return CmOption.builder()
                .key(key)
                .type(type)
                .description(description)
                .kind(kind)
                .allowedValues(allowedValues)
                .required(required)
                .experimental(experimental)
                .deprecated(deprecated)
                .provider(provider)
                .defaultValue(defaultValue)
                .build();
    }

    private String optionDefaultValue(Annotation annot, TypeName typeName, Object... originElements) {
        var defaultValue = annot.stringValue()
                .filter(not(UNCONFIGURED_OPTION::equals))
                .orElse(null);
        if (defaultValue != null) {
            try {
                parseValue(typeName, defaultValue);
            } catch (NumberFormatException ex) {
                logger.log(Level.WARNING,
                        "Unable to parse \"%s\" as %s".formatted(defaultValue, typeName.fqName()),
                        originElements);
            }
        }
        return defaultValue;
    }

    private TypeInfo optionTypeInfo(TypeName typeName, Object... originElements) {
        if (typeName.equals(ConfigMetadataTypes.OBJECT)) {
            logger.log(Level.WARNING, "Invalid option type: " + typeName, originElements);
            return null;
        } else {
            var rawTypeName = typeName.genericTypeName();
            return ctx.typeInfo(rawTypeName).orElseThrow(() ->
                    new CodegenException("Unable to resolve type: " + typeName, originElements));
        }
    }

    private TypeName optionTypeName(TypeData data) {
        var typeName = data.typeName;
        var rawTypeName = typeName.genericTypeName();
        if (typeName.isList() || typeName.isSet() || rawTypeName.equals(ConfigMetadataTypes.ITERABLE)) {
            typeName = listOptionTypeName(data);
        } else if (typeName.isMap()) {
            typeName = mapOptionTypeName(data);
        } else if (typeName.equals(ConfigMetadataTypes.OBJECT)) {
            return ConfigMetadataTypes.OBJECT;
        } else if (typeName.equals(ConfigMetadataTypes.CHAR_ARRAY)) {
            return ConfigMetadataTypes.STRING;
        } else {
            if (typeName.array()) {
                logger.log(Level.WARNING, "Invalid array type: " + typeName, data.originElements);
                return typeName.boxed();
            }

            // warn iterables subtypes
            var rawTypeNameBoxed = rawTypeName.boxed();
            var typeInfo = ctx.typeInfo(rawTypeNameBoxed).orElseThrow(() ->
                    new CodegenException("Unable to resolve type: " + rawTypeNameBoxed, data.originElements));
            var iterableTypeInfo = resolver.superType(typeInfo, ConfigMetadataTypes.ITERABLE);
            if (iterableTypeInfo != null) {
                var typeArgs = iterableTypeInfo.typeArguments();
                if (typeArgs.isEmpty() || !typeInfo.typeName().equals(typeArgs.getFirst())) {
                    logger.log(Level.WARNING, "Invalid iterable type: " + typeName, data.originElements);
                }
                return typeName.boxed();
            }

            // warn map subtypes
            var mapTypeInfo = resolver.superType(typeInfo, ConfigMetadataTypes.MAP);
            if (mapTypeInfo != null) {
                logger.log(Level.WARNING, "Invalid map type: " + typeName, data.originElements);
            }
        }
        return typeName.boxed();
    }

    private TypeData optionTypeData(Annotation annot, Object originElement) {
        var typeName = annot.typeValue("type").orElseThrow();
        if (typeName.equals(ConfigMetadataTypes.OPTION)) {
            if (originElement instanceof TypedElementInfo methodInfo) {
                return methodTypeData(methodInfo);
            } else {
                throw new CodegenException("Type is required", originElement, annot);
            }
        }
        return new TypeData(typeName, true, originElement, annot);
    }

    private TypeName listOptionTypeName(TypeData optionType) {
        var typeName = optionType.typeName;
        var typeArgs = typeName.typeArguments();
        if (typeArgs.size() == 1) {
            var typeArg = typeArgs.getFirst();
            return typeArg.genericTypeName();
        } else if (optionType.literal) {
            throw new CodegenException("Invalid literal option type: " + typeName, optionType.originElements);
        } else {
            throw new CodegenException("Cannot infer iterable option type: " + typeName, optionType.originElements);
        }
    }

    private TypeName mapOptionTypeName(TypeData optionType) {
        var typeName = optionType.typeName;
        var typeArgs = typeName.typeArguments();
        if (typeArgs.size() == 2) {
            var keyTypeName = typeArgs.getFirst();
            if (!keyTypeName.equals(ConfigMetadataTypes.STRING)) {
                throw new CodegenException("Map key type must be String", optionType.originElements);
            }
            var valueTypeName = typeArgs.get(1);
            return valueTypeName.genericTypeName();
        } else if (optionType.literal) {
            throw new CodegenException("Invalid literal option type: " + typeName, optionType.originElements);
        } else {
            throw new CodegenException("Cannot infer map option type: " + typeName, optionType.originElements);
        }
    }

    private CmOption.Kind optionKind(Annotation annot, Object originElement) {
        var kind = annot.stringValue("kind")
                .map(CmOption.Kind::valueOf)
                .orElseThrow();
        if (kind == CmOption.DEFAULT_KIND) {
            if (originElement instanceof TypedElementInfo methodInfo) {
                return optionKind(methodInfo);
            }
        }
        return kind;
    }

    private CmOption.Kind optionKind(TypedElementInfo methodInfo) {
        var optionTypeData = methodTypeData(methodInfo);
        var typeName = optionTypeData.typeName;
        if (typeName.array() || typeName.isList() || typeName.isSet() || typeName.equals(ConfigMetadataTypes.ITERABLE)) {
            return CmOption.Kind.LIST;
        } else if (typeName.isMap()) {
            return CmOption.Kind.MAP;
        }
        return CmOption.DEFAULT_KIND;
    }

    private List<CmAllowedValue> allowedValues(Annotation annot, TypeInfo typeInfo, Object originElement) {
        var allowedValues = annot.annotationValues("allowedValues")
                .filter(not(List::isEmpty))
                .map(it -> allowedValues(it, originElement))
                .orElse(null);
        if (typeInfo != null && typeInfo.kind() == ElementKind.ENUM) {
            if (allowedValues == null) {
                allowedValues = enumValues(typeInfo, originElement, annot);
            }
        } else if (allowedValues != null) {
            logger.log(Level.WARNING, "Allowed values not backed by enum", originElement, annot);
        } else {
            allowedValues = List.of();
        }
        return allowedValues;
    }

    private List<CmAllowedValue> allowedValues(List<Annotation> annots, Object originElement) {
        var result = new ArrayList<CmAllowedValue>();
        for (var annot : annots) {
            var value = annot.stringValue("value").orElseThrow();
            var description = annot.stringValue("description")
                    .filter(not(String::isBlank))
                    .orElseThrow(() -> new CodegenException("Invalid description", originElement, annot));
            result.add(CmAllowedValue.of(value, description));
        }
        return result;
    }

    private List<CmAllowedValue> enumValues(TypeInfo typeInfo, Object... originElements) {
        var result = new ArrayList<CmAllowedValue>();
        for (var e : typeInfo.elementInfo()) {
            if (e.kind() == ElementKind.ENUM_CONSTANT) {
                var value = e.elementName();
                var description = javadoc(e);
                if (description == null || description.isBlank()) {
                    description = "<code>N/A</code>";
                } else if (!Character.isUpperCase(description.charAt(0))) {
                    logger.log(Level.WARNING, "Description is not capitalized", originElements);
                }
                result.add(CmAllowedValue.of(value, description));
            }
        }
        return result;
    }

    private String description(Annotation annot, TypeInfo typeInfo, Object originElement) {
        var description = annot.stringValue("description")
                .map(String::trim)
                .map(TypeHandler::removeTrailingDots)
                .orElse(null);
        if (description == null || description.isBlank()) {
            if (originElement instanceof TypedElementInfo elementInfo) {
                description = javadoc(typeInfo, elementInfo);
            } else if (originElement instanceof TypeInfo targetType) {
                description = javadoc(typeInfo);
                if (description == null || description.isBlank()) {
                    description = javadoc(targetType);
                }
            }
            if (description == null || description.isBlank()) {
                logger.log(Level.WARNING, "Missing javadoc", originElement, annot);
            }
        }
        if (description == null || description.isBlank()) {
            description = "<code>N/A</code>";
        } else if (!Character.isUpperCase(description.charAt(0))) {
            logger.log(Level.WARNING, "Description is not capitalized", originElement, annot);
        }
        return description;
    }

    private List<String> inheritedTypes(TypeInfo typeInfo) {
        var inherits = new ArrayList<String>();
        var ancestors = configuredAncestors(typeInfo);
        for (var ancestor : ancestors) {
            var transitive = configuredAncestors(ancestor);
            if (transitive.stream().noneMatch(ancestors::contains)) {
                inherits.add(ancestor.typeName().fqName());
            }
        }
        return inherits;
    }

    private Set<TypeInfo> configuredAncestors(TypeInfo typeInfo) {
        return configuredAncestors.computeIfAbsent(typeInfo, k ->
                resolver.typeHierarchy(k, it -> it != k && it.hasAnnotation(ConfigMetadataTypes.CONFIGURED)));
    }

    private String javadoc(TypeInfo typeInfo) {
        return typeInfo.description()
                .map(TypeHandler::javadoc)
                .filter(not(String::isBlank))
                .orElse(null);
    }

    private String javadoc(TypeInfo typeInfo, TypedElementInfo elementInfo) {
        var description = javadoc(elementInfo);
        if (description == null || description.isBlank()) {
            for (var m : resolver.methodHierarchy(typeInfo, elementInfo)) {
                description = javadoc(m);
                if (description != null && !description.isBlank()) {
                    break;
                }
            }
        }
        return description;
    }

    private String javadoc(TypedElementInfo elementInfo) {
        return elementInfo.description()
                .map(TypeHandler::javadoc)
                .filter(not(String::isBlank))
                .orElse(null);
    }

    private static String optionKey(Annotation annot, Object originElement) {
        var key = annot.stringValue("key").orElse(null);
        if (key != null && !key.isBlank()) {
            return key;
        }
        if (originElement instanceof TypedElementInfo methodInfo) {
            return optionKey(methodInfo);
        } else {
            throw new CodegenException("key is required", originElement, annot);
        }
    }

    private static String optionKey(TypedElementInfo methodInfo) {
        var methodName = methodInfo.elementName();
        //noinspection DuplicatedCode
        var sb = new StringBuilder();
        for (int i = 0; i < methodName.length(); i++) {
            char c = methodName.charAt(i);
            if (Character.isUpperCase(c)) {
                if (!sb.isEmpty()) {
                    sb.append('-');
                }
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static TypeData methodTypeData(TypedElementInfo methodInfo) {
        var args = methodInfo.parameterArguments();
        if (args.size() == 1) {
            var typeArg = args.getFirst();
            return new TypeData(typeArg.typeName(), false, typeArg);
        } else {
            var typeName = methodInfo.typeName().boxed();
            if (typeName.equals(ConfigMetadataTypes.BOXED_VOID)) {
                throw new CodegenException("Unable to infer option type", methodInfo);
            } else {
                return new TypeData(typeName, false, methodInfo);
            }
        }
    }

    private static List<String> providedTypes(Annotation annot) {
        return annot.typeValues("provides")
                .map(it -> it.stream()
                        .map(TypeName::name)
                        .toList())
                .orElseGet(List::of);
    }

    private static String removeTrailingDots(String str) {
        for (int index = str.length() - 1; index >= 0; index--) {
            if (str.charAt(index) != '.') {
                return str.substring(0, index + 1);
            }
        }
        return "";
    }

    private static String javadoc(String description) {
        var reader = JavadocReader.create(description);
        var document = reader.read();
        var buf = new StringBuilder();
        var writer = JavadocWriter.create(buf);
        writer.write(document.firstSentence());

        // remove new lines and the trailing dot
        for (int i = 0, len = buf.length(), offset = 0; i < len; i++) {
            int index = i - offset;
            char c = buf.charAt(index);
            if (c == '\n' || (i + 1 == len && c == '.')) {
                buf.deleteCharAt(index);
                offset++;
            }
        }
        return buf.toString();
    }

    @SuppressWarnings("UnusedReturnValue")
    private static Object parseValue(TypeName typeName, String defaultValue) {
        if (typeName.equals(ConfigMetadataTypes.BOXED_DOUBLE)) {
            return Double.parseDouble(defaultValue);
        } else if (typeName.equals(ConfigMetadataTypes.BOXED_BOOLEAN)) {
            return Boolean.parseBoolean(defaultValue);
        } else if (typeName.equals(ConfigMetadataTypes.BOXED_BYTE)) {
            return Byte.parseByte(defaultValue);
        } else if (typeName.equals(ConfigMetadataTypes.BOXED_SHORT)) {
            return Short.parseShort(defaultValue);
        } else if (typeName.equals(ConfigMetadataTypes.BOXED_FLOAT)) {
            return Float.parseFloat(defaultValue);
        } else if (typeName.equals(ConfigMetadataTypes.BOXED_LONG)) {
            return Long.parseLong(defaultValue);
        } else if (typeName.equals(ConfigMetadataTypes.BOXED_INT)) {
            return Integer.parseInt(defaultValue);
        } else {
            return null;
        }
    }

    private record TypeData(TypeName typeName, boolean literal, Object... originElements) {
    }
}
