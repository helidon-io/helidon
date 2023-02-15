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

package io.helidon.pico.configdriven.processor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;

import io.helidon.builder.AttributeVisitor;
import io.helidon.builder.Builder;
import io.helidon.builder.config.ConfigBean;
import io.helidon.builder.config.spi.ConfigBeanInfo;
import io.helidon.common.config.Config;
import io.helidon.common.types.AnnotationAndValue;
import io.helidon.common.types.DefaultAnnotationAndValue;
import io.helidon.common.types.TypeName;
import io.helidon.pico.configdriven.ConfiguredBy;
import io.helidon.pico.configdriven.services.AbstractConfiguredServiceProvider;
import io.helidon.pico.processor.ServiceAnnotationProcessor;
import io.helidon.pico.tools.ServicesToProcess;
import io.helidon.pico.tools.ToolsException;
import io.helidon.pico.tools.TypeTools;
import io.helidon.pico.tools.spi.ActivatorCreatorProvider;

import static io.helidon.builder.processor.tools.BuilderTypeTools.createTypeNameFromElement;
import static io.helidon.builder.processor.tools.BuilderTypeTools.extractValues;
import static io.helidon.builder.processor.tools.BuilderTypeTools.findAnnotationMirror;
import static io.helidon.builder.processor.tools.BuilderTypeTools.toTypeElement;
import static io.helidon.common.types.DefaultTypeName.create;
import static io.helidon.common.types.DefaultTypeName.createFromGenericDeclaration;
import static io.helidon.common.types.DefaultTypeName.createFromTypeName;
import static io.helidon.common.types.DefaultTypeName.toBuilder;

/**
 * Processor for @{@link io.helidon.pico.configdriven.ConfiguredBy} type annotations.
 *
 * @deprecated
 */
public class ConfiguredByProcessor extends ServiceAnnotationProcessor {
    private final System.Logger logger = System.getLogger(getClass().getName());
    private final LinkedHashSet<Element> elementsProcessed = new LinkedHashSet<>();

    static final String TAG_OVERRIDE_BEAN = "overrideBean";

    /**
     * Service loader based constructor.
     *
     * @deprecated
     */
    public ConfiguredByProcessor() {
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> supported = new LinkedHashSet<>(super.getSupportedAnnotationTypes());
        supported.add(ConfiguredBy.class.getName());
        return supported;
    }

    @Override
    public Set<String> contraAnnotations() {
        return Set.of();
    }

    @Override
    public boolean process(
            Set<? extends TypeElement> annotations,
            RoundEnvironment roundEnv) {
        super.process(annotations, roundEnv);

        if (roundEnv.processingOver()) {
            elementsProcessed.clear();
            // we claim this annotation!
            return true;
        }

        try {
            Set<? extends Element> typesToProcess = roundEnv.getElementsAnnotatedWith(ConfiguredBy.class);
            for (Element element : typesToProcess) {
                if (!elementsProcessed.add(element)) {
                    continue;
                }

                try {
                    process(element);
                } catch (Throwable e) {
                    throw new ToolsException("Failed while processing " + element + "; " + e.getMessage(), e);
                }
            }
        } catch (Throwable e) {
            logger.log(System.Logger.Level.ERROR, e.getMessage(), e);
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, e.getMessage());
            throw new RuntimeException(e.getMessage(), e);
        }

