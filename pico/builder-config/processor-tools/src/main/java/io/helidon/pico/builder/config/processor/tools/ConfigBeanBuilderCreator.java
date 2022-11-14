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

package io.helidon.pico.builder.config.processor.tools;

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

import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.config.metadata.ConfiguredOption;
import io.helidon.pico.Contract;
import io.helidon.pico.ExternalContracts;
import io.helidon.pico.builder.config.ConfigBean;
import io.helidon.pico.builder.config.ConfiguredBy;
import io.helidon.pico.builder.config.spi.ConfigBeanInfo;
import io.helidon.pico.builder.config.spi.ConfigBuilderValidator;
import io.helidon.pico.builder.config.spi.ConfigBuilderValidatorProvider;
import io.helidon.pico.builder.config.spi.ConfigResolver;
import io.helidon.pico.builder.config.spi.ConfigResolverProvider;
import io.helidon.pico.builder.config.spi.ConfigUtils;
import io.helidon.pico.builder.config.spi.MetaConfigBeanInfo;
import io.helidon.pico.builder.processor.spi.TypeInfo;
import io.helidon.pico.builder.processor.tools.DefaultBuilderCreator;
import io.helidon.pico.types.AnnotationAndValue;
import io.helidon.pico.types.DefaultAnnotationAndValue;
import io.helidon.pico.types.DefaultTypeName;
import io.helidon.pico.types.TypeName;
import io.helidon.pico.types.TypedElementName;

/**
 * A specialization of {@link io.helidon.pico.builder.processor.tools.DefaultBuilderCreator} to support the additional
 * caveats for binding to the config subsystem. When applied this will generate code that will require
 * the config-driven-services SPI module for resolving the required runtime types that will be generated.
 */
@Weight(Weighted.DEFAULT_WEIGHT)
public class ConfigBeanBuilderCreator extends DefaultBuilderCreator {

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
    protected void preValidate(TypeName implTypeName,
                               TypeInfo typeInfo,
                               AnnotationAndValue builderAnnotation) {
        assertNoAnnotation(Contract.class.getName(), typeInfo);
        assertNoAnnotation(ExternalContracts.class.getName(), typeInfo);
        assertNoAnnotation(ConfiguredBy.class.getName(), typeInfo);
        assertNoAnnotation(jakarta.inject.Singleton.class.getName(), typeInfo);
        assertNoAnnotation("javax.inject.Singleton", typeInfo);
    }

