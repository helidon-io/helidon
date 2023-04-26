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

package io.helidon.builder.config.processor;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.helidon.builder.config.ConfigBean;
import io.helidon.builder.config.spi.ConfigBeanBuilderValidator;
import io.helidon.builder.config.spi.ConfigBeanInfo;
import io.helidon.builder.config.spi.ConfigResolver;
import io.helidon.builder.config.spi.ConfigResolverRequestDefault;
import io.helidon.builder.config.spi.GeneratedConfigBean;
import io.helidon.builder.config.spi.GeneratedConfigBeanBase;
import io.helidon.builder.config.spi.GeneratedConfigBeanBuilder;
import io.helidon.builder.config.spi.GeneratedConfigBeanBuilderBase;
import io.helidon.builder.config.spi.MetaConfigBeanInfo;
import io.helidon.builder.config.spi.ResolutionContext;
import io.helidon.builder.processor.tools.BodyContext;
import io.helidon.builder.processor.tools.DefaultBuilderCreatorProvider;
import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.common.config.Config;
import io.helidon.common.types.AnnotationAndValue;
import io.helidon.common.types.DefaultAnnotationAndValue;
import io.helidon.common.types.DefaultTypeName;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementName;
import io.helidon.config.metadata.ConfiguredOption;

import static io.helidon.builder.config.spi.ConfigBeanInfo.LevelType;
import static io.helidon.builder.config.spi.ConfigBeanInfo.TAG_AT_LEAST_ONE;
import static io.helidon.builder.config.spi.ConfigBeanInfo.TAG_DRIVES_ACTIVATION;
import static io.helidon.builder.config.spi.ConfigBeanInfo.TAG_KEY;
import static io.helidon.builder.config.spi.ConfigBeanInfo.TAG_LEVEL_TYPE;
import static io.helidon.builder.config.spi.ConfigBeanInfo.TAG_REPEATABLE;
import static io.helidon.builder.config.spi.ConfigBeanInfo.TAG_WANT_DEFAULT_CONFIG_BEAN;

/**
 * A specialization of {@link io.helidon.builder.processor.tools.DefaultBuilderCreatorProvider} that supports the additional
 * add-ons to the builder generated classes that binds to the config sub-system.
 *
 * @see GeneratedConfigBean
 * @see GeneratedConfigBeanBuilder
 */
@Weight(Weighted.DEFAULT_WEIGHT - 2)   // allow all other creators to take precedence over us...
public class ConfigBeanBuilderCreator extends DefaultBuilderCreatorProvider {
    static final String PICO_CONTRACT_TYPENAME = "io.helidon.pico.api.Contract";
    static final String PICO_EXTERNAL_CONTRACT_TYPENAME = "io.helidon.pico.api.ExternalContracts";
    static final String PICO_CONFIGUREDBY_TYPENAME = "io.helidon.pico.configdriven.ConfiguredBy";

    /**
     * Default constructor.
     */
    // note: this needs to remain public since it will be resolved via service loader ...
    @Deprecated
    public ConfigBeanBuilderCreator() {
    }

    @Override
    public Set<Class<? extends Annotation>> supportedAnnotationTypes() {
        return Set.of(ConfigBean.class);
    }

    @Override
    protected void preValidate(TypeName implTypeName,
                               TypeInfo typeInfo,
                               AnnotationAndValue configBeanAnno) {
        assertNoAnnotation(PICO_CONTRACT_TYPENAME, typeInfo);
        assertNoAnnotation(PICO_EXTERNAL_CONTRACT_TYPENAME, typeInfo);
        assertNoAnnotation(PICO_CONFIGUREDBY_TYPENAME, typeInfo);
        assertNoAnnotation(jakarta.inject.Singleton.class.getName(), typeInfo);
        assertNoAnnotation("javax.inject.Singleton", typeInfo);

        if (!typeInfo.typeKind().equals(TypeInfo.KIND_INTERFACE)) {
            throw new IllegalStateException("@" + configBeanAnno.typeName().className()
                                                    + " is only supported on interface types: " + typeInfo.typeName());
        }

        boolean drivesActivation = Boolean.parseBoolean(configBeanAnno.value(TAG_DRIVES_ACTIVATION).orElseThrow());
        LevelType levelType = LevelType.valueOf(configBeanAnno.value(TAG_LEVEL_TYPE).orElseThrow());
        if (drivesActivation && levelType != LevelType.ROOT) {
            throw new IllegalStateException("Only levelType {" + LevelType.ROOT + "} config beans can drive activation for: "
                                                    + typeInfo.typeName());
        }

        boolean wantDefaultConfigBean = Boolean.parseBoolean(configBeanAnno.value(TAG_WANT_DEFAULT_CONFIG_BEAN).orElseThrow());
        if (wantDefaultConfigBean && levelType != LevelType.ROOT) {
            throw new IllegalStateException("Only levelType {" + LevelType.ROOT + "} config beans can have a default bean for: "
                                                    + typeInfo.typeName());
        }

        assertNoGenericMaps(typeInfo);

        super.preValidate(implTypeName, typeInfo, configBeanAnno);
    }

