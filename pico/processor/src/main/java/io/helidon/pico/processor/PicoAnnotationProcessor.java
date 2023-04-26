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
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
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

import io.helidon.common.Weight;
import io.helidon.common.types.AnnotationAndValue;
import io.helidon.common.types.DefaultAnnotationAndValue;
import io.helidon.common.types.DefaultTypeInfo;
import io.helidon.common.types.DefaultTypeName;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementName;
import io.helidon.pico.api.Contract;
import io.helidon.pico.api.DefaultQualifierAndValue;
import io.helidon.pico.api.ElementInfo;
import io.helidon.pico.api.ExternalContracts;
import io.helidon.pico.api.QualifierAndValue;
import io.helidon.pico.api.RunLevel;
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

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Provider;
import jakarta.inject.Qualifier;
import jakarta.inject.Singleton;

import static io.helidon.builder.processor.tools.BeanUtils.isBuiltInJavaType;
import static io.helidon.builder.processor.tools.BuilderTypeTools.createTypeNameFromElement;
import static io.helidon.builder.processor.tools.BuilderTypeTools.createTypeNameFromMirror;
import static io.helidon.pico.processor.ProcessorUtils.hasValue;
import static io.helidon.pico.processor.ProcessorUtils.isInThisModule;
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
                List<TypedElementName> elementsOfInterest = new ArrayList<>();
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
            logger.log(loggerLevel(), "processing: " + serviceTypeName);
            try {
                process(services, typesToCodeGenerate.keySet(), serviceTypeName, service);
            } catch (Throwable t) {
                throw new ToolsException("Error processing: " + serviceTypeName, t);
            }
        });
        return services;
    }

    void process(ServicesToProcess services,
                 Set<TypeName> serviceTypeNamesToCodeGenerate,
                 TypeName serviceTypeName,
                 TypeInfo service) {
        TypeInfo superTypeInfo = service.superTypeInfo().orElse(null);
        if (superTypeInfo != null) {
            TypeName superTypeName = superTypeInfo.typeName();
            services.addParentServiceType(serviceTypeName, superTypeName);
        }

        toRunLevel(service).ifPresent(it -> services.addDeclaredRunLevel(serviceTypeName, it));
        toWeight(service).ifPresent(it -> services.addDeclaredWeight(serviceTypeName, it));
        toScopeNames(service).forEach(it -> services.addScopeTypeName(serviceTypeName, it));
        toPostConstructMethod(service).ifPresent(it -> services.addPostConstructMethod(serviceTypeName, it));
        toPreDestroyMethod(service).ifPresent(it -> services.addPreDestroyMethod(serviceTypeName, it));
        services.addAccessLevel(serviceTypeName,
                                toAccess(service.modifierNames()));
        services.addIsAbstract(serviceTypeName,
                               service.modifierNames().contains(TypeInfo.MODIFIER_ABSTRACT));
        services.addServiceTypeHierarchy(serviceTypeName,
                                         toServiceTypeHierarchy(service));
        services.addQualifiers(serviceTypeName,
                               toQualifiers(service));
        gatherContractsIntoServicesToProcess(services, serviceTypeName, service, serviceTypeNamesToCodeGenerate);
    }

    void gatherContractsIntoServicesToProcess(ServicesToProcess services,
                                              TypeName serviceTypeName,
                                              TypeInfo service,
                                              Set<TypeName> serviceTypeNamesToCodeGenerate) {
        Set<TypeName> contracts = new LinkedHashSet<>();
        Set<TypeName> externalContracts = new LinkedHashSet<>();
        Set<TypeName> providerForSet = new LinkedHashSet<>();
        Set<String> externalModuleNames = new LinkedHashSet<>();

        boolean autoAddInterfaces = Options.isOptionEnabled(Options.TAG_AUTO_ADD_NON_CONTRACT_INTERFACES);
        gatherContracts(contracts,
                        externalContracts,
                        providerForSet,
                        externalModuleNames,
                        service,
                        serviceTypeNamesToCodeGenerate,
                        autoAddInterfaces,
                        false);

        contracts.forEach(it -> services.addTypeForContract(serviceTypeName, it, false));
        externalContracts.forEach(it -> services.addTypeForContract(serviceTypeName, it, true));
        services.addProviderFor(serviceTypeName, providerForSet);
        services.addExternalRequiredModules(serviceTypeName, externalModuleNames);
    }

    static void gatherContracts(Set<TypeName> contracts,
                                Set<TypeName> externalContracts,
                                Set<TypeName> providerForSet,
                                Set<String> externalModuleNamesRequired,
                                TypeInfo typeInfo,
                                Set<TypeName> serviceTypeNamesToCodeGenerate,
                                boolean autoAddInterfaces,
                                boolean thisTypeQualifiesAsContract) {
        TypeName typeName = typeInfo.typeName();
        String moduleName = typeInfo.referencedModuleNames().get(typeName);
        if ((thisTypeQualifiesAsContract && serviceTypeNamesToCodeGenerate.contains(typeName))
                || (autoAddInterfaces && typeInfo.typeKind().equals(TypeInfo.KIND_INTERFACE))
                || DefaultAnnotationAndValue.findFirst(Contract.class, typeInfo.annotations()).isPresent()) {
            addContract(contracts, providerForSet, typeName);

            if (!providerForSet.isEmpty()) {
                externalContracts.add(DefaultTypeName.create(Provider.class));
            }

            if (moduleName != null) {
                externalContracts.add(typeName.genericTypeName());
            }
        }

        AnnotationAndValue anno = DefaultAnnotationAndValue
                .findFirst(ExternalContracts.class, typeInfo.annotations())
                .orElse(null);
        if (anno != null) {
            TypeName externalContract = DefaultTypeName.createFromTypeName(anno.value().orElseThrow());
            addContract(externalContracts, null, externalContract);

            String[] moduleNames = anno.value("moduleNames").orElseThrow().split(",[ \t]*");
            if (moduleNames.length > 0) {
                for (String externalModuleName : moduleNames) {
                    if (!externalModuleName.isBlank()) {
                        externalModuleNamesRequired.add(externalModuleName);
                    }
                }
            } else {
                // try to determine the external module name
                String externalModuleName = typeInfo.referencedModuleNames().get(externalContract);
                if (externalModuleName != null && !isBuiltInJavaType(externalContract)) {
                    externalModuleNamesRequired.add(externalModuleName);
                }
            }
        }

        typeInfo.superTypeInfo().ifPresent(it -> gatherContracts(contracts,
                                                                 externalContracts,
                                                                 providerForSet,
                                                                 externalModuleNamesRequired,
                                                                 it,
                                                                 serviceTypeNamesToCodeGenerate,
                                                                 autoAddInterfaces,
                                                                 true));
        typeInfo.interfaceTypeInfo().forEach(it -> gatherContracts(contracts,
                                                                   externalContracts,
                                                                   providerForSet,
                                                                   externalModuleNamesRequired,
                                                                   it,
                                                                   serviceTypeNamesToCodeGenerate,
                                                                   autoAddInterfaces,
                                                                   false));
    }

    static void addContract(Set<TypeName> contracts,
                            Set<TypeName> providerForSet,
                            TypeName typeName) {
        TypeName providerType = qualifiedProviderType(typeName).orElse(null);
        if (providerType != null) {
            if (providerForSet != null) {
                providerForSet.add(providerType.genericTypeName());
            }
            contracts.add(providerType.genericTypeName());
        } else {
            contracts.add(typeName.genericTypeName());
        }
    }

    static Optional<TypeName> qualifiedProviderType(TypeName typeName) {
        TypeName componentType = (typeName.typeArguments().size() == 1) ? typeName.typeArguments().get(0) : null;
        if (componentType == null || componentType.generic()) {
            return Optional.empty();
        }

        if (!TypeTools.isProviderType(typeName.name())) {
            return Optional.empty();
        }

        return Optional.of(componentType);
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

    Optional<Integer> toRunLevel(TypeInfo service) {
        AnnotationAndValue runLevelAnno =
                DefaultAnnotationAndValue.findFirst(RunLevel.class, service.annotations()).orElse(null);
        if (runLevelAnno != null) {
            return Optional.of(Integer.valueOf(runLevelAnno.value().orElseThrow()));
        }

        return Optional.empty();
    }

    Optional<Double> toWeight(TypeInfo service) {
        AnnotationAndValue weightAnno =
                DefaultAnnotationAndValue.findFirst(Weight.class, service.annotations()).orElse(null);
        if (weightAnno != null) {
            return Optional.of(Double.valueOf(weightAnno.value().orElseThrow()));
        }

        return Optional.empty();
    }

    Optional<String> toPostConstructMethod(TypeInfo service) {
        List<String> postConstructs = service.elementInfo().stream()
                .filter(it -> {
                    AnnotationAndValue postConstructAnno =
                            DefaultAnnotationAndValue.findFirst(PostConstruct.class, it.annotations()).orElse(null);
                    return (postConstructAnno != null);
                })
                .map(TypedElementName::elementName)
                .toList();
        if (postConstructs.size() == 1) {
            return Optional.of(postConstructs.get(0));
        } else if (postConstructs.size() > 1) {
            throw new IllegalStateException("There can be at most one "
                                                    + PostConstruct.class.getName()
                                                    + " annotated method per type: " + service.typeName());
        }

        if (service.superTypeInfo().isPresent()) {
            return toPostConstructMethod(service.superTypeInfo().get());
        }

        return Optional.empty();
    }

    Optional<String> toPreDestroyMethod(TypeInfo service) {
        List<String> preDestroys = service.elementInfo().stream()
                .filter(it -> {
                    AnnotationAndValue postConstructAnno =
                            DefaultAnnotationAndValue.findFirst(PreDestroy.class, it.annotations()).orElse(null);
                    return (postConstructAnno != null);
                })
                .map(TypedElementName::elementName)
                .toList();
        if (preDestroys.size() == 1) {
            return Optional.of(preDestroys.get(0));
        } else if (preDestroys.size() > 1) {
            throw new IllegalStateException("There can be at most one "
                                                    + PreDestroy.class.getName()
                                                    + " annotated method per type: " + service.typeName());
        }

        if (service.superTypeInfo().isPresent()) {
            return toPostConstructMethod(service.superTypeInfo().get());
        }

        return Optional.empty();
    }

    Set<String> toScopeNames(TypeInfo service) {
        Set<String> scopeAnnotations = new LinkedHashSet<>();
        service.referencedTypeNamesToAnnotations()
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

    List<TypeName> toServiceTypeHierarchy(TypeInfo service) {
        List<TypeName> result = new ArrayList<>();
        result.add(service.typeName());
        service.superTypeInfo().ifPresent(it -> result.addAll(toServiceTypeHierarchy(it)));
        return result;
    }

    Set<QualifierAndValue> toQualifiers(TypeInfo service) {
        Set<QualifierAndValue> result = new LinkedHashSet<>();

        for (AnnotationAndValue anno : service.annotations()) {
            List<AnnotationAndValue> metaAnnotations = service.referencedTypeNamesToAnnotations().get(anno.typeName());
            Optional<? extends AnnotationAndValue> qual = DefaultAnnotationAndValue.findFirst(Qualifier.class, metaAnnotations);
            if (qual.isPresent()) {
                result.add(DefaultQualifierAndValue.convert(anno));
            }
        }

        // TODO:
//        service.superTypeInfo().ifPresent(it -> result.addAll(toQualifiers(it)));
//        service.interfaceTypeInfo().forEach(it -> result.addAll(toQualifiers(it)));

        return result;
    }

    void gatherElementsOfInterestInThisModule(List<TypedElementName> result) {
        Elements elementUtils = processingEnv.getElementUtils();
        for (String annoType : supportedElementTargetAnnotations()) {
            // annotation may not be on the classpath, in such a case just ignore it
            TypeElement annoTypeElement = elementUtils.getTypeElement(annoType);
            if (annoTypeElement != null) {
                Set<? extends Element> typesToProcess = roundEnv.getElementsAnnotatedWith(annoTypeElement);
                typesToProcess.forEach(it -> result.add(createTypedElementNameFromElement(it, elementUtils)));
            }
        }
    }

    void gatherTypeInfosToProcessInThisModule(Map<TypeName, TypeInfo> result,
                                              List<TypedElementName> elementsOfInterest) {
        for (String annoType : supportedServiceClassTargetAnnotations()) {
            // annotation may not be on the classpath, in such a case just ignore it
            TypeElement annoTypeElement = processingEnv.getElementUtils().getTypeElement(annoType);
            if (annoTypeElement != null) {
                Set<? extends Element> typesToProcess = roundEnv.getElementsAnnotatedWith(annoTypeElement);
                typesToProcess.forEach(it -> {
                    TypeName typeName = createTypeNameFromElement(it).orElseThrow();
                    if (!result.containsKey(typeName)) {
                        Optional<TypeInfo> typeInfo = toTypeInfo((TypeElement) it, typeName, elementsOfInterest);
                        typeInfo.ifPresent(it2 -> result.put(typeName, it2));
                    }
                });
            }
        }

        Set<TypeName> enclosingElementsOfInterest = elementsOfInterest.stream()
                .map(TypedElementName::enclosingTypeName)
                .collect(Collectors.toSet());
        enclosingElementsOfInterest.removeAll(result.keySet());
        enclosingElementsOfInterest.forEach(it -> {
            TypeElement element = Objects.requireNonNull(processingEnv.getElementUtils().getTypeElement(it.name()), it.name());
            result.put(it, toTypeInfo(element, it, elementsOfInterest).orElseThrow());
        });
    }

    Optional<TypeInfo> toTypeInfo(TypeElement element,
                                  TypeName typeName,
                                  List<TypedElementName> elementsOfInterest) {
        if (typeName.name().equals(Object.class.getName())) {
            return Optional.empty();
        }

        try {
            Elements elementUtils = processingEnv.getElementUtils();
            Set<AnnotationAndValue> annotations = createAnnotationAndValueSet(elementUtils.getTypeElement(typeName.name()));
            Map<TypeName, List<AnnotationAndValue>> referencedAnnotations = toReferencedTypeNamesToAnnotations(annotations);

            List<TypeName> allTypeNames = new ArrayList<>();
            allTypeNames.add(typeName);
            allTypeNames.addAll(referencedAnnotations.keySet());

            DefaultTypeInfo.Builder builder = DefaultTypeInfo.builder()
                    .typeName(typeName)
                    .typeKind(String.valueOf(element.getKind()))
                    .annotations(annotations)
                    .referencedTypeNamesToAnnotations(referencedAnnotations)
                    .modifierNames(toModifierNames(element.getModifiers()))
                    .elementInfo(toElementsOfInterestEnclosedInThisType(typeName, elementsOfInterest));

            TypeName superType = createTypeNameFromMirror(element.getSuperclass()).orElse(null);
            if (superType != null) {
                TypeElement superTypeElement = elementUtils.getTypeElement(superType.name());
                if (superTypeElement != null) {
                    Optional<TypeInfo> superTypeInfo = toTypeInfo(superTypeElement, superType, elementsOfInterest);
                    superTypeInfo.ifPresent(builder::superTypeInfo);
                    superTypeInfo.ifPresent(it -> allTypeNames.add(it.typeName()));
                }
            }

            element.getInterfaces().forEach(it -> {
                TypeName interfaceType = createTypeNameFromMirror(it).orElse(null);
                if (interfaceType != null) {
                    allTypeNames.add(interfaceType);
                    TypeElement interfaceTypeElement = elementUtils.getTypeElement(interfaceType.name());
                    if (interfaceTypeElement != null) {
                        Optional<TypeInfo> superTypeInfo = toTypeInfo(interfaceTypeElement, interfaceType, elementsOfInterest);
                        superTypeInfo.ifPresent(builder::addInterfaceTypeInfo);
                    }
                }
            });

            builder.referencedModuleNames(toReferencedModuleNames(allTypeNames));

            return Optional.of(builder.build());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to process: " + element, e);
        }
    }

    Map<TypeName, String> toReferencedModuleNames(List<TypeName> allTypeNames) {
        Map<TypeName, String> result = new LinkedHashMap<>();
        Elements elementUtils = processingEnv.getElementUtils();
        AtomicReference<String> moduleName = new AtomicReference<>();
        allTypeNames.forEach(it -> {
            TypeElement typeElement = elementUtils.getTypeElement(it.name());
            if (typeElement == null
                    || !isInThisModule(typeElement, moduleName, roundEnv, processingEnv, this)) {
                if (hasValue(moduleName.get())) {
                    result.put(it, moduleName.get());
                }
            }
        });
        return result;
    }

    List<TypedElementName> toElementsOfInterestEnclosedInThisType(TypeName typeName,
                                                                  List<TypedElementName> allElementsOfInterest) {
        return allElementsOfInterest.stream()
                .filter(it -> typeName.equals(it.enclosingTypeName()))
                .collect(Collectors.toList());
    }

    System.Logger.Level loggerLevel() {
        return (Options.isOptionEnabled(Options.TAG_DEBUG)) ? System.Logger.Level.INFO : System.Logger.Level.DEBUG;
    }

    void validate(Collection<TypeInfo> typesToCreateActivatorsFor) {
        // TODO:
    }

}
