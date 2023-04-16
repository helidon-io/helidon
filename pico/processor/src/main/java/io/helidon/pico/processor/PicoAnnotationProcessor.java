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

package io.helidon.pico.processor;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;

import io.helidon.common.types.DefaultTypeInfo;
import io.helidon.common.types.DefaultTypeName;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementName;
import io.helidon.pico.tools.ActivatorCreatorCodeGen;
import io.helidon.pico.tools.ActivatorCreatorRequest;
import io.helidon.pico.tools.ActivatorCreatorResponse;
import io.helidon.pico.tools.CodeGenFiler;
import io.helidon.pico.tools.DefaultActivatorCreatorCodeGen;
import io.helidon.pico.tools.DefaultActivatorCreatorConfigOptions;
import io.helidon.pico.tools.DefaultActivatorCreatorRequest;
import io.helidon.pico.tools.Messager;
import io.helidon.pico.tools.Options;
import io.helidon.pico.tools.ServicesToProcess;
import io.helidon.pico.tools.ToolsException;
import io.helidon.pico.tools.TypeNames;

import static io.helidon.builder.processor.tools.BuilderTypeTools.createTypeNameFromElement;
import static io.helidon.builder.processor.tools.BuilderTypeTools.createTypeNameFromMirror;
import static io.helidon.pico.processor.ProcessorUtils.rootStackTraceElementOf;
import static io.helidon.pico.tools.TypeTools.createAnnotationAndValueSet;
import static io.helidon.pico.tools.TypeTools.createTypedElementNameFromElement;

/**
 * An annotation processor that will find everything needing to be processed related to Pico conde generation.
 */
public class PicoAnnotationProcessor extends AbstractProcessor implements Messager {
    private static final boolean FAIL_ON_ERROR = true;
    private static final Set<String> SUPPORTED_SERVICE_CLASS_TARGET_ANNOTATIONS = Set.of(
            TypeNames.JAKARTA_SINGLETON,
            TypeNames.JAVAX_SINGLETON,
            TypeNames.JAKARTA_APPLICATION_SCOPED,
            TypeNames.JAVAX_APPLICATION_SCOPED,
            TypeNames.PICO_EXTERNAL_CONTRACTS,
            TypeNames.PICO_INTERCEPTED);

    private static final Set<String> SUPPORTED_CONTRACT_CLASS_TARGET_ANNOTATIONS = Set.of(
            TypeNames.PICO_CONTRACT);

    private static final Set<String> SUPPORTED_ELEMENT_TARGET_ANNOTATIONS = Set.of(
            TypeNames.JAKARTA_INJECT,
            TypeNames.JAVAX_INJECT,
            TypeNames.JAKARTA_PRE_DESTROY,
            TypeNames.JAKARTA_POST_CONSTRUCT,
            TypeNames.JAVAX_PRE_DESTROY,
            TypeNames.JAVAX_POST_CONSTRUCT);

    private final System.Logger logger = System.getLogger(getClass().getName());
    private final ConcurrentHashMap<TypeName, TypeInfo> typeInfoToCreateActivatorsForInThisModule = new ConcurrentHashMap<>();
    private ActivatorCreatorHandler creator;
    private RoundEnvironment roundEnv;

    /**
     * Service loader based constructor.
     *
     * @deprecated this is a Java ServiceLoader implementation and the constructor should not be used directly
     */
    @Deprecated
    public PicoAnnotationProcessor() {
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Stream.of(supportedServiceClassTargetAnnotations(),
                         supportedContractClassTargetAnnotations(),
                         supportedElementTargetAnnotations())
                .flatMap(Set::stream)
                .collect(Collectors.toSet());
    }