    /**
     * Generic/simple map types are not supported on config beans, only &lt;String, &lt;Known ConfigBean types&gt;&gt;.
     */
    private void assertNoGenericMaps(TypeInfo typeInfo) {
        List<TypedElementName> list = typeInfo.elementInfo().stream()
                .filter(it -> it.typeName().isMap())
                .filter(it -> {
                    TypeName typeName = it.typeName();
                    List<TypeName> componentArgs = typeName.typeArguments();
                    boolean bad = (componentArgs.size() != 2);
                    if (!bad) {
                        bad = !componentArgs.get(0).name().equals(String.class.getName());
                        // right now we will accept any component type - ConfigBean Type or other (just not generic)
//                        bad |= !typeInfo.referencedTypeNamesToAnnotations().containsKey(componentArgs.get(1));
                        bad |= componentArgs.get(1).generic();
                    }
                    return bad;
                })
                .collect(Collectors.toList());

        if (!list.isEmpty()) {
            throw new IllegalStateException(list + ": only methods returning Map<String, <any-non-generic-type>> are supported "
                                                    + "for: " + typeInfo.typeName());
        }
    }

    @Override
    protected String generatedVersionFor(BodyContext ctx) {
        return Versions.CURRENT_BUILDER_CONFIG_VERSION;
    }

    @Override
    protected Optional<TypeName> baseExtendsTypeName(BodyContext ctx) {
        return Optional.of(DefaultTypeName.create(GeneratedConfigBeanBase.class));
    }

    @Override
    protected Optional<TypeName> baseExtendsBuilderTypeName(BodyContext ctx) {
        return Optional.of(DefaultTypeName.create(GeneratedConfigBeanBuilderBase.class));
    }

    @Override
    protected String instanceIdRef(BodyContext ctx) {
        return "__instanceId()";
    }

    @Override
    protected void appendExtraImports(StringBuilder builder,
                                      BodyContext ctx) {
        builder.append("\nimport ").append(AtomicInteger.class.getName()).append(";\n");

        builder.append("import ").append(Optional.class.getName()).append(";\n");
        builder.append("import ").append(Function.class.getName()).append(";\n\n");
        builder.append("import ").append(Supplier.class.getName()).append(";\n\n");

        super.appendExtraImports(builder, ctx);

        builder.append("import ").append(Config.class.getName()).append(";\n");
        builder.append("import ").append(ConfigResolver.class.getName()).append(";\n");
        builder.append("import ").append(ConfigBeanBuilderValidator.class.getName()).append(";\n\n");
    }

    @Override
    protected void appendMetaAttributes(StringBuilder builder,
                                        BodyContext ctx) {
        if (ctx.doingConcreteType()) {
            super.appendMetaAttributes(builder, ctx);
            return;
        }

        builder.append("\t@Override\n");
        builder.append("\tpublic Class<?> __configBeanType() {\n"
                               + "\t\treturn ").append(ctx.typeInfo().typeName().name()).append(".class;\n"
                                                                                                        + "\t}\n\n");
        javaDocMetaAttributesGetter(builder);
        builder.append("\tpublic static Class<?> __thisConfigBeanType() {\n"
                               + "\t\treturn ").append(ctx.typeInfo().typeName().name()).append(".class;\n"
                                                                                                        + "\t}\n\n");

        super.appendMetaAttributes(builder, ctx);
    }

