/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.pico.builder.tools;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.pico.builder.api.Annotated;
import io.helidon.pico.builder.api.Builder;
import io.helidon.pico.builder.api.ConfiguredOption;
import io.helidon.pico.builder.api.Singular;
import io.helidon.pico.builder.runtime.tools.BeanUtils;
import io.helidon.pico.builder.spi.BuilderCreator;
import io.helidon.pico.builder.spi.TypeAndBody;
import io.helidon.pico.builder.spi.TypeAndBodyImpl;
import io.helidon.pico.builder.spi.TypeInfo;
import io.helidon.pico.spi.DefaultAnnotationAndValue;
import io.helidon.pico.spi.DefaultTypeName;
import io.helidon.pico.spi.TypedElementName;
import io.helidon.pico.types.AnnotationAndValue;
import io.helidon.pico.types.TypeName;

/**
 * Default implementation for {@link io.helidon.pico.builder.spi.BuilderCreator}.
 */
@Weight(Weighted.DEFAULT_WEIGHT - 1)   // allow all other creators to take precedence over us...
public class DefaultBuilderCreator implements BuilderCreator {
    private static final boolean DEFAULT_HAS_META_ATTRIBUTES = Builder.DEFAULT_INCLUDE_META_ATTRIBUTES;
    private static final String DEFUALT_PREFIX = Builder.DEFAULT_PREFIX;
    private static final String DEFUALT_SUFFIX = Builder.DEFAULT_SUFFIX;
    private static final String DEFAULT_LIST_TYPE = Builder.DEFAULT_LIST_TYPE.getName();
    private static final String DEFAULT_MAP_TYPE = Builder.DEFAULT_MAP_TYPE.getName();
    private static final String DEFAULT_SET_TYPE = Builder.DEFAULT_SET_TYPE.getName();
    private static final TypeName BUILDER_ANNO_TYPE_NAME = DefaultTypeName.create(Builder.class);

    private static final boolean SUPPORT_STREAMS = true;
    // TODO: tlanger to review (toggle AVOID... to true)
    private static final boolean AVOID_GENERIC_BUILDER = false;

    @Override
    public Set<Class<? extends Annotation>> getSupportedAnnotationTypes() {
        return Collections.singleton(Builder.class);
    }

    @Override
    public TypeAndBody create(TypeInfo typeInfo, AnnotationAndValue builderAnnotation) {
        try {
            TypeName implTypeName = toImplTypeName(typeInfo.getTypeName(), builderAnnotation);
            preValidate(implTypeName, typeInfo, builderAnnotation);
            return postValidate(TypeAndBodyImpl.builder()
                    .typeName(implTypeName)
                    .body(toBody(implTypeName, typeInfo, builderAnnotation))
                    .build());
        } catch (Exception e) {
            throw new RuntimeException("Failed while processing " + typeInfo, e);
        }
    }

    protected void preValidate(TypeName implTypeName,
                               TypeInfo typeInfo,
                               AnnotationAndValue builderAnnotation) {
        // NOP
    }

    protected TypeAndBody postValidate(TypeAndBody build) {
        return build;
    }

    protected String toPackageName(String packageName, AnnotationAndValue builderAnnotation) {
        String packageNameFromAnno = builderAnnotation.valueOf("packageName");
        if (Objects.isNull(packageNameFromAnno) || packageNameFromAnno.isBlank()) {
            return packageName;
        } else if (packageNameFromAnno.startsWith(".")) {
            return packageName + packageNameFromAnno;
        } else {
            return packageNameFromAnno;
        }
    }

    protected String toImplTypePrefix(AnnotationAndValue builderAnnotation) {
        String prefix = builderAnnotation.valueOf("implPrefix");
        return (Objects.isNull(prefix)) ? DEFUALT_PREFIX : prefix;
    }

    protected String toImplTypeSuffix(AnnotationAndValue builderAnnotation) {
        String suffix = builderAnnotation.valueOf("implSuffix");
        return (Objects.isNull(suffix)) ? DEFUALT_SUFFIX : suffix;
    }

    protected boolean hasStreamSupport(AnnotationAndValue ignoreBuilderAnnotation) {
//        String streamSupport = ignoreBuilderAnnotation.getValue("supportStreams");
//        return Boolean.parseBoolean(streamSupport);
        return SUPPORT_STREAMS;
    }

    protected boolean hasMetaAttributes(AnnotationAndValue builderAnnotation) {
        String hasMetaAttributes = builderAnnotation.valueOf("includeMetaAttributes");
        return Objects.isNull(hasMetaAttributes) ? DEFAULT_HAS_META_ATTRIBUTES : Boolean.parseBoolean(hasMetaAttributes);
    }

    protected boolean toRequireBeanStyle(AnnotationAndValue builderAnnotation, TypeInfo typeInfo) {
        String val = searchForBuilderAnnotation("requireBeanStyle", builderAnnotation, typeInfo);
        return Boolean.parseBoolean(val);
    }

    protected String toListImplType(AnnotationAndValue builderAnnotation, TypeInfo typeInfo) {
        String type = searchForBuilderAnnotation("listImplType", builderAnnotation, typeInfo);
        return (!AnnotationAndValue.hasNonBlankValue(type)) ? DEFAULT_LIST_TYPE : type;
    }

    protected String toMapImplType(AnnotationAndValue builderAnnotation, TypeInfo typeInfo) {
        String type = searchForBuilderAnnotation("mapImplType", builderAnnotation, typeInfo);
        return (!AnnotationAndValue.hasNonBlankValue(type)) ? DEFAULT_MAP_TYPE : type;
    }

    protected String toSetImplType(AnnotationAndValue builderAnnotation, TypeInfo typeInfo) {
        String type = searchForBuilderAnnotation("setImplType", builderAnnotation, typeInfo);
        return (!AnnotationAndValue.hasNonBlankValue(type)) ? DEFAULT_SET_TYPE : type;
    }