    @Override
    public void init(ProcessingEnvironment processingEnv) {
        Options.init(processingEnv);
        debug("*** Processor " + getClass().getSimpleName() + " initialized ***");
        this.processingEnv = processingEnv;
        this.creator = new ActivatorCreatorHandler(getClass().getSimpleName(), processingEnv, this);
        creator.activateSimulationMode();
        super.init(processingEnv);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations,
                           RoundEnvironment roundEnv) {
        this.roundEnv = roundEnv;

        ServicesToProcess.onBeginProcessing(this, getSupportedAnnotationTypes(), roundEnv);
        try {
            if (!roundEnv.processingOver()) {
                // build the model
                Map<TypedElementName, TypeName> elementsOfInterest = new ConcurrentHashMap<>();
                gatherElementsOfInterestInThisModule(elementsOfInterest);
                gatherTypeInfosToProcessInThisModule(typeInfoToCreateActivatorsForInThisModule, elementsOfInterest);

                // validate the model
                validate(typeInfoToCreateActivatorsForInThisModule.values());

                // code generate the model
                doFiler(Map.copyOf(typeInfoToCreateActivatorsForInThisModule));
            }

            return false;
        } catch (Throwable t) {
            ToolsException exc = new ToolsException("Error during processing: " + t
                                                            + " @ " + rootStackTraceElementOf(t)
                                                            + " in " + getClass().getSimpleName(), t);
            error(exc.getMessage(), t);
            // we typically will not even get to this next line since the messager.error() call above will trigger things to halt
            throw exc;
        } finally {
            ServicesToProcess.onEndProcessing(this, getSupportedAnnotationTypes(), roundEnv);
            this.roundEnv = null;
        }
    }