    @Override
    protected void appendMetaProps(StringBuilder builder,
                                   BodyContext ctx,
                                   String tag,
                                   AtomicBoolean needsCustomMapOf) {
        builder.append("\t\t").append(tag);
        builder.append(".put(" + ConfigBeanInfo.class.getName() + ".TAG_META, Map.of(").append(ConfigBeanInfo.class.getName());
        builder.append(".class.getName(),\n\t\t\t\t").append(MetaConfigBeanInfo.class.getName()).append(".builder()\n");
        appendConfigBeanInfoAttributes(builder,
                                       ctx.typeInfo(),
                                       DefaultAnnotationAndValue
                                               .findFirst(ConfigBean.class.getTypeName(),
                                                          ctx.typeInfo().annotations()).orElseThrow());
        builder.append("\t\t\t\t\t\t.build()));\n");
        super.appendMetaProps(builder, ctx, tag, needsCustomMapOf);
    }

    @Override
    protected void appendExtraFields(StringBuilder builder,
                                     BodyContext ctx) {
        super.appendExtraFields(builder, ctx);
        if (!ctx.hasParent() && !ctx.doingConcreteType()) {
            builder.append("\tprivate static final AtomicInteger __INSTANCE_ID = new AtomicInteger();\n");
        }
    }

    @Override
    protected void appendExtraCtorCode(StringBuilder builder,
                                       BodyContext ctx,
                                       String builderTag) {
        if (!ctx.hasParent()) {
            builder.append("\t\tsuper(b, b.__config().isPresent() ? String.valueOf(__INSTANCE_ID.getAndIncrement()) : "
                                   + "\"-1\");\n");
        }

        super.appendExtraCtorCode(builder, ctx, builderTag);
    }

    @Override
    protected void appendExtraToBuilderBuilderFunctions(StringBuilder builder,
                                                        BodyContext ctx,
                                                        String decl) {
        if (ctx.doingConcreteType()) {
            String decl1 = decl.replace("{args}", Config.class.getName() + " cfg");
            javaDocToBuilder(builder, ctx, "cfg");
            builder.append("\t").append(decl1).append(" {\n");
            builder.append("\t\tBuilder b = builder();\n"
                                   + "\t\tb.acceptConfig(cfg, true);\n"
                                   + "\t\treturn b;\n"
                                   + "\t}\n");
        }

        super.appendExtraToBuilderBuilderFunctions(builder, ctx, decl);
    }

