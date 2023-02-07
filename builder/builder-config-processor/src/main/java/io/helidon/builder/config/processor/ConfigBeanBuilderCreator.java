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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import io.helidon.builder.config.ConfigBean;
import io.helidon.builder.config.spi.ConfigBeanBuilderValidator;
import io.helidon.builder.config.spi.ConfigBeanInfo;
import io.helidon.builder.config.spi.ConfigResolver;
import io.helidon.builder.config.spi.DefaultConfigResolverRequest;
import io.helidon.builder.config.spi.IConfigBeanBase;
import io.helidon.builder.config.spi.IConfigBeanBuilderBase;
import io.helidon.builder.config.spi.MetaConfigBeanInfo;
import io.helidon.builder.config.spi.ResolutionContext;
import io.helidon.builder.processor.spi.TypeInfo;
import io.helidon.builder.processor.tools.BodyContext;
import io.helidon.builder.processor.tools.DefaultBuilderCreatorProvider;
import io.helidon.builder.types.AnnotationAndValue;
import io.helidon.builder.types.DefaultAnnotationAndValue;
import io.helidon.builder.types.DefaultTypeName;
import io.helidon.builder.types.TypeName;
import io.helidon.builder.types.TypedElementName;
import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.common.config.Config;
import io.helidon.config.metadata.ConfiguredOption;

/**
 * A specialization of {@link io.helidon.builder.processor.tools.DefaultBuilderCreatorProvider} that supports the additional
 * add-ons to the builder generated classes that binds to the config sub-system.
 *
 * @see io.helidon.builder.config.spi.IConfigBean
 * @see io.helidon.builder.config.spi.IConfigBeanBuilder
 */
@Weight(Weighted.DEFAULT_WEIGHT)
public class ConfigBeanBuilderCreator extends DefaultBuilderCreatorProvider {