    protected String searchForBuilderAnnotation(String key, AnnotationAndValue builderAnnotation, TypeInfo typeInfo) {
        String val = builderAnnotation.valueOf(key);
        if (Objects.nonNull(val)) {
            return val;
        }

        if (!builderAnnotation.typeName().equals(BUILDER_ANNO_TYPE_NAME)) {
            builderAnnotation = DefaultAnnotationAndValue
                .findFirst(BUILDER_ANNO_TYPE_NAME, typeInfo.getAnnotations()).orElse(null);
            if (Objects.nonNull(builderAnnotation)) {
                val = builderAnnotation.valueOf(key);
            }
        }

        return val;
    }

    protected TypeName toImplTypeName(TypeName typeName, AnnotationAndValue builderAnnotation) {
        String toPackageName = toPackageName(typeName.packageName(), builderAnnotation);
        String prefix = toImplTypePrefix(builderAnnotation);
        String suffix = toImplTypeSuffix(builderAnnotation);
        return DefaultTypeName.create(toPackageName, prefix + typeName.className() + suffix);
    }

    protected String toBody(TypeName implTypeName, TypeInfo typeInfo, AnnotationAndValue builderAnnotation) {
        final boolean hasStreamSupport = hasStreamSupport(builderAnnotation);
        final boolean hasMetaAttributes = hasMetaAttributes(builderAnnotation);
        final boolean isBeanStyleRequired = toRequireBeanStyle(builderAnnotation, typeInfo);
        final String listType = toListImplType(builderAnnotation, typeInfo);
        final String mapType = toMapImplType(builderAnnotation, typeInfo);
        final String setType = toSetImplType(builderAnnotation, typeInfo);
        final Map<String, TypedElementName> map = new LinkedHashMap<>();
        final List<TypedElementName> allTypeInfos = new ArrayList<>();
        final List<String> allAttributeNames = new ArrayList<>();
        final AtomicReference<TypeName> parentTypeName = new AtomicReference<>();
        final AtomicReference<TypeName> parentAnnotationType = new AtomicReference<>();
        gatherAllAttributeNames(map, allTypeInfos, allAttributeNames,
                                parentTypeName, parentAnnotationType,
                                typeInfo, builderAnnotation, isBeanStyleRequired);
        assert (allTypeInfos.size() == allAttributeNames.size());
        final boolean hasParent = Objects.nonNull(parentTypeName.get());
        final TypeName ctorBuilderAcceptTypeName = (hasParent)
                ? typeInfo.getTypeName()
                : (Objects.nonNull(parentAnnotationType.get()) && typeInfo.getElementInfo().isEmpty()
                           ? typeInfo.getSuperTypeInfo().getTypeName() : typeInfo.getTypeName());

        StringBuilder builder = new StringBuilder();
        builder.append("package ").append(implTypeName.packageName()).append(";\n\n");
        builder.append("import java.util.Collections;\n");
        builder.append("import java.util.Map;\n");
        builder.append("import java.util.Objects;\n");
        appendExtraImports(builder, typeInfo);
        builder.append("\n");
        builder.append("/**\n");
        builder.append(" * Concrete implementation w/ builder for {@link ").append(typeInfo.getTypeName()).append("}.\n");
        builder.append(" */\n");
        builder.append(BuilderTemplateHelper.getDefaultGeneratedSticker(getClass().getSimpleName())).append("\n");
        appendAnnotations(builder, typeInfo.getAnnotations(), "");
        builder.append("@SuppressWarnings(\"unchecked\")\n");
        builder.append("public class ").append(implTypeName.className());
        if (!hasParent && hasStreamSupport) {
            builder.append("<T extends ").append(implTypeName.className()).append(">");
        }
        if (hasParent) {
            builder.append(" extends ").append(toImplTypeName(parentTypeName.get(), builderAnnotation));
        }
        builder.append(" implements ").append(typeInfo.getTypeName());
        if (!hasParent && hasStreamSupport) {
            builder.append(", java.util.function.Supplier<T>");
        }
        builder.append(" {\n");

        if (hasMetaAttributes) {
            builder.append("\tprivate static final Map<String, Map<String, Object>> __metaProps = Collections.unmodifiableMap(__calcMeta());\n\n");
        }
        appendExtraFields(builder, hasParent, typeInfo);

        for (int i = 0; i < allTypeInfos.size(); i++) {
            TypedElementName method = allTypeInfos.get(i);
            String beanAttributeName = allAttributeNames.get(i);
            appendAnnotations(builder, method.getAnnotations(), "\t");
            builder.append("\tprivate ");
            builder.append(getFieldModifier());
            builder.append(toGenerics(method, false)).append(" ");
            builder.append(beanAttributeName).append(";\n");
        }

        String genericBuilderClassDecl;
        if (AVOID_GENERIC_BUILDER) {
            genericBuilderClassDecl = "Bldr";
        } else {
            genericBuilderClassDecl = "Builder";
        }

        builder.append("\n\tprotected ").append(implTypeName.className());
        builder.append("(").append(genericBuilderClassDecl).append(" b) {\n");
        appendExtraCtorCode(builder, hasParent, "b", typeInfo);
        appendCtorCode(builder, hasParent, "b", typeInfo, allAttributeNames, allTypeInfos,
                       listType, mapType, setType);
        builder.append("\t}\n\n");

        appendExtraPostCtorCode(builder, implTypeName, hasParent, typeInfo,
                                allAttributeNames, allTypeInfos, listType, mapType, setType);

        if (hasMetaAttributes) {
            builder.append("\tpublic static Class<?> __getMetaConfigBeanType() {\n"
                                   + "\t\treturn " + typeInfo.getTypeName().getName() + ".class;\n"
                                   + "\t}\n\n");

            builder.append("\tprivate static Map<String, Map<String, Object>> __calcMeta() {\n");
            builder.append("\t\tMap<String, Map<String, Object>> metaProps = new java.util.LinkedHashMap<>();\n");
            appendMetaProps(builder, "metaProps", typeInfo, map, allAttributeNames, allTypeInfos);
            builder.append("\t\treturn metaProps;\n");
            builder.append("\t}\n\n");

            builder.append("\tpublic static Map<String, Map<String, Object>> __getMetaAttributes() {\n"
                                   + "\t\treturn __metaProps;\n"
                                   + "\t}\n\n");
        }

        if (!hasParent && hasStreamSupport) {
            builder.append("\t@Override\n"
                                   + "\tpublic T get() {\n"
                                   + "\t\treturn (T) this;\n"
                                   + "\t}\n\n");
        }

        if (Objects.nonNull(parentAnnotationType.get())) {
            builder.append("\t@Override\n");
            builder.append("\tpublic Class<? extends java.lang.annotation.Annotation> annotationType() {\n");
            builder.append("\t\treturn ").append(typeInfo.getSuperTypeInfo().getTypeName()).append(".class;\n");
            builder.append("\t}\n\n");
        }

        appendToStringMethod(builder, hasParent, typeInfo);

        if (hasParent) {
            builder.append("\t@Override\n");
        }
        builder.append("\tprotected String toStringInner() {\n");
        if (hasParent) {
            builder.append("\t\tString result = super.toStringInner();\n");
            if (!allAttributeNames.isEmpty()) {
                builder.append("\t\tif (!result.isEmpty() && !result.endsWith(\", \")) {\n");
                builder.append("\t\t\tresult += \", \";\n");
                builder.append("\t\t}\n");
            }
        } else {
            builder.append("\t\tString result = \"\";\n");
        }

        int i = 0;
        for (String beanAttributeName : allAttributeNames) {
            TypedElementName method = allTypeInfos.get(i++);
            TypeName typeName = method.getTypeName();
            builder.append("\t\tresult += \"").append(beanAttributeName).append("=\" + ");
            if (typeName.array()) {
                builder.append("(Objects.isNull(").append(beanAttributeName).append(") ? null : ");
                if (typeName.primitive()) {
                    builder.append("\"not-null\"");
                } else {
                    builder.append("java.util.Arrays.asList(");
                    builder.append(method.getElementName()).append("())");
                }
                builder.append(")");
            } else {
                builder.append(method.getElementName()).append("()");
            }
            if (i < allAttributeNames.size()) {
                builder.append(" + \", \"");
            }
            builder.append(";\n");
        }
        builder.append("\t\treturn result;\n");
        builder.append("\t}\n\n");

        builder.append("\t@Override\n");
        builder.append("\tpublic int hashCode() {\n");
        if (hasParent) {
            builder.append("\t\tint hashCode = super.hashCode();\n");
        } else {
            builder.append("\t\tint hashCode = 0;\n");
        }
        for (TypedElementName method : allTypeInfos) {
            builder.append("\t\thashCode ^= Objects.hashCode(").append(method.getElementName()).append("());\n");
        }
        builder.append("\t\treturn hashCode;\n");
        builder.append("\t}\n\n");

        builder.append("\t@Override\n");
        builder.append("\tpublic boolean equals(Object another) {\n");
        builder.append("\t\tif (this == another) {\n\t\t\treturn true;\n\t\t}\n");
        builder.append("\t\tif (!(another instanceof ").append(typeInfo.getTypeName()).append(")) {\n");
        builder.append("\t\t\treturn false;\n");
        builder.append("\t\t}\n");
        builder.append("\t\t").append(typeInfo.getTypeName()).append(" other = (")
                .append(typeInfo.getTypeName()).append(") another;\n");
        if (hasParent) {
            builder.append("\t\tboolean equals = super.equals(other);\n");
        } else {
            builder.append("\t\tboolean equals = true;\n");
        }
        for (TypedElementName method : allTypeInfos) {
            builder.append("\t\tequals &= Objects.equals(").append(method.getElementName()).append("(), other.")
                    .append(method.getElementName()).append("());\n");
        }
        builder.append("\t\treturn equals;\n");
        builder.append("\t}\n\n");

        appendExtraMethods(builder, builderAnnotation, hasParent, typeInfo, allAttributeNames, allTypeInfos);

        i = 0;
        for (String beanAttributeName : allAttributeNames) {
            TypedElementName method = allTypeInfos.get(i);
            appendAnnotations(builder, method.getAnnotations(), "\t");
            builder.append("\t@Override\n");
            builder.append("\tpublic ").append(toGenerics(method, false)).append(" ").append(method.getElementName())
                    .append("() {\n");
            builder.append("\t\treturn ").append(beanAttributeName).append(";\n");
            builder.append("\t}\n\n");
            i++;
        }

        builder.append("\t/**\n\t * @return A builder for {@link ");
        builder.append(typeInfo.getTypeName());
        builder.append("}\n\t */\n");

        if (AVOID_GENERIC_BUILDER) {
            builder.append("\tpublic static Builder ");
        } else {
            builder.append("\tpublic static ").append(genericBuilderClassDecl);
            builder.append("<? extends ").append(genericBuilderClassDecl).append(", ? extends ");
            builder.append(ctorBuilderAcceptTypeName).append("> ");
        }
        builder.append("builder() {\n");
        builder.append("\t\treturn new Builder((").append(typeInfo.getTypeName()).append(") null);\n");
        builder.append("\t}\n\n");

        builder.append("\t/**\n\t * @return A builder for {@link " + typeInfo.getTypeName());
        builder.append("}\n\t */\n");

        if (AVOID_GENERIC_BUILDER) {
            builder.append("\tpublic static Builder ");
        } else {
            builder.append("\tpublic static ").append(genericBuilderClassDecl);
            builder.append("<? extends ").append(genericBuilderClassDecl).append(", ? extends ");
            builder.append(typeInfo.getTypeName()).append("> ");
        }

        builder.append("toBuilder(").append(ctorBuilderAcceptTypeName).append(" val) {\n");
        builder.append("\t\treturn new Builder(val);\n");
        builder.append("\t}\n\n");

        String decl;
        if (AVOID_GENERIC_BUILDER) {
            decl = "public static Builder toBuilder({args}) {";
        } else {
            decl = "public static Builder<? extends Builder, ? extends "
                    + typeInfo.getTypeName() + "> toBuilder({args}) {";
        }
        appendExtraToBuilderBuilderFunctions(builder,
                                             decl, typeInfo, parentTypeName.get(), allAttributeNames, allTypeInfos);

        builder.append("\tpublic static class ").append(genericBuilderClassDecl);
        builder.append("<B extends ").append(genericBuilderClassDecl).append("<B, T>, T extends ");
        builder.append(ctorBuilderAcceptTypeName).append("> ");
        if (hasParent) {
            builder.append("extends ").append(toImplTypeName(parentTypeName.get(), builderAnnotation))
                    .append(".").append(genericBuilderClassDecl).append("<B, T>");
        } else if (hasStreamSupport) {
            builder.append("implements java.util.function.Supplier<T>, java.util.function.Consumer<T> ");
        }
        builder.append(" {\n");

        appendExtraBuilderFields(builder, genericBuilderClassDecl, builderAnnotation,
                                typeInfo, parentTypeName.get(), allAttributeNames, allTypeInfos);

        i = 0;
        for (String beanAttributeName : allAttributeNames) {
            TypedElementName method = allTypeInfos.get(i);
            TypeName type = method.getTypeName();
            builder.append("\t\tprivate ").append(type.array() ? type.fqName() : type.getName()).append(" ")
                    .append(beanAttributeName);
            String defaultVal = toValue(ConfiguredOption.class, method, true, true);
            if (Objects.nonNull(defaultVal)) {
                builder.append(" = ");
                appendDefaultValueAssignment(builder, method, defaultVal);
            }
            builder.append(";\n");
            i++;
        }
        builder.append("\n");

        builder.append("\t\tprotected ").append(genericBuilderClassDecl).append("(T val) {\n");
        if (hasParent) {
            builder.append("\t\t\tsuper(val);\n");
        }
        appendOverridesOfDefaultValues(builder, isBeanStyleRequired, typeInfo, allAttributeNames, allTypeInfos);
        builder.append("\t\t\tacceptThis(val);\n");
        builder.append("\t\t}\n\n");

        appendExtraBuilderMethods(builder, genericBuilderClassDecl, builderAnnotation,
                                  typeInfo, parentTypeName.get(), allAttributeNames, allTypeInfos);

        if (!hasParent && hasStreamSupport) {
            builder.append("\t\tprotected B identity() {\n"
                                   + "\t\t\treturn (B) this;\n"
                                   + "\t\t}\n\n"
                                   + "\t\t@Override\n"
                                   + "\t\tpublic T get() {\n"
                                   + "\t\t\treturn (T) build();\n"
                                   + "\t\t}\n\n");
            builder.append("\t\tpublic B update(java.util.function.Consumer<T> consumer) {\n"
                                   + "\t\t\tconsumer.accept(get());\n"
                                   + "\t\t\treturn identity();\n"
                                   + "\t\t}\n\n");
        }

        if (hasStreamSupport || hasParent) {
            builder.append("\t\t@Override\n");
        }

        builder.append("\t\tpublic void accept(T val) {\n");
        if (hasParent) {
            builder.append("\t\t\tsuper.accept(val);\n");
        }
        builder.append("\t\t\tacceptThis(val);\n");
        builder.append("\t\t}\n\n");

        builder.append("\t\tprivate void acceptThis(T val) {\n");
        builder.append("\t\t\tif (Objects.isNull(val)) {\n"
                               + "\t\t\t\treturn;\n"
                               + "\t\t\t}\n\n");
        i = 0;
        for (String beanAttributeName : allAttributeNames) {
            TypedElementName method = allTypeInfos.get(i++);
            String getterName = method.getElementName();
            builder.append("\t\t\t").append(beanAttributeName).append("(");
            boolean isList = isList(method);
            boolean isMap = !isList && isMap(method);
            boolean isSet = !isMap && isSet(method);
            if (isList || isSet) {
                builder.append("(java.util.Collection) ");
            }
            builder.append("val.").append(getterName).append("());\n");
        }
        builder.append("\t\t}\n\n");

        i = 0;
        for (String beanAttributeName : allAttributeNames) {
            TypedElementName method = allTypeInfos.get(i);
            boolean isList = isList(method);
            boolean isMap = !isList && isMap(method);
            boolean isSet = !isMap && isSet(method);
            boolean upLevel = isSet || isList;
            String ignore = appendSetter(null, builder, beanAttributeName, null,
                                         method, isList, isMap, isSet, upLevel,
                                         listType, mapType, setType);
            if (isList || isMap || isSet) {
                // overload, and up-level it...
//                appendSetter(ignore, builder, beanName, method, isList, isMap, isSet, true, listType, mapType, setType);
            } else {
                boolean isBoolean = BeanUtils.isBooleanType(method.getTypeName().getName());
                if (isBoolean && beanAttributeName.startsWith("is")) {
                    // possibly overload setter to strip the "is"...
                    String basicAttributeName = "" + Character.toLowerCase(beanAttributeName.charAt(2)) + beanAttributeName.substring(3);
                    if (!allAttributeNames.contains(basicAttributeName)) {
                        ignore = appendSetter(null, builder, beanAttributeName, basicAttributeName,
                                                     method, isList, isMap, isSet, upLevel,
                                                     listType, mapType, setType);
                    }
                }
            }

            maybeAppendSingularSetter(builder, method, beanAttributeName,
                                      isList, isMap, isSet,
                                      listType, mapType, setType);

            i++;
        }

        builder.append("\t\tpublic ").append(implTypeName).append(" build() {\n");
        appendBuilderBuildPreSteps(builder);
        builder.append("\t\t\treturn new ").append(implTypeName.className()).append("(this);\n");
        builder.append("\t\t}\n");

        builder.append("\t}\n");

        if (AVOID_GENERIC_BUILDER) {
            builder.append("\n\tpublic static class Builder extends ");
            builder.append(genericBuilderClassDecl).append("<Builder, ").append(ctorBuilderAcceptTypeName).append("> {\n");
            builder.append("\t\tprotected Builder(").append(ctorBuilderAcceptTypeName).append(" val) {\n");
            builder.append("\t\t\tsuper(val);\n");
            builder.append("\t\t}\n");
            builder.append("\t}\n\n");
        }

        appendExtraInnerClasses(builder, hasParent, typeInfo);

        builder.append("}\n");

        return builder.toString();
    }

