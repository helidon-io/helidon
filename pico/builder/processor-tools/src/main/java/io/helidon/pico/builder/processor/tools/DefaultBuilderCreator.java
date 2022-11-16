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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
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
import io.helidon.pico.builder.spi.RequiredAttributeVisitor;
import io.helidon.pico.types.AnnotationAndValue;
import io.helidon.pico.types.DefaultAnnotationAndValue;
import io.helidon.pico.types.DefaultTypeName;
import io.helidon.pico.types.TypeName;
import io.helidon.pico.types.TypedElementName;

import static io.helidon.pico.builder.processor.tools.BodyContext.toBeanAttributeName;

/**
 * Default implementation for {@link io.helidon.pico.builder.processor.spi.BuilderCreator}.
 */
@Weight(Weighted.DEFAULT_WEIGHT - 1)   // allow all other creators to take precedence over us...
public class DefaultBuilderCreator implements BuilderCreator {
    static final boolean DEFAULT_INCLUDE_META_ATTRIBUTES = true;
    static final boolean DEFAULT_REQUIRE_LIBRARY_DEPENDENCIES = true;
    static final String DEFAULT_IMPL_PREFIX = Builder.DEFAULT_IMPL_PREFIX;
    static final String DEFAULT_ABSTRACT_IMPL_PREFIX = Builder.DEFAULT_ABSTRACT_IMPL_PREFIX;
    static final String DEFAULT_SUFFIX = Builder.DEFAULT_SUFFIX;
    static final String DEFAULT_LIST_TYPE = Builder.DEFAULT_LIST_TYPE.getName();
    static final String DEFAULT_MAP_TYPE = Builder.DEFAULT_MAP_TYPE.getName();
    static final String DEFAULT_SET_TYPE = Builder.DEFAULT_SET_TYPE.getName();
    static final TypeName BUILDER_ANNO_TYPE_NAME = DefaultTypeName.create(Builder.class);
    static final boolean SUPPORT_STREAMS_ON_IMPL = false;
    static final boolean SUPPORT_STREAMS_ON_BUILDER = true;

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

    /**
     * Appends the simple {@link io.helidon.config.metadata.ConfiguredOption#required()} validation inside the build() method.
     *
     * @param builder the builder
     * @param ctx     the context
     */
    protected void appendRequiredValidator(StringBuilder builder,
                                           BodyContext ctx) {
        if (ctx.includeMetaAttributes()) {
            builder.append("\t\t\tRequiredAttributeVisitor visitor = new RequiredAttributeVisitor();\n"
                                   + "\t\t\tvisitAttributes(visitor, null);\n"
                                   + "\t\t\tvisitor.validate();\n");
        }
    }