    @Override
    public void debug(String message,
                      Throwable t) {
        if (Options.isOptionEnabled(Options.TAG_DEBUG)) {
            if (logger.isLoggable(loggerLevel())) {
                logger.log(loggerLevel(), getClass().getSimpleName() + ": Debug: " + message, t);
            }
        }

        if (processingEnv != null && processingEnv.getMessager() != null) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.OTHER, message);
        }
    }

    @Override
    public void debug(String message) {
        if (Options.isOptionEnabled(Options.TAG_DEBUG)) {
            if (logger.isLoggable(loggerLevel())) {
                logger.log(loggerLevel(), getClass().getSimpleName() + ": Debug: " + message);
            }
        }

        if (processingEnv != null && processingEnv.getMessager() != null) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.OTHER, message);
        }
    }

    @Override
    public void log(String message) {
        if (processingEnv != null && processingEnv.getMessager() != null) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, message);
        }
    }

    @Override
    public void warn(String message,
                     Throwable t) {
        if (Options.isOptionEnabled(Options.TAG_DEBUG) && t != null) {
            logger.log(System.Logger.Level.WARNING, getClass().getSimpleName() + ": Warning: " + message, t);
            t.printStackTrace();
        }

        if (processingEnv != null && processingEnv.getMessager() != null) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING, message);
        }
    }

    @Override
    public void warn(String message) {
        if (Options.isOptionEnabled(Options.TAG_DEBUG)) {
            logger.log(System.Logger.Level.WARNING, getClass().getSimpleName() + ": Warning: " + message);
        }

        if (processingEnv != null && processingEnv.getMessager() != null) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING, message);
        }
    }

    @Override
    public void error(String message,
                      Throwable t) {
        logger.log(System.Logger.Level.ERROR, getClass().getSimpleName() + ": Error: " + message, t);
        if (processingEnv != null && processingEnv.getMessager() != null) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, message);
        }
    }

    /**
     * The annotation types we handle that will trigger activator creation.
     *
     * @return annotation types we handle on services
     */
    protected Set<String> supportedServiceClassTargetAnnotations() {
        return SUPPORTED_SERVICE_CLASS_TARGET_ANNOTATIONS;
    }

    /**
     * The annotation types we handle that will be advertised as contracts.
     *
     * @return annotation types we handle on contracts
     */
    protected Set<String> supportedContractClassTargetAnnotations() {
        return SUPPORTED_CONTRACT_CLASS_TARGET_ANNOTATIONS;
    }

    /**
     * The annotation types we expect to see on method and field type elements.
     *
     * @return annotation types we handle on elements
     */
    protected Set<String> supportedElementTargetAnnotations() {
        return SUPPORTED_ELEMENT_TARGET_ANNOTATIONS;
    }

    /**
     * Code generate these {@link io.helidon.pico.api.Activator}'s ad {@link io.helidon.pico.api.Module}'s.
     *
     * @param typesToCodeGenerate the types to code generate
     */
    protected void doFiler(Map<TypeName, TypeInfo> typesToCodeGenerate) {
//        // don't do filer until very end of the round
//        boolean isProcessingOver = roundEnv.processingOver();

//        ActivatorCreator creator = ActivatorCreatorProvider.instance();
//        CodeGenFiler filer = createCodeGenFiler();
//        Map<TypeName, InterceptionPlan> interceptionPlanMap = services.interceptorPlans();
//        if (!interceptionPlanMap.isEmpty()) {
//            GeneralCreatorRequest req = DefaultGeneralCreatorRequest.builder()
//                    .filer(filer);
//            creator.codegenInterceptors(req, interceptionPlanMap);
//            services.clearInterceptorPlans();
//        }
//
//        if (!isProcessingOver) {
////            return MAYBE_ANNOTATIONS_CLAIMED_BY_THIS_PROCESSOR;
//            return;
//        }

        Optional<ActivatorCreatorCodeGen> codeGen = toActivatorCreatorCodeGen(typesToCodeGenerate);
        if (codeGen.isPresent()) {
            DefaultActivatorCreatorConfigOptions configOptions = DefaultActivatorCreatorConfigOptions.builder()
                    .applicationPreCreated(Options.isOptionEnabled(Options.TAG_APPLICATION_PRE_CREATE))
                    .moduleCreated(/*isProcessingOver*/ true)
                    .build();
            ActivatorCreatorRequest req =
                    toActivatorCreatorRequest(typesToCodeGenerate, codeGen.get(), configOptions, creator.filer());
            ActivatorCreatorResponse res = creator.createModuleActivators(req);
            if (!res.success()) {
                ToolsException exc = new ToolsException("Error during codegen", res.error().orElse(null));
                if (FAIL_ON_ERROR) {
                    error(exc.getMessage(), exc);
                    // should not get here
                    throw exc;
                } else {
                    warn(exc.getMessage(), exc);
                }
            }
        }
    }

    protected ActivatorCreatorRequest toActivatorCreatorRequest(Map<TypeName, TypeInfo> typesToCodeGenerate,
                                                                ActivatorCreatorCodeGen codeGen,
                                                                DefaultActivatorCreatorConfigOptions configOptions,
                                                                CodeGenFiler filer) {
        return DefaultActivatorCreatorRequest.builder()
                .codeGen(codeGen)
                .configOptions(configOptions)
                .filer(filer)
                .serviceTypeNames(toServiceTypeNames(typesToCodeGenerate))
                .build();
    }

    protected Optional<ActivatorCreatorCodeGen> toActivatorCreatorCodeGen(Map<TypeName, TypeInfo> typesToCodeGenerate) {
        if (typesToCodeGenerate.isEmpty()) {
            return Optional.empty();
        }

        DefaultActivatorCreatorCodeGen.Builder builder = DefaultActivatorCreatorCodeGen.builder();
//        builder.serviceTypeToParentServiceTypes(serviceTypeToParentServiceTypes(typesToCodeGenerate));
//        builder.serviceTypeHierarchy(serviceTypeHierarchy(typesToCodeGenerate));
//        builder.

        return Optional.of(builder.build());
    }

    Collection<TypeName> toServiceTypeNames(Map<TypeName, TypeInfo> typesToCodeGenerate) {
        return typesToCodeGenerate.keySet();
    }

    void gatherElementsOfInterestInThisModule(Map<TypedElementName, TypeName> result) {
        Elements elementUtils = processingEnv.getElementUtils();
        for (String annoType : supportedElementTargetAnnotations()) {
            // annotation may not be on the classpath, in such a case just ignore it
            TypeElement annoTypeElement = elementUtils.getTypeElement(annoType);
            if (annoTypeElement != null) {
                Set<? extends Element> typesToProcess = roundEnv.getElementsAnnotatedWith(annoTypeElement);
                typesToProcess.forEach(it -> {
                            TypeName enclosingClassElement = createTypeNameFromElement(it.getEnclosingElement()).orElseThrow();
                            TypedElementName element = createTypedElementNameFromElement(it, elementUtils);
                            result.put(element, enclosingClassElement);
                        });
            }
        }
    }

    void gatherTypeInfosToProcessInThisModule(Map<TypeName, TypeInfo> result,
                                              Map<TypedElementName, TypeName> elementsOfInterest) {
        for (String annoType : supportedServiceClassTargetAnnotations()) {
            // annotation may not be on the classpath, in such a case just ignore it
            TypeElement annoTypeElement = processingEnv.getElementUtils().getTypeElement(annoType);
            if (annoTypeElement != null) {
                Set<? extends Element> typesToProcess = roundEnv.getElementsAnnotatedWith(annoTypeElement);
                typesToProcess.forEach(it -> {
                    TypeName typeName = createTypeNameFromElement(it).orElseThrow();
                    if (!result.containsKey(typeName)) {
                        result.put(typeName,
                                   toTypeInfo((TypeElement) it, typeName, elementsOfInterest));
                    }
                });
            }
        }
    }

    TypeInfo toTypeInfo(TypeElement typeElement,
                        TypeName typeName,
                        Map<TypedElementName, TypeName> elementsOfInterest) {
        try {
            assert (processingEnv != null);
            Elements elementUtils = processingEnv.getElementUtils();
            DefaultTypeInfo.Builder builder = DefaultTypeInfo.builder()
                    .typeName(typeName)
                    .annotations(createAnnotationAndValueSet(elementUtils.getTypeElement(typeName.name())))
                    .elementInfo(
                            toElementsOfInterestInThisClass(typeName, elementsOfInterest));

            Optional<DefaultTypeName> superType = createTypeNameFromMirror(typeElement.getSuperclass());
            if (superType.isPresent()) {
                TypeElement superTypeElement = elementUtils.getTypeElement(superType.get().name());
                if (superTypeElement != null) {
                    builder.superTypeInfo(
                            toTypeInfo(superTypeElement, superType.get(), elementsOfInterest));
                }
            }

            typeElement.getInterfaces().forEach(it -> {
                Optional<DefaultTypeName> interfaceType = createTypeNameFromMirror(it);
                if (interfaceType.isPresent()) {
                    TypeElement interfaceTypeElement = elementUtils.getTypeElement(interfaceType.get().name());
                    if (interfaceTypeElement != null) {
                        builder.addInterfaceTypeInfo(
                                toTypeInfo(interfaceTypeElement, interfaceType.get(), elementsOfInterest));
                    }
                }
            });

            return builder.build();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to process: " + typeElement, e);
        }
    }

    Collection<TypedElementName> toElementsOfInterestInThisClass(TypeName typeName,
                                                                 Map<TypedElementName, TypeName> allElementsOfInterest) {
        return allElementsOfInterest.entrySet().stream()
                .filter(e -> typeName.equals(e.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    void validate(Collection<TypeInfo> typesToCreateActivatorsFor) {
        // TODO:
    }

    System.Logger logger() {
        return logger;
    }

    System.Logger.Level loggerLevel() {
        return (Options.isOptionEnabled(Options.TAG_DEBUG)) ? System.Logger.Level.INFO : System.Logger.Level.DEBUG;
    }

}
