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

package io.helidon.pico.builder.processor.tools;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
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
import io.helidon.pico.builder.Annotated;
import io.helidon.pico.builder.AttributeVisitor;
import io.helidon.pico.builder.Builder;
import io.helidon.pico.builder.Singular;
import io.helidon.pico.builder.processor.spi.BuilderCreator;
import io.helidon.pico.builder.processor.spi.DefaultTypeAndBody;
import io.helidon.pico.builder.processor.spi.TypeAndBody;
import io.helidon.pico.builder.processor.spi.TypeInfo;
import io.helidon.pico.builder.spi.BeanUtils;
import io.helidon.pico.builder.spi.RequiredAttributeVisitor;
import io.helidon.pico.types.AnnotationAndValue;
import io.helidon.pico.types.DefaultAnnotationAndValue;
import io.helidon.pico.types.DefaultTypeName;
import io.helidon.pico.types.TypeName;
import io.helidon.pico.types.TypedElementName;

/**
 * Default implementation for {@link io.helidon.pico.builder.processor.spi.BuilderCreator}.
 */
@Weight(Weighted.DEFAULT_WEIGHT - 1)   // allow all other creators to take precedence over us...
public class DefaultBuilderCreator implements BuilderCreator {
    private static final boolean DEFAULT_INCLUDE_META_ATTRIBUTES = true;
    private static final boolean DEFAULT_REQUIRE_LIBRARY_DEPENDENCIES = true;
    private static final String DEFAULT_IMPL_PREFIX = Builder.DEFAULT_IMPL_PREFIX;
    private static final String DEFAULT_ABSTRACT_IMPL_PREFIX = Builder.DEFAULT_ABSTRACT_IMPL_PREFIX;
    private static final String DEFAULT_SUFFIX = Builder.DEFAULT_SUFFIX;
    private static final String DEFAULT_LIST_TYPE = Builder.DEFAULT_LIST_TYPE.getName();
    private static final String DEFAULT_MAP_TYPE = Builder.DEFAULT_MAP_TYPE.getName();
    private static final String DEFAULT_SET_TYPE = Builder.DEFAULT_SET_TYPE.getName();
    private static final TypeName BUILDER_ANNO_TYPE_NAME = DefaultTypeName.create(Builder.class);
    private static final boolean SUPPORT_STREAMS_ON_IMPL = false;
    private static final boolean SUPPORT_STREAMS_ON_BUILDER = true;

    /**
     * Default constructor.
     */
    // note: this needs to remain public since it will be resolved via service loader ...
    @Deprecated
    public DefaultBuilderCreator() {
    }

    @Override
    public Set<Class<? extends Annotation>> supportedAnnotationTypes() {
        return Collections.singleton(Builder.class);
    }