    /**
     * Adds the basic getters to the generated builder output.
     *
     * @param builder the builder
     * @param ctx     the context
     */
    protected void appendBasicGetters(StringBuilder builder,
                                      BodyContext ctx) {
        if (ctx.doingConcreteType()) {
            return;
        }

        if (Objects.nonNull(ctx.parentAnnotationType().get())) {
            builder.append("\t@Override\n");
            builder.append("\tpublic Class<? extends java.lang.annotation.Annotation> annotationType() {\n");
            builder.append("\t\treturn ").append(ctx.typeInfo().superTypeInfo().get().typeName()).append(".class;\n");
            builder.append("\t}\n\n");
        }

        if (!ctx.hasParent() && ctx.hasStreamSupportOnImpl()) {
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
        if (!ctx.doingConcreteType() && ctx.includeMetaAttributes()) {
            builder.append("\tprivate static Map<String, Map<String, Object>> __calcMeta() {\n");
            builder.append("\t\tMap<String, Map<String, Object>> metaProps = new java.util.LinkedHashMap<>();\n");

            AtomicBoolean needsCustomMapOf = new AtomicBoolean();
            appendMetaProps(builder, "metaProps",
                            ctx.typeInfo(), ctx.map(), ctx.allAttributeNames(), ctx.allTypeInfos(), needsCustomMapOf);
            builder.append("\t\treturn metaProps;\n");
            builder.append("\t}\n\n");

            if (needsCustomMapOf.get()) {
                appendCustomMapOf(builder);
            }

            GenerateMethod.internalMetaAttributes(builder);
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
        if (ctx.doingConcreteType()) {
            return;
        }

        for (int i = 0; i < ctx.allTypeInfos().size(); i++) {
            TypedElementName method = ctx.allTypeInfos().get(i);
            String beanAttributeName = ctx.allAttributeNames().get(i);
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
        builder.append("package ").append(ctx.implTypeName().packageName()).append(";\n\n");
        builder.append("import java.util.Collections;\n");
        builder.append("import java.util.List;\n");
        builder.append("import java.util.Map;\n");
        builder.append("import java.util.Set;\n");
        builder.append("import java.util.Objects;\n\n");
        appendExtraImports(builder, ctx);

        builder.append("/**\n");
        String type = (ctx.doingConcreteType()) ? "Concrete" : "Abstract";
        builder.append(" * ").append(type).append(" implementation w/ builder for {@link ");
        builder.append(ctx.typeInfo().typeName()).append("}.\n");
        builder.append(" */\n");
        builder.append(BuilderTemplateHelper.getDefaultGeneratedSticker(getClass().getSimpleName())).append("\n");
        builder.append("@SuppressWarnings(\"unchecked\")\t\n");
        appendAnnotations(builder, ctx.typeInfo().annotations(), "");
        builder.append("public ");
        if (!ctx.doingConcreteType()) {
            builder.append("abstract ");
        }
        builder.append("class ").append(ctx.implTypeName().className());

        if (ctx.hasParent() || ctx.doingConcreteType()) {
            builder.append(" extends ");
        }

        if (ctx.doingConcreteType()) {
            builder.append(toAbstractImplTypeName(ctx.typeInfo().typeName(), ctx.builderAnnotation()).get());
        } else {
            if (ctx.hasParent()) {
                builder.append(toAbstractImplTypeName(ctx.parentTypeName().get(), ctx.builderAnnotation()).get());
            }

            if (!ctx.hasParent() && ctx.hasStreamSupportOnImpl()) {
                builder.append("<").append(ctx.genericBuilderAcceptAliasDecl()).append(" extends ")
                        .append(ctx.implTypeName().className()).append(">");
            }

            builder.append(" implements ").append(ctx.typeInfo().typeName());
            if (!ctx.hasParent() && ctx.hasStreamSupportOnImpl()) {
                builder.append(", Supplier<").append(ctx.genericBuilderAcceptAliasDecl()).append(">");
            }
        }

        builder.append(" {\n");
    }

    /**
     * Adds extra imports to the generated builder.
     *
     * @param builder the builder
     * @param ctx     the context
     */
    protected void appendExtraImports(StringBuilder builder,
                                      BodyContext ctx) {
        if (!ctx.doingConcreteType()) {
            builder.append("import java.util.function.Consumer;\n");
            builder.append("import java.util.function.Supplier;\n");
            builder.append("\n");
        }

        if (ctx.requireLibraryDependencies()) {
            builder.append("import ").append(AttributeVisitor.class.getName()).append(";\n");
            if (ctx.doingConcreteType()) {
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
        if (ctx.doingConcreteType()) {
            return;
        }

        builder.append("\t@Override\n");
        builder.append("\tpublic String toString() {\n");
        builder.append("\t\treturn ").append(ctx.typeInfo().typeName());
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
        if (ctx.includeMetaAttributes()) {
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
        GenerateVisitor.appendAttributeVisitors(builder, ctx);
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
        if (ctx.hasParent()) {
            builder.append(extraTabs).append("\t@Override\n");
        } else {
            GenerateJavadoc.visitAttributes(builder, ctx, extraTabs);
        }
        builder.append(extraTabs).append("\tpublic void visitAttributes(AttributeVisitor visitor, Object userDefinedCtx) {\n");
        if (ctx.hasParent()) {
            builder.append(extraTabs).append("\t\tsuper.visitAttributes(visitor, userDefinedCtx);\n");
        }

        // void visit(String key, Object value, Object userDefinedCtx, Class<?> type, Class<?>... typeArgument);
        int i = 0;
        for (String attrName : ctx.allAttributeNames()) {
            TypedElementName method = ctx.allTypeInfos().get(i);
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
        if (!ctx.doingConcreteType() && ctx.includeMetaAttributes()) {
            GenerateJavadoc.internalMetaPropsField(builder);
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
        if (ctx.doingConcreteType()) {
            return;
        }

        if (ctx.includeMetaAttributes()) {
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
            GenerateMethod.singularSetter(builder, ctx, method, beanAttributeName, methodName);
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
     * @param methodName        the method name
     * @param method            the method
     * @param ctx               the body context
     */
    protected void appendSetter(StringBuilder mainBuilder,
                                String beanAttributeName,
                                String methodName,
                                TypedElementName method,
                                BodyContext ctx) {

        TypeName typeName = method.typeName();
        boolean isList = typeName.isList();
        boolean isMap = !isList && typeName.isMap();
        boolean isSet = !isMap && typeName.isSet();
        boolean upLevel = isSet || isList;

        StringBuilder builder = new StringBuilder();
        GenerateJavadoc.setter(builder, beanAttributeName, method);
        builder.append("\t\tpublic ").append(ctx.genericBuilderAliasDecl()).append(" ").append(methodName).append("(")
                .append(toGenerics(method, upLevel)).append(" val) {\n");

        /*
         Make sure that arguments are not null
         */
        builder.append("\t\t\tObjects.requireNonNull(val);\n");

        /*
         Assign field, or update collection
         */
        builder.append("\t\t\tthis.")
                .append(beanAttributeName);

        if (isList) {
            builder.append(".clear();\n");
            builder.append("\t\t\tthis.")
                    .append(beanAttributeName)
                    .append(".addAll(val);\n");
        } else if (isMap) {
            builder.append(".clear();\n");
            builder.append("\t\t\tthis.")
                    .append(beanAttributeName)
                    .append(".putAll(val);\n");
        } else if (isSet) {
            builder.append(".clear();\n");
            builder.append("\t\t\tthis.")
                    .append(beanAttributeName)
                    .append(".addAll(val);\n");
        } else if (typeName.array()) {
            builder.append(" = val.clone();\n");
        } else {
            builder.append(" = val;\n");
        }
        builder.append("\t\t\treturn identity();\n");
        builder.append("\t\t}\n\n");

        if (typeName.fqName().equals("char[]")) {
            GenerateMethod.stringToCharSetter(builder, ctx, beanAttributeName, method, methodName);
        }

        mainBuilder.append(builder);

        if (typeName.isOptional() && !typeName.typeArguments().isEmpty()) {
            TypeName genericType = typeName.typeArguments().get(0);
            appendDirectNonOptionalSetter(mainBuilder, ctx, beanAttributeName, method, methodName, genericType);
        }
    }

    /**
     * Append the setters for the given bean attribute name.
     *
     * @param builder           the builder
     * @param ctx               the body context
     * @param beanAttributeName the bean attribute name
     * @param method            the method
     * @param methodName        the method name
     * @param genericType       the generic return type name of the method
     */
    protected void appendDirectNonOptionalSetter(StringBuilder builder,
                                                 BodyContext ctx,
                                                 String beanAttributeName,
                                                 TypedElementName method,
                                                 String methodName,
                                                 TypeName genericType) {
        GenerateMethod.nonOptionalSetter(builder, ctx, beanAttributeName, method, methodName, genericType);
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
     * @param coll         the collection
     * @param optFnc       the optional function to apply, defaulting to {@link String#valueOf(java.lang.Object)}
     * @param optSeparator the optional separator, defaulting to ", "
     * @param <T>          the types held by the collection
     * @return the string representation
     */
    protected static <T> String toString(Collection<T> coll,
                                         Optional<Function<T, String>> optFnc,
                                         Optional<String> optSeparator) {
        Function<T, String> fn = optFnc.orElse(String::valueOf);
        String separator = optSeparator.orElse(", ");
        return coll.stream().map(fn).collect(Collectors.joining(separator));
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
            if (typeName.isList() || typeName.isSet()) {
                return Collection.class.getName() + "<" + toString(upLevelInner) + ">";
            } else if (typeName.isMap()) {
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

    private static char[] reverseBeanName(String beanName) {
        char[] c = beanName.toCharArray();
        c[0] = Character.toUpperCase(c[0]);
        return c;
    }

    /**
     * In support of {@link io.helidon.pico.builder.Builder#packageName()}.
     */
    private String toPackageName(String packageName,
                                 AnnotationAndValue builderAnnotation) {
        String packageNameFromAnno = builderAnnotation.value("packageName").orElse(null);
        if (packageNameFromAnno == null || packageNameFromAnno.isBlank()) {
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

    private void appendBuilder(StringBuilder builder,
                               BodyContext ctx) {
        appendBuilderHeader(builder, ctx);
        appendExtraBuilderFields(builder, ctx.genericBuilderClassDecl(), ctx.builderAnnotation(),
                                 ctx.typeInfo(), ctx.parentTypeName().get(), ctx.allAttributeNames(), ctx.allTypeInfos());
        appendBuilderBody(builder, ctx);

        appendExtraBuilderMethods(builder, ctx);

        if (ctx.doingConcreteType()) {
            if (ctx.hasParent()) {
                builder.append("\t\t@Override\n");
            } else {
                GenerateJavadoc.buildMethod(builder);
            }
            builder.append("\t\tpublic ").append(ctx.implTypeName()).append(" build() {\n");
            appendRequiredValidator(builder, ctx);
            appendBuilderBuildPreSteps(builder, ctx);
            builder.append("\t\t\treturn new ").append(ctx.implTypeName().className()).append("(this);\n");
            builder.append("\t\t}\n");
        } else {
            int i = 0;
            for (String beanAttributeName : ctx.allAttributeNames()) {
                TypedElementName method = ctx.allTypeInfos().get(i);
                TypeName typeName = method.typeName();
                boolean isList = typeName.isList();
                boolean isMap = !isList && typeName.isMap();
                boolean isSet = !isMap && typeName.isSet();
                boolean ignoredUpLevel = isSet || isList;
                appendSetter(builder, beanAttributeName, beanAttributeName, method, ctx);
                if (!isList && !isMap && !isSet) {
                    boolean isBoolean = BeanUtils.isBooleanType(typeName.name());
                    if (isBoolean && beanAttributeName.startsWith("is")) {
                        // possibly overload setter to strip the "is"...
                        String basicAttributeName = ""
                                + Character.toLowerCase(beanAttributeName.charAt(2))
                                + beanAttributeName.substring(3);
                        if (!ctx.allAttributeNames().contains(basicAttributeName)) {
                            appendSetter(builder, beanAttributeName, basicAttributeName, method, ctx);
                        }
                    }
                }

                maybeAppendSingularSetter(builder, method, beanAttributeName, isList, isMap, isSet, ctx);
                i++;
            }

            if (!ctx.hasParent() && !ctx.requireLibraryDependencies()) {
                GenerateJavadoc.buildMethod(builder);
                builder.append("\t\tpublic abstract ")
                        .append(ctx.genericBuilderAcceptAliasDecl())
                        .append(" build();\n\n");

                if (ctx.hasStreamSupportOnBuilder()) {
                    GenerateJavadoc.updateConsumer(builder);
                    builder.append("\t\tpublic B update(Consumer<")
                            .append(ctx.genericBuilderAcceptAliasDecl())
                            .append("> consumer) {\n"
                                            + "\t\t\tconsumer.accept(get());\n"
                                            + "\t\t\treturn identity();\n"
                                            + "\t\t}\n\n");
                }

                if (!ctx.requireLibraryDependencies()) {
                    GenerateJavadoc.identity(builder);
                    builder.append("\t\t@SuppressWarnings(\"unchecked\")\n");
                    builder.append("\t\tprotected ").append(ctx.genericBuilderAliasDecl()).append(" identity() {\n"
                                                                                                        + "\t\t\treturn (")
                            .append(ctx.genericBuilderAliasDecl()).append(") this;\n"
                                                                                + "\t\t}\n\n"
                                                                                + "\t\t@Override\n"
                                                                                + "\t\tpublic ")
                            .append(ctx.genericBuilderAcceptAliasDecl()).append(" get() {\n"
                                                                                      + "\t\t\treturn (")
                            .append(ctx.genericBuilderAcceptAliasDecl()).append(") build();\n"
                                                                                      + "\t\t}\n\n");
                }
            }

            GenerateJavadoc.accept(builder);
            builder.append("\t\tpublic ")
                    .append(ctx.genericBuilderAliasDecl())
                    .append(" accept(").append(ctx.genericBuilderAcceptAliasDecl()).append(" val) {\n");
            builder.append("\t\t\tObjects.requireNonNull(val);\n");
            if (ctx.hasParent()) {
                builder.append("\t\t\tsuper.accept(val);\n");
            }
            builder.append("\t\t\tacceptThis(val);\n");
            builder.append("\t\t\treturn identity();\n");
            builder.append("\t\t}\n\n");

            builder.append("\t\tprivate void acceptThis(").append(ctx.genericBuilderAcceptAliasDecl()).append(" val) {\n");

            i = 0;
            for (String beanAttributeName : ctx.allAttributeNames()) {
                TypedElementName method = ctx.allTypeInfos().get(i++);
                TypeName typeName = method.typeName();
                String getterName = method.elementName();
                builder.append("\t\t\t").append(beanAttributeName).append("(");
                boolean isList = typeName.isList();
                boolean isMap = !isList && typeName.isMap();
                boolean isSet = !isMap && typeName.isSet();
                if (isList || isSet) {
                    builder.append("(java.util.Collection) ");
                } else if (isMap) {
                    builder.append("(java.util.Map) ");
                }
                builder.append("val.").append(getterName).append("());\n");
            }
            builder.append("\t\t}\n");
        }

        // end of the generated builder inner class here
        builder.append("\t}\n");
    }

    private void appendBuilderBody(StringBuilder builder, BodyContext ctx) {
        if (!ctx.doingConcreteType()) {
            // prepare builder fields, starting with final (list, map, set)
            boolean hasFinal = false;
            for (int i = 0; i < ctx.allAttributeNames().size(); i++) {
                String beanAttributeName = ctx.allAttributeNames().get(i);
                TypedElementName method = ctx.allTypeInfos().get(i);
                TypeName typeName = method.typeName();
                if (typeName.isList() || typeName.isMap() || typeName.isSet()) {
                    hasFinal = true;
                    addCollectionField(builder, ctx, method, typeName, beanAttributeName);
                }
            }
            if (hasFinal) {
                // eol to separate final from mutable fields
                builder.append("\n");
            }
            // then any other field
            for (int i = 0; i < ctx.allAttributeNames().size(); i++) {
                String beanAttributeName = ctx.allAttributeNames().get(i);
                TypedElementName method = ctx.allTypeInfos().get(i);
                TypeName typeName = method.typeName();
                if (typeName.isList() || typeName.isMap() || typeName.isSet()) {
                    continue;
                }
                addField(builder, method, typeName, beanAttributeName);
            }
            builder.append("\n");
        }

        GenerateJavadoc.builderConstructor(builder);
        if (ctx.doingConcreteType()) {
            builder.append("\t\tprotected ").append(ctx.genericBuilderClassDecl()).append("() {\n");
            builder.append("\t\t\tsuper();\n");
        } else {
            builder.append("\t\tprotected ").append(ctx.genericBuilderClassDecl()).append("() {\n");
            if (ctx.hasParent()) {
                builder.append("\t\t\tsuper();\n");
            }
            appendOverridesOfDefaultValues(builder, ctx);
        }
        builder.append("\t\t}\n\n");
    }

    private void addField(StringBuilder builder,
                          TypedElementName method,
                          TypeName type,
                          String beanAttributeName) {

        GenerateJavadoc.builderField(builder, method);
        builder.append("\t\tprotected ").append(type.array() ? type.fqName() : type.name()).append(" ")
                .append(beanAttributeName);
        Optional<String> defaultVal = toConfiguredOptionValue(method, true, true);
        if (defaultVal.isPresent()) {
            builder.append(" = ");
            appendDefaultValueAssignment(builder, method, defaultVal.get());
        } else {
            if (type.isOptional()) {
                builder.append(" = java.util.Optional.empty()");
            }
        }
        builder.append(";\n");
    }

    private void addCollectionField(StringBuilder builder,
                                    BodyContext ctx,
                                    TypedElementName method,
                                    TypeName typeName,
                                    String beanAttributeName) {
        GenerateJavadoc.builderField(builder, method);

        builder.append("\t\tprotected final ")
                .append(typeName.name())
                .append(" ")
                .append(beanAttributeName)
                .append(" = new ")
                .append(collectionType(ctx, typeName))
                .append("<>();\n");
    }

    private String collectionType(BodyContext ctx, TypeName type) {
        if (type.isList()) {
            return ctx.listType();
        }
        if (type.isMap()) {
            return ctx.mapType();
        }
        if (type.isSet()) {
            return ctx.setType();
        }
        throw new IllegalStateException("Type is not a known collection: " + type);
    }

    private void appendBuilderHeader(StringBuilder builder,
                                     BodyContext ctx) {
        GenerateJavadoc.builderClass(builder, ctx);
        builder.append("\tpublic ");
        if (!ctx.doingConcreteType()) {
            builder.append("abstract ");
        }
        builder.append("static class ").append(ctx.genericBuilderClassDecl());

        if (ctx.doingConcreteType()) {
            builder.append(" extends ");
            builder.append(toAbstractImplTypeName(ctx.typeInfo().typeName(), ctx.builderAnnotation()).get());
            builder.append(".").append(ctx.genericBuilderClassDecl());
            builder.append("<").append(ctx.genericBuilderClassDecl()).append(", ").append(ctx.ctorBuilderAcceptTypeName())
                    .append("> {\n");
        } else {
            builder.append("<").append(ctx.genericBuilderAliasDecl()).append(" extends ").append(ctx.genericBuilderClassDecl());
            builder.append("<").append(ctx.genericBuilderAliasDecl()).append(", ");
            builder.append(ctx.genericBuilderAcceptAliasDecl()).append(">, ").append(ctx.genericBuilderAcceptAliasDecl())
                    .append(" extends ");
            builder.append(ctx.ctorBuilderAcceptTypeName()).append("> ");
            if (ctx.hasParent()) {
                builder.append("extends ")
                        .append(toAbstractImplTypeName(ctx.parentTypeName().get(), ctx.builderAnnotation()).get())
                        .append(".").append(ctx.genericBuilderClassDecl());
                builder.append("<").append(ctx.genericBuilderAliasDecl())
                        .append(", ").append(ctx.genericBuilderAcceptAliasDecl());
                builder.append(">");
            } else if (ctx.hasStreamSupportOnBuilder()) {
                builder.append("implements Supplier<").append(ctx.genericBuilderAcceptAliasDecl()).append(">");
            }
            if (!ctx.hasParent()) {
                if (ctx.requireLibraryDependencies()) {
                    builder.append(", io.helidon.common.Builder<").append(ctx.genericBuilderAliasDecl())
                            .append(", ").append(ctx.genericBuilderAcceptAliasDecl()).append(">");
                } else {
                    builder.append("/*, io.helidon.common.Builder<").append(ctx.genericBuilderAliasDecl())
                            .append(", ").append(ctx.genericBuilderAcceptAliasDecl()).append("> */");
                }
            }

            builder.append(" {\n");
        }
    }

    private void appendToBuilderMethods(StringBuilder builder,
                                        BodyContext ctx) {
        if (!ctx.doingConcreteType()) {
            return;
        }

        GenerateMethod.builderMethods(builder, ctx);

        String decl = "public static Builder toBuilder({args}) {";
        appendExtraToBuilderBuilderFunctions(builder, decl, ctx);
    }

    private void appendInterfaceBasedGetters(StringBuilder builder,
                                             BodyContext ctx) {
        if (ctx.doingConcreteType()) {
            return;
        }

        int i = 0;
        for (String beanAttributeName : ctx.allAttributeNames()) {
            TypedElementName method = ctx.allTypeInfos().get(i);
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

        GenerateJavadoc.typeConstructorWithBuilder(builder);
        builder.append("\tprotected ").append(ctx.implTypeName().className());
        builder.append("(");
        builder.append(ctx.genericBuilderClassDecl());
        if (ctx.doingConcreteType()) {
            builder.append(" b) {\n");
            builder.append("\t\tsuper(b);\n");
        } else {
            if (!ctx.doingConcreteType()) {
                builder.append("<?, ?>");
            }
            builder.append(" b) {\n");
            appendExtraCtorCode(builder, ctx.hasParent(), "b", ctx.typeInfo());
            appendCtorCode(builder, "b", ctx);
        }

        builder.append("\t}\n\n");
    }

    private void appendHashCodeAndEquals(StringBuilder builder,
                                         BodyContext ctx) {
        if (ctx.doingConcreteType()) {
            return;
        }

        builder.append("\t@Override\n");
        builder.append("\tpublic int hashCode() {\n");
        if (ctx.hasParent()) {
            builder.append("\t\tint hashCode = super.hashCode();\n");
        } else {
            builder.append("\t\tint hashCode = 1;\n");
        }
        List<String> methods = new ArrayList<>();
        for (TypedElementName method : ctx.allTypeInfos()) {
            methods.add(method.elementName() + "()");
        }
        builder.append("\t\thashCode = 31 * hashCode + Objects.hash(").append(String.join(", ", methods)).append(");\n");
        builder.append("\t\treturn hashCode;\n");
        builder.append("\t}\n\n");

        builder.append("\t@Override\n");
        builder.append("\tpublic boolean equals(Object another) {\n");
        builder.append("\t\tif (this == another) {\n\t\t\treturn true;\n\t\t}\n");
        builder.append("\t\tif (!(another instanceof ").append(ctx.typeInfo().typeName()).append(")) {\n");
        builder.append("\t\t\treturn false;\n");
        builder.append("\t\t}\n");
        builder.append("\t\t").append(ctx.typeInfo().typeName()).append(" other = (")
                .append(ctx.typeInfo().typeName()).append(") another;\n");
        if (ctx.hasParent()) {
            builder.append("\t\tboolean equals = super.equals(other);\n");
        } else {
            builder.append("\t\tboolean equals = true;\n");
        }
        for (TypedElementName method : ctx.allTypeInfos()) {
            builder.append("\t\tequals &= Objects.equals(").append(method.elementName()).append("(), other.")
                    .append(method.elementName()).append("());\n");
        }
        builder.append("\t\treturn equals;\n");
        builder.append("\t}\n\n");
    }

    private void appendInnerToStringMethod(StringBuilder builder,
                                           BodyContext ctx) {
        if (ctx.doingConcreteType()) {
            return;
        }

        GenerateJavadoc.innerToString(builder);
        if (ctx.hasParent()) {
            builder.append("\t@Override\n");
        }
        builder.append("\tprotected String toStringInner() {\n");
        if (ctx.hasParent()) {
            builder.append("\t\tString result = super.toStringInner();\n");
            if (!ctx.allAttributeNames().isEmpty()) {
                builder.append("\t\tif (!result.isEmpty() && !result.endsWith(\", \")) {\n");
                builder.append("\t\t\tresult += \", \";\n");
                builder.append("\t\t}\n");
            }
        } else {
            builder.append("\t\tString result = \"\";\n");
        }

        int i = 0;
        for (String beanAttributeName : ctx.allAttributeNames()) {
            TypedElementName method = ctx.allTypeInfos().get(i++);
            TypeName typeName = method.typeName();

            builder.append("\t\tresult += \"").append(beanAttributeName).append("=\" + ");

            boolean handled = false;

            if (typeName.isOptional()) {
                if (!typeName.typeArguments().isEmpty()) {
                    TypeName innerType = typeName.typeArguments().get(0);
                    if (innerType.array() && innerType.primitive()) {
                        // primitive types only if present or not
                        builder.append("(")
                                .append(method.elementName())
                                .append("().isEmpty() ? \"Optional.empty\" : \"not-empty\")");
                        handled = true;
                    }
                }
            }

            if (!handled) {
                if (typeName.array()) {
                    builder.append("(").append(beanAttributeName).append(" == null ? \"null\" : ");
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
            }
            if (i < ctx.allAttributeNames().size()) {
                builder.append(" + \", \"");
            }
            builder.append(";\n");
        }
        builder.append("\t\treturn result;\n");
        builder.append("\t}\n\n");
    }

    private void appendDefaultValueAssignment(StringBuilder builder,
                                              TypedElementName method,
                                              String defaultVal) {
        TypeName type = method.typeName();
        boolean isOptional = type.isOptional();
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

    private void appendCtorCode(StringBuilder builder,
                                String ignoredBuilderTag,
                                BodyContext ctx) {
        if (ctx.hasParent()) {
            builder.append("\t\tsuper(b);\n");
        }
        int i = 0;
        for (String beanAttributeName : ctx.allAttributeNames()) {
            TypedElementName method = ctx.allTypeInfos().get(i++);
            builder.append("\t\tthis.").append(beanAttributeName).append(" = ");

            if (method.typeName().isList()) {
                builder.append("Collections.unmodifiableList(new ")
                        .append(ctx.listType()).append("<>(b.").append(beanAttributeName).append("));\n");
            } else if (method.typeName().isMap()) {
                builder.append("Collections.unmodifiableMap(new ")
                        .append(ctx.mapType()).append("<>(b.").append(beanAttributeName).append("));\n");
            } else if (method.typeName().isSet()) {
                builder.append("Collections.unmodifiableSet(new ")
                        .append(ctx.setType()).append("<>(b.").append(beanAttributeName).append("));\n");
            } else {
                builder.append("b.").append(beanAttributeName).append(";\n");
            }
        }
    }

    private void appendOverridesOfDefaultValues(StringBuilder builder,
                                                BodyContext ctx) {
        boolean first = true;
        for (TypedElementName method : ctx.typeInfo().elementInfo()) {
            String beanAttributeName = toBeanAttributeName(method, ctx.isBeanStyleRequired());
            if (!ctx.allAttributeNames().contains(beanAttributeName)) {
                // candidate for override...
                String thisDefault = toConfiguredOptionValue(method, true, true).orElse(null);
                String superDefault = superValue(ctx.typeInfo().superTypeInfo(), beanAttributeName, ctx.isBeanStyleRequired());
                if (BuilderTypeTools.hasNonBlankValue(thisDefault) && !Objects.equals(thisDefault, superDefault)) {
                    appendDefaultOverride(builder, beanAttributeName, method, thisDefault);
                }
            }
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
        builder.append("\t\t\t").append(attrName).append("(");
        appendDefaultValueAssignment(builder, method, override);
        builder.append(");\n");
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

}
