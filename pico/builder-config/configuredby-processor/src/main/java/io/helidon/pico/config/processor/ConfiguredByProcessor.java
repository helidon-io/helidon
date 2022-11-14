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

package io.helidon.pico.config.processor;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;

import io.helidon.config.Config;
import io.helidon.pico.builder.Builder;
import io.helidon.pico.builder.processor.tools.BuilderTypeTools;
import io.helidon.pico.config.api.ConfigBean;
import io.helidon.pico.config.api.ConfiguredBy;
import io.helidon.pico.config.services.AbstractConfiguredServiceProvider;
import io.helidon.pico.config.spi.ConfigBeanAttributeVisitor;
import io.helidon.pico.config.spi.ConfigResolver;
import io.helidon.pico.config.spi.ConfigResolverProvider;
import io.helidon.pico.processor.ServiceAnnotationProcessor;
import io.helidon.pico.types.AnnotationAndValue;
import io.helidon.pico.types.DefaultAnnotationAndValue;
import io.helidon.pico.types.DefaultTypeName;
import io.helidon.pico.types.TypeName;
import io.helidon.pico.tools.ToolsException;
import io.helidon.pico.tools.creator.ActivatorCreatorProvider;
import io.helidon.pico.tools.processor.ServicesToProcess;
import io.helidon.pico.tools.processor.TypeTools;

import static io.helidon.pico.builder.processor.tools.BuilderTypeTools.createTypeNameFromElement;

public class ConfiguredByProcessor extends ServiceAnnotationProcessor {

    private final System.Logger logger = System.getLogger(getClass().getName());