    protected void appendDefaultValueAssignment(StringBuilder builder, TypedElementName method, String defaultVal) {
        TypeName type = method.getTypeName();
        boolean isOptional = type.getName().equals(Optional.class.getName());
        if (isOptional) {
            builder.append(Optional.class.getName()).append(".of(");
            if (!type.typeArguments().isEmpty()) {
                type = type.typeArguments().get(0);
            }
        }

        boolean isString = type.getName().equals(String.class.getName()) && !type.array();
        boolean isCharArr = type.fqName().equals("char[]");
        if ((isString || isCharArr) && !defaultVal.startsWith("\"")) {
            builder.append("\"");
        }

        builder.append(defaultVal);

        if ((isString || isCharArr) && !defaultVal.endsWith("\"")) {
            builder.append("\"");
            if (isCharArr) {
                builder.append(".toCharArray()");
            }
        }

        if (isOptional) {
            builder.append(")");
        }
    }

    protected void appendExtraImports(StringBuilder builder,
                                      TypeInfo typeInfo) {
        // NOP
    }

    protected void appendToStringMethod(StringBuilder builder, boolean hasParent, TypeInfo typeInfo) {
        builder.append("\t@Override\n");
        builder.append("\tpublic String toString() {\n");
        builder.append("\t\treturn getClass().getSimpleName() + \"(\" + toStringInner() + \")\";\n");
        builder.append("\t}\n\n");
    }

