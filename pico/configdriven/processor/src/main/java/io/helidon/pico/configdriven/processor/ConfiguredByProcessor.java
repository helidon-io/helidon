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

import io.helidon.builder.config.ConfigBean;
import io.helidon.builder.processor.tools.BuilderTypeTools;
import io.helidon.common.types.AnnotationAndValue;
import io.helidon.common.types.AnnotationAndValueDefault;
import io.helidon.common.types.TypeName;
import io.helidon.pico.configdriven.api.ConfiguredBy;
import io.helidon.pico.configdriven.runtime.AbstractConfiguredServiceProvider;
import io.helidon.pico.processor.ServiceAnnotationProcessor;
import io.helidon.pico.tools.ActivatorCreatorProvider;
import io.helidon.pico.tools.ServicesToProcess;
import io.helidon.pico.tools.ToolsException;
import io.helidon.pico.tools.TypeTools;

import static io.helidon.builder.processor.tools.BuilderTypeTools.createTypeNameFromElement;
import static io.helidon.builder.processor.tools.BuilderTypeTools.extractValues;
import static io.helidon.builder.processor.tools.BuilderTypeTools.findAnnotationMirror;
import static io.helidon.builder.processor.tools.BuilderTypeTools.toTypeElement;
import static io.helidon.common.types.TypeNameDefault.create;
import static io.helidon.common.types.TypeNameDefault.createFromGenericDeclaration;
import static io.helidon.common.types.TypeNameDefault.createFromTypeName;
import static io.helidon.common.types.TypeNameDefault.toBuilder;
import static io.helidon.pico.configdriven.processor.ConfiguredByProcessorUtils.createExtraActivatorClassComments;
import static io.helidon.pico.configdriven.processor.ConfiguredByProcessorUtils.createExtraCodeGen;

/**
 * Processor for @{@link io.helidon.pico.configdriven.api.ConfiguredBy} type annotations.
 */
// NOTE: Scheduled for destruction
public class ConfiguredByProcessor extends ServiceAnnotationProcessor {
    private final System.Logger logger = System.getLogger(getClass().getName());
    private final LinkedHashSet<Element> elementsProcessed = new LinkedHashSet<>();

    /**
     * Service loader based constructor.
     *
     * @deprecated this is a Java ServiceLoader implementation and the constructor should not be used directly
     */
    @Deprecated
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
    public boolean process(Set<? extends TypeElement> annotations,
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

        return false;
    }

    void process(Element element) {
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

        if (hasParent && !isConfiguredService(parent)) {
            // we treat this as a regular configured service, since its parent is NOT a configured service
            hasParent = false;
            parentServiceTypeName = null;
        }

        if (hasParent) {
            // we already know our parent, but we need to morph it with our activator and new CB reference
            TypeName parentActivatorImplTypeName = ActivatorCreatorProvider.instance()
                    .toActivatorImplTypeName(parentServiceTypeName);
            parentServiceTypeName = toBuilder(parentActivatorImplTypeName)
                    .typeArguments(List.of(genericCB))
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

        List<String> extraActivatorClassComments = createExtraActivatorClassComments();
        extraActivatorClassComments.forEach(fn -> servicesToProcess.addExtraActivatorClassComments(serviceTypeName, fn));

        processServiceType(serviceTypeName, (TypeElement) element);
    }

    boolean isConfiguredService(TypeElement te) {
        Set<AnnotationAndValue> annotations = TypeTools.createAnnotationAndValueSet(te);
        Optional<? extends AnnotationAndValue> configuredByAnno =
                AnnotationAndValueDefault.findFirst(ConfiguredBy.class, annotations);
        return configuredByAnno.isPresent();
    }

    void validate(TypeElement element,
                  TypeName configBeanType,
                  TypeName serviceTypeName,
                  TypeName parentServiceTypeName) {
        assertNoAnnotation(create(jakarta.inject.Singleton.class), element);
        validateBeanType(configBeanType);
        validateServiceType(serviceTypeName, parentServiceTypeName);
    }

    void assertNoAnnotation(TypeName annoType,
                            TypeElement element) {
        Set<AnnotationAndValue> annos = TypeTools.createAnnotationAndValueSet(element);
        Optional<? extends AnnotationAndValue> anno = AnnotationAndValueDefault.findFirst(annoType, annos);
        if (anno.isPresent()) {
            throw new IllegalStateException(annoType + " cannot be used in conjunction with "
                                                    + ConfiguredBy.class + " on " + element);
        }
    }

    void validateBeanType(TypeName configBeanType) {
        TypeElement typeElement = (configBeanType == null)
                ? null : processingEnv.getElementUtils().getTypeElement(configBeanType.name());
        if (typeElement == null) {
            throw new ToolsException("Unknown config bean type: " + configBeanType);
        }

        if (typeElement.getKind() != ElementKind.INTERFACE) {
            throw new ToolsException("The config bean must be an interface: " + typeElement);
        }

        Optional<? extends AnnotationMirror> cfgBean = BuilderTypeTools
                .findAnnotationMirror(ConfigBean.class.getName(), typeElement.getAnnotationMirrors());
        if (cfgBean.isEmpty()) {
            throw new ToolsException("The config bean must be annotated with @" + ConfigBean.class.getSimpleName()
                                             + ": " + configBeanType);
        }
    }

    void validateServiceType(TypeName serviceTypeName,
                             TypeName ignoredParentServiceTypeName) {
        TypeElement typeElement = (serviceTypeName == null)
                ? null : processingEnv.getElementUtils().getTypeElement(serviceTypeName.name());
        if (typeElement == null) {
            throw new ToolsException("Unknown service type: " + serviceTypeName);
        }

        if (typeElement.getKind() != ElementKind.CLASS) {
            throw new ToolsException("The configured service must be a concrete class: " + typeElement);
        }
    }

}
