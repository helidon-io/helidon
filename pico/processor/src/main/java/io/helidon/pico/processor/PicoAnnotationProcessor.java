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

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
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
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;

import io.helidon.common.types.AnnotationAndValue;
import io.helidon.common.types.DefaultAnnotationAndValue;
import io.helidon.common.types.DefaultTypeInfo;
import io.helidon.common.types.DefaultTypeName;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementName;
import io.helidon.pico.api.Contract;
import io.helidon.pico.api.ElementInfo;
import io.helidon.pico.tools.ActivatorCreatorCodeGen;
import io.helidon.pico.tools.ActivatorCreatorRequest;
import io.helidon.pico.tools.ActivatorCreatorResponse;
import io.helidon.pico.tools.DefaultActivatorCreator;
import io.helidon.pico.tools.DefaultActivatorCreatorConfigOptions;
import io.helidon.pico.tools.Messager;
import io.helidon.pico.tools.Options;
import io.helidon.pico.tools.ServicesToProcess;
import io.helidon.pico.tools.ToolsException;
import io.helidon.pico.tools.TypeNames;
import io.helidon.pico.tools.TypeTools;

import jakarta.inject.Singleton;

import static io.helidon.builder.processor.tools.BuilderTypeTools.createTypeNameFromElement;
import static io.helidon.builder.processor.tools.BuilderTypeTools.createTypeNameFromMirror;
import static io.helidon.pico.processor.ProcessorUtils.rootStackTraceElementOf;
import static io.helidon.pico.tools.TypeTools.createAnnotationAndValueSet;
import static io.helidon.pico.tools.TypeTools.createTypedElementNameFromElement;

/**
 * An annotation processor that will find everything needing to be processed related to Pico conde generation.
 */
public class PicoAnnotationProcessor extends AbstractProcessor implements Messager {
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
        ServicesToProcess.addOnEndRunnable(ActivatorCreatorHandler.reporting());
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
////        // don't do filer until very end of the round
////        boolean isProcessingOver = roundEnv.processingOver();
//
////        ActivatorCreator creator = ActivatorCreatorProvider.instance();
////        CodeGenFiler filer = createCodeGenFiler();
////        Map<TypeName, InterceptionPlan> interceptionPlanMap = services.interceptorPlans();
////        if (!interceptionPlanMap.isEmpty()) {
////            GeneralCreatorRequest req = DefaultGeneralCreatorRequest.builder()
////                    .filer(filer);
////            creator.codegenInterceptors(req, interceptionPlanMap);
////            services.clearInterceptorPlans();
////        }
////

        ServicesToProcess services = toServicesToProcess(typesToCodeGenerate);
        ActivatorCreatorCodeGen codeGen = DefaultActivatorCreator.createActivatorCreatorCodeGen(services).orElse(null);
        if (codeGen == null) {
            return;
        }