    private final LinkedHashSet<Element> elementsProcessed = new LinkedHashSet<>();

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> supported = new LinkedHashSet<>(super.getSupportedAnnotationTypes());
        supported.add(ConfiguredBy.class.getName());
        return supported;
    }

    @Override
    public Set<String> getContraAnnotations() {
        return Collections.emptySet();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
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

        // we claim this annotation!
        return true;
    }

    protected void process(Element element) {
        if (!(element instanceof TypeElement)) {
            throw new ToolsException("Expected " + element + " to be processed as a TypeElement");
        }

        AnnotationMirror am = BuilderTypeTools.findAnnotationMirror(ConfiguredBy.class.getName(), element.getAnnotationMirrors());
        Map<String, String> vals = BuilderTypeTools.extractValues(am, processingEnv.getElementUtils());
        TypeName configBeanType = DefaultTypeName.createFromTypeName(vals.get("value"));

        TypeName serviceTypeName = BuilderTypeTools.createTypeNameFromElement(element);
        DefaultTypeName parentServiceTypeName = createTypeNameFromElement(
                TypeTools.toTypeElement(((TypeElement) element).getSuperclass()));

        DefaultTypeName activatorImplTypeName = (DefaultTypeName)
                ActivatorCreatorProvider.getInstance().toActivatorImplTypeName(serviceTypeName);

        TypeName genericCB = DefaultTypeName.createFromGenericDeclaration("CB");
        TypeName genericExtendsCB = DefaultTypeName.createFromGenericDeclaration(
                "CB extends " + configBeanType.name());

        boolean hasParent = Objects.nonNull(parentServiceTypeName);
        if (hasParent) {
            // we already know our parent, but we need to morph it with our activator and new CB reference
            DefaultTypeName parentActivatorImplTypeName = (DefaultTypeName)
                    ActivatorCreatorProvider.getInstance().toActivatorImplTypeName(parentServiceTypeName);
            parentServiceTypeName = parentActivatorImplTypeName.toBuilder()
                    .typeArguments(Collections.singletonList(genericCB))
                    .build();
        } else {
            List<TypeName> typeArgs = List.of(serviceTypeName, genericCB);
            parentServiceTypeName = DefaultTypeName.create(AbstractConfiguredServiceProvider.class)
                    .toBuilder()
                    .typeArguments(typeArgs)
                    .build();
        }

        //        processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, getClass().getSimpleName() + ": "
        //                                                 + serviceTypeName + " locked parent = " + parentServiceTypeName);

        validate((TypeElement) element, configBeanType, serviceTypeName, parentServiceTypeName);

        List<String> extraCodeGen = createExtraCodeGen(activatorImplTypeName, configBeanType, hasParent);

        ServicesToProcess servicesToProcess = ServicesToProcess.getServicesInstance();
        boolean accepted = servicesToProcess.setParentServiceType(serviceTypeName, parentServiceTypeName, true);
        assert (accepted);
        servicesToProcess.setActivatorGenericDecl(serviceTypeName,"<" + genericExtendsCB.fqName() + ">");
        extraCodeGen.forEach(fn -> servicesToProcess.addExtraCodeGen(serviceTypeName, fn));

        processServiceType(serviceTypeName, (TypeElement) element);
    }

    protected void validate(TypeElement element, TypeName configBeanType, TypeName serviceTypeName, TypeName parentServiceTypeName) {
        assertNoAnnotation(DefaultTypeName.create(jakarta.inject.Singleton.class), element);
        validateBeanType(configBeanType);
        validateServiceType(serviceTypeName, parentServiceTypeName);
    }

    protected void assertNoAnnotation(TypeName annoType, TypeElement element) {
        Set<AnnotationAndValue> annos = TypeTools.createAnnotationAndValueSet(element);
        Optional<? extends AnnotationAndValue> anno = DefaultAnnotationAndValue.findFirst(annoType, annos);
        if (anno.isPresent()) {
            throw new IllegalStateException(annoType + " cannot be used in conjunction with " + ConfiguredBy.class
                                                    + " on " + element);
        }
    }
    protected void validateBeanType(TypeName configBeanType) {
        TypeElement typeElement = Objects.isNull(configBeanType)
                ? null : processingEnv.getElementUtils().getTypeElement(configBeanType.name());
        if (Objects.isNull(typeElement)) {
            throw new ToolsException("unknown type: " + configBeanType);
        }

        if (typeElement.getKind() != ElementKind.INTERFACE) {
            throw new ToolsException("The config bean must be an interface: " + typeElement);
        }

        ConfigBean cfgBean = typeElement.getAnnotation(ConfigBean.class);
        if (Objects.isNull(cfgBean)) {
            throw new ToolsException("The config bean must be annotated with @" + ConfigBean.class.getSimpleName()
                                             + ": " + configBeanType);
        }
    }

    protected void validateServiceType(TypeName serviceTypeName, TypeName ignoredParentServiceTypeName) {
        TypeElement typeElement = Objects.isNull(serviceTypeName)
                ? null : processingEnv.getElementUtils().getTypeElement(serviceTypeName.name());
        if (Objects.isNull(typeElement)) {
            throw new ToolsException("unknown type: " + serviceTypeName);
        }

        if (typeElement.getKind() != ElementKind.CLASS) {
            throw new ToolsException("The configured service must be a concrete class: " + typeElement);
        }
    }

    protected List<String> createExtraCodeGen(TypeName activatorImplTypeName,
                                              TypeName configBeanType,
                                              boolean hasParent) {
        List<String> result = new LinkedList<>();
        TypeName configBeanImplName = toDefaultImpl(configBeanType);

        if (hasParent) {
            result.add("\n\tprotected " + activatorImplTypeName.className() + "(" + configBeanType + " configBean) {\n"
                               + "\t\tsuper(configBean);\n"
                               + "\t\tsetServiceInfo(serviceInfo);\n"
                               + "\t}\n");
        } else {
            result.add("\n\tprotected " + configBeanType + " configBean;\n"
                               + "\n\tprotected " + activatorImplTypeName.className() + "(" + configBeanType + " configBean) {\n"
                               + "\t\tthis.configBean = Objects.requireNonNull(configBean);\n"
                               + "\t\tassertIsRootProvider(false, true);\n"
                               + "\t\tsetServiceInfo(serviceInfo);\n"
                               + "\t}\n");
        }

        result.add("\t@Override\n"
                           + "\tprotected " + activatorImplTypeName + " createInstance(Object configBean) {\n"
                           + "\t\treturn new " + activatorImplTypeName.className() + "((" + configBeanType + ") configBean);\n"
                           + "\t}\n");

        if (!hasParent) {
            result.add("\t@Override\n"
                               + "\tpublic CB getConfigBean() {\n"
                               + "\t\treturn (CB) configBean;\n"
                               + "\t}\n");
            result.add("\t@Override\n"
                               + "\tpublic java.util.Optional<" + Config.class.getName() + "> getRawConfig() {\n"
                               + "\t\tif (Objects.isNull(configBean)) {\n"
                               + "\t\t\treturn Optional.empty();\n"
                               + "\t\t}\n"
                               + "\t\treturn ((" + configBeanImplName + ") configBean).__config();\n"
                               + "\t}\n");
        }

        result.add("\t@Override\n"
                           + "\tpublic Class<?> getConfigBeanType() {\n"
                           + "\t\treturn " + configBeanType + ".class;\n"
                           + "\t}\n");
        result.add("\t@Override\n"
                           + "\tpublic Map<String, Map<String, Object>> getConfigBeanAttributes() {\n"
                           + "\t\treturn " + configBeanImplName + ".__getMetaAttributes();\n"
                           + "\t}\n");
        result.add("\t@Override\n"
                           + "\tpublic <R> void visitAttributes(CB configBean, " + ConfigBeanAttributeVisitor.class.getName()
                           + " visitor, R userDefinedCtx) {\n"
                           + "\t\t" + configBeanImplName + ".AttributeVisitor beanVisitor = visitor::visit;\n"
                           + "\t\t((" + configBeanImplName + ") configBean).visitAttributes(beanVisitor, userDefinedCtx);\n"
                           + "\t}\n");
        result.add("\t@Override\n"
                           + "\tpublic CB toConfigBean(" + Config.class.getName() + " config) {\n"
                           + "\t\treturn toConfigBean(config, "
                           + ConfigResolverProvider.class.getName() + ".getInstance());\n"
                           + "\t}\n");
        result.add("\t@Override\n"
                           + "\tpublic CB toConfigBean(" + Config.class.getName() + " cfg, "
                           + ConfigResolver.class.getName() + " resolver) {\n"
                           + "\t\treturn (CB) " + configBeanImplName + "\n\t\t\t.toBuilder(cfg, resolver)\n\t\t\t.build();\n"
                           + "\t}\n");
        result.add("\t@Override\n"
                           + "\tpublic void resolveFrom(" + Config.class.getName() + " config, "
                           + ConfigResolver.class.getName() + " resolver) {\n"
                           + "\t\t((" + configBeanImplName + ") configBean)"
                           + ".__copyFrom(config, resolver);\n"
                           + "\t}\n");

        if (!hasParent) {
            result.add("\t@Override\n"
                               + "\tpublic String getConfigBeanInstanceId(CB configBean) {\n"
                               + "\t\treturn ((" + configBeanImplName
                               + ") configBean).__instanceId();\n"
                               + "\t}\n");
            result.add("\t@Override\n"
                               + "\tpublic void setConfigBeanInstanceId(CB configBean, String val) {\n"
                               + "\t\t((" + configBeanImplName + ") configBean).__overrideInstanceId(val);\n"
                               + "\t}\n");
        }
        return result;
    }

    protected TypeName toDefaultImpl(TypeName configBeanType) {
        return DefaultTypeName.create(configBeanType.packageName(),
                              Builder.DEFAULT_IMPL_PREFIX + configBeanType.className() + Builder.DEFAULT_SUFFIX);
    }

}