    @Override
    protected void appendExtraBuilderMethods(StringBuilder builder,
                                             BodyContext ctx) {
        if (ctx.doingConcreteType()) {
            super.appendExtraBuilderMethods(builder, ctx);
            return;
        }

        if (!ctx.hasParent()) {
            builder.append("\t\t@Override\n");
            builder.append("\t\tpublic void acceptConfig"
                                   + "(Config cfg, ConfigResolver resolver, ConfigBeanBuilderValidator<?> validator) {\n");
            builder.append("\t\t\t").append(ResolutionContext.class.getName())
                    .append(" ctx = createResolutionContext(__configBeanType(), cfg, resolver, validator, __mappers());\n");
            builder.append("\t\t\t__config(ctx.config());\n");
            builder.append("\t\t\t__acceptAndResolve(ctx);\n");
            builder.append("\t\t\tsuper.finishedResolution(ctx);\n");
            builder.append("\t\t}\n\n");
        }

        if (!ctx.doingConcreteType()) {
            if (ctx.hasParent()) {
                builder.append("\t\t@Override\n");
            } else {
                javaDocAcceptResolveConfigCtx(builder, ctx, "ctx");
            }
            builder.append("\t\tprotected void __acceptAndResolve(")
                    .append(ResolutionContext.class.getName())
                    .append(" ctx) {\n");
            if (ctx.hasParent()) {
                builder.append("\t\t\tsuper.__acceptAndResolve(ctx);\n");
            }

            int i = 0;
            for (String attrName : ctx.allAttributeNames()) {
                TypedElementName method = ctx.allTypeInfos().get(i);
                String configKey = toConfigKey(attrName, method, ctx.builderTriggerAnnotation());

                // resolver.of(config, "port", int.class).ifPresent(this::port);
                String ofClause = "of";
                TypeName outerType = method.typeName();
                String outerTypeName = outerType.declaredName();
                TypeName type = outerType;
                String typeName = type.declaredName();
                TypeName mapKeyType = null;
                TypeName mapKeyComponentType = null;
                boolean isMap = typeName.equals(Map.class.getName());
                boolean isCollection = (typeName.equals(Collection.class.getName())
                                                || typeName.equals(Set.class.getName())
                                                || typeName.equals(List.class.getName()));
                if (isCollection) {
                    ofClause = "ofCollection";
                    type = type.typeArguments().get(0);
                    typeName = type.declaredName();
                } else if (isMap) {
                    ofClause = "ofMap";
                    mapKeyType = type.typeArguments().get(0);
                    if (!mapKeyType.typeArguments().isEmpty()) {
                        mapKeyComponentType = mapKeyType.typeArguments().get(0);
                    }
                    type = type.typeArguments().get(1);
                    typeName = type.declaredName();
                } else if (Optional.class.getName().equals(typeName)) {
                    type = type.typeArguments().get(0);
                    typeName = type.declaredName();
                }

                builder.append("\t\t\tctx.resolver().").append(ofClause);
                builder.append("(ctx, __metaAttributes(), ").append(ConfigResolverRequestDefault.class.getPackage().getName());
                builder.append(".ConfigResolver");
                if (isMap) {
                    builder.append("Map");
                }
                builder.append("RequestDefault.builder()\n\t\t\t\t\t");
                builder.append(".configKey(\"").append(configKey);
                builder.append("\").attributeName(\"").append(attrName).append("\")");
                builder.append(".valueType(").append(outerTypeName).append(".class)");
                if (type != outerType) {
                    builder.append(".valueComponentType(").append(typeName).append(".class)");
                }
                if (isMap) {
                    builder.append(".keyType(").append(Objects.requireNonNull(mapKeyType)).append(".class)");
                    if (mapKeyComponentType != null) {
                        builder.append(".keyComponentType(").append(mapKeyComponentType.name()).append(".class)");
                    }
                }
                builder.append(".build())\n\t\t\t\t\t.ifPresent(val -> this.").append(method.elementName()).append("((");
                builder.append(outerTypeName).append(") val));\n");
                i++;
            }
            builder.append("\t\t}\n\n");

            builder.append("\t\t@Override\n");
            builder.append("\t\tpublic Class<?> __configBeanType() {\n"
                                   + "\t\t\treturn ")
                    .append(ctx.typeInfo().typeName().name()).append(".class;\n\t\t}\n\n");

            builder.append("\t\t@Override\n");
            builder.append("\t\tpublic Map<Class<?>, Function<Config, ?>> __mappers() {\n"
                                   + "\t\t\tMap<Class<?>, Function<Config, ?>> result = ");
            if (ctx.hasParent()) {
                builder.append("super.__mappers();\n");
            } else {
                builder.append("new java.util.LinkedHashMap<>();\n");
            }
            appendAvailableReferencedBuilders(builder, "\t\t\tresult.", ctx.typeInfo());
            builder.append("\t\t\treturn result;\n");
            builder.append("\t\t}\n\n");
        }

        super.appendExtraBuilderMethods(builder, ctx);
    }

    private void appendAvailableReferencedBuilders(StringBuilder builder,
                                                   String prefix,
                                                   TypeInfo typeInfo) {
        typeInfo.referencedTypeNamesToAnnotations().forEach((k, v) -> {
            AnnotationAndValue builderAnnotation = DefaultAnnotationAndValue
                    .findFirst(io.helidon.builder.Builder.class.getName(), v).orElse(null);
            if (builderAnnotation == null) {
                builderAnnotation = DefaultAnnotationAndValue
                        .findFirst(ConfigBean.class.getName(), v).orElse(null);
            }

            if (builderAnnotation != null) {
                TypeName referencedBuilderTypeName = toBuilderImplTypeName(k, builderAnnotation);
                builder.append(prefix).append("put(").append(k.name()).append(".class, ");
                builder.append(referencedBuilderTypeName).append("::toBuilder);\n");
            }
        });
    }

    @Override
    protected boolean overridesVisitAttributes(BodyContext ctx) {
        return true;
    }

    @Override
    protected String toConfigKey(String name,
                                 boolean isAttribute) {
        return (isAttribute) ? ConfigBeanInfo.toConfigAttributeName(name) : ConfigBeanInfo.toConfigBeanName(name);
    }