    protected void appendExtraMethods(StringBuilder builder,
                                      AnnotationAndValue builderAnnotation,
                                      boolean hasParent,
                                      TypeInfo typeInfo,
                                      List<String> allAttributeNames,
                                      List<TypedElementName> allTypeInfos) {
        // NOP
    }

    protected void appendExtraInnerClasses(StringBuilder builder,
                                           boolean hasParent,
                                           TypeInfo typeInfo) {
        // NOP
    }

    protected String getFieldModifier() {
        return "final ";
    }

    public void appendCtorCode(StringBuilder builder,
                               boolean hasParent,
                               String builderTag,
                               TypeInfo typeInfo,
                               List<String> allAttributeNames,
                               List<TypedElementName> allTypeInfos,
                               String listType,
                               String mapType,
                               String setType) {
        if (hasParent) {
            builder.append("\t\tsuper(b);\n");
        }
        int i = 0;
        for (String beanAttributeName : allAttributeNames) {
            TypedElementName method = allTypeInfos.get(i++);
            builder.append("\t\tthis.").append(beanAttributeName).append(" = ");

            if (isList(method)) {
                builder.append("Objects.isNull(b.").append(beanAttributeName).append(")\n");
                builder.append("\t\t\t? Collections.emptyList() : Collections.unmodifiableList(new ")
                        .append(listType).append("<>(b.").append(beanAttributeName).append("));\n");
            } else if (isMap(method)) {
                builder.append("Objects.isNull(b.").append(beanAttributeName).append(")\n");
                builder.append("\t\t\t? Collections.emptyMap() : Collections.unmodifiableMap(new ")
                        .append(mapType).append("<>(b.").append(beanAttributeName).append("));\n");
            } else if (isSet(method)) {
                builder.append("Objects.isNull(b.").append(beanAttributeName).append(")\n");
                builder.append("\t\t\t? Collections.emptySet() : Collections.unmodifiableSet(new ")
                        .append(setType).append("<>(b.").append(beanAttributeName).append("));\n");
            } else {
                builder.append("b.").append(beanAttributeName).append(";\n");
            }
        }
    }

