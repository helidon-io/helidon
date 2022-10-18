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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.config.metadata.ConfiguredOption;
import io.helidon.pico.builder.api.Annotated;
import io.helidon.pico.builder.api.Builder;
import io.helidon.pico.builder.api.Singular;
import io.helidon.pico.builder.runtime.tools.BeanUtils;
import io.helidon.pico.builder.spi.BuilderCreator;
import io.helidon.pico.builder.spi.DefaultTypeAndBody;
import io.helidon.pico.builder.spi.TypeAndBody;
import io.helidon.pico.builder.spi.TypeInfo;
import io.helidon.pico.types.AnnotationAndValue;
import io.helidon.pico.types.DefaultAnnotationAndValue;
import io.helidon.pico.types.DefaultTypeName;
import io.helidon.pico.types.TypeName;
import io.helidon.pico.types.TypedElementName;

/**
 * Default implementation for {@link io.helidon.pico.builder.spi.BuilderCreator}.
 */
@Weight(Weighted.DEFAULT_WEIGHT - 1)   // allow all other creators to take precedence over us...
public class DefaultBuilderCreator implements BuilderCreator {
    private static final boolean DEFAULT_HAS_META_ATTRIBUTES = Builder.DEFAULT_INCLUDE_META_ATTRIBUTES;
    private static final String DEFAULT_PREFIX = Builder.DEFAULT_PREFIX;
    private static final String DEFAULT_SUFFIX = Builder.DEFAULT_SUFFIX;
    private static final String DEFAULT_LIST_TYPE = Builder.DEFAULT_LIST_TYPE.getName();
    private static final String DEFAULT_MAP_TYPE = Builder.DEFAULT_MAP_TYPE.getName();
    private static final String DEFAULT_SET_TYPE = Builder.DEFAULT_SET_TYPE.getName();
    private static final TypeName BUILDER_ANNO_TYPE_NAME = DefaultTypeName.create(Builder.class);

    private static final boolean SUPPORT_STREAMS = true;
    // note: tlanger to review (toggle AVOID... to true)
    private static final boolean AVOID_GENERIC_BUILDER = false;