        // we need to claim this annotation!
        return true;
    }

    void process(
            Element element) {
        if (!(element instanceof TypeElement)) {
            throw new ToolsException("Expected " + element + " to be processed as a TypeElement");
        }

        AnnotationMirror am = findAnnotationMirror(ConfiguredBy.class.getName(), element.getAnnotationMirrors()).orElseThrow();
        Map<String, String> configuredByAttributes = extractValues(am, processingEnv.getElementUtils());
        TypeName configBeanType = createFromTypeName(configuredByAttributes.get("value"));
        TypeName serviceTypeName = createTypeNameFromElement(element).orElseThrow();
        TypeElement parent = toTypeElement(((TypeElement) element).getSuperclass()).orElse(null);
        TypeName parentServiceTypeName = (parent == null) ? null : createTypeNameFromElement(parent).orElseThrow();
        TypeName activatorImplTypeName = ActivatorCreatorProvider.instance().toActivatorImplTypeName(serviceTypeName);
        TypeName genericCB = createFromGenericDeclaration("CB");
        TypeName genericExtendsCB = createFromGenericDeclaration("CB extends " + configBeanType.name());
        boolean hasParent = (parentServiceTypeName != null);
        if (hasParent) {
            // we already know our parent, but we need to morph it with our activator and new CB reference
            TypeName parentActivatorImplTypeName = ActivatorCreatorProvider.instance()
                    .toActivatorImplTypeName(parentServiceTypeName);
            parentServiceTypeName = toBuilder(parentActivatorImplTypeName)
                    .typeArguments(Collections.singletonList(genericCB))
                    .build();
        } else {
            List<TypeName> typeArgs = List.of(serviceTypeName, genericCB);
            parentServiceTypeName = create(AbstractConfiguredServiceProvider.class).toBuilder()
                    .typeArguments(typeArgs)
                    .build();
        }

        validate((TypeElement) element, configBeanType, serviceTypeName, parentServiceTypeName);

        List<String> extraCodeGen = createExtraCodeGen(activatorImplTypeName, configBeanType, hasParent, configuredByAttributes);

        ServicesToProcess servicesToProcess = ServicesToProcess.servicesInstance();
        boolean accepted = servicesToProcess.addParentServiceType(serviceTypeName, parentServiceTypeName, Optional.of(true));
        assert (accepted);
        servicesToProcess.addActivatorGenericDecl(serviceTypeName, "<" + genericExtendsCB.fqName() + ">");
        extraCodeGen.forEach(fn -> servicesToProcess.addExtraCodeGen(serviceTypeName, fn));

        processServiceType(serviceTypeName, (TypeElement) element);
    }

    void validate(
            TypeElement element,
            TypeName configBeanType,
            TypeName serviceTypeName,
            TypeName parentServiceTypeName) {
        assertNoAnnotation(create(jakarta.inject.Singleton.class), element);
        validateBeanType(configBeanType);
        validateServiceType(serviceTypeName, parentServiceTypeName);
    }

    void assertNoAnnotation(
            TypeName annoType,
            TypeElement element) {
        Set<AnnotationAndValue> annos = TypeTools.createAnnotationAndValueSet(element);
        Optional<? extends AnnotationAndValue> anno = DefaultAnnotationAndValue.findFirst(annoType.name(), annos);
        if (anno.isPresent()) {
            throw new IllegalStateException(annoType + " cannot be used in conjunction with "
                                                    + ConfiguredBy.class + " on " + element);
        }
    }

    void validateBeanType(
            TypeName configBeanType) {
        TypeElement typeElement = (configBeanType == null)
                ? null : processingEnv.getElementUtils().getTypeElement(configBeanType.name());
        if (typeElement == null) {
            throw new ToolsException("unknown type: " + configBeanType);
        }

        if (typeElement.getKind() != ElementKind.INTERFACE) {
            throw new ToolsException("The config bean must be an interface: " + typeElement);
        }

        ConfigBean cfgBean = typeElement.getAnnotation(ConfigBean.class);
        if (cfgBean == null) {
            throw new ToolsException("The config bean must be annotated with @" + ConfigBean.class.getSimpleName()
                                             + ": " + configBeanType);
        }
    }

    void validateServiceType(
            TypeName serviceTypeName,
            TypeName ignoredParentServiceTypeName) {
        TypeElement typeElement = (serviceTypeName == null)
                ? null : processingEnv.getElementUtils().getTypeElement(serviceTypeName.name());
        if (typeElement == null) {
            throw new ToolsException("unknown type: " + serviceTypeName);
        }

        if (typeElement.getKind() != ElementKind.CLASS) {
            throw new ToolsException("The configured service must be a concrete class: " + typeElement);
        }
    }

    List<String> createExtraCodeGen(
            TypeName activatorImplTypeName,
            TypeName configBeanType,
            boolean hasParent,
            Map<String, String> configuredByAttributes) {
        List<String> result = new ArrayList<>();
        TypeName configBeanImplName = toDefaultImpl(configBeanType);

        String comment = "\n\t/**\n"
                + "\t * Config-driven service constructor.\n"
                + "\t * \n"
                + "\t * @param configBean config bean\n"
                + "\t */";
        if (hasParent) {
            result.add(comment + "\n\tprotected " + activatorImplTypeName.className() + "(" + configBeanType + " configBean) {\n"
                               + "\t\tsuper(configBean);\n"
                               + "\t\tserviceInfo(serviceInfo);\n"
                               + "\t}\n");
        } else {
            result.add("\n\tprivate " + configBeanType + " configBean;\n");
            result.add(comment + "\n\tprotected " + activatorImplTypeName.className() + "(" + configBeanType + " configBean) {\n"
                               + "\t\tthis.configBean = Objects.requireNonNull(configBean);\n"
                               + "\t\tassertIsRootProvider(false, true);\n"
                               + "\t\tserviceInfo(serviceInfo);\n"
                               + "\t}\n");
        }

        comment = "\n\t/**\n"
                + "\t * Creates an instance given a config bean.\n"
                + "\t * \n"
                + "\t * @param configBean config bean\n"
                + "\t */\n";
        result.add(comment + "\t@Override\n"
                           + "\tprotected " + activatorImplTypeName + " createInstance(Object configBean) {\n"
                           + "\t\treturn new " + activatorImplTypeName.className() + "((" + configBeanType + ") configBean);\n"
                           + "\t}\n");

        if (!hasParent) {
            result.add("\t@Override\n"
                               + "\tpublic Optional<CB> configBean() {\n"
                               + "\t\treturn Optional.ofNullable((CB) configBean);\n"
                               + "\t}\n");
            result.add("\t@Override\n"
                               + "\tpublic Optional<" + Config.class.getName() + "> rawConfig() {\n"
                               + "\t\tif (configBean == null) {\n"
                               + "\t\t\treturn Optional.empty();\n"
                               + "\t\t}\n"
                               + "\t\treturn ((" + configBeanImplName + ") configBean).__config();\n"
                               + "\t}\n");
        }

        result.add("\t@Override\n"
                           + "\tpublic Class<?> configBeanType() {\n"
                           + "\t\treturn " + configBeanType + ".class;\n"
                           + "\t}\n");
        result.add("\t@Override\n"
                           + "\tpublic Map<String, Map<String, Object>> configBeanAttributes() {\n"
                           + "\t\treturn " + configBeanImplName + ".__metaAttributes();\n"
                           + "\t}\n");
        result.add("\t@Override\n"
                           + "\tpublic <R> void visitAttributes(CB configBean, " + AttributeVisitor.class.getName()
                           + "<Object> visitor, R userDefinedCtx) {\n"
                           + "\t\t" + AttributeVisitor.class.getName() + "<Object> beanVisitor = visitor::visit;\n"
                           + "\t\t((" + configBeanImplName + ") configBean).visitAttributes(beanVisitor, userDefinedCtx);\n"
                           + "\t}\n");
        result.add("\t@Override\n"
                           + "\tpublic CB toConfigBean(" + Config.class.getName() + " config) {\n"
                           + "\t\treturn (CB) " + configBeanImplName + "\n\t\t\t.toBuilder(config)\n\t\t\t.build();\n"
                           + "\t}\n");
        result.add("\t@Override\n"
                           + "\tpublic " + configBeanImplName + ".Builder "
                           + "toConfigBeanBuilder(" + Config.class.getName() + " config) {\n"
                           + "\t\treturn " + configBeanImplName + ".toBuilder(config);\n"
                           + "\t}\n");

        if (!hasParent) {
            result.add("\t@Override\n"
                               + "\tprotected CB acceptConfig(io.helidon.common.config.Config config) {\n"
                               + "\t\tthis.configBean = (CB) super.acceptConfig(config);\n"
                               + "\t\treturn (CB) this.configBean;\n"
                               + "\t}\n");
            result.add("\t@Override\n"
                               + "\tpublic String toConfigBeanInstanceId(CB configBean) {\n"
                               + "\t\treturn ((" + configBeanImplName
                               + ") configBean).__instanceId();\n"
                               + "\t}\n");
            result.add("\t@Override\n"
                               + "\tpublic void configBeanInstanceId(CB configBean, String val) {\n"
                               + "\t\t((" + configBeanImplName + ") configBean).__instanceId(val);\n"
                               + "\t}\n");
        }

        String overridesEnabledStr = configuredByAttributes.get(TAG_OVERRIDE_BEAN);
        if (Boolean.parseBoolean(overridesEnabledStr)) {
            String drivesActivationStr = configuredByAttributes.get(ConfigBeanInfo.TAG_DRIVES_ACTIVATION);
            result.add("\t@Override\n"
                       + "\tprotected boolean drivesActivation() {\n"
                       + "\t\treturn " + Boolean.parseBoolean(drivesActivationStr) + ";\n"
                       + "\t}\n");
        }

        return result;
    }

    TypeName toDefaultImpl(
            TypeName configBeanType) {
        return create(configBeanType.packageName(),
                              Builder.DEFAULT_IMPL_PREFIX + configBeanType.className() + Builder.DEFAULT_SUFFIX);
    }

}