    protected void appendExtraCtorCode(StringBuilder builder,
                                     boolean hasParent,
                                     String builderTag,
                                     TypeInfo typeInfo) {
        // NOP
    }

    protected void appendExtraPostCtorCode(StringBuilder builder,
                                           TypeName implTypeName, boolean hasParent,
                                           TypeInfo typeInfo,
                                           List<String> allAttributeNames,
                                           List<TypedElementName> allTypeInfos,
                                           String listType,
                                           String mapType,
                                           String setType) {
        // NOP
    }

    protected void appendExtraFields(StringBuilder builder,
                                   boolean hasParent,
                                   TypeInfo typeInfo) {
        // NOP
    }

    protected void appendExtraToBuilderBuilderFunctions(StringBuilder builder,
                                                        String decl,
                                                        TypeInfo typeInfo,
                                                        TypeName parentTypeName,
                                                        List<String> allAttributeNames,
                                                        List<TypedElementName> allTypeInfos) {
        // NOP
    }

    protected void appendExtraBuilderFields(StringBuilder builder,
                                           String builderGeneratedClassName,
                                           AnnotationAndValue builderAnnotation,
                                           TypeInfo typeInfo,
                                           TypeName parentTypeName,
                                           List<String> allAttributeNames,
                                           List<TypedElementName> allTypeInfos) {
        // NOP
    }

    protected void appendOverridesOfDefaultValues(StringBuilder builder,
                                                  boolean isBeanStyleRequired,
                                                  TypeInfo typeInfo,
                                                  List<String> allAttributeNames,
                                                  List<TypedElementName> ignoredAllTypeInfos) {
        boolean first = true;
        for (TypedElementName method : typeInfo.getElementInfo()) {
            String beanAttributeName = toBeanAttributeName(method, isBeanStyleRequired);
            if (!allAttributeNames.contains(beanAttributeName)) {
                // candidate for override...
                String thisDefault = toValue(ConfiguredOption.class, method, true, true);
                String superDefault = getSuperValue(typeInfo.getSuperTypeInfo(), beanAttributeName, isBeanStyleRequired);
                if (AnnotationAndValue.hasNonBlankValue(thisDefault) && !Objects.equals(thisDefault, superDefault)) {
                    if (first) {
                        builder.append("\t\t\tif (Objects.isNull(val)) {\n");
                        first = false;
                    }
                    appendDefaultOverride(builder, beanAttributeName, method, thisDefault);
                }
            }
        }

        if (!first) {
            builder.append("\t\t\t}\n");
        }
    }