    /**
     * Default ctor.
     */
    public DefaultBuilderCreator() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<Class<? extends Annotation>> getSupportedAnnotationTypes() {
        return Collections.singleton(Builder.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<TypeAndBody> create(TypeInfo typeInfo, AnnotationAndValue builderAnnotation) {
        try {
            TypeName implTypeName = toImplTypeName(typeInfo.typeName(), builderAnnotation);
            preValidate(implTypeName, typeInfo, builderAnnotation);
            return Optional.ofNullable(postValidate(DefaultTypeAndBody.builder()
                    .typeName(implTypeName)
                    .body(toBody(createBodyContext(implTypeName, typeInfo, builderAnnotation)))
                    .build()));
        } catch (Exception e) {
            throw new RuntimeException("Failed while processing " + typeInfo, e);
        }
    }

    /**
     * Validates the integrity of the provided arguments in the context of what is being code generated.
     *
     * @param implTypeName      the implementation type name
     * @param typeInfo          the type info
     * @param builderAnnotation the builder annotation triggering the code generation
     */
    protected void preValidate(TypeName implTypeName,
                               TypeInfo typeInfo,
                               AnnotationAndValue builderAnnotation) {
        // NOP
    }

    private TypeAndBody postValidate(TypeAndBody build) {
        return build;
    }

    private String toPackageName(String packageName,
                                   AnnotationAndValue builderAnnotation) {
        String packageNameFromAnno = builderAnnotation.value("packageName").orElse(null);
        if (Objects.isNull(packageNameFromAnno) || packageNameFromAnno.isBlank()) {
            return packageName;
        } else if (packageNameFromAnno.startsWith(".")) {
            return packageName + packageNameFromAnno;
        } else {
            return packageNameFromAnno;
        }
    }

    private String toImplTypePrefix(AnnotationAndValue builderAnnotation) {
        return builderAnnotation.value("implPrefix").orElse(DEFAULT_PREFIX);
    }

    private String toImplTypeSuffix(AnnotationAndValue builderAnnotation) {
        return builderAnnotation.value("implSuffix").orElse(DEFAULT_SUFFIX);
    }

    private static boolean hasStreamSupport(AnnotationAndValue ignoreBuilderAnnotation) {
        return SUPPORT_STREAMS;
    }

    private static boolean hasMetaAttributes(AnnotationAndValue builderAnnotation) {
        String hasMetaAttributes = builderAnnotation.value("includeMetaAttributes").orElse(null);
        return Objects.isNull(hasMetaAttributes) ? DEFAULT_HAS_META_ATTRIBUTES : Boolean.parseBoolean(hasMetaAttributes);
    }

    private static boolean toRequireBeanStyle(AnnotationAndValue builderAnnotation,
                                         TypeInfo typeInfo) {
        String val = searchForBuilderAnnotation("requireBeanStyle", builderAnnotation, typeInfo);
        return Boolean.parseBoolean(val);
    }

    private static String toListImplType(AnnotationAndValue builderAnnotation,
                                    TypeInfo typeInfo) {
        String type = searchForBuilderAnnotation("listImplType", builderAnnotation, typeInfo);
        return (!AnnotationAndValue.hasNonBlankValue(type)) ? DEFAULT_LIST_TYPE : type;
    }

    private static String toMapImplType(AnnotationAndValue builderAnnotation,
                                   TypeInfo typeInfo) {
        String type = searchForBuilderAnnotation("mapImplType", builderAnnotation, typeInfo);
        return (!AnnotationAndValue.hasNonBlankValue(type)) ? DEFAULT_MAP_TYPE : type;
    }

    private static String toSetImplType(AnnotationAndValue builderAnnotation,
                                   TypeInfo typeInfo) {
        String type = searchForBuilderAnnotation("setImplType", builderAnnotation, typeInfo);
        return (!AnnotationAndValue.hasNonBlankValue(type)) ? DEFAULT_SET_TYPE : type;
    }

    private static String searchForBuilderAnnotation(String key,
                                                AnnotationAndValue builderAnnotation,
                                                TypeInfo typeInfo) {
        String val = builderAnnotation.value(key).orElse(null);
        if (Objects.nonNull(val)) {
            return val;
        }

        if (!builderAnnotation.typeName().equals(BUILDER_ANNO_TYPE_NAME)) {
            builderAnnotation = DefaultAnnotationAndValue
                .findFirst(BUILDER_ANNO_TYPE_NAME, typeInfo.annotations(), false).orElse(null);
            if (Objects.nonNull(builderAnnotation)) {
                val = builderAnnotation.value(key).orElse(null);
            }
        }

        return val;
    }

    private TypeName toImplTypeName(TypeName typeName,
                                      AnnotationAndValue builderAnnotation) {
        String toPackageName = toPackageName(typeName.packageName(), builderAnnotation);
        String prefix = toImplTypePrefix(builderAnnotation);
        String suffix = toImplTypeSuffix(builderAnnotation);
        return DefaultTypeName.create(toPackageName, prefix + typeName.className() + suffix);
    }

    /**
     * Represents the context of the body being code generated.
     */
    protected static class BodyContext {
        private final TypeName implTypeName;
        private final TypeInfo typeInfo;
        private final AnnotationAndValue builderAnnotation;
        private final Map<String, TypedElementName> map = new LinkedHashMap<>();
        private final List<TypedElementName> allTypeInfos = new ArrayList<>();
        private final List<String> allAttributeNames = new ArrayList<>();
        private final AtomicReference<TypeName> parentTypeName = new AtomicReference<>();
        private final AtomicReference<TypeName> parentAnnotationType = new AtomicReference<>();
        private final boolean hasStreamSupport;
        private final boolean hasMetaAttributes;
        private final boolean isBeanStyleRequired;
        private final String listType;
        private final String mapType;
        private final String setType;
        private final boolean hasParent;
        private final TypeName ctorBuilderAcceptTypeName;
        private final String genericBuilderClassDecl;

        /**
         * Ctor.
         *
         * @param implTypeName      the impl type name
         * @param typeInfo          the type info
         * @param builderAnnotation the builder annotation
         */
        protected BodyContext(TypeName implTypeName,
                    TypeInfo typeInfo,
                    AnnotationAndValue builderAnnotation) {
            this.implTypeName = implTypeName;
            this.typeInfo = typeInfo;
            this.builderAnnotation = builderAnnotation;
            this.hasStreamSupport = hasStreamSupport(builderAnnotation);
            this.hasMetaAttributes = hasMetaAttributes(builderAnnotation);
            this.isBeanStyleRequired = toRequireBeanStyle(builderAnnotation, typeInfo);
            this.listType = toListImplType(builderAnnotation, typeInfo);
            this.mapType = toMapImplType(builderAnnotation, typeInfo);
            this.setType = toSetImplType(builderAnnotation, typeInfo);
            gatherAllAttributeNames(this, typeInfo);
            assert (allTypeInfos.size() == allAttributeNames.size());
            this.hasParent = Objects.nonNull(parentTypeName.get());
            this.ctorBuilderAcceptTypeName = (hasParent)
                    ? typeInfo.typeName()
                    : (Objects.nonNull(parentAnnotationType.get()) && typeInfo.elementInfo().isEmpty()
                                    ? typeInfo.superTypeInfo().get().typeName() : typeInfo.typeName());
            if (AVOID_GENERIC_BUILDER) {
                this.genericBuilderClassDecl = "Bldr";
            } else {
                this.genericBuilderClassDecl = "Builder";
            }
        }
    }

    /**
     * Creates the context for the class being built.
     *
     * @param implTypeName      the implementation type name that will be generated
     * @param typeInfo          the type info describing the target interface
     * @param builderAnnotation the builder annotation that triggered the builder being created
     * @return the context describing what is being built
     */
    protected BodyContext createBodyContext(TypeName implTypeName,
                                            TypeInfo typeInfo,
                                            AnnotationAndValue builderAnnotation) {
        return new BodyContext(implTypeName, typeInfo, builderAnnotation);
    }

    /**
     * Generates the body of the generated builder class.
     *
     * @param ctx   the context for what is being built
     * @return      the string representation of the class being built
     */
    protected String toBody(BodyContext ctx) {
        StringBuilder builder = new StringBuilder();
        appendHeader(builder, ctx);
        appendExtraFields(builder, ctx.hasParent, ctx.hasMetaAttributes, ctx.typeInfo);
        appendFields(builder, ctx);
        appendCtor(builder, ctx);
        appendExtraPostCtorCode(builder, ctx);
        appendMetaAttributes(builder, ctx);
        appendBasicGetters(builder, ctx);
        appendToStringMethod(builder, ctx.hasParent, ctx.typeInfo);
        appendInnerToStringMethod(builder, ctx);
        appendHashCodeAndEquals(builder, ctx);
        appendExtraMethods(builder, ctx.builderAnnotation, ctx.hasParent, ctx.typeInfo, ctx.allAttributeNames, ctx.allTypeInfos);
        appendInterfaceBasedGetters(builder, ctx);
        appendToBuilderMethods(builder, ctx);
        appendBuilder(builder, ctx);
        appendExtraInnerClasses(builder, ctx.hasParent, ctx.typeInfo);
        appendFooter(builder, ctx);
        return builder.toString();
    }

    /**
     * Appends the footer of the generated class.
     *
     * @param builder       the builder
     * @param ignoredCtx    the context
     */
    protected void appendFooter(StringBuilder builder,
                                BodyContext ignoredCtx) {
        builder.append("}\n");
    }

    private void appendBuilder(StringBuilder builder,
                                 BodyContext ctx) {
        builder.append("\tpublic static class ").append(ctx.genericBuilderClassDecl);
        builder.append("<B extends ").append(ctx.genericBuilderClassDecl).append("<B, T>, T extends ");
        builder.append(ctx.ctorBuilderAcceptTypeName).append("> ");
        if (ctx.hasParent) {
            builder.append("extends ").append(toImplTypeName(ctx.parentTypeName.get(), ctx.builderAnnotation))
                    .append(".").append(ctx.genericBuilderClassDecl).append("<B, T>");
        } else if (ctx.hasStreamSupport) {
            builder.append("implements java.util.function.Supplier<T>, java.util.function.Consumer<T> ");
        }
        builder.append(" {\n");

        appendExtraBuilderFields(builder, ctx.genericBuilderClassDecl, ctx.builderAnnotation,
                                 ctx.typeInfo, ctx.parentTypeName.get(), ctx.allAttributeNames, ctx.allTypeInfos);

        int i = 0;
        for (String beanAttributeName : ctx.allAttributeNames) {
            TypedElementName method = ctx.allTypeInfos.get(i);
            TypeName type = method.typeName();
            builder.append("\t\tprivate ").append(type.array() ? type.fqName() : type.name()).append(" ")
                    .append(beanAttributeName);
            String defaultVal = toConfiguredOptionValue(method, true, true);
            if (Objects.nonNull(defaultVal)) {
                builder.append(" = ");
                appendDefaultValueAssignment(builder, method, defaultVal);
            }
            builder.append(";\n");
            i++;
        }
        builder.append("\n");

        builder.append("\t\tprotected ").append(ctx.genericBuilderClassDecl).append("(T val) {\n");
        if (ctx.hasParent) {
            builder.append("\t\t\tsuper(val);\n");
        }
        appendOverridesOfDefaultValues(builder, ctx.isBeanStyleRequired, ctx.typeInfo, ctx.allAttributeNames, ctx.allTypeInfos);
        builder.append("\t\t\tacceptThis(val);\n");
        builder.append("\t\t}\n\n");

        appendExtraBuilderMethods(builder, ctx.genericBuilderClassDecl, ctx.builderAnnotation,
                                  ctx.typeInfo, ctx.parentTypeName.get(), ctx.allAttributeNames, ctx.allTypeInfos);

        if (!ctx.hasParent && ctx.hasStreamSupport) {
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

        if (ctx.hasStreamSupport || ctx.hasParent) {
            builder.append("\t\t@Override\n");
        }

        builder.append("\t\tpublic void accept(T val) {\n");
        if (ctx.hasParent) {
            builder.append("\t\t\tsuper.accept(val);\n");
        }
        builder.append("\t\t\tacceptThis(val);\n");
        builder.append("\t\t}\n\n");

        builder.append("\t\tprivate void acceptThis(T val) {\n");
        builder.append("\t\t\tif (Objects.isNull(val)) {\n"
                               + "\t\t\t\treturn;\n"
                               + "\t\t\t}\n\n");
        i = 0;
        for (String beanAttributeName : ctx.allAttributeNames) {
            TypedElementName method = ctx.allTypeInfos.get(i++);
            String getterName = method.elementName();
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
        for (String beanAttributeName : ctx.allAttributeNames) {
            TypedElementName method = ctx.allTypeInfos.get(i);
            boolean isList = isList(method);
            boolean isMap = !isList && isMap(method);
            boolean isSet = !isMap && isSet(method);
            boolean upLevel = isSet || isList;
            appendSetter(builder, beanAttributeName, null, method, ctx);
            if (isList || isMap || isSet) {
                // NOP
            } else {
                boolean isBoolean = BeanUtils.isBooleanType(method.typeName().name());
                if (isBoolean && beanAttributeName.startsWith("is")) {
                    // possibly overload setter to strip the "is"...
                    String basicAttributeName = ""
                            + Character.toLowerCase(beanAttributeName.charAt(2))
                            + beanAttributeName.substring(3);
                    if (!ctx.allAttributeNames.contains(basicAttributeName)) {
                        appendSetter(builder, beanAttributeName, basicAttributeName, method, ctx);
                    }
                }
            }

            maybeAppendSingularSetter(builder, method, beanAttributeName, isList, isMap, isSet, ctx);

            i++;
        }

        builder.append("\t\tpublic ").append(ctx.implTypeName).append(" build() {\n");
        appendBuilderBuildPreSteps(builder);
        builder.append("\t\t\treturn new ").append(ctx.implTypeName.className()).append("(this);\n");
        builder.append("\t\t}\n");

        builder.append("\t}\n");

        if (AVOID_GENERIC_BUILDER) {
            builder.append("\n\tpublic static class Builder extends ");
            builder.append(ctx.genericBuilderClassDecl).append("<Builder, ")
                    .append(ctx.ctorBuilderAcceptTypeName).append("> {\n");
            builder.append("\t\tprotected Builder(").append(ctx.ctorBuilderAcceptTypeName).append(" val) {\n");
            builder.append("\t\t\tsuper(val);\n");
            builder.append("\t\t}\n");
            builder.append("\t}\n\n");
        }
    }

    private void appendToBuilderMethods(StringBuilder builder,
                                          BodyContext ctx) {
        builder.append("\t/**\n\t * @return A builder for {@link ");
        builder.append(ctx.typeInfo.typeName());
        builder.append("}\n\t */\n");

        if (AVOID_GENERIC_BUILDER) {
            builder.append("\tpublic static Builder ");
        } else {
            builder.append("\tpublic static ").append(ctx.genericBuilderClassDecl);
            builder.append("<? extends ").append(ctx.genericBuilderClassDecl).append(", ? extends ");
            builder.append(ctx.ctorBuilderAcceptTypeName).append("> ");
        }
        builder.append("builder() {\n");
        builder.append("\t\treturn new Builder((").append(ctx.typeInfo.typeName()).append(") null);\n");
        builder.append("\t}\n\n");

        builder.append("\t/**\n\t * @return A builder for {@link ").append(ctx.typeInfo.typeName());
        builder.append("}\n\t */\n");

        if (AVOID_GENERIC_BUILDER) {
            builder.append("\tpublic static Builder ");
        } else {
            builder.append("\tpublic static ").append(ctx.genericBuilderClassDecl);
            builder.append("<? extends ").append(ctx.genericBuilderClassDecl).append(", ? extends ");
            builder.append(ctx.typeInfo.typeName()).append("> ");
        }

        builder.append("toBuilder(").append(ctx.ctorBuilderAcceptTypeName).append(" val) {\n");
        builder.append("\t\treturn new Builder(val);\n");
        builder.append("\t}\n\n");

        String decl;
        if (AVOID_GENERIC_BUILDER) {
            decl = "public static Builder toBuilder({args}) {";
        } else {
            decl = "public static Builder<? extends Builder, ? extends "
                    + ctx.typeInfo.typeName() + "> toBuilder({args}) {";
        }

        appendExtraToBuilderBuilderFunctions(builder,
                                     decl, ctx.typeInfo, ctx.parentTypeName.get(), ctx.allAttributeNames, ctx.allTypeInfos);
    }

    private void appendInterfaceBasedGetters(StringBuilder builder,
                                               BodyContext ctx) {
        int i = 0;
        for (String beanAttributeName : ctx.allAttributeNames) {
            TypedElementName method = ctx.allTypeInfos.get(i);
            appendAnnotations(builder, method.annotations(), "\t");
            builder.append("\t@Override\n");
            builder.append("\tpublic ").append(toGenerics(method, false)).append(" ").append(method.elementName())
                    .append("() {\n");
            builder.append("\t\treturn ").append(beanAttributeName).append(";\n");
            builder.append("\t}\n\n");
            i++;
        }
    }

    private void appendCtor(StringBuilder builder,
                              BodyContext ctx) {
        builder.append("\n\tprotected ").append(ctx.implTypeName.className());
        builder.append("(").append(ctx.genericBuilderClassDecl).append(" b) {\n");
        appendExtraCtorCode(builder, ctx.hasParent, "b", ctx.typeInfo);
        appendCtorCode(builder, "b", ctx);
        builder.append("\t}\n\n");
    }

    private void appendHashCodeAndEquals(StringBuilder builder,
                                           BodyContext ctx) {
        builder.append("\t@Override\n");
        builder.append("\tpublic int hashCode() {\n");
        if (ctx.hasParent) {
            builder.append("\t\tint hashCode = super.hashCode();\n");
        } else {
            builder.append("\t\tint hashCode = 0;\n");
        }
        for (TypedElementName method : ctx.allTypeInfos) {
            builder.append("\t\thashCode ^= Objects.hashCode(").append(method.elementName()).append("());\n");
        }
        builder.append("\t\treturn hashCode;\n");
        builder.append("\t}\n\n");

        builder.append("\t@Override\n");
        builder.append("\tpublic boolean equals(Object another) {\n");
        builder.append("\t\tif (this == another) {\n\t\t\treturn true;\n\t\t}\n");
        builder.append("\t\tif (!(another instanceof ").append(ctx.typeInfo.typeName()).append(")) {\n");
        builder.append("\t\t\treturn false;\n");
        builder.append("\t\t}\n");
        builder.append("\t\t").append(ctx.typeInfo.typeName()).append(" other = (")
                .append(ctx.typeInfo.typeName()).append(") another;\n");
        if (ctx.hasParent) {
            builder.append("\t\tboolean equals = super.equals(other);\n");
        } else {
            builder.append("\t\tboolean equals = true;\n");
        }
        for (TypedElementName method : ctx.allTypeInfos) {
            builder.append("\t\tequals &= Objects.equals(").append(method.elementName()).append("(), other.")
                    .append(method.elementName()).append("());\n");
        }
        builder.append("\t\treturn equals;\n");
        builder.append("\t}\n\n");
    }

    private void appendInnerToStringMethod(StringBuilder builder,
                                             BodyContext ctx) {
        if (ctx.hasParent) {
            builder.append("\t@Override\n");
        }
        builder.append("\tprotected String toStringInner() {\n");
        if (ctx.hasParent) {
            builder.append("\t\tString result = super.toStringInner();\n");
            if (!ctx.allAttributeNames.isEmpty()) {
                builder.append("\t\tif (!result.isEmpty() && !result.endsWith(\", \")) {\n");
                builder.append("\t\t\tresult += \", \";\n");
                builder.append("\t\t}\n");
            }
        } else {
            builder.append("\t\tString result = \"\";\n");
        }

        int i = 0;
        for (String beanAttributeName : ctx.allAttributeNames) {
            TypedElementName method = ctx.allTypeInfos.get(i++);
            TypeName typeName = method.typeName();
            builder.append("\t\tresult += \"").append(beanAttributeName).append("=\" + ");
            if (typeName.array()) {
                builder.append("(Objects.isNull(").append(beanAttributeName).append(") ? null : ");
                if (typeName.primitive()) {
                    builder.append("\"not-null\"");
                } else {
                    builder.append("java.util.Arrays.asList(");
                    builder.append(method.elementName()).append("())");
                }
                builder.append(")");
            } else {
                builder.append(method.elementName()).append("()");
            }
            if (i < ctx.allAttributeNames.size()) {
                builder.append(" + \", \"");
            }
            builder.append(";\n");
        }
        builder.append("\t\treturn result;\n");
        builder.append("\t}\n\n");
    }

    /**
     * Adds the basic getters to the generated builder output.
     *
     * @param builder   the builder
     * @param ctx       the context
     */
    protected void appendBasicGetters(StringBuilder builder,
                                      BodyContext ctx) {
        if (!ctx.hasParent && ctx.hasStreamSupport) {
            builder.append("\t@Override\n"
                                   + "\tpublic T get() {\n"
                                   + "\t\treturn (T) this;\n"
                                   + "\t}\n\n");
        }

        if (Objects.nonNull(ctx.parentAnnotationType.get())) {
            builder.append("\t@Override\n");
            builder.append("\tpublic Class<? extends java.lang.annotation.Annotation> annotationType() {\n");
            builder.append("\t\treturn ").append(ctx.typeInfo.superTypeInfo().get().typeName()).append(".class;\n");
            builder.append("\t}\n\n");
        }
    }

    private void appendMetaAttributes(StringBuilder builder,
                                        BodyContext ctx) {
        if (ctx.hasMetaAttributes) {
            builder.append("\tpublic static Class<?> __getMetaConfigBeanType() {\n"
                                   + "\t\treturn " + ctx.typeInfo.typeName().name() + ".class;\n"
                                   + "\t}\n\n");

            builder.append("\tprivate static Map<String, Map<String, Object>> __calcMeta() {\n");
            builder.append("\t\tMap<String, Map<String, Object>> metaProps = new java.util.LinkedHashMap<>();\n");
            AtomicBoolean needsCustomMapOf = new AtomicBoolean();
            appendMetaProps(builder, "metaProps",
                            ctx.typeInfo, ctx.map, ctx.allAttributeNames, ctx.allTypeInfos, needsCustomMapOf);
            builder.append("\t\treturn metaProps;\n");
            builder.append("\t}\n\n");

            if (needsCustomMapOf.get()) {
                appendCustomMapOf(builder);
            }

            builder.append("\tpublic static Map<String, Map<String, Object>> __getMetaAttributes() {\n"
                                   + "\t\treturn __metaProps;\n"
                                   + "\t}\n\n");
        }
    }

    /**
     * Adds the fields part of the generated builder.
     *
     * @param builder   the builder
     * @param ctx       the context
     */
    protected void appendFields(StringBuilder builder,
                                BodyContext ctx) {
        for (int i = 0; i < ctx.allTypeInfos.size(); i++) {
            TypedElementName method = ctx.allTypeInfos.get(i);
            String beanAttributeName = ctx.allAttributeNames.get(i);
            appendAnnotations(builder, method.annotations(), "\t");
            builder.append("\tprivate ");
            builder.append(getFieldModifier());
            builder.append(toGenerics(method, false)).append(" ");
            builder.append(beanAttributeName).append(";\n");
        }
    }

    /**
     * Adds the header part of the generated builder.
     *
     * @param builder   the builder
     * @param ctx       the context
     */
    protected void appendHeader(StringBuilder builder,
                                BodyContext ctx) {
        builder.append("package ").append(ctx.implTypeName.packageName()).append(";\n\n");
        builder.append("import java.util.Collections;\n");
        builder.append("import java.util.Map;\n");
        builder.append("import java.util.Objects;\n");
        appendExtraImports(builder, ctx.typeInfo);
        builder.append("\n");
        builder.append("/**\n");
        builder.append(" * Concrete implementation w/ builder for {@link ").append(ctx.typeInfo.typeName()).append("}.\n");
        builder.append(" */\n");
        builder.append(BuilderTemplateHelper.getDefaultGeneratedSticker(getClass().getSimpleName())).append("\n");
        appendAnnotations(builder, ctx.typeInfo.annotations(), "");
        builder.append("@SuppressWarnings(\"unchecked\")\n");
        builder.append("public class ").append(ctx.implTypeName.className());
        if (!ctx.hasParent && ctx.hasStreamSupport) {
            builder.append("<T extends ").append(ctx.implTypeName.className()).append(">");
        }
        if (ctx.hasParent) {
            builder.append(" extends ").append(toImplTypeName(ctx.parentTypeName.get(), ctx.builderAnnotation));
        }
        builder.append(" implements ").append(ctx.typeInfo.typeName());
        if (!ctx.hasParent && ctx.hasStreamSupport) {
            builder.append(", java.util.function.Supplier<T>");
        }
        builder.append(" {\n");
    }

    private void appendDefaultValueAssignment(StringBuilder builder, TypedElementName method, String defaultVal) {
        TypeName type = method.typeName();
        boolean isOptional = type.name().equals(Optional.class.getName());
        if (isOptional) {
            builder.append(Optional.class.getName()).append(".of(");
            if (!type.typeArguments().isEmpty()) {
                type = type.typeArguments().get(0);
            }
        }

        boolean isString = type.name().equals(String.class.getName()) && !type.array();
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

    /**
     * Adds extra imports to the generated builder.
     *
     * @param builder   the builder
     * @param typeInfo  the context
     */
    protected void appendExtraImports(StringBuilder builder,
                                      TypeInfo typeInfo) {
        // NOP
    }

    /**
     * Generated the toString method on the generated builder.
     *
     * @param builder   the builder
     * @param hasParent true if the type has a parent
     * @param typeInfo  the context
     */
    protected void appendToStringMethod(StringBuilder builder,
                                        boolean hasParent,
                                        TypeInfo typeInfo) {
        builder.append("\t@Override\n");
        builder.append("\tpublic String toString() {\n");
        builder.append("\t\treturn getClass().getSimpleName() + \"(\" + toStringInner() + \")\";\n");
        builder.append("\t}\n\n");
    }

    /**
     * Adds extra methods to the generated builder.
     *
     * @param builder               the builder
     * @param builderAnnotation     the builder annotation
     * @param hasParent             true if there is a parent for the generated type
     * @param typeInfo              the type info
     * @param allAttributeNames     all the bean attribute names belonging to the builder
     * @param allTypeInfos          all the methods belonging to the builder
     */
    protected void appendExtraMethods(StringBuilder builder,
                                      AnnotationAndValue builderAnnotation,
                                      boolean hasParent,
                                      TypeInfo typeInfo,
                                      List<String> allAttributeNames,
                                      List<TypedElementName> allTypeInfos) {
        // NOP
    }

    /**
     * Adds extra inner classes to write on the builder.
     *
     * @param builder               the builder
     * @param hasParent             true if there is a parent for the generated type
     * @param typeInfo              the type info
     */
    protected void appendExtraInnerClasses(StringBuilder builder,
                                           boolean hasParent,
                                           TypeInfo typeInfo) {
        // NOP
    }

    /**
     * Returns the "final" field modifier by default.
     *
     * @return the field modifier
     */
    protected String getFieldModifier() {
        return "final ";
    }

    private void appendCtorCode(StringBuilder builder,
                                  String ignoredBuilderTag,
                                  BodyContext ctx) {
        if (ctx.hasParent) {
            builder.append("\t\tsuper(b);\n");
        }
        int i = 0;
        for (String beanAttributeName : ctx.allAttributeNames) {
            TypedElementName method = ctx.allTypeInfos.get(i++);
            builder.append("\t\tthis.").append(beanAttributeName).append(" = ");

            if (isList(method)) {
                builder.append("Objects.isNull(b.").append(beanAttributeName).append(")\n");
                builder.append("\t\t\t? Collections.emptyList() : Collections.unmodifiableList(new ")
                        .append(ctx.listType).append("<>(b.").append(beanAttributeName).append("));\n");
            } else if (isMap(method)) {
                builder.append("Objects.isNull(b.").append(beanAttributeName).append(")\n");
                builder.append("\t\t\t? Collections.emptyMap() : Collections.unmodifiableMap(new ")
                        .append(ctx.mapType).append("<>(b.").append(beanAttributeName).append("));\n");
            } else if (isSet(method)) {
                builder.append("Objects.isNull(b.").append(beanAttributeName).append(")\n");
                builder.append("\t\t\t? Collections.emptySet() : Collections.unmodifiableSet(new ")
                        .append(ctx.setType).append("<>(b.").append(beanAttributeName).append("));\n");
            } else {
                builder.append("b.").append(beanAttributeName).append(";\n");
            }
        }
    }

    /**
     * Adds extra default ctor code.
     *
     * @param builder               the builder
     * @param hasParent             true if there is a parent for the generated type
     * @param builderTag            the tag (variable name) used for the builder arg
     * @param typeInfo              the type info
     */
    protected void appendExtraCtorCode(StringBuilder builder,
                                     boolean hasParent,
                                     String builderTag,
                                     TypeInfo typeInfo) {
        // NOP
    }

    /**
     * Adds extra code following the ctor decl.
     *
     * @param builder   the builder
     * @param ctx       the context
     */
    protected void appendExtraPostCtorCode(StringBuilder builder,
                                           BodyContext ctx) {
        // NOP
    }

    /**
     * Adds extra fields on the main generated class.
     *
     * @param builder               the builder
     * @param hasParent             true if there is a parent for the generated type
     * @param hasMetaAttributes     true if there are meta attributes present
     * @param typeInfo              the type info
     */
    protected void appendExtraFields(StringBuilder builder,
                                     boolean hasParent,
                                     boolean hasMetaAttributes,
                                     TypeInfo typeInfo) {
        if (hasMetaAttributes) {
            builder.append(
                    "\tprivate static final Map<String, Map<String, Object>> __metaProps = "
                            + "Collections.unmodifiableMap(__calcMeta());\n\n");
        }
    }

    /**
     * Adds extra toBuilder() methods.
     *
     * @param builder               the builder
     * @param decl                  the declaration template for the toBuilder method
     * @param typeInfo              the type info
     * @param parentTypeName        the parent type name
     * @param allAttributeNames     all the bean attribute names belonging to the builder
     * @param allTypeInfos          all the methods belonging to the builder
     */
    protected void appendExtraToBuilderBuilderFunctions(StringBuilder builder,
                                                        String decl,
                                                        TypeInfo typeInfo,
                                                        TypeName parentTypeName,
                                                        List<String> allAttributeNames,
                                                        List<TypedElementName> allTypeInfos) {
        // NOP
    }

    /**
     * Adds extra builder methods.
     *
     * @param builder               the builder
     * @param builderGeneratedClassName the builder class name (as written in source form)
     * @param builderAnnotation     the builder annotation
     * @param typeInfo              the type info
     * @param parentTypeName        the parent type name
     * @param allAttributeNames     all the bean attribute names belonging to the builder
     * @param allTypeInfos          all the methods belonging to the builder
     */
    protected void appendExtraBuilderFields(StringBuilder builder,
                                           String builderGeneratedClassName,
                                           AnnotationAndValue builderAnnotation,
                                           TypeInfo typeInfo,
                                           TypeName parentTypeName,
                                           List<String> allAttributeNames,
                                           List<TypedElementName> allTypeInfos) {
        // NOP
    }

    private void appendOverridesOfDefaultValues(StringBuilder builder,
                                                  boolean isBeanStyleRequired,
                                                  TypeInfo typeInfo,
                                                  List<String> allAttributeNames,
                                                  List<TypedElementName> ignoredAllTypeInfos) {
        boolean first = true;
        for (TypedElementName method : typeInfo.elementInfo()) {
            String beanAttributeName = toBeanAttributeName(method, isBeanStyleRequired);
            if (!allAttributeNames.contains(beanAttributeName)) {
                // candidate for override...
                String thisDefault = toConfiguredOptionValue(method, true, true);
                String superDefault = getSuperValue(typeInfo.superTypeInfo(), beanAttributeName, isBeanStyleRequired);
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

    private String getSuperValue(Optional<TypeInfo> optSuperTypeInfo,
                                   String elemName,
                                   boolean isBeanStyleRequired) {
        if (optSuperTypeInfo.isEmpty()) {
            return null;
        }
        TypeInfo superTypeInfo = optSuperTypeInfo.get();
        Optional<TypedElementName> method = superTypeInfo.elementInfo().stream()
                .filter(it -> toBeanAttributeName(it, isBeanStyleRequired).equals(elemName))
                .findFirst();
        if (method.isPresent()) {
            String defaultValue = toConfiguredOptionValue(method.get(), true, true);
            if (AnnotationAndValue.hasNonBlankValue(defaultValue)) {
                return defaultValue;
            }
        } else {
            return getSuperValue(superTypeInfo.superTypeInfo(), elemName, isBeanStyleRequired);
        }

        return null;
    }

    private void appendDefaultOverride(StringBuilder builder,
                                         String attrName,
                                         TypedElementName method,
                                         String override) {
        builder.append("\t\t\t\t").append(attrName).append("(");
        appendDefaultValueAssignment(builder, method, override);
        builder.append(");\n");
    }

    /**
     * Adds extra builder pre-steps.
     *
     * @param builder               the builder
     */
    protected void appendBuilderBuildPreSteps(StringBuilder builder) {
        // NOP
    }

    /**
     * Adds extra builder methods.
     *
     * @param builder               the builder
     * @param builderGeneratedClassName the builder class name (as written in source form)
     * @param builderAnnotation     the builder annotation
     * @param typeInfo              the type info
     * @param parentTypeName        the parent type name
     * @param allAttributeNames     all the bean attribute names belonging to the builder
     * @param allTypeInfos          all the methods belonging to the builder
     */
    protected void appendExtraBuilderMethods(StringBuilder builder,
                                             String builderGeneratedClassName,
                                             AnnotationAndValue builderAnnotation,
                                             TypeInfo typeInfo,
                                             TypeName parentTypeName,
                                             List<String> allAttributeNames,
                                             List<TypedElementName> allTypeInfos) {
        // NOP
    }

    /**
     * Adds extra meta properties to the generated code.
     *
     * @param builder               the builder
     * @param tag                   the tag used to represent the meta props variable on the generated code
     * @param typeInfo              the type info
     * @param map                   the map of all the methods
     * @param allAttributeNames     all the bean attribute names belonging to the builder
     * @param allTypeInfos          all the methods belonging to the builder
     * @param needsCustomMapOf      will be set to true if a custom map.of() function needs to be generated (i.e., if over 9 tuples)
     */
    protected void appendMetaProps(StringBuilder builder,
                                   String tag,
                                   TypeInfo typeInfo,
                                   Map<String, TypedElementName> map,
                                   List<String> allAttributeNames,
                                   List<TypedElementName> allTypeInfos,
                                   AtomicBoolean needsCustomMapOf) {
        map.forEach((attrName, method) ->
                            builder.append("\t\t")
                                    .append(tag)
                                    .append(".put(\"")
                                    .append(attrName)
                                    .append("\", ")
                                    .append(mapOf(attrName, method, needsCustomMapOf))
                                    .append(");\n"));
    }

    private void appendCustomMapOf(StringBuilder builder) {
        builder.append("\tprivate static Map<String, Object> __mapOf(Object... args) {\n"
                               + "\t\tMap<String, Object> result = new java.util.LinkedHashMap<>(args.length / 2);\n"
                               + "\t\tint i = 0;\n"
                               + "\t\twhile (i < args.length) {\n"
                               + "\t\t\tresult.put((String) args[i], args[i + 1]);\n"
                               + "\t\t\ti += 2;\n"
                               + "\t\t}\n"
                               + "\t\treturn result;\n"
                               + "\t}\n\n");
    }

    private String mapOf(String attrName,
                           TypedElementName method,
                           AtomicBoolean needsCustomMapOf) {
        final Optional<? extends AnnotationAndValue> configuredOptions = DefaultAnnotationAndValue
                .findFirst(ConfiguredOption.class.getName(), method.annotations());

        TypeName typeName = method.typeName();
        String typeDecl = "\"type\", " + typeName.name() + ".class";
        if (!typeName.typeArguments().isEmpty()) {
            int pos = typeName.typeArguments().size() - 1;
            typeDecl += ", \"componentType\", " + typeName.typeArguments().get(pos).name() + ".class";
        }

        String key = (configuredOptions.isEmpty()) ? null : configuredOptions.get().value("key").orElse(null);
        key = normalizeConfiguredOptionKey(key, attrName, method);
        typeDecl += ", " + quotedTupleOf("key", Objects.requireNonNull(key));

        String defaultValue = method.defaultValue().orElse(null);

        if (configuredOptions.isEmpty() && !AnnotationAndValue.hasNonBlankValue(defaultValue)) {
            return "Map.of(" + typeDecl + ")";
        }

        needsCustomMapOf.set(true);
        StringBuilder result = new StringBuilder();
        result.append("__mapOf(").append(typeDecl);

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

    /**
     * Normalize the configured option key.
     *
     * @param key           the key attribute
     * @param attrName      the attribute name
     * @param method        the method
     * @return the key to write on the generated output.
     */
    protected String normalizeConfiguredOptionKey(String key,
                                                  String attrName,
                                                  TypedElementName method) {
        return AnnotationAndValue.hasNonBlankValue(key) ? key : "";
    }

    private String quotedTupleOf(String key,
                                   String val) {
        assert (Objects.nonNull(key));
        assert (AnnotationAndValue.hasNonBlankValue(val)) : key;
        if (key.equals("value") && ConfiguredOption.UNCONFIGURED.equals(val)) {
            val = ConfiguredOption.class.getName() + ".UNCONFIGURED";
        } else {
            val = quotedValueOf(val);
        }
        return quotedValueOf(key) + ", " + val;
    }

    private String quotedValueOf(String val) {
        if (val.startsWith("\"") && val.endsWith("\"")) {
            return val;
        }

        return "\"" + val + "\"";
    }

    private static void gatherAllAttributeNames(BodyContext ctx,
                                                TypeInfo typeInfo) {
        TypeInfo superTypeInfo = typeInfo.superTypeInfo().orElse(null);
        if (Objects.nonNull(superTypeInfo)) {
            Optional<? extends AnnotationAndValue> superBuilderAnnotation = DefaultAnnotationAndValue
                    .findFirst(ctx.builderAnnotation.typeName(), superTypeInfo.annotations(), false);
            if (superBuilderAnnotation.isEmpty()) {
                gatherAllAttributeNames(ctx, superTypeInfo);
            } else {
                populateMap(ctx.map, superTypeInfo, ctx.isBeanStyleRequired);
            }

            if (Objects.isNull(ctx.parentTypeName.get())
                    && superTypeInfo.typeKind().equals("INTERFACE")) {
                ctx.parentTypeName.set(superTypeInfo.typeName());
            } else if (Objects.isNull(ctx.parentAnnotationType.get())
                    && superTypeInfo.typeKind().equals("ANNOTATION_TYPE")) {
                ctx.parentAnnotationType.set(superTypeInfo.typeName());
            }
        }

        for (TypedElementName method : typeInfo.elementInfo()) {
            String beanAttributeName = toBeanAttributeName(method, ctx.isBeanStyleRequired);
            TypedElementName existing = ctx.map.get(beanAttributeName);
            if (Objects.nonNull(existing)
                    && BeanUtils.isBooleanType(method.typeName().name())
                    && method.elementName().startsWith("is")) {
                AtomicReference<List<String>> alternateNames = new AtomicReference<>();
                BeanUtils.validateAndParseMethodName(method.elementName(),
                                                     method.typeName().name(), true, alternateNames);
                assert (Objects.nonNull(alternateNames.get()));
                final String currentAttrName = beanAttributeName;
                Optional<String> alternateName = alternateNames.get().stream()
                        .filter(it -> !it.equals(currentAttrName))
                        .findFirst();
                if (alternateName.isPresent() && !ctx.map.containsKey(alternateName.get())) {
                    beanAttributeName = alternateName.get();
                    existing = ctx.map.get(beanAttributeName);
                }
            }

            if (Objects.nonNull(existing)) {
                if (!existing.typeName().equals(method.typeName())) {
                    throw new IllegalStateException(method + " cannot redefine types from super for " + beanAttributeName);
                }

                // allow the subclass to override the defaults, etc.
                Objects.requireNonNull(ctx.map.put(beanAttributeName, method));
                int pos = ctx.allAttributeNames.indexOf(beanAttributeName);
                if (pos >= 0) {
                    ctx.allTypeInfos.set(pos, method);
                }
                continue;
            }

            Object prev = ctx.map.put(beanAttributeName, method);
            assert (Objects.isNull(prev));

            ctx.allTypeInfos.add(method);
            if (ctx.allAttributeNames.contains(beanAttributeName)) {
                throw new AssertionError("duplicate attribute name: " + beanAttributeName + " processing " + typeInfo);
            }
            ctx.allAttributeNames.add(beanAttributeName);
        }
    }

    private static void populateMap(Map<String, TypedElementName> map,
                                    TypeInfo typeInfo,
                                    boolean isBeanStyleRequired) {
        if (typeInfo.superTypeInfo().isPresent()) {
            populateMap(map, typeInfo.superTypeInfo().get(), isBeanStyleRequired);
        }

        for (TypedElementName method : typeInfo.elementInfo()) {
            String beanAttributeName = toBeanAttributeName(method, isBeanStyleRequired);
            TypedElementName existing = map.get(beanAttributeName);
            if (Objects.nonNull(existing)) {
                if (!existing.typeName().equals(method.typeName())) {
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

    /**
     * Appends the singular setter methods on the builder.
     *
     * @param builder               the builder
     * @param method                the method
     * @param beanAttributeName     the bean attribute name
     * @param isList                true if the output involves List type
     * @param isMap                 true if the output involves Map type
     * @param isSet                 true if the output involves Set type
     * @param ctx                   the context
     */
    protected void maybeAppendSingularSetter(StringBuilder builder,
                                             TypedElementName method,
                                             String beanAttributeName,
                                             boolean isList, boolean isMap, boolean isSet,
                                             BodyContext ctx) {
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
                builder.append(ctx.listType);
            } else if (isMap) {
                builder.append(ctx.mapType);
            } else { // isSet
                builder.append(ctx.setType);
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

    /**
     * Append the setters for the given bean attribute name.
     *
     * @param mainBuilder           the builder
     * @param beanAttributeName     the bean attribute name
     * @param methodName            the method name
     * @param method                the method
     * @param ctx                   the body context
     */
    protected void appendSetter(StringBuilder mainBuilder,
                                String beanAttributeName,
                                String methodName,
                                TypedElementName method,
                                BodyContext ctx) {
        if (Objects.isNull(methodName)) {
            methodName = beanAttributeName;
        }
        boolean isList = isList(method);
        boolean isMap = !isList && isMap(method);
        boolean isSet = !isMap && isSet(method);
        boolean upLevel = isSet || isList;

        StringBuilder builder = new StringBuilder();
        builder.append("\t\t/**\n");
        builder.append("\t\t * Setter for ").append(beanAttributeName).append(".\n");
        builder.append("\t\t */\n");
        builder.append("\t\tpublic B ").append(methodName).append("(")
                .append(toGenerics(method, upLevel)).append(" val) {\n");
        builder.append("\t\t\tthis.").append(beanAttributeName).append(" = ");

        if (isList) {
            builder.append("Objects.isNull(val) ? null : new ").append(ctx.listType).append("<>(val);\n");
        } else if (isMap) {
            builder.append("Objects.isNull(val) ? null : new ").append(ctx.mapType).append("<>(val);\n");
        } else if (isSet) {
            builder.append("Objects.isNull(val) ? null : new ").append(ctx.setType).append("<>(val);\n");
        } else if (method.typeName().array()) {
            builder.append("Objects.isNull(val) ? null : val.clone();\n");
        } else {
            builder.append("val;\n");
        }
        builder.append("\t\t\treturn identity();\n");
        builder.append("\t\t}\n\n");

        TypeName typeName = method.typeName();
        if (typeName.fqName().equals("char[]")) {
            builder.append("\t\t/**\n");
            builder.append("\t\t * Setter for ").append(beanAttributeName).append(".\n");
            builder.append("\t\t */\n");
            builder.append("\t\tpublic B ").append(methodName).append("(String val) {\n");
            builder.append("\t\t\tthis.").append(beanAttributeName)
                    .append(" = Objects.isNull(val) ? null : val.toCharArray();\n");
            builder.append("\t\t\treturn identity();\n");
            builder.append("\t\t}\n\n");
        }

        mainBuilder.append(builder);

        TypeName type = method.typeName();
        if (type.name().equals(Optional.class.getName()) && !type.typeArguments().isEmpty()) {
            TypeName genericType = type.typeArguments().get(0);
            appendDirectNonOptionalSetter(mainBuilder, beanAttributeName, method, genericType);
        }
    }

    /**
     * Append the setters for the given bean attribute name.
     *
     * @param builder               the builder
     * @param beanAttributeName     the bean attribute name
     * @param ignoreMethod          the method
     * @param genericType           the generic return type name of the method
     */
    protected void appendDirectNonOptionalSetter(StringBuilder builder,
                                                 String beanAttributeName,
                                                 TypedElementName ignoreMethod,
                                                 TypeName genericType) {
        builder.append("\t\t/**\n");
        builder.append("\t\t * Setter for ").append(beanAttributeName).append(".\n");
        builder.append("\t\t */\n");
        builder.append("\t\tpublic B ").append(beanAttributeName).append("(")
                .append(genericType.fqName()).append(" val) {\n");
        builder.append("\t\t\treturn ").append(beanAttributeName).append("(").append(Optional.class.getName());
        builder.append(".ofNullable(val));\n");
        builder.append("\t\t}\n\n");
    }

    /**
     * Append {@link io.helidon.pico.builder.api.Annotated} annotations if any.
     *
     * @param builder       the builder
     * @param annotations   the list of annotations
     * @param prefix        the spacing prefix
     */
    protected void appendAnnotations(StringBuilder builder,
                                     List<AnnotationAndValue> annotations,
                                     String prefix) {
        for (AnnotationAndValue methodAnno : annotations) {
            if (methodAnno.typeName().name().equals(Annotated.class.getName())) {
                String val = methodAnno.value().orElse("");
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

    /**
     * Returns true if the provided method involved {@link java.util.List}.
     *
     * @param method    the method
     * @return true if list is part of the type
     */
    protected static boolean isList(TypedElementName method) {
        return isList(method.typeName());
    }

    /**
     * Returns true if the provided method involved {@link java.util.List}.
     *
     * @param typeName  the type name
     * @return true if list is part of the type
     */
    protected static boolean isList(TypeName typeName) {
        return (typeName.name().equals(List.class.getName()));
    }

    /**
     * Returns true if the provided method involved {@link java.util.Map}.
     *
     * @param method    the method
     * @return true if map is part of the type
     */
    protected static boolean isMap(TypedElementName method) {
        return isMap(method.typeName());
    }

    /**
     * Returns true if the provided method involved {@link java.util.Map}.
     *
     * @param typeName  the type name
     * @return true if map is part of the type
     */
    protected static boolean isMap(TypeName typeName) {
        return (typeName.name().equals(Map.class.getName()));
    }

    /**
     * Returns true if the provided method involved {@link java.util.Set}.
     *
     * @param method    the method
     * @return true if set is part of the type
     */
    protected static boolean isSet(TypedElementName method) {
        return isSet(method.typeName());
    }

    /**
     * Returns true if the provided method involved {@link java.util.Set}.
     *
     * @param typeName  the type name
     * @return true if set is part of the type
     */
    protected static boolean isSet(TypeName typeName) {
        return (typeName.name().equals(Set.class.getName()));
    }

    /**
     * Produces the generic descriptor decl for the method.
     *
     * @param method                the method
     * @param upLevelToCollection   true if the generics should be "up leveled"
     * @return the generic decl
     */
    protected static String toGenerics(TypedElementName method,
                                boolean upLevelToCollection) {
        return toGenerics(method.typeName(), upLevelToCollection);
    }

    /**
     * Produces the generic descriptor decl for the method.
     *
     * @param typeName              the type name
     * @param upLevelToCollection   true if the generics should be "up leveled"
     * @return the generic decl
     */
    protected static String toGenerics(TypeName typeName,
                                boolean upLevelToCollection) {
        return toGenerics(typeName, upLevelToCollection, 0);
    }

    private static String toGenerics(TypeName typeName,
                                boolean upLevelToCollection,
                                int depth) {
        if (typeName.typeArguments().isEmpty()) {
            return (typeName.array() || Optional.class.getName().equals(typeName.name()))
                    ? typeName.fqName() : typeName.name();
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

    private static String toGenericsDecl(TypedElementName method) {
        List<TypeName> compTypeNames = method.typeName().typeArguments();
        if (1 == compTypeNames.size()) {
            return avoidWildcard(compTypeNames.get(0)) + " val";
        } else if (2 == compTypeNames.size()) {
            return avoidWildcard(compTypeNames.get(0)) + " key, " + avoidWildcard(compTypeNames.get(1)) + " val";
        }
        return "Object val";
    }

    private static String avoidWildcard(TypeName typeName) {
        return typeName.wildcard() ? typeName.name() : typeName.fqName();
    }

    /**
     * Walk the collection to build a separator-delimited string value.
     *
     * @param coll          the collection
     * @return              the string representation
     */
    protected static String toString(Collection<?> coll) {
        return toString(coll, null, null);
    }

    /**
     * Walk the collection to build a separator-delimited string value.
     *
     * @param coll          the collection
     * @param fnc           the function to apply, defaulting to {@link String#valueOf(java.lang.Object)}
     * @param separator     the separator, defaulting to ", "
     * @return              the string representation
     * @param <T>           the types held by the collection
     */
    protected static <T> String toString(Collection<T> coll,
                                         Function<T, String> fnc,
                                         String separator) {
        Function<T, String> fn = Objects.isNull(fnc) ? String::valueOf : fnc;
        separator = Objects.isNull(separator) ? ", " : separator;
        return coll.stream().map(fn::apply).collect(Collectors.joining(separator));
    }

    /**
     * Extracts the value from the method, ignoring {@link io.helidon.config.metadata.ConfiguredOption#UNCONFIGURED}.
     *
     * @param method                    the method
     * @param wantTypeElementDefaults   flag indicating whether the method passed can be used to obtain the default values
     * @param avoidBlanks               flag indicating whether blank values should be ignored
     * @return the default value, or null if there is no default value applicable for the given arguments
     */
    protected static String toConfiguredOptionValue(TypedElementName method,
                                             boolean wantTypeElementDefaults,
                                             boolean avoidBlanks) {
        String val = toValue(ConfiguredOption.class, method, wantTypeElementDefaults, avoidBlanks);
        return ConfiguredOption.UNCONFIGURED.equals(val) ? null : val;
    }

    /**
     * Retrieves the default value of the method to a string value.
     *
     * @param annoType                  the annotation that is being applied, that might have the default value
     * @param method                    the method
     * @param wantTypeElementDefaults   flag indicating whether the method passed can be used to obtain the default values
     * @param avoidBlanks               flag indicating whether blank values should be ignored
     * @return the default value, or null if there is no default value applicable for the given arguments
     */
    protected static String toValue(Class<? extends Annotation> annoType,
                             TypedElementName method,
                             boolean wantTypeElementDefaults,
                             boolean avoidBlanks) {
        if (wantTypeElementDefaults && Objects.nonNull(method.defaultValue())) {
            if (!avoidBlanks || AnnotationAndValue.hasNonBlankValue(method.defaultValue())) {
                return method.defaultValue().orElse(null);
            }
        }

        TypeName searchFor = DefaultTypeName.create(annoType);
        for (AnnotationAndValue anno : method.annotations()) {
            if (anno.typeName().equals(searchFor)) {
                String val = anno.value().orElse(null);
                if (!avoidBlanks) {
                    return val;
                }
                return AnnotationAndValue.hasNonBlankValue(val) ? val : null;
            }
        }

        return null;
    }

    private static char[] reverseBeanName(String beanName) {
        char[] c = beanName.toCharArray();
        c[0] = Character.toUpperCase(c[0]);
        return c;
    }

    private static String toBeanAttributeName(TypedElementName method,
                                         boolean isBeanStyleRequired) {
        AtomicReference<List<String>> attrNames = new AtomicReference<>();
        BeanUtils.validateAndParseMethodName(method.elementName(), method.typeName().name(), isBeanStyleRequired, attrNames);
        if (!isBeanStyleRequired) {
            return Objects.nonNull(attrNames.get()) ? attrNames.get().get(0) : method.elementName();
        }
        return Objects.requireNonNull(attrNames.get().get(0));
    }

}
