/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.builder.processor.tools;

import java.lang.annotation.Annotation;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.helidon.builder.Annotated;
import io.helidon.builder.AttributeVisitor;
import io.helidon.builder.Builder;
import io.helidon.builder.BuilderInterceptor;
import io.helidon.builder.RequiredAttributeVisitor;
import io.helidon.builder.Singular;
import io.helidon.builder.processor.spi.BuilderCreatorProvider;
import io.helidon.builder.processor.spi.TypeAndBodyDefault;
import io.helidon.builder.processor.spi.TypeAndBody;
import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.common.types.AnnotationAndValue;
import io.helidon.common.types.AnnotationAndValueDefault;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNameDefault;
import io.helidon.common.types.TypedElementName;
import io.helidon.config.metadata.ConfiguredOption;

import static io.helidon.builder.processor.tools.BodyContext.TAG_META_PROPS;
import static io.helidon.builder.processor.tools.BodyContext.toBeanAttributeName;
import static io.helidon.builder.processor.tools.BuilderTypeTools.copyrightHeaderFor;
import static io.helidon.builder.processor.tools.BuilderTypeTools.hasNonBlankValue;

/**
 * Default implementation for {@link io.helidon.builder.processor.spi.BuilderCreatorProvider}.
 */
@Weight(Weighted.DEFAULT_WEIGHT - 2)   // allow all other creators to take precedence over us...
public class DefaultBuilderCreatorProvider implements BuilderCreatorProvider {
    static final boolean DEFAULT_INCLUDE_META_ATTRIBUTES = true;
    static final boolean DEFAULT_REQUIRE_LIBRARY_DEPENDENCIES = true;
    static final String DEFAULT_IMPL_PREFIX = Builder.DEFAULT_IMPL_PREFIX;
    static final String DEFAULT_ABSTRACT_IMPL_PREFIX = Builder.DEFAULT_ABSTRACT_IMPL_PREFIX;
    static final String DEFAULT_ABSTRACT_IMPL_SUFFIX = Builder.DEFAULT_ABSTRACT_IMPL_SUFFIX;
    static final String DEFAULT_SUFFIX = Builder.DEFAULT_SUFFIX;
    static final String DEFAULT_LIST_TYPE = Builder.DEFAULT_LIST_TYPE.getName();
    static final String DEFAULT_MAP_TYPE = Builder.DEFAULT_MAP_TYPE.getName();
    static final String DEFAULT_SET_TYPE = Builder.DEFAULT_SET_TYPE.getName();
    static final TypeName BUILDER_ANNO_TYPE_NAME = TypeNameDefault.create(Builder.class);
    static final boolean SUPPORT_STREAMS_ON_IMPL = false;
    static final boolean SUPPORT_STREAMS_ON_BUILDER = true;

    /**
     * Default constructor.
     */
    // note: this needs to remain public since it will be resolved via service loader ...
    @Deprecated
    public DefaultBuilderCreatorProvider() {
    }

    @Override
    public Set<Class<? extends Annotation>> supportedAnnotationTypes() {
        return Set.of(Builder.class);
    }