    private void appendConfigBeanInfoAttributes(StringBuilder builder,
                                                TypeInfo typeInfo,
                                                AnnotationAndValue configBeanAnno) {
        String configKey = configBeanAnno.value(TAG_KEY).orElse(null);
        configKey = Objects.requireNonNull(normalizeConfiguredOptionKey(configKey, typeInfo.typeName().className(), false));
        builder.append("\t\t\t\t\t\t.value(\"")
                .append(configKey).append("\")\n");
        builder.append("\t\t\t\t\t\t.").append(TAG_REPEATABLE).append("(")
                .append(configBeanAnno.value(TAG_REPEATABLE).orElseThrow()).append(")\n");
        builder.append("\t\t\t\t\t\t.").append(TAG_DRIVES_ACTIVATION).append("(")
                .append(configBeanAnno.value(TAG_DRIVES_ACTIVATION).orElseThrow()).append(")\n");
        builder.append("\t\t\t\t\t\t.").append(TAG_AT_LEAST_ONE).append("(")
                .append(configBeanAnno.value(TAG_AT_LEAST_ONE).orElseThrow()).append(")\n");
        builder.append("\t\t\t\t\t\t.").append(TAG_WANT_DEFAULT_CONFIG_BEAN).append("(")
                .append(configBeanAnno.value(TAG_WANT_DEFAULT_CONFIG_BEAN).orElseThrow()).append(")\n");
        builder.append("\t\t\t\t\t\t.").append(TAG_LEVEL_TYPE).append("(").append(LevelType.class.getCanonicalName()).append(".")
                .append(configBeanAnno.value(TAG_LEVEL_TYPE).orElseThrow()).append(")\n");
    }

    private void javaDocMetaAttributesGetter(StringBuilder builder) {
        builder.append("\t/**\n"
                               + "\t * Returns the {@code ConfigBean} type.\n"
                               + "\t *\n"
                               + "\t * @return the config bean type\n"
                               + "\t */\n");
    }

    private void javaDocToBuilder(StringBuilder builder,
                                  BodyContext ctx,
                                  String argTag) {
        builder.append("\t/**\n"
                               + "\t * Creates a builder for this type, initialized with the Config value passed.\n"
                               + "\t *\n");
        builder.append("\t * @param ").append(argTag).append(" the config to copy and initialize from\n");
        builder.append("\t * @return a fluent builder for {@link ").append(ctx.typeInfo().typeName());
        builder.append("}\n\t */\n");
    }

    private void javaDocAcceptResolveConfigCtx(StringBuilder builder,
                                               BodyContext ctx,
                                               String argTag) {
        builder.append("\t\t/**\n"
                               + "\t\t * Accept the config, resolves it, optionally validates.\n"
                               + "\t\t *\n");
        builder.append("\t\t * @param ").append(argTag).append(" the config resolution context\n");
        builder.append("\t\t */\n");
    }

    private String toConfigKey(String attrName,
                               TypedElementName method,
                               AnnotationAndValue ignoredBuilderAnnotation) {
        String configKey = null;
        Optional<? extends AnnotationAndValue> configuredOptions = DefaultAnnotationAndValue
                .findFirst(ConfiguredOption.class.getName(), method.annotations());
        if (configuredOptions.isPresent()) {
            configKey = configuredOptions.get().value("key").orElse(null);
        }
        if (configKey == null || configKey.isBlank()) {
            configKey = ConfigBeanInfo.toConfigAttributeName(attrName);
        }
        return configKey;
    }

    private static void assertNoAnnotation(String annoTypeName,
                                    TypeInfo typeInfo) {
        Optional<? extends AnnotationAndValue> anno = DefaultAnnotationAndValue
                .findFirst(annoTypeName, typeInfo.annotations());
        if (anno.isPresent()) {
            throw new IllegalStateException(annoTypeName + " cannot be used in conjunction with "
                                                    + ConfigBean.class.getName()
                                                    + " on " + typeInfo.typeName());
        }

        for (TypedElementName elem : typeInfo.elementInfo()) {
            anno = DefaultAnnotationAndValue.findFirst(annoTypeName, elem.annotations());
            if (anno.isEmpty()) {
                anno = DefaultAnnotationAndValue.findFirst(annoTypeName, elem.elementTypeAnnotations());
            }
            if (anno.isPresent()) {
                throw new IllegalStateException(annoTypeName + " cannot be used in conjunction with "
                                                        + ConfigBean.class.getName()
                                                        + " on " + typeInfo.typeName() + "." + elem + "()");
            }
        }

        if (typeInfo.superTypeInfo().isPresent()) {
            assertNoAnnotation(annoTypeName, typeInfo.superTypeInfo().get());
        }
    }

}