    protected String getSuperValue(TypeInfo superTypeInfo, String elemName, boolean isBeanStyleRequired) {
        if (Objects.isNull(superTypeInfo)) {
            return null;
        }

        Optional<TypedElementName> method = superTypeInfo.getElementInfo().stream()
                .filter(it -> toBeanAttributeName(it, isBeanStyleRequired).equals(elemName))
                .findFirst();
        if (method.isPresent()) {
            String defaultValue = toValue(ConfiguredOption.class, method.get(), true, true);
            if (AnnotationAndValue.hasNonBlankValue(defaultValue)) {
                return defaultValue;
            }
        } else {
            return getSuperValue(superTypeInfo.getSuperTypeInfo(), elemName, isBeanStyleRequired);
        }

        return null;
    }

    protected void appendDefaultOverride(StringBuilder builder, String attrName, TypedElementName method, String override) {
        builder.append("\t\t\t\t").append(attrName).append("(");
        appendDefaultValueAssignment(builder, method, override);
        builder.append(");\n");
    }

    protected void appendBuilderBuildPreSteps(StringBuilder builder) {
        // NOP
    }

    protected void appendExtraBuilderMethods(StringBuilder builder,
                                             String builderGeneratedClassName,
                                             AnnotationAndValue builderAnnotation,
                                             TypeInfo typeInfo,
                                             TypeName parentTypeName,
                                             List<String> allAttributeNames,
                                             List<TypedElementName> allTypeInfos) {
        // NOP
    }

    protected void appendMetaProps(StringBuilder builder,
                                   String tag,
                                   TypeInfo typeInfo,
                                   Map<String, TypedElementName> map,
                                   List<String> allAttributeNames,
                                   List<TypedElementName> allTypeInfos) {
        map.forEach((attrName, method) ->
                            builder.append("\t\t")
                                    .append(tag)
                                    .append(".put(\"")
                                    .append(attrName)
                                    .append("\", ")
                                    .append(mapOf(attrName, method))
                                    .append(");\n"));
    }

    protected String mapOf(String attrName, TypedElementName method) {
        final Optional<AnnotationAndValue> configuredOptions = DefaultAnnotationAndValue
                .findFirst(ConfiguredOption.class.getName(), method.getAnnotations());

        TypeName typeName = method.getTypeName();
        String typeDecl = "\"type\", " + typeName.getName() + ".class";
        if (!typeName.typeArguments().isEmpty()) {
            int pos = typeName.typeArguments().size() - 1;
            typeDecl += ", \"componentType\", " + typeName.typeArguments().get(pos).getName() + ".class";
        }

        String key = (configuredOptions.isEmpty()) ? null : configuredOptions.get().valueOf("key");
        key = normalizeConfiguredOptionKey(key, attrName, method);
        typeDecl += ", " + quotedTupleOf("key", Objects.requireNonNull(key));

        String defaultValue = method.getDefaultValue();

        if (configuredOptions.isEmpty() && !AnnotationAndValue.hasNonBlankValue(defaultValue)) {
            return "Map.of(" + typeDecl + ")";
        }

        StringBuilder result = new StringBuilder();
        result.append("Map.of(" + typeDecl);

        if (configuredOptions.isEmpty()) {
            if (defaultValue.startsWith("{")) {
                defaultValue = "new String[] " + defaultValue;
            }
            result.append(", ");
            result.append(quotedValueOf("value")).append(", ").append(defaultValue);
        } else {
            configuredOptions.get().values().entrySet().stream()
                    .filter(e -> AnnotationAndValue.hasNonBlankValue(e.getValue()))
                    .filter(e -> !e.getKey().equals("key"))
                    .forEach((e) -> {
                        result.append(", ");
                        result.append(quotedTupleOf(e.getKey(), e.getValue()));
                    });
        }
        result.append(")");

        return result.toString();
    }

    protected String normalizeConfiguredOptionKey(String key, String attrName, TypedElementName method) {
        return (AnnotationAndValue.hasNonBlankValue(key)) ? key : "";
    }

    protected String quotedTupleOf(String key, String val) {
        assert (Objects.nonNull(key));
        assert (AnnotationAndValue.hasNonBlankValue(val)) : key;
        return quotedValueOf(key) + ", " + quotedValueOf(val);
    }

    protected String quotedValueOf(String val) {
        if (val.startsWith("\"") && val.endsWith("\"")) {
            return val;
        }

        return "\"" + val + "\"";
    }