    @Override
    public List<TypeAndBody> create(TypeInfo typeInfo, AnnotationAndValue builderAnnotation) {
        try {
            Optional<TypeName> abstractImplTypeName = toAbstractImplTypeName(typeInfo.typeName(), builderAnnotation);
            Optional<TypeName> implTypeName = toImplTypeName(typeInfo.typeName(), builderAnnotation);
            if (implTypeName.isEmpty()) {
                return Collections.emptyList();
            }
            preValidate(implTypeName.get(), typeInfo, builderAnnotation);

            LinkedList<TypeAndBody> builds = new LinkedList<>();
            if (abstractImplTypeName.isPresent()) {
                TypeName typeName = abstractImplTypeName.get();
                builds.add(DefaultTypeAndBody.builder()
                                   .typeName(typeName)
                                   .body(toBody(createBodyContext(false, typeName, typeInfo, builderAnnotation)))
                                   .build());
            }
            TypeName typeName = implTypeName.get();
            builds.add(DefaultTypeAndBody.builder()
                               .typeName(typeName)
                               .body(toBody(createBodyContext(true, typeName, typeInfo, builderAnnotation)))
                               .build());

            return postValidate(builds);
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
    }

    /**
     * Can be overridden to validate the result before it is returned to the framework.
     *
     * @param builds the builds of the TypeAndBody that will be code generated by this creator
     * @return the validated list
     */
    protected List<TypeAndBody> postValidate(List<TypeAndBody> builds) {
        return builds;
    }

    /**
     * In support of {@link io.helidon.pico.builder.Builder#packageName()}.
     */
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

    /**
     * In support of {@link io.helidon.pico.builder.Builder#abstractImplPrefix()}.
     */
    private String toAbstractImplTypePrefix(AnnotationAndValue builderAnnotation) {
        return builderAnnotation.value("abstractImplPrefix").orElse(DEFAULT_ABSTRACT_IMPL_PREFIX);
    }

    /**
     * In support of {@link io.helidon.pico.builder.Builder#implPrefix()}.
     */
    private String toImplTypePrefix(AnnotationAndValue builderAnnotation) {
        return builderAnnotation.value("implPrefix").orElse(DEFAULT_IMPL_PREFIX);
    }

    /**
     * In support of {@link io.helidon.pico.builder.Builder#implSuffix()}.
     */
    private String toImplTypeSuffix(AnnotationAndValue builderAnnotation) {
        return builderAnnotation.value("implSuffix").orElse(DEFAULT_SUFFIX);
    }

    /**
     * In support of {@link io.helidon.pico.builder.Builder#includeMetaAttributes()}.
     */
    private static boolean toIncludeMetaAttributes(AnnotationAndValue builderAnnotation,
                                                   TypeInfo typeInfo) {
        String val = searchForBuilderAnnotation("includeMetaAttributes", builderAnnotation, typeInfo);
        return Objects.isNull(val) ? DEFAULT_INCLUDE_META_ATTRIBUTES : Boolean.parseBoolean(val);
    }

    /**
     * In support of {@link io.helidon.pico.builder.Builder#requireLibraryDependencies()}.
     */
    private static boolean toRequireLibraryDependencies(AnnotationAndValue builderAnnotation,
                                                        TypeInfo typeInfo) {
        String val = searchForBuilderAnnotation("requireLibraryDependencies", builderAnnotation, typeInfo);
        return Objects.isNull(val) ? DEFAULT_REQUIRE_LIBRARY_DEPENDENCIES : Boolean.parseBoolean(val);
    }

    /**
     * In support of {@link io.helidon.pico.builder.Builder#requireBeanStyle()}.
     */
    private static boolean toRequireBeanStyle(AnnotationAndValue builderAnnotation,
                                              TypeInfo typeInfo) {
        String val = searchForBuilderAnnotation("requireBeanStyle", builderAnnotation, typeInfo);
        return Boolean.parseBoolean(val);
    }

    /**
     * In support of {@link io.helidon.pico.builder.Builder#listImplType()}.
     */
    private static String toListImplType(AnnotationAndValue builderAnnotation,
                                         TypeInfo typeInfo) {
        String type = searchForBuilderAnnotation("listImplType", builderAnnotation, typeInfo);
        return (!BuilderTypeTools.hasNonBlankValue(type)) ? DEFAULT_LIST_TYPE : type;
    }

    /**
     * In support of {@link io.helidon.pico.builder.Builder#mapImplType()} ()}.
     */
    private static String toMapImplType(AnnotationAndValue builderAnnotation,
                                        TypeInfo typeInfo) {
        String type = searchForBuilderAnnotation("mapImplType", builderAnnotation, typeInfo);
        return (!BuilderTypeTools.hasNonBlankValue(type)) ? DEFAULT_MAP_TYPE : type;
    }

    /**
     * In support of {@link io.helidon.pico.builder.Builder#setImplType()}.
     */
    private static String toSetImplType(AnnotationAndValue builderAnnotation,
                                        TypeInfo typeInfo) {
        String type = searchForBuilderAnnotation("setImplType", builderAnnotation, typeInfo);
        return (!BuilderTypeTools.hasNonBlankValue(type)) ? DEFAULT_SET_TYPE : type;
    }

    private static boolean hasStreamSupportOnImpl(boolean ignoreDoingConcreteClass,
                                                  AnnotationAndValue ignoreBuilderAnnotation) {
        return SUPPORT_STREAMS_ON_IMPL;
    }

    private static boolean hasStreamSupportOnBuilder(boolean ignoreDoingConcreteClass,
                                                     AnnotationAndValue ignoreBuilderAnnotation) {
        return SUPPORT_STREAMS_ON_BUILDER;
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

    /**
     * Constructs the abstract implementation type name for what is code generated.
     *
     * @param typeName          the target interface that the builder applies to
     * @param builderAnnotation the builder annotation triggering the build
     * @return the abstract type name of the implementation, or empty if this should not be code generated
     */
    protected Optional<TypeName> toAbstractImplTypeName(TypeName typeName,
                                                        AnnotationAndValue builderAnnotation) {
        String toPackageName = toPackageName(typeName.packageName(), builderAnnotation);
        String prefix = toAbstractImplTypePrefix(builderAnnotation);
        String suffix = toImplTypeSuffix(builderAnnotation);
        return Optional.of(DefaultTypeName.create(toPackageName, prefix + typeName.className() + suffix));
    }

    /**
     * Constructs the default implementation type name for what is code generated.
     *
     * @param typeName          the target interface that the builder applies to
     * @param builderAnnotation the builder annotation triggering the build
     * @return the type name of the implementation, or empty if this should not be code generated
     */
    protected Optional<TypeName> toImplTypeName(TypeName typeName,
                                                AnnotationAndValue builderAnnotation) {
        String toPackageName = toPackageName(typeName.packageName(), builderAnnotation);
        String prefix = toImplTypePrefix(builderAnnotation);
        String suffix = toImplTypeSuffix(builderAnnotation);
        return Optional.of(DefaultTypeName.create(toPackageName, prefix + typeName.className() + suffix));
    }

    /**
     * Represents the context of the body being code generated.
     */
    protected static class BodyContext {
        private final boolean doingConcreteType;
        private final TypeName implTypeName;
        private final TypeInfo typeInfo;
        private final AnnotationAndValue builderAnnotation;
        private final Map<String, TypedElementName> map = new LinkedHashMap<>();
        private final List<TypedElementName> allTypeInfos = new ArrayList<>();
        private final List<String> allAttributeNames = new ArrayList<>();
        private final AtomicReference<TypeName> parentTypeName = new AtomicReference<>();
        private final AtomicReference<TypeName> parentAnnotationType = new AtomicReference<>();
        private final boolean hasStreamSupportOnImpl;
        private final boolean hasStreamSupportOnBuilder;
        private final boolean includeMetaAttributes;
        private final boolean requireLibraryDependencies;
        private final boolean isBeanStyleRequired;
        private final String listType;
        private final String mapType;
        private final String setType;
        private final boolean hasParent;
        private final TypeName ctorBuilderAcceptTypeName;
        private final String genericBuilderClassDecl;
        private final String genericBuilderAliasDecl;
        private final String genericBuilderAcceptAliasDecl;

        /**
         * Constructor.
         *
         * @param doingConcreteType true if the concrete type is being generated, otherwise the abstract class
         * @param implTypeName      the impl type name
         * @param typeInfo          the type info
         * @param builderAnnotation the builder annotation
         */
        protected BodyContext(boolean doingConcreteType,
                              TypeName implTypeName,
                              TypeInfo typeInfo,
                              AnnotationAndValue builderAnnotation) {
            this.doingConcreteType = doingConcreteType;
            this.implTypeName = implTypeName;
            this.typeInfo = typeInfo;
            this.builderAnnotation = builderAnnotation;
            this.hasStreamSupportOnImpl = hasStreamSupportOnImpl(doingConcreteType, builderAnnotation);
            this.hasStreamSupportOnBuilder = hasStreamSupportOnBuilder(doingConcreteType, builderAnnotation);
            this.includeMetaAttributes = toIncludeMetaAttributes(builderAnnotation, typeInfo);
            this.requireLibraryDependencies = toRequireLibraryDependencies(builderAnnotation, typeInfo);
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
            this.genericBuilderClassDecl = "Builder";
            this.genericBuilderAliasDecl = ("B".equals(typeInfo.typeName().className())) ? "BU" : "B";
            this.genericBuilderAcceptAliasDecl = ("T".equals(typeInfo.typeName().className())) ? "TY" : "T";
        }
    }

    /**
     * Creates the context for the class being built.
     *
     * @param doingConcreteType true if the concrete type is being generated, otherwise the abstract class
     * @param typeName          the type name that will be code generated
     * @param typeInfo          the type info describing the target interface
     * @param builderAnnotation the builder annotation that triggered the builder being created
     * @return the context describing what is being built
     */
    protected BodyContext createBodyContext(boolean doingConcreteType,
                                            TypeName typeName,
                                            TypeInfo typeInfo,
                                            AnnotationAndValue builderAnnotation) {
        return new BodyContext(doingConcreteType, typeName, typeInfo, builderAnnotation);
    }

    /**
     * Generates the body of the generated builder class.
     *
     * @param ctx the context for what is being built
     * @return the string representation of the class being built
     */
    protected String toBody(BodyContext ctx) {
        StringBuilder builder = new StringBuilder();
        appendHeader(builder, ctx);
        appendExtraFields(builder, ctx);
        appendFields(builder, ctx);
        appendCtor(builder, ctx);
        appendExtraPostCtorCode(builder, ctx);
        appendInterfaceBasedGetters(builder, ctx);
        appendBasicGetters(builder, ctx);
        appendMetaAttributes(builder, ctx);
        appendToStringMethod(builder, ctx);
        appendInnerToStringMethod(builder, ctx);
        appendHashCodeAndEquals(builder, ctx);
        appendExtraMethods(builder, ctx);
        appendToBuilderMethods(builder, ctx);
        appendBuilder(builder, ctx);
        appendExtraInnerClasses(builder, ctx);
        appendFooter(builder, ctx);
        return builder.toString();
    }

    /**
     * Appends the footer of the generated class.
     *
     * @param builder    the builder
     * @param ignoredCtx the context
     */
    protected void appendFooter(StringBuilder builder,
                                BodyContext ignoredCtx) {
        builder.append("}\n");
    }

    private void appendBuilder(StringBuilder builder,
                               BodyContext ctx) {
        appendBuilderHeader(builder, ctx);
        appendExtraBuilderFields(builder, ctx.genericBuilderClassDecl, ctx.builderAnnotation,
                                 ctx.typeInfo, ctx.parentTypeName.get(), ctx.allAttributeNames, ctx.allTypeInfos);
        appendBuilderBody(builder, ctx);

        appendExtraBuilderMethods(builder, ctx);

        if (ctx.doingConcreteType) {
            if (ctx.hasParent) {
                builder.append("\t\t@Override\n");
            } else {
                builder.append("\t\t/**\n"
                                       + "\t\t * Builds the instance.\n"
                                       + "\t\t *\n"
                                       + "\t\t * @return the built instance\n"
                                       + "\t\t * @throws java.lang.AssertionError if any required attributes are missing\n"
                                       + "\t\t */\n");
            }
            builder.append("\t\tpublic ").append(ctx.implTypeName).append(" build() {\n");
            appendRequiredValidator(builder, ctx);
            appendBuilderBuildPreSteps(builder, ctx);
            builder.append("\t\t\treturn new ").append(ctx.implTypeName.className()).append("(this);\n");
            builder.append("\t\t}\n");
        } else {
            int i = 0;
            for (String beanAttributeName : ctx.allAttributeNames) {
                TypedElementName method = ctx.allTypeInfos.get(i);
                boolean isList = isList(method);
                boolean isMap = !isList && isMap(method);
                boolean isSet = !isMap && isSet(method);
                boolean ignoredUpLevel = isSet || isList;
                appendSetter(builder, beanAttributeName, Optional.empty(), method, ctx);
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
                            appendSetter(builder, beanAttributeName, Optional.of(basicAttributeName), method, ctx);
                        }
                    }
                }

                maybeAppendSingularSetter(builder, method, beanAttributeName, isList, isMap, isSet, ctx);
                i++;
            }

            if (!ctx.hasParent && !ctx.requireLibraryDependencies) {
                builder.append("\t\t/**\n"
                                       + "\t\t * Build the instance from this builder.\n"
                                       + "\t\t *\n"
                                       + "\t\t * @return instance of the built type\n"
                                       + "\t\t */\n"
                                       + "\t\tpublic abstract ").append(ctx.genericBuilderAcceptAliasDecl)
                        .append(" build();\n\n");

                if (ctx.hasStreamSupportOnBuilder) {
                    builder.append("\t\t/**\n"
                                           + "\t\t * Update the builder in a fluent API way.\n"
                                           + "\t\t *\n"
                                           + "\t\t * @param consumer consumer of the builder instance\n"
                                           + "\t\t * @return updated builder instance\n"
                                           + "\t\t */\n");
                    builder.append("\t\tpublic B update(Consumer<")
                            .append(ctx.genericBuilderAcceptAliasDecl)
                            .append("> consumer) {\n"
                                            + "\t\t\tconsumer.accept(get());\n"
                                            + "\t\t\treturn identity();\n"
                                            + "\t\t}\n\n");
                }

                if (!ctx.requireLibraryDependencies) {
                    builder.append("\t\t/**\n"
                                           + "\t\t * Instance of this builder as the correct type.\n"
                                           + "\t\t *\n"
                                           + "\t\t * @return this instance typed to correct type\n"
                                           + "\t\t */\n");
                    builder.append("\t\t@SuppressWarnings(\"unchecked\")\n");
                    builder.append("\t\tprotected ").append(ctx.genericBuilderAliasDecl).append(" identity() {\n"
                                                                                                        + "\t\t\treturn (")
                            .append(ctx.genericBuilderAliasDecl).append(") this;\n"
                                                                                + "\t\t}\n\n"
                                                                                + "\t\t@Override\n"
                                                                                + "\t\tpublic ")
                            .append(ctx.genericBuilderAcceptAliasDecl).append(" get() {\n"
                                                                                      + "\t\t\treturn (")
                            .append(ctx.genericBuilderAcceptAliasDecl).append(") build();\n"
                                                                                      + "\t\t}\n\n");
                }
            }

            if (ctx.hasStreamSupportOnBuilder || ctx.requireLibraryDependencies) {
                builder.append("\t\t@Override\n");
            }
            builder.append("\t\tpublic void accept(").append(ctx.genericBuilderAcceptAliasDecl).append(" val) {\n");
            if (ctx.hasParent) {
                builder.append("\t\t\tsuper.accept(val);\n");
            }
            builder.append("\t\t\tacceptThis(val);\n");
            builder.append("\t\t}\n\n");

            builder.append("\t\tprivate void acceptThis(").append(ctx.genericBuilderAcceptAliasDecl).append(" val) {\n");
            builder.append("\t\t\tif (Objects.isNull(val)) {\n"
                                   + "\t\t\t\treturn;\n"
                                   + "\t\t\t}\n");
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
            builder.append("\t\t}\n");
        }