    private void assertNoAnnotation(String annoTypeName,
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
                anno = DefaultAnnotationAndValue.findFirst(annoTypeName, elem.elementTypeAnnotations(), false);
            }
            if (anno.isPresent()) {
                throw new IllegalStateException(annoTypeName + " cannot be used in conjunction with "
                                                        + ConfigBean.class.getName()
                                                        + " on " + typeInfo.typeName() + "." + elem + "()");
            }
        }

        if (Objects.nonNull(typeInfo.superTypeInfo())) {
            assertNoAnnotation(annoTypeName, typeInfo.superTypeInfo().get());
        }
    }

    @Override
    protected void appendToStringMethod(StringBuilder builder,
                                        BodyContext ctx) {
        builder.append("\t@Override\n");
        builder.append("\tpublic String toString() {\n");
        builder.append(
                "\t\treturn getClass().getSimpleName() + \"{\" + __instanceId() + \"}(\" + toStringInner() + \")\";\n");
        builder.append("\t}\n\n");
    }

    @Override
    protected void appendExtraImports(StringBuilder builder,
                                      BodyContext ctx) {
        builder.append("\nimport ").append(AtomicInteger.class.getName()).append(";\n");
        builder.append("import ").append(Optional.class.getName()).append(";\n");
        builder.append("import ").append(Supplier.class.getName()).append(";\n");
        super.appendExtraImports(builder, ctx.typeInfo);
    }

    @Override
    protected void appendMetaAttributes(StringBuilder builder,
                                        BodyContext ctx) {
        builder.append("\tpublic static Class<?> __getMetaConfigBeanType() {\n"
                               + "\t\treturn " + ctx.typeInfo.typeName().name() + ".class;\n"
                               + "\t}\n\n");

        super.appendMetaAttributes(builder, ctx);
    }

    @Override
    protected void appendMetaProps(StringBuilder builder,
                                   String tag,
                                   TypeInfo typeInfo,
                                   Map<String, TypedElementName> map,
                                   List<String> allAttributeNames,
                                   List<TypedElementName> allTypeInfos,
                                   AtomicBoolean needsCustomMapOf) {
        builder.append("\t\t").append(tag).append(".put(\"__meta\", Map.of(" + ConfigBeanInfo.class.getName()
                               + ".class.getName(),\n\t\t\t\t").append(MetaConfigBeanInfo.class.getName()).append(".builder()\n");
        appendConfigBeanInfoAttributes(builder,
                                       typeInfo,
                                       DefaultAnnotationAndValue
                                               .findFirst(ConfigBean.class.getTypeName(),
                                                          typeInfo.annotations(), true, false));
        builder.append("\t\t\t\t\t\t.build()));\n");
        super.appendMetaProps(builder, tag, typeInfo, map, allAttributeNames, allTypeInfos, needsCustomMapOf);
    }

    protected void appendConfigBeanInfoAttributes(StringBuilder builder,
                                                  TypeInfo typeInfo,
                                                  AnnotationAndValue configBeanAnno) {
        String configKey = configBeanAnno.value("key").orElse(null);
        configKey = normalizeConfiguredOptionKey(configKey, typeInfo.typeName().className(), null);

        builder.append("\t\t\t\t\t\t.key(\"")
                .append(Objects.requireNonNull(configKey)).append("\")\n");
        builder.append("\t\t\t\t\t\t.repeatable(")
                .append(configBeanAnno.value("repeatable").orElseThrow()).append(")\n");
        builder.append("\t\t\t\t\t\t.drivesActivation(")
                .append(configBeanAnno.value("drivesActivation").orElseThrow()).append(")\n");
        builder.append("\t\t\t\t\t\t.defaultConfigBeanUsingDefaults(")
                .append(configBeanAnno.value("defaultConfigBeanUsingDefaults").orElseThrow()).append(")\n");
    }

    @Override
    protected void appendExtraFields(StringBuilder builder,
                                     BodyContext ctx) {
        super.appendExtraFields(builder, hasParent, hasMetaAttributes, typeInfo);
        if (!hasParent) {
            builder.append("\tprivate static final AtomicInteger __instanceId = new AtomicInteger();\n"
                                   + "\tprivate String __thisInstanceId;\n");
            builder.append("\tprotected ").append(Config.class.getName()).append(" __cfg;\n");
        }
    }

    @Override
    protected String getFieldModifier() {
        return "";
    }

    @Override
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
            builder.append("\t\tthis();\n");
        }
        builder.append("\t\t__copyFrom(").append(builderTag).append(", false);\n");
    }

    @Override
    protected void appendExtraCtorCode(StringBuilder builder,
                                       boolean hasParent,
                                       String builderTag,
                                       TypeInfo typeInfo) {
        if (!hasParent) {
            builder.append("\t\tthis();\n");
        }

        super.appendExtraCtorCode(builder, hasParent, builderTag, typeInfo);
    }

    @Override
    protected void appendExtraPostCtorCode(StringBuilder builder,
                                           TypeName implTypeName,
                                           boolean hasParent,
                                           TypeInfo typeInfo,
                                           List<String> allAttributeNames,
                                           List<TypedElementName> allTypeInfos,
                                           String listType,
                                           String mapType,
                                           String setType) {
        builder.append("\tprotected ").append(implTypeName.className()).append("() {\n");
        if (!hasParent) {
            builder.append("\t\tthis.__thisInstanceId = String.valueOf(__instanceId.getAndIncrement());\n");
        }
        builder.append("\t}\n\n");

        builder.append("\t/**\n"
                               + "\t * Reserved for internal use.\n"
                               + "\t *\n"
                               + "\t * @param b the new builder values.\n"
                               + "\t */\n");
        builder.append("\tpublic void __copyFrom(Builder b, boolean validate) {\n");
        if (hasParent) {
            builder.append("\t\tsuper.__copyFrom(b, validate);\n");
        } else {
            builder.append("\t\tif (validate) {\n");
            builder.append("\t\t\tb.validate(this, true);\n");
            builder.append("\t\t}\n");
            builder.append("\t\tthis.__cfg = b.__cfg;\n");
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
        builder.append("\t}\n\n");

        builder.append("\t/**\n"
                               + "\t * Reserved for internal use.\n"
                               + "\t */\n");
        builder.append("\tpublic void __copyFrom(").append(Config.class.getName()).append(" config, ");
        builder.append(ConfigResolver.class.getName()).append(" resolver) {\n");
        builder.append("\t\tif (Objects.isNull(config)) {\n");
        builder.append("\t\t\tconfig = __cfg;\n");
        builder.append("\t\t}\n");
        builder.append("\t\t__copyFrom(toBuilder(config, Objects.requireNonNull(resolver)), true);\n");
        builder.append("\t}\n\n");

        if (!hasParent) {
            builder.append("\t/**\n"
                                   + "\t * Reserved for internal use.\n"
                                   + "\t *\n"
                                   + "\t * @param val the new instance identifier.\n"
                                   + "\t */\n"
                                   + "\tpublic void __overrideInstanceId(String val) {\n"
                                   + "\t\tassert (" + AnnotationAndValue.class.getName() + ".hasNonBlankValue(val));\n"
                                   + "\t\tthis.__thisInstanceId = val;\n"
                                   + "\t}\n"
                                   + "\n"
                                   + "\t/**\n"
                                   + "\t * Instance identifier.  Reserved for internal use.\n"
                                   + "\t * \n"
                                   + "\t * @return the internal instance identifier\n"
                                   + "\t */\n"
                                   + "\tpublic String __instanceId() {\n"
                                   + "\t\treturn __thisInstanceId;\n"
                                   + "\t}\n\n");

            builder.append("\tpublic Optional<" + Config.class.getName() + "> __config() {\n"
                                   + "\t\treturn Optional.ofNullable(__cfg);\n"
                                   + "\t}\n\n");
        }

        super.appendExtraPostCtorCode(builder, implTypeName, hasParent, typeInfo,
                                      allAttributeNames,
                                      allTypeInfos,
                                      listType,
                                      mapType,
                                      setType);
    }

    @Override
    protected void appendExtraMethods(StringBuilder builder,
                                      AnnotationAndValue builderAnnotation,
                                      boolean hasParent,
                                      TypeInfo typeInfo,
                                      List<String> allAttributeNames,
                                      List<TypedElementName> allTypeInfos) {
//        appendVisitAttributes(builder, "", false, hasParent, typeInfo, allAttributeNames, allTypeInfos);
        super.appendExtraMethods(builder, builderAnnotation, hasParent, typeInfo, allAttributeNames, allTypeInfos);
    }

//    protected void appendVisitAttributes(StringBuilder builder,
//                                         String extraTabs,
//                                         boolean beanNameRef,
//                                         boolean hasParent,
//                                         TypeInfo ignoredTypeInfo,
//                                         List<String> allAttributeNames,
//                                         List<TypedElementName> allTypeInfos) {
//        if (hasParent) {
//            builder.append(extraTabs).append("\t@Override\n");
//        }
//        builder.append(extraTabs).append("\tpublic void visitAttributes(AttributeVisitor visitor, Object userDefinedCtx) {\n");
//        if (hasParent) {
//            builder.append(extraTabs).append("\t\tsuper.visitAttributes(visitor, userDefinedCtx);\n");
//        }
//
//        // void visit(String key, Object value, Object userDefinedCtx, Class<?> type, Class<?>... typeArgument);
//        int i = 0;
//        for (String attrName : allAttributeNames) {
//            TypedElementName method = allTypeInfos.get(i);
//            String typeName = method.getTypeName().declaredName();
//            List<String> typeArgs = method.getTypeName().typeArguments().stream()
//                    .map(it -> it.declaredName() + ".class")
//                    .collect(Collectors.toList());
//            String typeArgsStr = String.join(", ", typeArgs);
//
//            builder.append(extraTabs).append("\t\tvisitor.visit(\"").append(attrName).append("\", () -> ");
//            if (beanNameRef) {
//                builder.append(attrName).append(", ");
//            } else {
//                builder.append(method.getElementName()).append("(), ");
//            }
//            builder.append("__metaProps.get(\"").append(attrName).append("\"), userDefinedCtx, ");
//            builder.append(typeName).append(".class");
//            if (!typeArgsStr.isBlank()) {
//                builder.append(", ").append(typeArgsStr);
//            }
//            builder.append(");\n");
//
//            i++;
//        }
//
//        builder.append(extraTabs).append("\t}\n\n");
//    }

    @Override
    protected void appendExtraToBuilderBuilderFunctions(StringBuilder builder,
                                                        String decl,
                                                        TypeInfo typeInfo,
                                                        TypeName parentTypeName,
                                                        List<String> allAttributeNames,
                                                        List<TypedElementName> allTypeInfos) {
        String decl1 = decl.replace("{args}", Config.class.getName() + " val, "
                + ConfigResolver.class.getName() + " resolver");
        builder.append("\t/**\n\t * @return A builder for {@link ");
        builder.append(typeInfo.typeName());
        builder.append("}\n\t */\n");
        builder.append("\t").append(decl1).append("\n");
        builder.append("\t\tClass<?> cb = __getMetaConfigBeanType();\n");
        builder.append("\t\tMap<String, Map<String, Object>> meta = __getMetaAttributes();\n");
        builder.append("\t\treturn new Builder(val, resolver, cb, meta);\n\t}\n\n");

        String decl2 = decl.replace("{args}", Config.class.getName() + " val");
        builder.append("\t/**\n\t * @return A builder for {@link ");
        builder.append(typeInfo.typeName());
        builder.append("}\n\t */\n");
        builder.append("\t").append(decl2).append("\n");
        builder.append("\t\t").append(ConfigResolver.class.getName()).append(" resolver = ");
        builder.append(ConfigResolverProvider.class.getName()).append(".getInstance();\n");
        builder.append("\t\treturn toBuilder(val, resolver);\n\t}\n\n");

        super.appendExtraToBuilderBuilderFunctions(builder,
                                                   decl,
                                                   typeInfo,
                                                   parentTypeName,
                                                   allAttributeNames,
                                                   allTypeInfos);
    }

    @Override
    protected void appendExtraBuilderFields(StringBuilder builder,
                                            String builderGeneratedClassName,
                                            AnnotationAndValue builderAnnotation,
                                            TypeInfo typeInfo,
                                            TypeName parentTypeName,
                                            List<String> allAttributeNames,
                                            List<TypedElementName> allTypeInfos) {
        if (Objects.isNull(parentTypeName)) {
            builder.append("\t\tprotected ").append(Config.class.getName()).append(" __cfg;\n");
        }

        super.appendExtraBuilderFields(builder, builderGeneratedClassName,
                                       builderAnnotation,
                                       typeInfo, parentTypeName, allAttributeNames, allTypeInfos);
    }

    @Override
    protected void appendBuilderBuildPreSteps(StringBuilder builder) {
        builder.append("\t\t\tvalidate(null, true);\n");
        super.appendBuilderBuildPreSteps(builder);
    }

    @Override
    protected void appendExtraBuilderMethods(StringBuilder builder,
                                             BodyContext ctx) {
        builder.append("\t\t/**\n\t\t * @return A builder for {@link " + typeInfo.typeName());
        builder.append("}\n\t\t */\n");
        builder.append("\t\tprotected ").append(builderGeneratedClassName).append("(").append(Config.class.getName())
                .append(" config, ");
        builder.append(ConfigResolver.class.getName())
                .append(" resolver, Class<?> cb, Map<String, Map<String, Object>> meta) {\n");
        builder.append(getCtorBody(builderAnnotation, typeInfo, parentTypeName, allAttributeNames, allTypeInfos));
        builder.append("\t\t}\n\n");

        boolean hasParent = Objects.nonNull(parentTypeName);
        if (hasParent) {
            builder.append("\t\t@Override\n");
        }
        builder.append("\t\tpublic B resolve(").append(Config.class.getName()).append(" config, ");
        builder.append(ConfigResolver.class.getName());
        builder.append(" resolver, Class<?> cb, Map<String, Map<String, Object>> meta) {\n");
        if (hasParent) {
            builder.append("\t\t\tsuper.resolve(config, resolver, cb, meta);\n");
        }
        builder.append("\t\t\tif (Objects.isNull(config)) {\n");
        builder.append("\t\t\t\tconfig = Objects.requireNonNull(__cfg);\n");
        builder.append("\t\t\t}\n");
        builder.append("\t\t\tresolveThis(config, Objects.requireNonNull(resolver), cb, meta);\n");
        builder.append("\t\t\treturn identity();\n");
        builder.append("\t\t}\n\n");

        builder.append("\t\tprivate void resolveThis(").append(Config.class.getName()).append(" config, ");
        builder.append(ConfigResolver.class.getName());
        builder.append(" resolver, Class<?> cb, Map<String, Map<String, Object>> meta) {\n");

        int i = 0;
        for (String attrName : allAttributeNames) {
            TypedElementName method = allTypeInfos.get(i);

            String configKey = toConfigKey(attrName, method, builderAnnotation);

            // resolver.of(config, "port", int.class).ifPresent(this::port);
            String ofClause = "of";
            TypeName outerType = method.getTypeName();
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
                outerType = type = type.typeArguments().get(0);
                typeName = type.declaredName();
            }

            builder.append("\t\t\tresolver.").append(ofClause).append("(config, \"");
            builder.append(configKey).append("\", \"").append(attrName).append("\", ");
            if (isMap) {
                builder.append(Objects.requireNonNull(mapKeyType)).append(".class, ");
                String mapKeyComponentTypeName = Objects.isNull(mapKeyComponentType)
                        ? null : mapKeyComponentType.name() + ".class";
                builder.append(mapKeyComponentTypeName).append(", ");
            }
            builder.append(outerTypeName).append(".class");
            if (type != outerType) {
                builder.append(", ").append(typeName).append(".class");
            }
            builder.append(", cb, meta).ifPresent(this::").append(attrName).append(");\n");
            i++;
        }
        builder.append("\t\t}\n\n");

        if (!hasParent) {
            String validationRoundClassName = ConfigBuilderValidator.ValidationRound.class.getCanonicalName();
            String validatorClassName = ConfigBuilderValidator.class.getCanonicalName();
            String validatorProviderClassName = ConfigBuilderValidatorProvider.class.getCanonicalName();
            builder.append("\t\tpublic Optional<" + validationRoundClassName + "> validate(T receiver, "
                                   + "boolean throwIfErrors) {\n"
                                   + "\t\t\t" + validatorClassName + " validator = " + validatorProviderClassName
                                   + ".getInstance();\n"
                                   + "\t\t\tif (Objects.isNull(validator)) {\n"
                                   + "\t\t\t\treturn Optional.empty();\n"
                                   + "\t\t\t}\n"
                                   + "\n"
                                   + "\t\t\t" + validationRoundClassName
                                   + " validation = validator.createValidationRound(this, receiver, "
                                   + "__getMetaConfigBeanType());\n"
                                   + "\t\t\tif (Objects.isNull(validation)) {\n"
                                   + "\t\t\t\treturn Optional.empty();\n"
                                   + "\t\t\t}\n\n"
                                   + "\t\t\tAttributeVisitor "
                                   + "visitor = validation::visit;\n"
                                   + "\t\t\tvisitAttributes(visitor, validation);\n"
                                   + "\t\t\treturn Optional.of(validation.finish(throwIfErrors));\n"
                                   + "\t\t}\n\n");
        }

//        appendVisitAttributes(builder, "\t", true, hasParent, typeInfo, allAttributeNames, allTypeInfos);

        super.appendExtraBuilderMethods(builder,
                                        builderGeneratedClassName,
                                        builderAnnotation,
                                        typeInfo, parentTypeName, allAttributeNames, allTypeInfos);
    }

    @Override
    protected void appendExtraInnerClasses(StringBuilder builder,
                                           BodyContext ctx) {
//        if (!hasParent) {
//            builder.append("\n\t@FunctionalInterface\n"
//                                   + "\tpublic static interface AttributeVisitor {\n"
//                                   + "\t\tvoid visit(String attrName, java.util.function.Supplier<Object> valueSupplier, "
//                                   + "Map<String, Object> meta, Object userDefinedCtx, Class<?> "
//                                   + "type, Class<?>... typeArgument);\n"
//                                   + "\t}\n\n");
//        }

        super.appendExtraInnerClasses(builder, hasParent, typeInfo);
    }

    protected String getCtorBody(AnnotationAndValue ignoredBuilderAnnotation,
                                 TypeInfo ignoredTypeInfo,
                                 TypeName parentTypeName,
                                 List<String> ignoredAllAttributeNames,
                                 List<TypedElementName> ignoredAllTypeInfos) {
        StringBuilder builder = new StringBuilder();
        if (Objects.nonNull(parentTypeName)) {
            builder.append("\t\t\tsuper(config, resolver, cb, meta);\n");
        } else {
            builder.append("\t\t\tthis.__cfg = config;\n");
        }
        builder.append("\t\t\tif (Objects.nonNull(resolver)) {\n"
                               + "\t\t\t\tresolveThis(config, resolver, cb, meta);\n"
                               + "\t\t\t}\n");
        return builder.toString();
    }

    protected String toConfigKey(String attrName,
                                 TypedElementName method,
                                 AnnotationAndValue ignoredBuilderAnnotation) {
        String configKey = null;
        AnnotationAndValue configuredOptions = DefaultAnnotationAndValue
                .findFirst(ConfiguredOption.class.getName(), method.getAnnotations(), false, false);
        if (Objects.nonNull(configuredOptions)) {
            configKey = configuredOptions.value("key").orElse(null);
        }
        if (!AnnotationAndValue.hasNonBlankValue(configKey)) {
            configKey = ConfigUtils.toConfigKey(attrName);
        }
        return configKey;
    }

    @Override
    protected String normalizeConfiguredOptionKey(String key,
                                                  String attrName,
                                                  TypedElementName ignoredMethod) {
        if (AnnotationAndValue.hasNonBlankValue(key)) {
            return key;
        }

        return ConfigUtils.toConfigKey(Objects.requireNonNull(attrName));
    }

}