    protected void gatherAllAttributeNames(Map<String, TypedElementName> map,
                                           List<TypedElementName> allTypeInfos,
                                           List<String> allAttributeNames,
                                           AtomicReference<TypeName> parentTypeName,
                                           AtomicReference<TypeName> parentAnnotationType,
                                           TypeInfo typeInfo,
                                           AnnotationAndValue builderAnnotation,
                                           boolean isBeanStyleRequired) {
        TypeInfo superTypeInfo = typeInfo.getSuperTypeInfo();
        if (Objects.nonNull(superTypeInfo)) {
            Optional<AnnotationAndValue> superBuilderAnnotation = DefaultAnnotationAndValue
                    .findFirst(builderAnnotation.typeName().getName(), superTypeInfo.getAnnotations());
            if (superBuilderAnnotation.isEmpty()) {
                gatherAllAttributeNames(map, allTypeInfos, allAttributeNames,
                                        parentTypeName,
                                        parentAnnotationType,
                                        superTypeInfo,
                                        builderAnnotation,
                                        isBeanStyleRequired);
            } else {
                populateMap(map, superTypeInfo, isBeanStyleRequired);
            }

            if (Objects.isNull(parentTypeName.get())
                    && superTypeInfo.getTypeKind().equals("INTERFACE")) {
                parentTypeName.set(superTypeInfo.getTypeName());
            } else if (Objects.isNull(parentAnnotationType.get())
                    && superTypeInfo.getTypeKind().equals("ANNOTATION_TYPE")) {
                parentAnnotationType.set(superTypeInfo.getTypeName());
            }
        }

        for (TypedElementName method : typeInfo.getElementInfo()) {
            String beanAttributeName = toBeanAttributeName(method, isBeanStyleRequired);
            TypedElementName existing = map.get(beanAttributeName);
            if (Objects.nonNull(existing)
                    && BeanUtils.isBooleanType(method.getTypeName().getName())
                    && method.getElementName().startsWith("is")) {
                AtomicReference<List<String>> alternateNames = new AtomicReference<>();
                BeanUtils.validateAndParseMethodName(method.getElementName(), method.getTypeName().getName(), true, alternateNames);
                assert (Objects.nonNull(alternateNames.get()));
                final String currentAttrName = beanAttributeName;
                Optional<String> alternateName = alternateNames.get().stream()
                        .filter(it -> !it.equals(currentAttrName))
                        .findFirst();
                if (alternateName.isPresent() && !map.containsKey(alternateName.get())) {
                    beanAttributeName = alternateName.get();
                    existing = map.get(beanAttributeName);
                }
            }

            if (Objects.nonNull(existing)) {
                if (!existing.getTypeName().equals(method.getTypeName())) {
                    throw new IllegalStateException(method + " cannot redefine types from super for " + beanAttributeName);
                }

                // allow the subclass to override the defaults, etc.
                Objects.requireNonNull(map.put(beanAttributeName, method));
                int pos = allAttributeNames.indexOf(beanAttributeName);
                if (pos >= 0) {
                    allTypeInfos.set(pos, method);
                }
                continue;
            }

            Object prev = map.put(beanAttributeName, method);
            assert (Objects.isNull(prev));

            allTypeInfos.add(method);
            if (allAttributeNames.contains(beanAttributeName)) {
                throw new AssertionError("duplicate attribute name: " + beanAttributeName + " processing " + typeInfo);
            }
            allAttributeNames.add(beanAttributeName);
        }
    }

    private void populateMap(Map<String, TypedElementName> map, TypeInfo typeInfo, boolean isBeanStyleRequired) {
        if (Objects.nonNull(typeInfo.getSuperTypeInfo())) {
            populateMap(map, typeInfo.getSuperTypeInfo(), isBeanStyleRequired);
        }

        for (TypedElementName method : typeInfo.getElementInfo()) {
            String beanAttributeName = toBeanAttributeName(method, isBeanStyleRequired);
            TypedElementName existing = map.get(beanAttributeName);
            if (Objects.nonNull(existing)) {
                if (!existing.getTypeName().equals(method.getTypeName())) {
                    throw new IllegalStateException(method + " cannot redefine types from super for " + beanAttributeName);
                }

                // allow the subclass to override the defaults, etc.
                Objects.requireNonNull(map.put(beanAttributeName, method));
            } else {
                Object prev = map.put(beanAttributeName, method);
                assert (Objects.isNull(prev));
            }
        }
    }

    protected void maybeAppendSingularSetter(StringBuilder builder,
                                             TypedElementName method,
                                             String beanAttributeName,
                                             boolean isList, boolean isMap, boolean isSet,
                                             String listType, String mapType, String setType) {
        String singularVal = toValue(Singular.class, method, false, false);
        if (Objects.nonNull(singularVal) && (isList || isMap || isSet)) {
            char[] methodName = reverseBeanName(singularVal.isBlank() ? beanAttributeName : singularVal);
            builder.append("\t\t/**\n");
            builder.append("\t\t * Singular setter for ").append(beanAttributeName).append(".\n");
            builder.append("\t\t */\n");
            builder.append("\t\tpublic B add").append(methodName).append("(").append(toGenericsDecl(method))
                    .append(") {\n");
            builder.append("\t\t\tif (Objects.isNull(").append(beanAttributeName).append(")) {\n");
            builder.append("\t\t\t\t").append(beanAttributeName).append(" = new ");
            if (isList) {
                builder.append(listType);
            } else if (isMap) {
                builder.append(mapType);
            } else { // isSet
                builder.append(setType);
            }

            builder.append("<>();\n");
            builder.append("\t\t\t}\n");
            builder.append("\t\t\tthis.").append(beanAttributeName);
            if (isList || isSet) {
                builder.append(".add(val);\n");
            } else { // isMap
                builder.append(".put(key, val);\n");
            }
            builder.append("\t\t\treturn identity();\n");
            builder.append("\t\t}\n\n");
        }
    }

    protected String appendSetter(String avoidDecl,
                                  StringBuilder mainBuilder, String beanAttributeName, String methodName,
                                  TypedElementName method,
                                  boolean isList, boolean isMap, boolean isSet, boolean upLevel,
                                  String listType, String mapType, String setType) {
        if (Objects.isNull(methodName)) {
            methodName = beanAttributeName;
        }

        StringBuilder builder = new StringBuilder();
        builder.append("\t\t/**\n");
        builder.append("\t\t * Setter for ").append(beanAttributeName).append(".\n");
        builder.append("\t\t */\n");
        builder.append("\t\tpublic B ").append(methodName).append("(")
                .append(toGenerics(method, upLevel)).append(" val) {\n");
        builder.append("\t\t\tthis.").append(beanAttributeName).append(" = ");

        if (isList) {
            builder.append("Objects.isNull(val) ? null : new ").append(listType).append("<>(val);\n");
        } else if (isMap) {
            builder.append("Objects.isNull(val) ? null : new ").append(mapType).append("<>(val);\n");
        } else if (isSet) {
            builder.append("Objects.isNull(val) ? null : new ").append(setType).append("<>(val);\n");
        } else if (method.getTypeName().array()) {
            builder.append("Objects.isNull(val) ? null : val.clone();\n");
        } else {
            builder.append("val;\n");
        }
        builder.append("\t\t\treturn identity();\n");
        builder.append("\t\t}\n\n");

        TypeName typeName = method.getTypeName();
        if (typeName.fqName().equals("char[]")) {
            builder.append("\t\t/**\n");
            builder.append("\t\t * Setter for ").append(beanAttributeName).append(".\n");
            builder.append("\t\t */\n");
            builder.append("\t\tpublic B ").append(methodName).append("(String val) {\n");
            builder.append("\t\t\tthis.").append(beanAttributeName).append(" = Objects.isNull(val) ? null : val.toCharArray();\n");
            builder.append("\t\t\treturn identity();\n");
            builder.append("\t\t}\n\n");
        }

        String thisOne = builder.toString();
        if (!thisOne.equals(avoidDecl)) {
            mainBuilder.append(thisOne);

            TypeName type = method.getTypeName();
            if (type.getName().equals(Optional.class.getName()) && !type.typeArguments().isEmpty()) {
                TypeName genericType = type.typeArguments().get(0);
                appendDirectNonOptionalSetter(mainBuilder, beanAttributeName, method, genericType);
            }
        }

        return thisOne;
    }