    static final String PICO_CONTRACT_TYPENAME = "io.helidon.pico.Contract";
    static final String PICO_EXTERNAL_CONTRACT_TYPENAME = "io.helidon.pico.ExternalContracts";
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
        return Collections.singleton(ConfigBean.class);
    }

    @Override
    protected void preValidate(
            TypeName implTypeName,
            TypeInfo typeInfo,
            AnnotationAndValue builderAnnotation) {
        assertNoAnnotation(PICO_CONTRACT_TYPENAME, typeInfo);
        assertNoAnnotation(PICO_EXTERNAL_CONTRACT_TYPENAME, typeInfo);
        assertNoAnnotation(PICO_CONFIGUREDBY_TYPENAME, typeInfo);
        assertNoAnnotation(jakarta.inject.Singleton.class.getName(), typeInfo);
        assertNoAnnotation("javax.inject.Singleton", typeInfo);

        if (!typeInfo.typeKind().equals("INTERFACE")) {
            throw new IllegalStateException("@" + builderAnnotation.typeName().className()
                                                    + " is only supported on interface types: " + typeInfo.typeName());
        }
    }

    @Override
    protected String generatedVersionFor(
            BodyContext ctx) {
        return Versions.CURRENT_BUILDER_CONFIG_VERSION;
    }

    @Override
    protected Optional<TypeName> baseExtendsTypeName(
            BodyContext ctx) {
        return Optional.of(DefaultTypeName.create(IConfigBeanBase.class));
    }

    @Override
    protected Optional<TypeName> baseExtendsBuilderTypeName(
            BodyContext ctx) {
        return Optional.of(DefaultTypeName.create(IConfigBeanBuilderBase.class));
    }

    @Override
    protected String instanceIdRef(
            BodyContext ctx) {
        return "__instanceId()";
    }

    @Override
    protected void appendExtraImports(
            StringBuilder builder,
            BodyContext ctx) {
        builder.append("\nimport ").append(AtomicInteger.class.getName()).append(";\n");
        builder.append("import ").append(Optional.class.getName()).append(";\n");
        builder.append("import ").append(Supplier.class.getName()).append(";\n\n");

        super.appendExtraImports(builder, ctx);

        builder.append("import ").append(Config.class.getName()).append(";\n");
        builder.append("import ").append(ConfigResolver.class.getName()).append(";\n");
        builder.append("import ").append(ConfigBeanBuilderValidator.class.getName()).append(";\n\n");
    }

    @Override
    protected void appendMetaAttributes(
            StringBuilder builder,
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
    protected void appendMetaProps(
            StringBuilder builder,
            BodyContext ctx,
            String tag,
            AtomicBoolean needsCustomMapOf) {
        builder.append("\t\t").append(tag);
        builder.append(".put(\"__meta\", Map.of(").append(ConfigBeanInfo.class.getName());
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
    protected void appendExtraFields(
            StringBuilder builder,
            BodyContext ctx) {
        super.appendExtraFields(builder, ctx);
        if (!ctx.hasParent() && !ctx.doingConcreteType()) {
            builder.append("\tprivate static final AtomicInteger __INSTANCE_ID = new AtomicInteger();\n");
        }
    }

    @Override
    protected void appendExtraCtorCode(
            StringBuilder builder,
            BodyContext ctx,
            String builderTag) {
        if (!ctx.hasParent()) {
            builder.append("\t\tsuper(b, b.__config().isPresent() ? String.valueOf(__INSTANCE_ID.getAndIncrement()) : "
                                   + "\"-1\");\n");
        }

        super.appendExtraCtorCode(builder, ctx, builderTag);
    }

    @Override
    protected void appendExtraToBuilderBuilderFunctions(
            StringBuilder builder,
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
    protected void appendExtraBuilderMethods(
            StringBuilder builder,
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
                    .append(" ctx = createResolutionContext(__configBeanType(), cfg, resolver, validator);\n");
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
                boolean isCollection = (
                        typeName.equals(Collection.class.getName())
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
                builder.append("(ctx, __metaAttributes(), ").append(DefaultConfigResolverRequest.class.getPackage().getName());
                builder.append(".DefaultConfigResolver");
                if (isMap) {
                    builder.append("Map");
                }
                builder.append("Request.builder()\n\t\t\t\t\t");
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
                builder.append(".build())\n\t\t\t\t\t.ifPresent((val) -> this.").append(method.elementName()).append("((");
                builder.append(outerTypeName).append(") val));\n");
                i++;
            }

            builder.append("\t\t}\n\n");
        }

        builder.append("\t\t@Override\n");
        builder.append("\t\tpublic Class<?> __configBeanType() {\n"
                               + "\t\t\treturn ")
                .append(ctx.typeInfo().typeName().name()).append(".class;\n\t\t}\n\n");

        super.appendExtraBuilderMethods(builder, ctx);
    }

    @Override
    protected boolean overridesVisitAttributes(
            BodyContext ctx) {
        return true;
    }

    @Override
    protected String toConfigKey(
            String attrName) {
        return ConfigBeanInfo.toConfigKey(attrName);
    }

    private void appendConfigBeanInfoAttributes(
            StringBuilder builder,
            TypeInfo typeInfo,
            AnnotationAndValue configBeanAnno) {
        String configKey = configBeanAnno.value(ConfigBeanInfo.TAG_KEY).orElse(null);
        configKey = Objects.requireNonNull(normalizeConfiguredOptionKey(configKey, typeInfo.typeName().className()));
        builder.append("\t\t\t\t\t\t.key(\"")
                .append(configKey).append("\")\n");
        builder.append("\t\t\t\t\t\t.repeatable(")
                .append(configBeanAnno.value(ConfigBeanInfo.TAG_REPEATABLE).orElseThrow()).append(")\n");
        builder.append("\t\t\t\t\t\t.drivesActivation(")
                .append(configBeanAnno.value(ConfigBeanInfo.TAG_DRIVES_ACTIVATION).orElseThrow()).append(")\n");
        builder.append("\t\t\t\t\t\t.atLeastOne(")
                .append(configBeanAnno.value(ConfigBeanInfo.TAG_AT_LEAST_ONE).orElseThrow()).append(")\n");
        builder.append("\t\t\t\t\t\t.wantDefaultConfigBean(")
                .append(configBeanAnno.value(ConfigBeanInfo.TAG_WANT_DEFAULT_CONFIG_BEAN).orElseThrow()).append(")\n");
    }

    private void javaDocMetaAttributesGetter(
            StringBuilder builder) {
        builder.append("\t/**\n"
                               + "\t * Returns the {@code ConfigBean} type.\n"
                               + "\t *\n"
                               + "\t * @return the config bean type\n"
                               + "\t */\n");
    }

    private void javaDocToBuilder(
            StringBuilder builder,
            BodyContext ctx,
            String argTag) {
        builder.append("\t/**\n"
                               + "\t * Creates a builder for this type, initialized with the Config value passed.\n"
                               + "\t *\n");
        builder.append("\t * @param ").append(argTag).append(" the config to copy and initialize from\n");
        builder.append("\t * @return a fluent builder for {@link ").append(ctx.typeInfo().typeName());
        builder.append("}\n\t */\n");
    }

    private void javaDocAcceptResolveConfigCtx(
            StringBuilder builder,
            BodyContext ctx,
            String argTag) {
        builder.append("\t\t/**\n"
                               + "\t\t * Accept the config, resolves it, optionally validates.\n"
                               + "\t\t *\n");
        builder.append("\t\t * @param ").append(argTag).append(" the config resolution context\n");
        builder.append("\t\t */\n");
    }

    private String toConfigKey(
            String attrName,
            TypedElementName method,
            AnnotationAndValue ignoredBuilderAnnotation) {
        String configKey = null;
        Optional<? extends AnnotationAndValue> configuredOptions = DefaultAnnotationAndValue
                .findFirst(ConfiguredOption.class.getName(), method.annotations());
        if (configuredOptions.isPresent()) {
            configKey = configuredOptions.get().value("key").orElse(null);
        }
        if (configKey == null || configKey.isBlank()) {
            configKey = ConfigBeanInfo.toConfigKey(attrName);
        }
        return configKey;
    }

    private void assertNoAnnotation(
            String annoTypeName,
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