        boolean isProcessingOver = roundEnv.processingOver();
        DefaultActivatorCreatorConfigOptions configOptions = DefaultActivatorCreatorConfigOptions.builder()
                .applicationPreCreated(Options.isOptionEnabled(Options.TAG_APPLICATION_PRE_CREATE))
                .moduleCreated(isProcessingOver)
                .build();
        ActivatorCreatorRequest req = DefaultActivatorCreator
                .createActivatorCreatorRequest(services, codeGen, configOptions, creator.filer(), false);
        ActivatorCreatorResponse res = creator.createModuleActivators(req);
        if (!res.success()) {
            ToolsException exc = new ToolsException("Error during codegen", res.error().orElse(null));
            error(exc.getMessage(), exc);
            // should not get here
            throw exc;
        }
    }

    ServicesToProcess toServicesToProcess(Map<TypeName, TypeInfo> typesToCodeGenerate) {
        ServicesToProcess services = ServicesToProcess.create();

        typesToCodeGenerate.forEach((serviceTypeName, service) -> {
            if (service.superTypeInfo().isPresent()) {
                services.addParentServiceType(serviceTypeName, service.superTypeInfo().get().typeName());
            }

            services.addAccessLevel(serviceTypeName,
                                    toAccess(service.modifierNames()));

            toScopeNames(service).forEach(it -> services.addScopeTypeName(serviceTypeName, it));

            gatherContractsIntoServicesToProcess(services, service);

        });

        return services;
    }

    void gatherContractsIntoServicesToProcess(ServicesToProcess services,
                         TypeInfo service) {
        Set<TypeName> providerForSet = new LinkedHashSet<>();
        Set<TypeName> contracts = toContracts(service, providerForSet);
        Set<TypeName> externalContracts = toExternalContracts(type, externalModuleNamesRequired);
        adjustContractsForExternals(contracts, externalContracts, externalModuleNamesRequired);

        Set<String> externalModuleNamesRequired = new LinkedHashSet<>();
    }

    Set<TypeName> toContracts(TypeInfo typeInfo,
                              Set<TypeName> providerForSet) {
        Set<TypeName> result = new LinkedHashSet<>();

        boolean autoAddInterfaces = Options.isOptionEnabled(Options.TAG_AUTO_ADD_NON_CONTRACT_INTERFACES);
        typeInfo.superTypeInfo().ifPresent(it -> gatherContracts(result, providerForSet, it, autoAddInterfaces));
        typeInfo.interfaceTypeInfo().forEach(it -> {
            if (autoAddInterfaces
                    || DefaultAnnotationAndValue.findFirst(Contract.class.getName(), it.annotations()).isPresent()) {
                result.add(it.typeName());
            }
            gatherContracts(result, providerForSet, it, autoAddInterfaces);
        });

//            // if we made it here then this provider qualifies, and we take what it provides into the result
//            TypeName teContractName = createTypeNameFromElement(teContract).orElseThrow();
//            result.add(teContractName);
//            if (isProviderType(teContractName.name())) {
//                result.add(DefaultTypeName.createFromTypeName(TypeNames.JAKARTA_PROVIDER));
//            }
//            providerForSet.add(gTypeName);
//        }

        return result;
    }

    void gatherContracts(Set<TypeName> result,
                         Set<TypeName> providerForSet,
                         TypeInfo typeInfo,
                         boolean autoAddInterfaces) {
        if (autoAddInterfaces && typeInfo.typeKind().equals(TypeInfo.KIND_INTERFACE)) {
            result.add(typeInfo.typeName());
            return;
        }


    }

    Map<TypeName, List<AnnotationAndValue>> toReferencedTypeNamesToAnnotations(Collection<AnnotationAndValue> annotations) {
        if (annotations.isEmpty()) {
            return Map.of();
        }

        Elements elements = processingEnv.getElementUtils();
        Map<TypeName, List<AnnotationAndValue>> result = new LinkedHashMap<>();
        annotations.stream()
                .filter(it -> !result.containsKey(it.typeName()))
                .forEach(it -> {
                    TypeElement typeElement = elements.getTypeElement(it.typeName().name());
                    if (typeElement != null) {
                        result.put(it.typeName(), new ArrayList<>(TypeTools.createAnnotationAndValueSet(typeElement)));
                    }
                });
        return result;
    }

    Set<String> toScopeNames(TypeInfo typeInfo) {
        Set<String> scopeAnnotations = new LinkedHashSet<>();
        typeInfo.referencedTypeNamesToAnnotations()
                .forEach((typeName, listOfAnnotations) -> {
                    if (listOfAnnotations.stream()
                            .map(it -> it.typeName().name())
                            .anyMatch(it -> it.equals(TypeNames.JAKARTA_SCOPE))) {
                        scopeAnnotations.add(typeName.name());
                    }
                });
        if (Options.isOptionEnabled(Options.TAG_MAP_APPLICATION_TO_SINGLETON_SCOPE)) {
            boolean hasApplicationScope = scopeAnnotations.stream()
                    .anyMatch(it -> it.equals(TypeNames.JAKARTA_APPLICATION_SCOPED)
                            || it.equals(TypeNames.JAVAX_APPLICATION_SCOPED));
            if (hasApplicationScope) {
                scopeAnnotations.add(Singleton.class.getName());
            }
        }
        return scopeAnnotations;
    }

    // TODO: put this into tools instead of here
    ElementInfo.Access toAccess(Set<String> modifierNames) {
        if (modifierNames.contains(TypeInfo.MODIFIER_PROTECTED)) {
            return ElementInfo.Access.PROTECTED;
        } else if (modifierNames.contains(TypeInfo.MODIFIER_PRIVATE)) {
            return ElementInfo.Access.PRIVATE;
        } else if (modifierNames.contains(TypeInfo.MODIFIER_PUBLIC)) {
            return ElementInfo.Access.PUBLIC;
        }
        return ElementInfo.Access.PACKAGE_PRIVATE;
    }

    // TODO: put this into tools instead of here
    Set<String> toModifierNames(Set<Modifier> modifiers) {
        return modifiers.stream()
                .map(Modifier::name)
                .collect(Collectors.toSet());
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
                        Optional<TypeInfo> superTypeInfo = toTypeInfo((TypeElement) it, typeName, elementsOfInterest);
                        superTypeInfo.ifPresent(typeInfo -> result.put(typeName, typeInfo));
                    }
                });
            }
        }
    }

    Optional<TypeInfo> toTypeInfo(TypeElement typeElement,
                                  TypeName typeName,
                                  Map<TypedElementName, TypeName> elementsOfInterest) {
        if (typeName.name().equals(Object.class.getName())) {
            return Optional.empty();
        }

        try {
            assert (processingEnv != null);
            Elements elementUtils = processingEnv.getElementUtils();
            Set<AnnotationAndValue> annotations = createAnnotationAndValueSet(elementUtils.getTypeElement(typeName.name()));
            DefaultTypeInfo.Builder builder = DefaultTypeInfo.builder()
                    .typeName(typeName)
                    .annotations(annotations)
                    .referencedTypeNamesToAnnotations(toReferencedTypeNamesToAnnotations(annotations))
                    .modifierNames(toModifierNames(typeElement.getModifiers()))
                    .elementInfo(
                            toElementsOfInterestInThisClass(typeName, elementsOfInterest));

            Optional<DefaultTypeName> superType = createTypeNameFromMirror(typeElement.getSuperclass());
            if (superType.isPresent()) {
                TypeElement superTypeElement = elementUtils.getTypeElement(superType.get().name());
                if (superTypeElement != null) {
                    Optional<TypeInfo> superTypeInfo = toTypeInfo(superTypeElement, superType.get(), elementsOfInterest);
                    superTypeInfo.ifPresent(builder::superTypeInfo);
                }
            }

            typeElement.getInterfaces().forEach(it -> {
                Optional<DefaultTypeName> interfaceType = createTypeNameFromMirror(it);
                if (interfaceType.isPresent()) {
                    TypeElement interfaceTypeElement = elementUtils.getTypeElement(interfaceType.get().name());
                    if (interfaceTypeElement != null) {
                        Optional<TypeInfo> superTypeInfo =
                                toTypeInfo(interfaceTypeElement, interfaceType.get(), elementsOfInterest);
                        superTypeInfo.ifPresent(builder::addInterfaceTypeInfo);
                    }
                }
            });

            return Optional.of(builder.build());
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

//    System.Logger logger() {
//        return logger;
//    }

    System.Logger.Level loggerLevel() {
        return (Options.isOptionEnabled(Options.TAG_DEBUG)) ? System.Logger.Level.INFO : System.Logger.Level.DEBUG;
    }

}