    protected void appendDirectNonOptionalSetter(StringBuilder builder, String beanAttributeName,
                                                 TypedElementName ignoreMethod, TypeName genericType) {
        builder.append("\t\t/**\n");
        builder.append("\t\t * Setter for ").append(beanAttributeName).append(".\n");
        builder.append("\t\t */\n");
        builder.append("\t\tpublic B ").append(beanAttributeName).append("(")
                .append(genericType.fqName()).append(" val) {\n");
        builder.append("\t\t\treturn ").append(beanAttributeName).append("(").append(Optional.class.getName());
        builder.append(".ofNullable(val));\n");
        builder.append("\t\t}\n\n");
    }

    protected void appendAnnotations(StringBuilder builder, List<AnnotationAndValue> annotations, String prefix) {
        for (AnnotationAndValue methodAnno : annotations) {
            if (methodAnno.typeName().getName().equals(Annotated.class.getName())) {
                String val = methodAnno.value();
                if (!AnnotationAndValue.hasNonBlankValue(val)) {
                    continue;
                }
                if (!val.startsWith("@")) {
                    val = "@" + val;
                }
                builder.append(prefix).append(val).append("\n");
            }
        }
    }

    protected boolean isList(TypedElementName method) {
        return isList(method.getTypeName());
    }

    protected boolean isList(TypeName typeName) {
        return (typeName.getName().equals(List.class.getName()));
    }

    protected boolean isMap(TypedElementName method) {
        return isMap(method.getTypeName());
    }

    protected boolean isMap(TypeName typeName) {
        return (typeName.getName().equals(Map.class.getName()));
    }

    protected boolean isSet(TypedElementName method) {
        return isSet(method.getTypeName());
    }

    protected boolean isSet(TypeName typeName) {
        return (typeName.getName().equals(Set.class.getName()));
    }

    protected String toGenerics(TypedElementName method, boolean upLevelToCollection) {
        return toGenerics(method.getTypeName(), upLevelToCollection);
    }

    protected String toGenerics(TypeName typeName, boolean upLevelToCollection) {
        return toGenerics(typeName, upLevelToCollection, 0);
    }

    protected String toGenerics(TypeName typeName, boolean upLevelToCollection, int depth) {
        if (typeName.typeArguments().isEmpty()) {
            return (typeName.array() || Optional.class.getName().equals(typeName.getName()))
                    ? typeName.fqName() : typeName.getName();
        }

        if (upLevelToCollection) {
            List<String> upLevelInner = typeName.typeArguments().stream()
                    .map(it -> toGenerics(it, upLevelToCollection && 0 == depth, depth + 1))
                    .collect(Collectors.toList());
            if (isList(typeName) || isSet(typeName)) {
                return Collection.class.getName() + "<" + toString(upLevelInner) + ">";
            } else if (isMap(typeName)) {
                return Map.class.getName() + "<" + toString(upLevelInner) + ">";
            }
        }

        return typeName.fqName();
    }

    protected String toGenericsDecl(TypedElementName method) {
        List<TypeName> compTypeNames = method.getTypeName().typeArguments();
        if (1 == compTypeNames.size()) {
            return avoidWildcard(compTypeNames.get(0)) + " val";
        } else if (2 == compTypeNames.size()) {
            return avoidWildcard(compTypeNames.get(0)) + " key, " + avoidWildcard(compTypeNames.get(1)) + " val";
        }
        return "Object val";
    }

    protected String avoidWildcard(TypeName typeName) {
        return typeName.wildcard() ? typeName.getName() : typeName.fqName();
    }

    protected static String toString(Collection<?> coll) {
        return toString(coll, null, null);
    }

    protected static <T> String toString(Collection<T> coll, Function<T, String> fnc, String separator) {
        Function<T, String> fn = Objects.isNull(fnc) ? String::valueOf : fnc;
        separator = Objects.isNull(separator) ? ", " : separator;
        return coll.stream().map(val -> fn.apply(val)).collect(Collectors.joining(separator));
    }

    protected String toValue(Class<? extends Annotation> annoType,
                             TypedElementName method,
                             boolean wantTypeElementDefaults,
                             boolean avoidBlanks) {
        if (wantTypeElementDefaults && Objects.nonNull(method.getDefaultValue())) {
            if (!avoidBlanks || AnnotationAndValue.hasNonBlankValue(method.getDefaultValue())) {
                return method.getDefaultValue();
            }
        }

        TypeName searchFor = DefaultTypeName.create(annoType);
        for (AnnotationAndValue anno : method.getAnnotations()) {
            if (anno.typeName().equals(searchFor)) {
                String val = anno.value();
                if (!avoidBlanks) {
                    return val;
                }
                return (AnnotationAndValue.hasNonBlankValue(val)) ? val : null;
            }
        }

        return null;
    }

    protected char[] reverseBeanName(String beanName) {
        char[] c = beanName.toCharArray();
        c[0] = Character.toUpperCase(c[0]);
        return c;
    }

    protected String toBeanAttributeName(TypedElementName method, boolean isBeanStyleRequired) {
        AtomicReference<List<String>> attrNames = new AtomicReference<>();
        BeanUtils.validateAndParseMethodName(method.getElementName(), method.getTypeName().getName(), isBeanStyleRequired, attrNames);
        if (!isBeanStyleRequired) {
            return Objects.nonNull(attrNames.get()) ? attrNames.get().get(0) : method.getElementName();
        }
        return Objects.requireNonNull(attrNames.get().get(0));
    }

}