    @Override
    public List<TypeAndBody> create(TypeInfo typeInfo,
                                    AnnotationAndValue builderAnnotation) {
        try {
            TypeName abstractImplTypeName = toAbstractImplTypeName(typeInfo.typeName(), builderAnnotation);
            TypeName implTypeName = toBuilderImplTypeName(typeInfo.typeName(), builderAnnotation);
            preValidate(implTypeName, typeInfo, builderAnnotation);

            List<TypeAndBody> builds = new ArrayList<>();
            builds.add(TypeAndBodyDefault.builder()
                               .typeName(abstractImplTypeName)
                               .body(toBody(createBodyContext(false, abstractImplTypeName, typeInfo, builderAnnotation)))
                               .build());
            builds.add(TypeAndBodyDefault.builder()
                               .typeName(implTypeName)
                               .body(toBody(createBodyContext(true, implTypeName, typeInfo, builderAnnotation)))
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
        assertNoDuplicateSingularNames(typeInfo);
    }

    private void assertNoDuplicateSingularNames(TypeInfo typeInfo) {
        Set<String> names = new LinkedHashSet<>();
        Set<String> duplicateNames = new LinkedHashSet<>();

        typeInfo.elementInfo().stream()
                .map(DefaultBuilderCreatorProvider::nameOf)
                .forEach(name -> {
                    if (!names.add(name)) {
                        duplicateNames.add(name);
                    }
                });

        if (!duplicateNames.isEmpty()) {
            throw new IllegalStateException("Duplicate methods are using the same names " + duplicateNames + " for: "
                                                    + typeInfo.typeName());
        }
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
     * @return the abstract type name of the implementation
     */
    protected TypeName toAbstractImplTypeName(TypeName typeName,
                                              AnnotationAndValue builderAnnotation) {
        String toPackageName = toPackageName(typeName.packageName(), builderAnnotation);
        String prefix = toAbstractImplTypePrefix(builderAnnotation);
        String suffix = toAbstractImplTypeSuffix(builderAnnotation);
        return TypeNameDefault.create(toPackageName, prefix + typeName.className() + suffix);
    }

    /**
     * Returns the default implementation Builder's class name for what is code generated.
     *
     * @param typeName          the target interface that the builder applies to
     * @param builderAnnotation the builder annotation triggering the build
     * @return the type name of the implementation
     */
    public static TypeName toBuilderImplTypeName(TypeName typeName,
                                                 AnnotationAndValue builderAnnotation) {
        String toPackageName = toPackageName(typeName.packageName(), builderAnnotation);
        String prefix = toImplTypePrefix(builderAnnotation);
        String suffix = toImplTypeSuffix(builderAnnotation);
        return TypeNameDefault.create(toPackageName, prefix + typeName.className() + suffix);
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
        try {
            return new BodyContext(doingConcreteType, typeName, typeInfo, builderAnnotation);
        } catch (Throwable t) {
            throw new IllegalStateException("Failed while processing: " + typeName, t);
        }
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
        appendInterfaceBasedGetters(builder, ctx, "", false);
        appendBasicGetters(builder, ctx);
        appendMetaAttributes(builder, ctx);
        appendToStringMethod(builder, ctx);
        appendInnerToStringMethod(builder, ctx);
        appendHashCodeAndEquals(builder, ctx);
        appendExtraMethods(builder, ctx);
        appendToBuilderMethods(builder, ctx);
        appendBuilder(builder, ctx);
        appendExtraBuilderMethods(builder, ctx);
        appendExtraInnerClasses(builder, ctx);
        appendFooter(builder, ctx);
        return builder.toString();
    }

    /**
     * Appends the footer of the generated class.
     *
     * @param builder the builder
     * @param ctx     the context
     */
    protected void appendFooter(StringBuilder builder,
                                BodyContext ctx) {
        builder.append("}\n");
    }

    /**
     * Appends any interceptor on the builder.
     *
     * @param builder       the builder
     * @param ctx           the context
     * @param builderTag    the tag (variable name) used for the builder arg
     */
    protected void maybeAppendInterceptor(StringBuilder builder,
                                          BodyContext ctx,
                                          String builderTag) {
        assert (!builderTag.equals("interceptor"));
        if (ctx.interceptorTypeName().isPresent()) {
            String impl = ctx.interceptorTypeName().get().name();
            builder.append("\t\t\t").append(impl).append(" interceptor = ");
            if (ctx.interceptorCreateMethod().isEmpty()) {
                builder.append("new ").append(impl).append("();\n");
            } else {
                builder.append(ctx.interceptorTypeName().get())
                        .append(".").append(ctx.interceptorCreateMethod().get()).append("();\n");
            }
            builder.append("\t\t\t").append(builderTag)
                    .append(" = (Builder) interceptor.intercept(").append(builderTag).append(");\n");
        }
    }

    /**
     * Appends the simple {@link io.helidon.config.metadata.ConfiguredOption#required()} validation inside the build() method.
     *
     * @param builder       the builder
     * @param ctx           the context
     * @param builderTag    the tag (variable name) used for the builder arg
     */
    protected void appendRequiredVisitor(StringBuilder builder,
                                         BodyContext ctx,
                                         String builderTag) {
        assert (!builderTag.equals("visitor"));
        if (ctx.includeMetaAttributes()) {
            builder.append("\t\t\tRequiredAttributeVisitor visitor = new RequiredAttributeVisitor(")
                    .append(ctx.allowNulls()).append(");\n"
                                   + "\t\t\t").append(builderTag).append(".visitAttributes(visitor, null);\n"
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
            appendMetaProps(builder, ctx, "metaProps", needsCustomMapOf);
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
            builder.append(fieldModifier());
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
        builder.append(generatedCopyrightHeaderFor(ctx)).append("\n");
        builder.append("package ").append(ctx.implTypeName().packageName()).append(";\n\n");
        builder.append("import java.util.Collections;\n");
        builder.append("import java.util.List;\n");
        builder.append("import java.util.Map;\n");
        builder.append("import java.util.Set;\n");
        builder.append("import java.util.Objects;\n\n");
        if (ctx.includeGeneratedAnnotation()) {
            builder.append("import jakarta.annotation.Generated;\n\n");
        }
        appendExtraImports(builder, ctx);

        builder.append("/**\n");
        String type = (ctx.doingConcreteType()) ? "Concrete" : "Abstract";
        builder.append(" * ").append(type).append(" implementation w/ builder for {@link ");
        builder.append(ctx.typeInfo().typeName()).append("}.\n");
        builder.append(" */\n");
        if (!ctx.includeGeneratedAnnotation()) {
            builder.append("// ");
        }
        builder.append("@Generated(")
                .append(generatedStickerFor(ctx))
                .append(")\n");
        builder.append("@SuppressWarnings(\"unchecked\")\t\n");
        appendAnnotations(builder, ctx.typeInfo().annotations(), "");
        builder.append(ctx.publicOrPackagePrivateDecl());
        if (!ctx.doingConcreteType()) {
            builder.append("abstract ");
        }
        builder.append("class ").append(ctx.implTypeName().className());

        Optional<TypeName> baseExtendsTypeName = baseExtendsTypeName(ctx);
        if (baseExtendsTypeName.isEmpty() && ctx.isExtendingAnAbstractClass()) {
            baseExtendsTypeName = Optional.of(ctx.typeInfo().typeName());
        }
        if (ctx.hasParent() || ctx.doingConcreteType() || baseExtendsTypeName.isPresent()) {
            builder.append(" extends ");
        }

        if (ctx.doingConcreteType()) {
            builder.append(toAbstractImplTypeName(ctx.typeInfo().typeName(), ctx.builderTriggerAnnotation()));
        } else {
            if (ctx.hasParent()) {
                builder.append(toAbstractImplTypeName(ctx.parentTypeName().orElseThrow(), ctx.builderTriggerAnnotation()));
            } else if (baseExtendsTypeName.isPresent()) {
                builder.append(baseExtendsTypeName.get().fqName());
            }

            List<String> impls = new ArrayList<>();
            if (!ctx.isExtendingAnAbstractClass()) {
                impls.add(ctx.typeInfo().typeName().fqName());
            }
            if (!ctx.hasParent() && ctx.hasStreamSupportOnImpl()) {
                impls.add("Supplier<" + ctx.genericBuilderAcceptAliasDecl() + ">");
            }
            List<TypeName> extraImplementContracts = extraImplementedTypeNames(ctx);
            extraImplementContracts.forEach(t -> impls.add(t.fqName()));

            if (!impls.isEmpty()) {
                builder.append(" implements ").append(String.join(", ", impls));
            }
        }

        builder.append(" {\n");
    }

    /**
     * Returns the copyright level header comment.
     *
     * @param ctx   the context
     * @return the copyright level header
     */
    protected String generatedCopyrightHeaderFor(BodyContext ctx) {
        return copyrightHeaderFor(getClass().getName());
    }

    /**
     * Returns the {@code Generated} sticker to be added.
     *
     * @param ctx   the context
     * @return the generated sticker
     */
    protected String generatedStickerFor(BodyContext ctx) {
        return BuilderTypeTools.generatedStickerFor(getClass().getName(), generatedVersionFor(ctx));
    }

    /**
     * Returns the {@code Generated} version identifier.
     *
     * @param ctx   the context
     * @return the generated version identifier
     */
    protected String generatedVersionFor(BodyContext ctx) {
        return Versions.CURRENT_BUILDER_VERSION;
    }

    /**
     * Returns any extra 'extends' type name that should be on the main generated type at the base level.
     *
     * @param ctx   the context
     * @return extra contracts implemented
     */
    protected Optional<TypeName> baseExtendsTypeName(BodyContext ctx) {
        return Optional.empty();
    }

    /**
     * Returns any extra 'extends' type name that should be on the main generated builder type at the base level.
     *
     * @param ctx   the context
     * @return extra contracts implemented
     */
    protected Optional<TypeName> baseExtendsBuilderTypeName(BodyContext ctx) {
        return Optional.empty();
    }

    /**
     * Returns any extra 'implements' contract types that should be on the main generated type.
     *
     * @param ctx   the context
     * @return extra contracts implemented
     */
    protected List<TypeName> extraImplementedTypeNames(BodyContext ctx) {
        return List.of();
    }

    /**
     * Returns any extra 'implements' contract types that should be on the main generated builder type.
     *
     * @param ctx   the context
     * @return extra contracts implemented
     */
    protected List<TypeName> extraImplementedBuilderContracts(BodyContext ctx) {
        return List.of();
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
            builder.append("import ").append(BuilderInterceptor.class.getName()).append(";\n");
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

        if (!ctx.hasOtherMethod("toString", ctx.typeInfo())) {
            builder.append("\t@Override\n");
            builder.append("\tpublic String toString() {\n");
            builder.append("\t\treturn ").append(ctx.typeInfo().typeName());
            builder.append(".class.getSimpleName() + ");

            String instanceIdRef = instanceIdRef(ctx);
            if (!instanceIdRef.isBlank()) {
                builder.append("\"{\" + ").append(instanceIdRef).append(" + \"}\" + ");
            }
            builder.append("\"(\" + toStringInner() + \")\";\n");
            builder.append("\t}\n\n");
        }
    }

    /**
     * The nuanced instance id for the {@link #appendToStringMethod(StringBuilder, BodyContext)}.
     *
     * @param ctx the context
     * @return the instance id
     */
    protected String instanceIdRef(BodyContext ctx) {
        return "";
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
            appendVisitAttributes(builder, ctx, "", false);
        }
    }

    /**
     * Adds extra inner classes to write on the builder. This default implementation will write the {@code AttributeVisitor} and
     * {@code RequiredAttributeVisitor} inner classes on the base abstract parent (ie, hasParent is false).
     *
     * @param builder the builder
     * @param ctx     the context
     */
    protected void appendExtraInnerClasses(StringBuilder builder,
                                           BodyContext ctx) {
        GenerateVisitorSupport.appendExtraInnerClasses(builder, ctx);
    }

    /**
     * Returns the "final" field modifier by default.
     *
     * @return the field modifier
     */
    protected String fieldModifier() {
        return "final ";
    }

    /**
     * Appends the visitAttributes() method on the generated class.
     *
     * @param builder     the builder
     * @param ctx         the context
     * @param extraTabs   spacing
     * @param beanNameRef refer to bean name? otherwise refer to the element name
     */
    protected void appendVisitAttributes(StringBuilder builder,
                                         BodyContext ctx,
                                         String extraTabs,
                                         boolean beanNameRef) {
        if (ctx.doingConcreteType()) {
            return;
        }

        if (overridesVisitAttributes(ctx)) {
            builder.append(extraTabs).append("\t@Override\n");
        } else {
            GenerateJavadoc.visitAttributes(builder, ctx, extraTabs);
        }
        builder.append(extraTabs).append("\tpublic <T> void visitAttributes(AttributeVisitor<T> visitor, T userDefinedCtx) {\n");
        if (ctx.hasParent()) {
            builder.append(extraTabs).append("\t\tsuper.visitAttributes(visitor, userDefinedCtx);\n");
        }

        // void visit(String key, Object value, Object userDefinedCtx, Class<?> type, Class<?>... typeArgument);
        int i = 0;
        for (String attrName : ctx.allAttributeNames()) {
            TypedElementName method = ctx.allTypeInfos().get(i);
            TypeName typeName = method.typeName();
            List<String> typeArgs = method.typeName().typeArguments().stream()
                    .map(this::normalize)
                    .toList();
            String typeArgsStr = String.join(", ", typeArgs);

            builder.append(extraTabs).append("\t\tvisitor.visit(\"").append(attrName).append("\", () -> this.");
            if (beanNameRef) {
                builder.append(attrName).append(", ");
            } else {
                builder.append(method.elementName()).append("(), ");
            }
            builder.append(TAG_META_PROPS).append(".get(\"").append(attrName).append("\"), userDefinedCtx, ");
            builder.append(normalize(typeName));
            if (!typeArgsStr.isBlank()) {
                builder.append(", ").append(typeArgsStr);
            }
            builder.append(");\n");

            i++;
        }

        builder.append(extraTabs).append("\t}\n\n");
    }

    /**
     * Return true if the visitAttributes() methods is being overridden.
     *
     * @param ctx   the context
     * @return true if overriding visitAttributes();
     */
    protected boolean overridesVisitAttributes(BodyContext ctx) {
        return ctx.hasParent();
    }

    /**
     * Adds extra default ctor code.
     *
     * @param builder    the builder
     * @param ctx        the context
     * @param builderTag the tag (variable name) used for the builder arg
     */
    protected void appendExtraCtorCode(StringBuilder builder,
                                       BodyContext ctx,
                                       String builderTag) {
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
            builder.append("\tprivate static final Map<String, Map<String, Object>> ")
                    .append(TAG_META_PROPS).append(" = Collections.unmodifiableMap(__calcMeta());\n");
        }
    }

    /**
     * Adds extra toBuilder() methods.
     *
     * @param builder the builder
     * @param ctx     the context
     * @param decl    the declaration template for the toBuilder method
     */
    protected void appendExtraToBuilderBuilderFunctions(StringBuilder builder,
                                                        BodyContext ctx,
                                                        String decl) {
    }

    /**
     * Adds extra builder methods.
     *
     * @param builder the builder
     * @param ctx     the context
     */
    protected void appendExtraBuilderFields(StringBuilder builder,
                                            BodyContext ctx) {
    }

    /**
     * Adds extra builder build() method pre-steps prior to the builder being built into the target.
     *
     * @param builder the builder
     * @param ctx     the context
     * @param builderTag        the tag (variable name) used for the builder arg
     */
    protected void appendBuilderBuildPreSteps(StringBuilder builder,
                                              BodyContext ctx,
                                              String builderTag) {
        maybeAppendInterceptor(builder, ctx, builderTag);
        appendRequiredVisitor(builder, ctx, builderTag);
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
        if (!ctx.doingConcreteType() && ctx.includeMetaAttributes()) {
            appendVisitAttributes(builder, ctx, "\t", true);
        }

        builder.append("\t}\n\n");
    }

    /**
     * Adds extra meta properties to the generated code.
     *
     * @param builder           the builder
     * @param ctx               the context
     * @param tag               the tag used to represent the meta props variable on the generated code
     * @param needsCustomMapOf  will be set to true if a custom map.of() function needs to be generated (i.e., if over 9 tuples)
     */
    protected void appendMetaProps(StringBuilder builder,
                                   BodyContext ctx,
                                   String tag,
                                   AtomicBoolean needsCustomMapOf) {
        builder.append("\t\t").append(tag);
        builder.append(".put(\"__generated\", Map.of(\"version\", \"")
                .append(io.helidon.builder.processor.tools.Versions.BUILDER_VERSION_1).append("\"));\n");
        ctx.map().forEach((attrName, method) ->
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
     * @param key           the key attribute
     * @param name          the name
     * @param isAttribute   if the name represents an attribute value (otherwise is a config bean name)
     * @return the key to write on the generated output
     */
    protected String normalizeConfiguredOptionKey(String key,
                                                  String name,
                                                  boolean isAttribute) {
        return hasNonBlankValue(key) ? key : toConfigKey(name, isAttribute);
    }

    /**
     * Applicable if this builder is intended for config beans.
     *
     * @param name          the name
     * @param isAttribute   if the name represents an attribute value (otherwise is a config bean name)
     * @return the config key
     */
    protected String toConfigKey(String name,
                                 boolean isAttribute) {
        return "";
    }

    /**
     * Appends the singular setter methods on the builder.
     *
     * @param builder           the builder
     * @param ctx               the context
     * @param method            the method
     * @param beanAttributeName the bean attribute name
     * @param isList            true if the output involves List type
     * @param isMap             true if the output involves Map type
     * @param isSet             true if the output involves Set type
     */
    protected void maybeAppendSingularSetter(StringBuilder builder,
                                             BodyContext ctx,
                                             TypedElementName method,
                                             String beanAttributeName,
                                             boolean isList, boolean isMap, boolean isSet) {
        String singularVal = toValue(Singular.class, method, false, false).orElse(null);
        if ((singularVal != null) && (isList || isMap || isSet)) {
            char[] methodName = reverseBeanName(beanAttributeName);
            appendSetter(builder, ctx, beanAttributeName, new String(methodName), method, false, GenerateMethod.SINGULAR_PREFIX);

            methodName = reverseBeanName(singularVal.isBlank() ? maybeSingularFormOf(beanAttributeName) : singularVal);
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
     * Attempts to use the singular name of the element, defaulting to the element name if no singular annotation exists.
     *
     * @param elem the element
     * @return the (singular) name of the element
     */
    protected static String nameOf(TypedElementName elem) {
        return AnnotationAndValueDefault.findFirst(Singular.class, elem.annotations())
                .flatMap(AnnotationAndValue::value)
                .filter(BuilderTypeTools::hasNonBlankValue)
                .orElseGet(elem::elementName);
    }

    /**
     * Append the setters for the given bean attribute name.
     *
     * @param mainBuilder       the builder
     * @param ctx               the body context
     * @param beanAttributeName the bean attribute name
     * @param methodName        the method name
     * @param method            the method
     */
    protected void appendSetter(StringBuilder mainBuilder,
                                BodyContext ctx,
                                String beanAttributeName,
                                String methodName,
                                TypedElementName method) {
        appendSetter(mainBuilder, ctx, beanAttributeName, methodName, method, true, "");
    }

    private void appendSetter(StringBuilder mainBuilder,
                              BodyContext ctx,
                              String beanAttributeName,
                              String methodName,
                              TypedElementName method,
                              boolean doClear,
                              String prefixName) {
        TypeName typeName = method.typeName();
        boolean isList = typeName.isList();
        boolean isMap = !isList && typeName.isMap();
        boolean isSet = !isMap && typeName.isSet();
        String publicOrPkdPrivate = (!typeName.isOptional() || ctx.allowPublicOptionals()) ? "public " : "";
        boolean upLevel = isSet || isList;

        StringBuilder builder = new StringBuilder();
        GenerateJavadoc.setter(builder, beanAttributeName);
        builder.append("\t\t").append(publicOrPkdPrivate).append(ctx.genericBuilderAliasDecl()).append(" ")
                .append(prefixName)
                .append(methodName).append("(")
                .append(toGenerics(method, upLevel)).append(" val) {\n");

        /*
         Assign field, or update collection
         */
        if (doClear) {
            builder.append("\t\t\tthis.")
                    .append(beanAttributeName);
        }

        if (isList) {
            if (doClear) {
                builder.append(".clear();\n");
            }
            builder.append("\t\t\tthis.")
                    .append(beanAttributeName)
                    .append(".addAll(").append(maybeRequireNonNull(ctx, "val")).append(");\n");
        } else if (isMap) {
            if (doClear) {
                builder.append(".clear();\n");
            }
            builder.append("\t\t\tthis.")
                    .append(beanAttributeName)
                    .append(".putAll(").append(maybeRequireNonNull(ctx, "val")).append(");\n");
        } else if (isSet) {
            if (doClear) {
                builder.append(".clear();\n");
            }
            builder.append("\t\t\tthis.")
                    .append(beanAttributeName)
                    .append(".addAll(").append(maybeRequireNonNull(ctx, "val")).append(");\n");
        } else if (typeName.array()) {
            if (ctx.allowNulls()) {
                builder.append(" = (val == null) ? null : val.clone();\n");
            } else {
                builder.append(" = val.clone();\n");
            }
        } else {
            builder.append(" = ").append(maybeRequireNonNull(ctx, "val")).append(";\n");
        }
        builder.append("\t\t\treturn identity();\n");
        builder.append("\t\t}\n\n");

        if (typeName.fqName().equals("char[]")) {
            GenerateMethod.stringToCharSetter(builder, ctx, beanAttributeName, method, methodName);
        }

        mainBuilder.append(builder);

        if (prefixName.isBlank() && typeName.isOptional() && !typeName.typeArguments().isEmpty()) {
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
     * Append {@link Annotated} annotations if any.
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
                if (!hasNonBlankValue(val)) {
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
            if (!avoidBlanks || hasNonBlankValue(method.defaultValue().orElse(null))) {
                return method.defaultValue();
            }
        }

        TypeName searchFor = TypeNameDefault.create(annoType);
        for (AnnotationAndValue anno : method.annotations()) {
            if (anno.typeName().equals(searchFor)) {
                Optional<String> val = anno.value();
                if (!avoidBlanks) {
                    return val;
                }
                return hasNonBlankValue(val.orElse(null)) ? val : Optional.empty();
            }
        }

        return Optional.empty();
    }

    private static String toGenerics(TypeName typeName,
                                     boolean upLevelToCollection,
                                     int depth) {
        if (typeName.typeArguments().isEmpty()) {
            return (typeName.array()
                            || Optional.class.getName().equals(typeName.name())
                            || (typeName.wildcard() && depth > 0))
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

    private static String avoidWildcard(TypeName typeName) {
        return typeName.wildcard() ? typeName.name() : typeName.fqName();
    }

    private static char[] reverseBeanName(String beanName) {
        char[] c = beanName.toCharArray();
        c[0] = Character.toUpperCase(c[0]);
        return c;
    }

    /**
     * In support of {@link io.helidon.builder.Builder#packageName()}.
     */
    private static String toPackageName(String packageName,
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
     * In support of {@link io.helidon.builder.Builder#abstractImplPrefix()}.
     */
    private String toAbstractImplTypePrefix(AnnotationAndValue builderAnnotation) {
        return builderAnnotation.value("abstractImplPrefix").orElse(DEFAULT_ABSTRACT_IMPL_PREFIX);
    }

    /**
     * In support of {@link io.helidon.builder.Builder#abstractImplSuffix()}.
     */
    private String toAbstractImplTypeSuffix(AnnotationAndValue builderAnnotation) {
        return builderAnnotation.value("abstractImplSuffix").orElse(DEFAULT_ABSTRACT_IMPL_SUFFIX);
    }

    /**
     * In support of {@link io.helidon.builder.Builder#implPrefix()}.
     */
    private static String toImplTypePrefix(AnnotationAndValue builderAnnotation) {
        return builderAnnotation.value("implPrefix").orElse(DEFAULT_IMPL_PREFIX);
    }

    /**
     * In support of {@link io.helidon.builder.Builder#implSuffix()}.
     */
    private static String toImplTypeSuffix(AnnotationAndValue builderAnnotation) {
        return builderAnnotation.value("implSuffix").orElse(DEFAULT_SUFFIX);
    }

    private void appendBuilder(StringBuilder builder,
                               BodyContext ctx) {
        appendBuilderHeader(builder, ctx);
        appendExtraBuilderFields(builder, ctx);
        appendBuilderBody(builder, ctx);

        if (ctx.hasAnyBuilderClashingMethodNames()) {
            builder.append("\t\t// *** IMPORTANT NOTE: There are getter methods that clash with the base Builder methods ***\n");
            appendInterfaceBasedGetters(builder, ctx, "\t//", true);
        } else {
            appendInterfaceBasedGetters(builder, ctx, "\t", true);
        }

        if (ctx.doingConcreteType()) {
            if (ctx.hasParent()) {
                builder.append("\t\t@Override\n");
            } else {
                GenerateJavadoc.buildMethod(builder);
            }
            builder.append("\t\tpublic ").append(ctx.implTypeName()).append(" build() {\n");
            builder.append("\t\t\tBuilder b = this;\n");
            appendBuilderBuildPreSteps(builder, ctx, "b");
            builder.append("\t\t\treturn new ").append(ctx.implTypeName().className()).append("(b);\n");
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
                appendSetter(builder, ctx, beanAttributeName, beanAttributeName, method);
                if (!isList && !isMap && !isSet) {
                    boolean isBoolean = BeanUtils.isBooleanType(typeName.name());
                    if (isBoolean && beanAttributeName.startsWith("is")) {
                        // possibly overload setter to strip the "is"...
                        String basicAttributeName = ""
                                + Character.toLowerCase(beanAttributeName.charAt(2))
                                + beanAttributeName.substring(3);
                        if (!BeanUtils.isReservedWord(basicAttributeName)
                                && !ctx.allAttributeNames().contains(basicAttributeName)) {
                            appendSetter(builder, ctx, beanAttributeName, basicAttributeName, method);
                        }
                    }
                }

                maybeAppendSingularSetter(builder, ctx, method, beanAttributeName, isList, isMap, isSet);
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

            if (ctx.hasParent()) {
                builder.append("\t\t@Override\n");
            } else {
                GenerateJavadoc.accept(builder);
            }
            builder.append("\t\tpublic ")
                    .append(ctx.genericBuilderAliasDecl())
                    .append(" accept(").append(ctx.genericBuilderAcceptAliasDecl()).append(" val) {\n");
            if (!ctx.allowNulls()) {
                builder.append("\t\t\tObjects.requireNonNull(val);\n");
            }
            if (ctx.hasParent()) {
                builder.append("\t\t\tsuper.accept(val);\n");
            }
            builder.append("\t\t\t__acceptThis(val);\n");
            builder.append("\t\t\treturn identity();\n");
            builder.append("\t\t}\n\n");

            builder.append("\t\tprivate void __acceptThis(").append(ctx.genericBuilderAcceptAliasDecl()).append(" val) {\n");
            if (!ctx.allowNulls()) {
                builder.append("\t\t\tObjects.requireNonNull(val);\n");
            }
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
                boolean isPrimitive = method.typeName().primitive();
                if (!isPrimitive && ctx.allowNulls()) {
                    builder.append("((val == null) ? null : ");
                }
                builder.append("val.").append(getterName).append("()");
                if (!isPrimitive && ctx.allowNulls()) {
                    builder.append(")");
                }
                builder.append(");\n");
            }
            builder.append("\t\t}\n\n");
        }
    }

    private void appendBuilderBody(StringBuilder builder,
                                   BodyContext ctx) {
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
                addBuilderField(builder, ctx, method, typeName, beanAttributeName);
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

    private void addBuilderField(StringBuilder builder,
                                 BodyContext ctx,
                                 TypedElementName method,
                                 TypeName type,
                                 String beanAttributeName) {
        GenerateJavadoc.builderField(builder, method);
        builder.append("\t\tprivate ");
        builder.append(type.array() ? type.fqName() : type.name()).append(" ")
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
        builder.append("\t").append(ctx.publicOrPackagePrivateDecl());
        if (!ctx.doingConcreteType()) {
            builder.append("abstract ");
        }
        builder.append("static class ").append(ctx.genericBuilderClassDecl());

        if (ctx.doingConcreteType()) {
            builder.append(" extends ");
            builder.append(toAbstractImplTypeName(ctx.typeInfo().typeName(), ctx.builderTriggerAnnotation()));
            builder.append(".").append(ctx.genericBuilderClassDecl());
            builder.append("<").append(ctx.genericBuilderClassDecl()).append(", ").append(ctx.ctorBuilderAcceptTypeName())
                    .append("> {\n");
        } else {
            builder.append("<").append(ctx.genericBuilderAliasDecl()).append(" extends ").append(ctx.genericBuilderClassDecl());
            builder.append("<").append(ctx.genericBuilderAliasDecl()).append(", ");
            builder.append(ctx.genericBuilderAcceptAliasDecl()).append(">, ").append(ctx.genericBuilderAcceptAliasDecl())
                    .append(" extends ");
            builder.append(ctx.ctorBuilderAcceptTypeName()).append(">");
            if (ctx.hasParent()) {
                builder.append(" extends ")
                        .append(toAbstractImplTypeName(ctx.parentTypeName().orElseThrow(), ctx.builderTriggerAnnotation()))
                        .append(".").append(ctx.genericBuilderClassDecl());
                builder.append("<").append(ctx.genericBuilderAliasDecl())
                        .append(", ").append(ctx.genericBuilderAcceptAliasDecl());
                builder.append(">");
            } else {
                Optional<TypeName> baseExtendsTypeName = baseExtendsBuilderTypeName(ctx);
                if (baseExtendsTypeName.isEmpty() && ctx.isExtendingAnAbstractClass()) {
                    baseExtendsTypeName = Optional.of(ctx.typeInfo().typeName());
                }
                if (baseExtendsTypeName.isPresent()) {
                    builder.append("\n\t\t\t\t\t\t\t\t\t\textends ")
                            .append(baseExtendsTypeName.get().fqName());
                }
            }

            List<String> impls = new ArrayList<>();
            if (!ctx.isExtendingAnAbstractClass() && !ctx.hasAnyBuilderClashingMethodNames()) {
                impls.add(ctx.typeInfo().typeName().name());
            }
            if (!ctx.hasParent()) {
                if (ctx.hasStreamSupportOnBuilder() && !ctx.requireLibraryDependencies()) {
                    impls.add("Supplier<" + ctx.genericBuilderAcceptAliasDecl() + ">");
                }

                if (ctx.requireLibraryDependencies()) {
                    impls.add(io.helidon.common.Builder.class.getName()
                                      + "<" + ctx.genericBuilderAliasDecl() + ", " + ctx.genericBuilderAcceptAliasDecl() + ">");
                }

                List<TypeName> extraImplementBuilderContracts = extraImplementedBuilderContracts(ctx);
                extraImplementBuilderContracts.forEach(t -> impls.add(t.fqName()));
            }

            if (!impls.isEmpty()) {
                builder.append(" implements ").append(String.join(", ", impls));
            }

            builder.append(" {\n");
        }
    }

    private void appendToBuilderMethods(StringBuilder builder,
                                        BodyContext ctx) {
        if (!ctx.doingConcreteType()) {
            return;
        }

        String decl = GenerateMethod.builderMethods(builder, ctx);
        appendExtraToBuilderBuilderFunctions(builder, ctx, decl);
    }

    private void appendInterfaceBasedGetters(StringBuilder builder,
                                             BodyContext ctx,
                                             String prefix,
                                             boolean isBuilder) {
        if (ctx.doingConcreteType()) {
            return;
        }

        int i = 0;
        for (String beanAttributeName : ctx.allAttributeNames()) {
            TypedElementName method = ctx.allTypeInfos().get(i);
            String extraPrefix = prefix + "\t";
            appendAnnotations(builder, method.annotations(), extraPrefix);
            builder.append(extraPrefix)
                    .append("@Override\n");
            builder.append(extraPrefix)
                    .append("public ").append(toGenerics(method, false)).append(" ").append(method.elementName())
                    .append("() {\n");
            builder.append(extraPrefix)
                    .append("\treturn ").append(beanAttributeName).append(";\n");
            builder.append(extraPrefix)
                    .append("}\n\n");
            i++;
        }

        if (ctx.parentAnnotationTypeName().isPresent()) {
            builder.append(prefix)
                    .append("\t@Override\n");
            builder.append(prefix)
                    .append("\tpublic Class<? extends java.lang.annotation.Annotation> annotationType() {\n");
            builder.append(prefix)
                    .append("\t\treturn ").append(ctx.typeInfo().superTypeInfo().get().typeName()).append(".class;\n");
            builder.append(prefix)
                    .append("\t}\n\n");
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
            appendExtraCtorCode(builder, ctx, "b");
            appendCtorCodeBody(builder, ctx, "b");
        }

        builder.append("\t}\n\n");
    }

    /**
     * Appends the constructor body.
     *
     * @param builder           the builder
     * @param ctx               the context
     * @param builderTag        the tag (variable name) used for the builder arg
     */
    protected void appendCtorCodeBody(StringBuilder builder,
                                      BodyContext ctx,
                                      String builderTag) {
        if (ctx.hasParent()) {
            builder.append("\t\tsuper(").append(builderTag).append(");\n");
        }
        int i = 0;
        for (String beanAttributeName : ctx.allAttributeNames()) {
            TypedElementName method = ctx.allTypeInfos().get(i++);
            builder.append("\t\tthis.").append(beanAttributeName).append(" = ");

            if (method.typeName().isList()) {
                builder.append("Collections.unmodifiableList(new ")
                        .append(ctx.listType()).append("<>(").append(builderTag).append(".")
                        .append(beanAttributeName).append("));\n");
            } else if (method.typeName().isMap()) {
                builder.append("Collections.unmodifiableMap(new ")
                        .append(ctx.mapType()).append("<>(").append(builderTag).append(".")
                        .append(beanAttributeName).append("));\n");
            } else if (method.typeName().isSet()) {
                builder.append("Collections.unmodifiableSet(new ")
                        .append(ctx.setType()).append("<>(").append(builderTag).append(".")
                        .append(beanAttributeName).append("));\n");
            } else {
                builder.append(builderTag).append(".").append(beanAttributeName).append(";\n");
            }
        }
    }

    private void appendHashCodeAndEquals(StringBuilder builder,
                                         BodyContext ctx) {
        if (ctx.doingConcreteType()) {
            return;
        }

        if (!ctx.hasOtherMethod("hashCode", ctx.typeInfo())) {
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
        }

        if (!ctx.hasOtherMethod("equals", ctx.typeInfo())) {
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
                String equalsClass = method.typeName().array() ? Arrays.class.getName() : "Objects";
                builder.append("\t\tequals &= ").append(equalsClass).append(".equals(")
                        .append(method.elementName()).append("(), other.")
                        .append(method.elementName()).append("());\n");
            }
            builder.append("\t\treturn equals;\n");
            builder.append("\t}\n\n");
        }
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

        if (Duration.class.getName().equals(type.name())) {
            builder.append("java.time.Duration.parse(\"").append(defaultVal).append("\")");
            return;
        }

        boolean isString = type.name().equals(String.class.getName()) && !type.array();
        boolean isCharArr = type.fqName().equals("char[]");
        if ((isString || isCharArr) && !defaultVal.startsWith("\"")) {
            builder.append("\"");
        } else if (!type.primitive() && !type.name().startsWith("java.")) {
            builder.append(type.name()).append(".");
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

    private void appendOverridesOfDefaultValues(StringBuilder builder,
                                                BodyContext ctx) {
        for (TypedElementName method : ctx.typeInfo().elementInfo()) {
            String beanAttributeName = toBeanAttributeName(method, ctx.beanStyleRequired());
            if (!ctx.allAttributeNames().contains(beanAttributeName)) {
                // candidate for override...
                String thisDefault = toConfiguredOptionValue(method, true, true).orElse(null);
                String superDefault = superValue(ctx.typeInfo().superTypeInfo(), beanAttributeName, ctx.beanStyleRequired());
                if (hasNonBlankValue(thisDefault) && !Objects.equals(thisDefault, superDefault)) {
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
            if (defaultValue.isPresent() && hasNonBlankValue(defaultValue.get())) {
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
        Optional<? extends AnnotationAndValue> configuredOptions = AnnotationAndValueDefault
                .findFirst(ConfiguredOption.class, method.annotations());

        TypeName typeName = method.typeName();
        String typeDecl = "\"__type\", " + typeName.name() + ".class";
        if (!typeName.typeArguments().isEmpty()) {
            int pos = typeName.typeArguments().size() - 1;
            TypeName arg = typeName.typeArguments().get(pos);
            typeDecl += ", \"__componentType\", " + normalize(arg);
        }

        String key = (configuredOptions.isEmpty())
                ? null : configuredOptions.get().value("key").orElse(null);
        key = normalizeConfiguredOptionKey(key, attrName, true);
        if (hasNonBlankValue(key)) {
            typeDecl += ", " + quotedTupleOf(method.typeName(), "key", key);
        }
        String defaultValue = method.defaultValue().orElse(null);

        if (configuredOptions.isEmpty() && !hasNonBlankValue(defaultValue)) {
            return "Map.of(" + typeDecl + ")";
        }

        needsCustomMapOf.set(true);
        StringBuilder result = new StringBuilder();
        result.append("__mapOf(").append(typeDecl);

        if (configuredOptions.isEmpty()) {
            result.append(", ");
            if (defaultValue.startsWith("{")) {
                defaultValue = "new String[] " + defaultValue;
                result.append(quotedValueOf("value"));
                result.append(", ");
                result.append(defaultValue);
            } else {
                result.append(quotedTupleOf(typeName, "value", defaultValue));
            }
        } else {
            configuredOptions.get().values().entrySet().stream()
                    .filter(e -> hasNonBlankValue(e.getValue()))
                    .filter(e -> !e.getKey().equals("key"))
                    .forEach(e -> {
                        result.append(", ");
                        result.append(quotedTupleOf(typeName, e.getKey(), e.getValue()));
                    });
        }
        result.append(")");

        return result.toString();
    }

    private String normalize(TypeName typeName) {
        return (typeName.generic() ? "Object" : typeName.name()) + ".class";
    }

    private String quotedTupleOf(TypeName valType,
                                 String key,
                                 String val) {
        assert (key != null);
        assert (hasNonBlankValue(val)) : key;
        boolean isEnumLikeType = isEnumLikeType(valType, key, val);
        if (isEnumLikeType) {
            val = valType + "." + val;
        } else if (!key.equals("value") || !val.startsWith(ConfiguredOption.class.getName())) {
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

    private boolean isEnumLikeType(TypeName valType,
                                   String key,
                                   String val) {
        if (!hasNonBlankValue(val) || valType.primitive()) {
            return false;
        }

        int dotPos = key.indexOf(".");
        if (dotPos < 0) {
            return false;
        }

        if (valType.isOptional() && !valType.typeArguments().isEmpty()) {
            return isEnumLikeType(valType.typeArguments().get(0), key, val);
        }

        return !BeanUtils.isBuiltInJavaType(valType);
    }

    private String maybeRequireNonNull(BodyContext ctx,
                                       String tag) {
        return ctx.allowNulls() ? tag : "Objects.requireNonNull(" + tag + ")";
    }

}