        // end of the generated builder inner class here
        builder.append("\t}\n");
    }

    private void appendBuilderBody(StringBuilder builder, BodyContext ctx) {
        if (!ctx.doingConcreteType) {
            int i = 0;
            for (String beanAttributeName : ctx.allAttributeNames) {
                TypedElementName method = ctx.allTypeInfos.get(i);
                TypeName type = method.typeName();
                builder.append("\t\t/**\n"
                                       + "\t\t * field value for {@code " + method + "()}.\n"
                                       + "\t\t */\n");
                builder.append("\t\tprotected ").append(type.array() ? type.fqName() : type.name()).append(" ")
                        .append(beanAttributeName);
                Optional<String> defaultVal = toConfiguredOptionValue(method, true, true);
                if (defaultVal.isPresent()) {
                    builder.append(" = ");
                    appendDefaultValueAssignment(builder, method, defaultVal.get());
                }
                builder.append(";\n");
                i++;
            }
            builder.append("\n");
        }

        builder.append("\t\t/**\n"
                               + "\t\t * The fluent builder constructor.\n"
                               + "\t\t *\n"
                               + "\t\t * @param val the value to copy to initialize the builder attributes\n"
                               + "\t\t */\n");
        if (ctx.doingConcreteType) {
            builder.append("\t\tprotected ").append(ctx.genericBuilderClassDecl).append("(");
            builder.append(ctx.ctorBuilderAcceptTypeName).append(" val) {\n");
            builder.append("\t\t\tsuper(val);\n");
        } else {
            builder.append("\t\tprotected ").append(ctx.genericBuilderClassDecl).append("(")
                    .append(ctx.genericBuilderAcceptAliasDecl).append(" val) {\n");
            if (ctx.hasParent) {
                builder.append("\t\t\tsuper(val);\n");
            }
            appendOverridesOfDefaultValues(builder, ctx);
            builder.append("\t\t\tacceptThis(val);\n");
        }
        builder.append("\t\t}\n\n");
    }

    private void appendBuilderHeader(StringBuilder builder,
                                     BodyContext ctx) {
        builder.append("\n\t/**\n"
                               + "\t * The fluent builder for this type.\n"
                               + "\t *\n");
        if (!ctx.doingConcreteType) {
            builder.append("\t * @param <").append(ctx.genericBuilderAliasDecl).append(">\tthe type of the builder\n");
            builder.append("\t * @param <").append(ctx.genericBuilderAcceptAliasDecl)
                    .append(">\tthe type of the built instance\n");
        }
        builder.append("\t */\n");
        builder.append("\tpublic ");
        if (!ctx.doingConcreteType) {
            builder.append("abstract ");
        }
        builder.append("static class ").append(ctx.genericBuilderClassDecl);

        if (ctx.doingConcreteType) {
            builder.append(" extends ");
            builder.append(toAbstractImplTypeName(ctx.typeInfo.typeName(), ctx.builderAnnotation).get());
            builder.append(".").append(ctx.genericBuilderClassDecl);
            builder.append("<").append(ctx.genericBuilderClassDecl).append(", ").append(ctx.ctorBuilderAcceptTypeName)
                    .append("> {\n");
        } else {
            builder.append("<").append(ctx.genericBuilderAliasDecl).append(" extends ").append(ctx.genericBuilderClassDecl);
            builder.append("<").append(ctx.genericBuilderAliasDecl).append(", ");
            builder.append(ctx.genericBuilderAcceptAliasDecl).append(">, ").append(ctx.genericBuilderAcceptAliasDecl)
                    .append(" extends ");
            builder.append(ctx.ctorBuilderAcceptTypeName).append("> ");
            if (ctx.hasParent) {
                builder.append("extends ").append(toAbstractImplTypeName(ctx.parentTypeName.get(), ctx.builderAnnotation).get())
                        .append(".").append(ctx.genericBuilderClassDecl);
                builder.append("<").append(ctx.genericBuilderAliasDecl).append(", ").append(ctx.genericBuilderAcceptAliasDecl);
                builder.append(">");
            } else if (ctx.hasStreamSupportOnBuilder) {
                builder.append("implements Supplier<").append(ctx.genericBuilderAcceptAliasDecl)
                        .append(">, Consumer<").append(ctx.genericBuilderAcceptAliasDecl).append(">");
            }
            if (!ctx.hasParent) {
                if (ctx.requireLibraryDependencies) {
                    builder.append(", io.helidon.common.Builder<").append(ctx.genericBuilderAliasDecl)
                            .append(", ").append(ctx.genericBuilderAcceptAliasDecl).append(">");
                } else {
                    builder.append("/*, io.helidon.common.Builder<").append(ctx.genericBuilderAliasDecl)
                            .append(", ").append(ctx.genericBuilderAcceptAliasDecl).append("> */");
                }
            }

            builder.append(" {\n");
        }
    }

    /**
     * Appends the simple {@link io.helidon.config.metadata.ConfiguredOption#required()} validation inside the build() method.
     *
     * @param builder the builder
     * @param ctx     the context
     */
    protected void appendRequiredValidator(StringBuilder builder,
                                           BodyContext ctx) {
        if (ctx.includeMetaAttributes) {
            builder.append("\t\t\tRequiredAttributeVisitor visitor = new RequiredAttributeVisitor();\n"
                                   + "\t\t\tvisitAttributes(visitor, null);\n"
                                   + "\t\t\tvisitor.validate();\n");
        }
    }

    private void appendToBuilderMethods(StringBuilder builder,
                                        BodyContext ctx) {
        if (!ctx.doingConcreteType) {
            return;
        }

        builder.append("\t/**\n"
                               + "\t * Creates a builder for this type.\n"
                               + "\t *\n");
        builder.append("\t * @return A builder for {@link ");
        builder.append(ctx.typeInfo.typeName());
        builder.append("}\n\t */\n");
        builder.append("\tpublic static ").append(ctx.genericBuilderClassDecl);
        builder.append(" builder() {\n");
        builder.append("\t\treturn new Builder((").append(ctx.typeInfo.typeName()).append(") null);\n");
        builder.append("\t}\n\n");

        builder.append("\t/**\n"
                               + "\t * Creates a builder for this type, initialized with the attributes from the values passed"
                               + ".\n\n");
        builder.append("\t * @param val the value to copy to initialize the builder attributes\n");
        builder.append("\t * @return A builder for {@link ").append(ctx.typeInfo.typeName());
        builder.append("}\n\t */\n");

        builder.append("\tpublic static ").append(ctx.genericBuilderClassDecl);
        builder.append(" toBuilder(").append(ctx.ctorBuilderAcceptTypeName).append(" val) {\n");
        builder.append("\t\treturn new Builder(val);\n");
        builder.append("\t}\n\n");

        String decl = "public static Builder toBuilder({args}) {";
        appendExtraToBuilderBuilderFunctions(builder, decl, ctx);
    }

    private void appendInterfaceBasedGetters(StringBuilder builder,
                                             BodyContext ctx) {
        if (ctx.doingConcreteType) {
            return;
        }

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
        builder.append("\n\t/**\n"
                               + "\t * Constructor using the builder argument.\n"
                               + "\t *\n"
                               + "\t * @param b\tthe builder\n"
                               + "\t */\n");
        builder.append("\tprotected ").append(ctx.implTypeName.className());
        builder.append("(");
        builder.append(ctx.genericBuilderClassDecl);
        if (ctx.doingConcreteType) {
            builder.append(" b) {\n");
            builder.append("\t\tsuper(b);\n");
        } else {
            if (!ctx.doingConcreteType) {
                builder.append("<?, ?>");
            }
            builder.append(" b) {\n");
            appendExtraCtorCode(builder, ctx.hasParent, "b", ctx.typeInfo);
            appendCtorCode(builder, "b", ctx);
        }

        builder.append("\t}\n\n");
    }

    private void appendHashCodeAndEquals(StringBuilder builder,
                                         BodyContext ctx) {
        if (ctx.doingConcreteType) {
            return;
        }

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
        if (ctx.doingConcreteType) {
            return;
        }

        builder.append("\t/**\n"
                               + "\t * Produces the inner portion of the toString() output (i.e., what is between the parens).\n"
                               + "\t *\n"
                               + "\t * @return portion of the toString output\n"
                               + "\t */\n");
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
     * @param builder the builder
     * @param ctx     the context
     */
    protected void appendBasicGetters(StringBuilder builder,
                                      BodyContext ctx) {
        if (ctx.doingConcreteType) {
            return;
        }

        if (Objects.nonNull(ctx.parentAnnotationType.get())) {
            builder.append("\t@Override\n");
            builder.append("\tpublic Class<? extends java.lang.annotation.Annotation> annotationType() {\n");
            builder.append("\t\treturn ").append(ctx.typeInfo.superTypeInfo().get().typeName()).append(".class;\n");
            builder.append("\t}\n\n");
        }

        if (!ctx.hasParent && ctx.hasStreamSupportOnImpl) {
            builder.append("\t@Override\n"
                                   + "\tpublic T get() {\n"
                                   + "\t\treturn (T) this;\n"
                                   + "\t}\n\n");
        }
    }

    /**
     * Appends meta attribute related methods.
     *
     * @param builder the builder
     * @param ctx     the context
     */
    protected void appendMetaAttributes(StringBuilder builder,
                                        BodyContext ctx) {
        if (!ctx.doingConcreteType && ctx.includeMetaAttributes) {
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

            builder.append("\t/**\n"
                                   + "\t * The map of meta attributes describing each element of this type.\n"
                                   + "\t *\n"
                                   + "\t * @return the map of meta attributes using the key being the attribute name\n"
                                   + "\t */\n");
            builder.append("\tpublic static Map<String, Map<String, Object>> __metaAttributes() {\n"
                                   + "\t\treturn META_PROPS;\n"
                                   + "\t}\n\n");
        }
    }

    /**
     * Adds the fields part of the generated builder.
     *
     * @param builder the builder
     * @param ctx     the context
     */
    protected void appendFields(StringBuilder builder,
                                BodyContext ctx) {
        if (ctx.doingConcreteType) {
            return;
        }

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
     * @param builder the builder
     * @param ctx     the context
     */
    protected void appendHeader(StringBuilder builder,
                                BodyContext ctx) {
        builder.append("package ").append(ctx.implTypeName.packageName()).append(";\n\n");
        builder.append("import java.util.Collections;\n");
        builder.append("import java.util.List;\n");
        builder.append("import java.util.Map;\n");
        builder.append("import java.util.Objects;\n\n");
        appendExtraImports(builder, ctx);

        builder.append("/**\n");
        String type = (ctx.doingConcreteType) ? "Concrete" : "Abstract";
        builder.append(" * ").append(type).append(" implementation w/ builder for {@link ");
        builder.append(ctx.typeInfo.typeName()).append("}.\n");
        builder.append(" */\n");
        builder.append(BuilderTemplateHelper.getDefaultGeneratedSticker(getClass().getSimpleName())).append("\n");
        builder.append("@SuppressWarnings(\"unchecked\")\t\n");
        appendAnnotations(builder, ctx.typeInfo.annotations(), "");
        builder.append("public ");
        if (!ctx.doingConcreteType) {
            builder.append("abstract ");
        }
        builder.append("class ").append(ctx.implTypeName.className());

        if (ctx.hasParent || ctx.doingConcreteType) {
            builder.append(" extends ");
        }

        if (ctx.doingConcreteType) {
            builder.append(toAbstractImplTypeName(ctx.typeInfo.typeName(), ctx.builderAnnotation).get());
        } else {
            if (ctx.hasParent) {
                builder.append(toAbstractImplTypeName(ctx.parentTypeName.get(), ctx.builderAnnotation).get());
            }

            if (!ctx.hasParent && ctx.hasStreamSupportOnImpl) {
                builder.append("<").append(ctx.genericBuilderAcceptAliasDecl).append(" extends ")
                        .append(ctx.implTypeName.className()).append(">");
            }

            builder.append(" implements ").append(ctx.typeInfo.typeName());
            if (!ctx.hasParent && ctx.hasStreamSupportOnImpl) {
                builder.append(", Supplier<").append(ctx.genericBuilderAcceptAliasDecl).append(">");
            }
        }

        builder.append(" {\n");
    }

    private void appendDefaultValueAssignment(StringBuilder builder,
                                              TypedElementName method,
                                              String defaultVal) {
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
     * @param builder the builder
     * @param ctx     the context
     */
    protected void appendExtraImports(StringBuilder builder,
                                      BodyContext ctx) {
        if (!ctx.doingConcreteType) {
            builder.append("import java.util.function.Consumer;\n");
            builder.append("import java.util.function.Supplier;\n");
            builder.append("\n");
        }

        if (ctx.requireLibraryDependencies) {
            builder.append("import ").append(AttributeVisitor.class.getName()).append(";\n");
            if (ctx.doingConcreteType) {
                builder.append("import ").append(RequiredAttributeVisitor.class.getName()).append(";\n");
            }
            builder.append("\n");
        }
    }

    /**
     * Generated the toString method on the generated builder.
     *
     * @param builder the builder
     * @param ctx     the context
     */
    protected void appendToStringMethod(StringBuilder builder,
                                        BodyContext ctx) {
        if (ctx.doingConcreteType) {
            return;
        }

        builder.append("\t@Override\n");
        builder.append("\tpublic String toString() {\n");
        builder.append("\t\treturn ").append(ctx.typeInfo.typeName());
        builder.append(".class.getSimpleName() + \"(\" + toStringInner() + \")\";\n");
        builder.append("\t}\n\n");
    }

    /**
     * Adds extra methods to the generated builder. This base implementation will generate the visitAttributes() for the main
     * generated class.
     *
     * @param builder the builder
     * @param ctx     the context
     */
    protected void appendExtraMethods(StringBuilder builder,
                                      BodyContext ctx) {
        if (ctx.includeMetaAttributes) {
            appendVisitAttributes(builder, "", false, ctx);
        }
    }

    /**
     * Adds extra inner classes to write on the builder. This default implementation will write the AttributeVisitor inner class
     * if this is the root interface (i.e., hasParent is false), as well as the RequiredAttributeVisitor.
     *
     * @param builder the builder
     * @param ctx     the context
     */
    protected void appendExtraInnerClasses(StringBuilder builder,
                                           BodyContext ctx) {
        if (ctx.doingConcreteType) {
            return;
        }

        if (!ctx.hasParent
                && ctx.includeMetaAttributes
                && !ctx.requireLibraryDependencies) {
            builder.append("\n\n\t/**\n"
                                   + "\t * A functional interface that can be used to visit all attributes of this type.\n"
                                   + "\t */\n");
            builder.append("\t@FunctionalInterface\n"
                                   + "\tpublic static interface AttributeVisitor {\n"
                                   + "\t\t/**\n"
                                   + "\t\t * Visits the attribute named 'attrName'.\n"
                                   + "\t\t *\n"
                                   + "\t\t * @param attrName\t\tthe attribute name\n"
                                   + "\t\t * @param valueSupplier\tthe attribute value supplier\n"
                                   + "\t\t * @param meta\t\t\tthe meta information for the attribute\n"
                                   + "\t\t * @param userDefinedCtx a user defined context that can be used for holding an "
                                   + "object of your choosing\n"
                                   + "\t\t * @param type\t\t\tthe type of the attribute\n"
                                   + "\t\t * @param typeArgument\tthe type arguments (if type is a parameterized / generic "
                                   + "type)\n"
                                   + "\t\t */\n"
                                   + "\t\tvoid visit(String attrName, Supplier<Object> valueSupplier, "
                                   + "Map<String, Object> meta, Object userDefinedCtx, Class<?> "
                                   + "type, Class<?>... typeArgument);\n"
                                   + "\t}");

            builder.append("\n\n\t/**\n"
                                   + "\t * An implementation of {@link AttributeVisitor} that will validate each attribute to "
                                   + "enforce not-null. The source\n"
                                   + "\t * must be annotated with {@code ConfiguredOption(required=true)} for this to be "
                                   + "enforced.\n"
                                   + "\t */\n");
            builder.append("\tprotected static class RequiredAttributeVisitor implements AttributeVisitor {\n"
                                   + "\t\tprivate List<String> errors;\n"
                                   + "\n"
                                   + "\t\t/**\n"
                                   + "\t\t * Default Constructor.\n"
                                   + "\t\t */\n"
                                   + "\t\tprotected RequiredAttributeVisitor() {\n"
                                   + "\t\t}\n\n");
            builder.append("\t\t@Override\n"
                                   + "\t\tpublic void visit(String attrName,\n"
                                   + "\t\t\t\t\t\t  Supplier<Object> valueSupplier,\n"
                                   + "\t\t\t\t\t\t  Map<String, Object> meta,\n"
                                   + "\t\t\t\t\t\t  Object userDefinedCtx,\n"
                                   + "\t\t\t\t\t\t  Class<?> type,\n"
                                   + "\t\t\t\t\t\t  Class<?>... typeArgument) {\n"
                                   + "\t\t\tboolean required = Boolean.valueOf((String) meta.get(\"required\"));\n"
                                   + "\t\t\tif (!required) {\n"
                                   + "\t\t\t\treturn;\n"
                                   + "\t\t\t}\n"
                                   + "\t\t\t\n"
                                   + "\t\t\tObject val = valueSupplier.get();\n"
                                   + "\t\t\tif (Objects.nonNull(val)) {\n"
                                   + "\t\t\t\treturn;\n"
                                   + "\t\t\t}\n"
                                   + "\t\t\t\n"
                                   + "\t\t\tif (Objects.isNull(errors)) {\n"
                                   + "\t\t\t\t errors = new java.util.LinkedList<>();\n"
                                   + "\t\t\t}\n"
                                   + "\t\t\terrors.add(\"'\" + attrName + \"' is a required attribute and should not be null\")"
                                   + ";\n"
                                   + "\t\t}\n"
                                   + "\n"
                                   + "\t\tvoid validate() {\n"
                                   + "\t\t\tif (Objects.nonNull(errors) && !errors.isEmpty()) {\n"
                                   + "\t\t\t\tthrow new AssertionError(String.join(\", \", errors));\n"
                                   + "\t\t\t}\n"
                                   + "\t\t}\n"
                                   + "\t}\n");
        }
    }

    /**
     * Returns the "final" field modifier by default.
     *
     * @return the field modifier
     */
    protected String getFieldModifier() {
        return "final ";
    }

    /**
     * Appends the visitAttributes() method on the generated class.
     *
     * @param builder     the builder
     * @param extraTabs   spacing
     * @param beanNameRef refer to bean name? otherwise refer to the element name
     * @param ctx         the context
     */
    protected void appendVisitAttributes(StringBuilder builder,
                                         String extraTabs,
                                         boolean beanNameRef,
                                         BodyContext ctx) {
        if (ctx.hasParent) {
            builder.append(extraTabs).append("\t@Override\n");
        } else {
            builder.append(extraTabs).append("\t/**\n");
            builder.append(extraTabs).append("\t * Visits all attributes of " + ctx.typeInfo.typeName() + ", calling the {@link "
                                                     + "AttributeVisitor} for each.\n");
            builder.append(extraTabs).append("\t *\n");
            builder.append(extraTabs).append("\t * @param visitor\t\t\tthe visitor called for each attribute\n");
            builder.append(extraTabs).append("\t * @param userDefinedCtx\tany object you wish to pass to each visit call\n");
            builder.append(extraTabs).append("\t */\n");
        }
        builder.append(extraTabs).append("\tpublic void visitAttributes(AttributeVisitor visitor, Object userDefinedCtx) {\n");
        if (ctx.hasParent) {
            builder.append(extraTabs).append("\t\tsuper.visitAttributes(visitor, userDefinedCtx);\n");
        }

        // void visit(String key, Object value, Object userDefinedCtx, Class<?> type, Class<?>... typeArgument);
        int i = 0;
        for (String attrName : ctx.allAttributeNames) {
            TypedElementName method = ctx.allTypeInfos.get(i);
            String typeName = method.typeName().declaredName();
            List<String> typeArgs = method.typeName().typeArguments().stream()
                    .map(it -> it.declaredName() + ".class")
                    .collect(Collectors.toList());
            String typeArgsStr = String.join(", ", typeArgs);

            builder.append(extraTabs).append("\t\tvisitor.visit(\"").append(attrName).append("\", () -> ");
            if (beanNameRef) {
                builder.append(attrName).append(", ");
            } else {
                builder.append(method.elementName()).append("(), ");
            }
            builder.append("META_PROPS.get(\"").append(attrName).append("\"), userDefinedCtx, ");
            builder.append(typeName).append(".class");
            if (!typeArgsStr.isBlank()) {
                builder.append(", ").append(typeArgsStr);
            }
            builder.append(");\n");

            i++;
        }

        builder.append(extraTabs).append("\t}\n\n");
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
     * @param builder    the builder
     * @param hasParent  true if there is a parent for the generated type
     * @param builderTag the tag (variable name) used for the builder arg
     * @param typeInfo   the type info
     */
    protected void appendExtraCtorCode(StringBuilder builder,
                                       boolean hasParent,
                                       String builderTag,
                                       TypeInfo typeInfo) {
    }

    /**
     * Adds extra code following the ctor decl.
     *
     * @param builder the builder
     * @param ctx     the context
     */
    protected void appendExtraPostCtorCode(StringBuilder builder,
                                           BodyContext ctx) {
    }

    /**
     * Adds extra fields on the main generated class.
     *
     * @param builder the builder
     * @param ctx     the context
     */
    protected void appendExtraFields(StringBuilder builder,
                                     BodyContext ctx) {
        if (!ctx.doingConcreteType && ctx.includeMetaAttributes) {
            builder.append("\t/**\n"
                                   + "\t * meta-props.\n"
                                   + "\t */\n");
            builder.append("\tprotected static final Map<String, Map<String, Object>> META_PROPS = "
                                   + "Collections.unmodifiableMap(__calcMeta());\n");
        }
    }

    /**
     * Adds extra toBuilder() methods.
     *
     * @param builder the builder
     * @param decl    the declaration template for the toBuilder method
     * @param ctx     the context
     */
    protected void appendExtraToBuilderBuilderFunctions(StringBuilder builder,
                                                        String decl,
                                                        BodyContext ctx) {
    }

    /**
     * Adds extra builder methods.
     *
     * @param builder                   the builder
     * @param builderGeneratedClassName the builder class name (as written in source form)
     * @param builderAnnotation         the builder annotation
     * @param typeInfo                  the type info
     * @param parentTypeName            the parent type name
     * @param allAttributeNames         all the bean attribute names belonging to the builder
     * @param allTypeInfos              all the methods belonging to the builder
     */
    protected void appendExtraBuilderFields(StringBuilder builder,
                                            String builderGeneratedClassName,
                                            AnnotationAndValue builderAnnotation,
                                            TypeInfo typeInfo,
                                            TypeName parentTypeName,
                                            List<String> allAttributeNames,
                                            List<TypedElementName> allTypeInfos) {
    }

    private void appendOverridesOfDefaultValues(StringBuilder builder,
                                                BodyContext ctx) {
        boolean first = true;
        for (TypedElementName method : ctx.typeInfo.elementInfo()) {
            String beanAttributeName = toBeanAttributeName(method, ctx.isBeanStyleRequired);
            if (!ctx.allAttributeNames.contains(beanAttributeName)) {
                // candidate for override...
                String thisDefault = toConfiguredOptionValue(method, true, true).orElse(null);
                String superDefault = superValue(ctx.typeInfo.superTypeInfo(), beanAttributeName, ctx.isBeanStyleRequired);
                if (BuilderTypeTools.hasNonBlankValue(thisDefault) && !Objects.equals(thisDefault, superDefault)) {
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

    private String superValue(Optional<TypeInfo> optSuperTypeInfo,
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
            Optional<String> defaultValue = toConfiguredOptionValue(method.get(), true, true);
            if (defaultValue.isPresent() && BuilderTypeTools.hasNonBlankValue(defaultValue.get())) {
                return defaultValue.orElse(null);
            }
        } else {
            return superValue(superTypeInfo.superTypeInfo(), elemName, isBeanStyleRequired);
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
     * @param builder the builder
     * @param ctx     the context
     */
    protected void appendBuilderBuildPreSteps(StringBuilder builder,
                                              BodyContext ctx) {
    }

    /**
     * Adds extra builder methods. This base implementation will write the visitAttributes() method on the
     * generated builder class.
     *
     * @param builder the builder
     * @param ctx     the context
     */
    protected void appendExtraBuilderMethods(StringBuilder builder,
                                             BodyContext ctx) {
        if (ctx.doingConcreteType) {
            return;
        }

        if (ctx.includeMetaAttributes) {
            appendVisitAttributes(builder, "\t", true, ctx);
        }
    }

    /**
     * Adds extra meta properties to the generated code.
     *
     * @param builder           the builder
     * @param tag               the tag used to represent the meta props variable on the generated code
     * @param typeInfo          the type info
     * @param map               the map of all the methods
     * @param allAttributeNames all the bean attribute names belonging to the builder
     * @param allTypeInfos      all the methods belonging to the builder
     * @param needsCustomMapOf  will be set to true if a custom map.of() function needs to be generated (i.e., if over 9 tuples)
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

        String key = (configuredOptions.isEmpty())
                ? null : configuredOptions.get().value("key").orElse(null);
        key = normalizeConfiguredOptionKey(key, attrName, method);
        if (BuilderTypeTools.hasNonBlankValue(key)) {
            typeDecl += ", " + quotedTupleOf("key", key);
        }
        String defaultValue = method.defaultValue().orElse(null);

        if (configuredOptions.isEmpty() && !BuilderTypeTools.hasNonBlankValue(defaultValue)) {
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
                    .filter(e -> BuilderTypeTools.hasNonBlankValue(e.getValue()))
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
     * @param key      the key attribute
     * @param attrName the attribute name
     * @param method   the method
     * @return the key to write on the generated output.
     */
    protected String normalizeConfiguredOptionKey(String key,
                                                  String attrName,
                                                  TypedElementName method) {
        return BuilderTypeTools.hasNonBlankValue(key) ? key : "";
    }

    private String quotedTupleOf(String key,
                                 String val) {
        assert (Objects.nonNull(key));
        assert (BuilderTypeTools.hasNonBlankValue(val)) : key;
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
                AtomicReference<Optional<List<String>>> alternateNames = new AtomicReference<>();
                BeanUtils.validateAndParseMethodName(method.elementName(),
                                                     method.typeName().name(), true, alternateNames);
                assert (Objects.nonNull(alternateNames.get()));
                final String currentAttrName = beanAttributeName;
                Optional<String> alternateName = alternateNames.get().orElse(Collections.emptyList()).stream()
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
     * @param builder           the builder
     * @param method            the method
     * @param beanAttributeName the bean attribute name
     * @param isList            true if the output involves List type
     * @param isMap             true if the output involves Map type
     * @param isSet             true if the output involves Set type
     * @param ctx               the context
     */
    protected void maybeAppendSingularSetter(StringBuilder builder,
                                             TypedElementName method,
                                             String beanAttributeName,
                                             boolean isList, boolean isMap, boolean isSet,
                                             BodyContext ctx) {
        String singularVal = toValue(Singular.class, method, false, false).orElse(null);
        if (Objects.nonNull(singularVal) && (isList || isMap || isSet)) {
            char[] methodName = reverseBeanName(singularVal.isBlank() ? maybeSingularFormOf(beanAttributeName) : singularVal);
            builder.append("\t\t/**\n");
            builder.append("\t\t * Singular setter for '").append(beanAttributeName).append("'.\n");
            builder.append("\t\t *\n");
            if (isMap) {
                builder.append("\t\t * @param key the key\n");
            }
            builder.append("\t\t * @param val the new value\n");
            builder.append("\t\t * @return this fluent builder\n");
            builder.append("\t\t * @see #").append(method.elementName()).append("()\n");
            builder.append("\t\t */\n");
            builder.append("\t\tpublic ").append(ctx.genericBuilderAliasDecl).append(" add")
                    .append(methodName).append("(").append(toGenericsDecl(method)).append(") {\n");
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
     * If the provided name ends in an "s" then this will return the base name with the s stripped off.
     *
     * @param beanAttributeName the name
     * @return the name stripped with any "s" suffix
     */
    protected static String maybeSingularFormOf(String beanAttributeName) {
        if (beanAttributeName.endsWith("s") && beanAttributeName.length() > 1) {
            beanAttributeName = beanAttributeName.substring(0, beanAttributeName.length() - 1);
        }

        return beanAttributeName;
    }

    /**
     * Append the setters for the given bean attribute name.
     *
     * @param mainBuilder       the builder
     * @param beanAttributeName the bean attribute name
     * @param optMethodName     the optional method name
     * @param method            the method
     * @param ctx               the body context
     */
    protected void appendSetter(StringBuilder mainBuilder,
                                String beanAttributeName,
                                Optional<String> optMethodName,
                                TypedElementName method,
                                BodyContext ctx) {
        String methodName = optMethodName.orElse(null);
        if (Objects.isNull(methodName)) {
            methodName = Objects.requireNonNull(beanAttributeName);
        }
        boolean isList = isList(method);
        boolean isMap = !isList && isMap(method);
        boolean isSet = !isMap && isSet(method);
        boolean upLevel = isSet || isList;

        StringBuilder builder = new StringBuilder();
        builder.append("\t\t/**\n");
        builder.append("\t\t * Setter for '").append(beanAttributeName).append("'.\n");
        builder.append("\t\t *\n");
        builder.append("\t\t * @param val the new value\n");
        builder.append("\t\t * @return this fluent builder\n");
        builder.append("\t\t * @see #").append(method.elementName()).append("()\n");
        builder.append("\t\t */\n");
        builder.append("\t\tpublic ").append(ctx.genericBuilderAliasDecl).append(" ").append(methodName).append("(")
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
            builder.append("\t\t * Setter for '").append(beanAttributeName).append("'.\n");
            builder.append("\t\t *\n");
            builder.append("\t\t * @param val the new value\n");
            builder.append("\t\t * @return this fluent builder\n");
            builder.append("\t\t * @see #").append(method.elementName()).append("()\n");
            builder.append("\t\t */\n");
            builder.append("\t\tpublic ").append(ctx.genericBuilderAliasDecl).append(" ").append(methodName)
                    .append("(String val) {\n");
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
     * @param builder           the builder
     * @param beanAttributeName the bean attribute name
     * @param method            the method
     * @param genericType       the generic return type name of the method
     */
    protected void appendDirectNonOptionalSetter(StringBuilder builder,
                                                 String beanAttributeName,
                                                 TypedElementName method,
                                                 TypeName genericType) {
        builder.append("\t\t/**\n");
        builder.append("\t\t * Setter for '").append(beanAttributeName).append("'.\n");
        builder.append("\t\t *\n");
        builder.append("\t\t * @param val the new value\n");
        builder.append("\t\t * @return this fluent builder\n");
        builder.append("\t\t * @see #").append(method.elementName()).append("()\n");
        builder.append("\t\t */\n");
        builder.append("\t\tpublic B ").append(beanAttributeName).append("(")
                .append(genericType.fqName()).append(" val) {\n");
        builder.append("\t\t\treturn ").append(beanAttributeName).append("(").append(Optional.class.getName());
        builder.append(".ofNullable(val));\n");
        builder.append("\t\t}\n\n");
    }

    /**
     * Append {@link io.helidon.pico.builder.Annotated} annotations if any.
     *
     * @param builder     the builder
     * @param annotations the list of annotations
     * @param prefix      the spacing prefix
     */
    protected void appendAnnotations(StringBuilder builder,
                                     List<AnnotationAndValue> annotations,
                                     String prefix) {
        for (AnnotationAndValue methodAnno : annotations) {
            if (methodAnno.typeName().name().equals(Annotated.class.getName())) {
                String val = methodAnno.value().orElse("");
                if (!BuilderTypeTools.hasNonBlankValue(val)) {
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
     * @param method the method
     * @return true if list is part of the type
     */
    protected static boolean isList(TypedElementName method) {
        return isList(method.typeName());
    }

    /**
     * Returns true if the provided method involved {@link java.util.List}.
     *
     * @param typeName the type name
     * @return true if list is part of the type
     */
    protected static boolean isList(TypeName typeName) {
        return (typeName.name().equals(List.class.getName()));
    }

    /**
     * Returns true if the provided method involved {@link java.util.Map}.
     *
     * @param method the method
     * @return true if map is part of the type
     */
    protected static boolean isMap(TypedElementName method) {
        return isMap(method.typeName());
    }

    /**
     * Returns true if the provided method involved {@link java.util.Map}.
     *
     * @param typeName the type name
     * @return true if map is part of the type
     */
    protected static boolean isMap(TypeName typeName) {
        return (typeName.name().equals(Map.class.getName()));
    }

    /**
     * Returns true if the provided method involved {@link java.util.Set}.
     *
     * @param method the method
     * @return true if set is part of the type
     */
    protected static boolean isSet(TypedElementName method) {
        return isSet(method.typeName());
    }

    /**
     * Returns true if the provided method involved {@link java.util.Set}.
     *
     * @param typeName the type name
     * @return true if set is part of the type
     */
    protected static boolean isSet(TypeName typeName) {
        return (typeName.name().equals(Set.class.getName()));
    }

    /**
     * Produces the generic descriptor decl for the method.
     *
     * @param method              the method
     * @param upLevelToCollection true if the generics should be "up leveled"
     * @return the generic decl
     */
    protected static String toGenerics(TypedElementName method,
                                       boolean upLevelToCollection) {
        return toGenerics(method.typeName(), upLevelToCollection);
    }

    /**
     * Produces the generic descriptor decl for the method.
     *
     * @param typeName            the type name
     * @param upLevelToCollection true if the generics should be "up leveled"
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
     * @param coll the collection
     * @return the string representation
     */
    protected static String toString(Collection<?> coll) {
        return toString(coll, Optional.empty(), Optional.empty());
    }

    /**
     * Walk the collection to build a separator-delimited string value.
     *
     * @param coll          the collection
     * @param optFnc        the optional function to apply, defaulting to {@link String#valueOf(java.lang.Object)}
     * @param optSeparator  the optional separator, defaulting to ", "
     * @param <T>           the types held by the collection
     * @return the string representation
     */
    protected static <T> String toString(Collection<T> coll,
                                         Optional<Function<T, String>> optFnc,
                                         Optional<String> optSeparator) {
        Function<T, String> fn = optFnc.isEmpty() ? String::valueOf : optFnc.get();
        String separator = optSeparator.isEmpty() ? ", " : optSeparator.get();
        return coll.stream().map(fn::apply).collect(Collectors.joining(separator));
    }

    /**
     * Extracts the value from the method, ignoring {@link io.helidon.config.metadata.ConfiguredOption#UNCONFIGURED}.
     *
     * @param method                  the method
     * @param wantTypeElementDefaults flag indicating whether the method passed can be used to obtain the default values
     * @param avoidBlanks             flag indicating whether blank values should be ignored
     * @return the default value, or empty if there is no default value applicable for the given arguments
     */
    protected static Optional<String> toConfiguredOptionValue(TypedElementName method,
                                                    boolean wantTypeElementDefaults,
                                                    boolean avoidBlanks) {
        String val = toValue(ConfiguredOption.class, method, wantTypeElementDefaults, avoidBlanks).orElse(null);
        return ConfiguredOption.UNCONFIGURED.equals(val) ? Optional.empty() : Optional.ofNullable(val);
    }

    /**
     * Retrieves the default value of the method to a string value.
     *
     * @param annoType                the annotation that is being applied, that might have the default value
     * @param method                  the method
     * @param wantTypeElementDefaults flag indicating whether the method passed can be used to obtain the default values
     * @param avoidBlanks             flag indicating whether blank values should be ignored
     * @return the default value, or empty if there is no default value applicable for the given arguments
     */
    protected static Optional<String> toValue(Class<? extends Annotation> annoType,
                                    TypedElementName method,
                                    boolean wantTypeElementDefaults,
                                    boolean avoidBlanks) {
        if (wantTypeElementDefaults && method.defaultValue().isPresent()) {
            if (!avoidBlanks || BuilderTypeTools.hasNonBlankValue(method.defaultValue().orElse(null))) {
                return method.defaultValue();
            }
        }

        TypeName searchFor = DefaultTypeName.create(annoType);
        for (AnnotationAndValue anno : method.annotations()) {
            if (anno.typeName().equals(searchFor)) {
                Optional<String> val = anno.value();
                if (!avoidBlanks) {
                    return val;
                }
                return BuilderTypeTools.hasNonBlankValue(val.orElse(null)) ? val : Optional.empty();
            }
        }

        return Optional.empty();
    }

    private static char[] reverseBeanName(String beanName) {
        char[] c = beanName.toCharArray();
        c[0] = Character.toUpperCase(c[0]);
        return c;
    }

    private static String toBeanAttributeName(TypedElementName method,
                                              boolean isBeanStyleRequired) {
        AtomicReference<Optional<List<String>>> refAttrNames = new AtomicReference<>();
        BeanUtils.validateAndParseMethodName(method.elementName(), method.typeName().name(), isBeanStyleRequired, refAttrNames);
        List<String> attrNames = (refAttrNames.get().isEmpty()) ? Collections.emptyList() : refAttrNames.get().get();
        if (!isBeanStyleRequired) {
            return (!attrNames.isEmpty()) ? attrNames.get(0) : method.elementName();
        }
        return Objects.requireNonNull(attrNames.get(0));
    }
}
