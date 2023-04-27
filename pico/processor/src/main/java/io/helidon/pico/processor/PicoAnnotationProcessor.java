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
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;

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
import io.helidon.pico.api.DependenciesInfo;
import io.helidon.pico.api.ExternalContracts;
import io.helidon.pico.api.QualifierAndValue;
import io.helidon.pico.api.RunLevel;
import io.helidon.pico.runtime.Dependencies;
import io.helidon.pico.tools.ActivatorCreatorCodeGen;
import io.helidon.pico.tools.ActivatorCreatorRequest;
import io.helidon.pico.tools.ActivatorCreatorResponse;
import io.helidon.pico.tools.DefaultActivatorCreator;
import io.helidon.pico.tools.DefaultActivatorCreatorConfigOptions;
import io.helidon.pico.tools.Options;
import io.helidon.pico.tools.ServicesToProcess;
import io.helidon.pico.tools.ToolsException;
import io.helidon.pico.tools.TypeNames;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Qualifier;
import jakarta.inject.Singleton;

import static io.helidon.builder.processor.tools.BeanUtils.isBuiltInJavaType;
import static io.helidon.builder.processor.tools.BuilderTypeTools.createTypeNameFromElement;
import static io.helidon.builder.processor.tools.BuilderTypeTools.createTypeNameFromMirror;
import static io.helidon.pico.processor.ActiveProcessorUtils.MAYBE_ANNOTATIONS_CLAIMED_BY_THIS_PROCESSOR;
import static io.helidon.pico.processor.GeneralProcessorUtils.findFirst;
import static io.helidon.pico.processor.GeneralProcessorUtils.hasValue;
import static io.helidon.pico.processor.GeneralProcessorUtils.rootStackTraceElementOf;
import static io.helidon.pico.tools.TypeTools.createAnnotationAndValueSet;
import static io.helidon.pico.tools.TypeTools.createTypedElementNameFromElement;
import static io.helidon.pico.tools.TypeTools.isProviderType;
import static io.helidon.pico.tools.TypeTools.toAccess;

/**
 * An annotation processor that will find everything needing to be processed related to core Pico conde generation.
 */
public class PicoAnnotationProcessor extends AbstractProcessor {
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

    private final Map<TypeName, TypeInfo> typeInfoToCreateActivatorsForInThisModule = new LinkedHashMap<>();
    private ActiveProcessorUtils utils;
    private ActivatorCreatorHandler creator;
    private boolean autoAddInterfaces;

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
        this.utils = new ActiveProcessorUtils(this, processingEnv, null);
        this.autoAddInterfaces = Options.isOptionEnabled(Options.TAG_AUTO_ADD_NON_CONTRACT_INTERFACES);
        this.creator = new ActivatorCreatorHandler(getClass().getSimpleName(), processingEnv, utils);
        this.creator.activateSimulationMode();
        super.init(processingEnv);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations,
                           RoundEnvironment roundEnv) {
        utils.roundEnv(roundEnv);
        ServicesToProcess.onBeginProcessing(utils, getSupportedAnnotationTypes(), roundEnv);
        ServicesToProcess.addOnDoneRunnable(ActivatorCreatorHandler.reporting());

        try {
            if (!roundEnv.processingOver()) {
                // build the model
                List<TypedElementName> elementsOfInterest = new ArrayList<>();
                gatherElementsOfInterestInThisModule(elementsOfInterest);

                // cumulatively collect the types to process in the module
                gatherTypeInfosToProcessInThisModule(typeInfoToCreateActivatorsForInThisModule, elementsOfInterest);

                // optionally intercept and validate the model
                Map<TypeName, TypeInfo> filtered = interceptorAndValidate(typeInfoToCreateActivatorsForInThisModule);

                // code generate the model
                ServicesToProcess services = toServicesToProcess(filtered, elementsOfInterest);
                doFiler(services);
            }

            return MAYBE_ANNOTATIONS_CLAIMED_BY_THIS_PROCESSOR;
        } catch (Throwable t) {
            ToolsException exc = new ToolsException("Error during processing: " + t
                                                            + " @ " + rootStackTraceElementOf(t)
                                                            + " in " + getClass().getSimpleName(), t);
            utils.error(exc.getMessage(), t);
            // we typically will not even get to this next line since the messager.error() call above will trigger things to halt
            throw exc;
        } finally {
            ServicesToProcess.onEndProcessing(utils, getSupportedAnnotationTypes(), roundEnv);
            utils.roundEnv(null);
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
     * Code generate these {@link io.helidon.pico.api.Activator}'s ad {@link io.helidon.pico.api.ModuleComponent}'s.
     *
     * @param services the services to code generate
     */
    protected void doFiler(ServicesToProcess services) {
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

        ActivatorCreatorCodeGen codeGen = DefaultActivatorCreator.createActivatorCreatorCodeGen(services).orElse(null);
        if (codeGen == null) {
            return;
        }

        boolean isProcessingOver = utils.roundEnv().processingOver();
        DefaultActivatorCreatorConfigOptions configOptions = DefaultActivatorCreatorConfigOptions.builder()
                .applicationPreCreated(Options.isOptionEnabled(Options.TAG_APPLICATION_PRE_CREATE))
                .moduleCreated(isProcessingOver)
                .build();
        ActivatorCreatorRequest req = DefaultActivatorCreator
                .createActivatorCreatorRequest(services, codeGen, configOptions, creator.filer(), false);
        ActivatorCreatorResponse res = creator.createModuleActivators(req);
        if (!res.success()) {
            ToolsException exc = new ToolsException("Error during codegen", res.error().orElse(null));
            utils.error(exc.getMessage(), exc);
            // should not get here
            throw exc;
        }
    }

    /**
     * Provides a means for anyone to validate and intercept the collection of types to process.
     *
     * @param typesToCreateActivatorsFor the types to process (where key is the proposed generated name)
     * @return the (possibly revised) map of types to process
     */
    protected Map<TypeName, TypeInfo> interceptorAndValidate(Map<TypeName, TypeInfo> typesToCreateActivatorsFor) {
        return Objects.requireNonNull(typesToCreateActivatorsFor);
    }

    /**
     * Called to process a single service that will eventually be code generated. The default implementation will take the
     * provided service {@link TypeInfo} and translate that into the {@link ServicesToProcess} instance. Eventually, the
     * {@link ServicesToProcess} instance will be fed as request inputs to one or more of the creators (e.g.,
     * {@link io.helidon.pico.tools.spi.ActivatorCreator}, {@link io.helidon.pico.tools.spi.InterceptorCreator}, etc.).
     *
     * @param services                          the services to process builder
     * @param service                           the service type info to process right now
     * @param serviceTypeNamesToCodeGenerate    the entire set of types that are planned to be code-generated
     * @param allElementsOfInterest             all of the elements of interest that pico "knows" about
     */
    protected void process(ServicesToProcess services,
                           TypeInfo service,
                           Set<TypeName> serviceTypeNamesToCodeGenerate,
                           List<TypedElementName> allElementsOfInterest) {
        TypeName serviceTypeName = service.typeName();
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
        toInjectionDependencies(service, allElementsOfInterest).ifPresent(it -> services.addDependencies(serviceTypeName, it));
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

    private ServicesToProcess toServicesToProcess(Map<TypeName, TypeInfo> typesToCodeGenerate,
                                                  List<TypedElementName> allElementsOfInterest) {
        // vvv : will need to be replaced with the global services instance (eventually) since the config-driven processor
        //       additionally adds to the global ServicesToProcess instance
        ServicesToProcess services = ServicesToProcess.create();
        // ^^^ : note that these will be removed in the future - it is here to compare the "old way" to the "new way"
        utils.relayModuleInfoToServicesToProcess(services);

        typesToCodeGenerate.forEach((serviceTypeName, service) -> {
            utils.debug("processing: " + serviceTypeName);
            try {
                process(services, service, typesToCodeGenerate.keySet(), allElementsOfInterest);
            } catch (Throwable t) {
                throw new ToolsException("Error processing: " + serviceTypeName, t);
            }
        });

        return services;
    }

    private void gatherContractsIntoServicesToProcess(ServicesToProcess services,
                                                      TypeName serviceTypeName,
                                                      TypeInfo service,
                                                      Set<TypeName> serviceTypeNamesToCodeGenerate) {
        Set<TypeName> contracts = new LinkedHashSet<>();
        Set<TypeName> externalContracts = new LinkedHashSet<>();
        Set<TypeName> providerForSet = new LinkedHashSet<>();
        Set<String> externalModuleNames = new LinkedHashSet<>();

        gatherContracts(contracts,
                        externalContracts,
                        providerForSet,
                        externalModuleNames,
                        service,
                        serviceTypeNamesToCodeGenerate,
                        false);

        contracts.forEach(it -> services.addTypeForContract(serviceTypeName, it, false));
        externalContracts.forEach(it -> services.addTypeForContract(serviceTypeName, it, true));
        services.addProviderFor(serviceTypeName, providerForSet);
        services.addExternalRequiredModules(serviceTypeName, externalModuleNames);
    }

    private void gatherContracts(Set<TypeName> contracts,
                                 Set<TypeName> externalContracts,
                                 Set<TypeName> providerForSet,
                                 Set<String> externalModuleNamesRequired,
                                 TypeInfo typeInfo,
                                 Set<TypeName> serviceTypeNamesToCodeGenerate,
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
                                                                 true));
        typeInfo.interfaceTypeInfo().forEach(it -> gatherContracts(contracts,
                                                                   externalContracts,
                                                                   providerForSet,
                                                                   externalModuleNamesRequired,
                                                                   it,
                                                                   serviceTypeNamesToCodeGenerate,
                                                                   false));
    }

    private static void addContract(Set<TypeName> contracts,
                                    Set<TypeName> providerForSet,
                                    TypeName typeName) {
        TypeName providerType = toQualifiedProviderType(typeName).orElse(null);
        if (providerType != null) {
            if (providerForSet != null) {
                providerForSet.add(providerType.genericTypeName());
            }
            contracts.add(providerType.genericTypeName());
        } else {
            contracts.add(typeName.genericTypeName());
        }
    }

    private static Optional<TypeName> toQualifiedProviderType(TypeName typeName) {
        TypeName componentType = (typeName.typeArguments().size() == 1) ? typeName.typeArguments().get(0) : null;
        if (componentType == null || componentType.generic()) {
            return Optional.empty();
        }

        if (!isProviderType(typeName.name())) {
            return Optional.empty();
        }

        return Optional.of(componentType);
    }

    private Map<TypeName, List<AnnotationAndValue>> toReferencedTypeNamesToAnnotations(
            Collection<AnnotationAndValue> annotations) {
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
                        result.put(it.typeName(), new ArrayList<>(createAnnotationAndValueSet(typeElement)));
                    }
                });
        return result;
    }

    private Optional<Integer> toRunLevel(TypeInfo service) {
        AnnotationAndValue runLevelAnno =
                DefaultAnnotationAndValue.findFirst(RunLevel.class, service.annotations()).orElse(null);
        if (runLevelAnno != null) {
            return Optional.of(Integer.valueOf(runLevelAnno.value().orElseThrow()));
        }

        return Optional.empty();
    }

    private Optional<Double> toWeight(TypeInfo service) {
        AnnotationAndValue weightAnno =
                DefaultAnnotationAndValue.findFirst(Weight.class, service.annotations()).orElse(null);
        if (weightAnno != null) {
            return Optional.of(Double.valueOf(weightAnno.value().orElseThrow()));
        }

        return Optional.empty();
    }

    private Optional<String> toPostConstructMethod(TypeInfo service) {
        List<String> postConstructs = service.elementInfo().stream()
                .filter(it -> {
                    AnnotationAndValue anno = findFirst(PostConstruct.class, it.annotations()).orElse(null);
                    return (anno != null);
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

    private Optional<String> toPreDestroyMethod(TypeInfo service) {
        List<String> preDestroys = service.elementInfo().stream()
                .filter(it -> {
                    AnnotationAndValue anno = findFirst(PreDestroy.class, it.annotations()).orElse(null);
                    return (anno != null);
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

    private Set<String> toScopeNames(TypeInfo service) {
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

    private static Set<String> toModifierNames(Set<javax.lang.model.element.Modifier> modifiers) {
        return modifiers.stream()
                .map(javax.lang.model.element.Modifier::name)
                .collect(Collectors.toSet());
    }

    private List<TypeName> toServiceTypeHierarchy(TypeInfo service) {
        List<TypeName> result = new ArrayList<>();
        result.add(service.typeName());
        service.superTypeInfo().ifPresent(it -> result.addAll(toServiceTypeHierarchy(it)));
        return result;
    }

    private Set<QualifierAndValue> toQualifiers(TypeInfo service) {
        Set<QualifierAndValue> result = new LinkedHashSet<>();

        for (AnnotationAndValue anno : service.annotations()) {
            List<AnnotationAndValue> metaAnnotations = service.referencedTypeNamesToAnnotations().get(anno.typeName());
            Optional<? extends AnnotationAndValue> qual = findFirst(Qualifier.class, metaAnnotations);
            if (qual.isPresent()) {
                result.add(DefaultQualifierAndValue.convert(anno));
            }
        }

        // note: should qualifiers be inheritable? Right now we assume not to support the jsr-330 spec
//        service.superTypeInfo().ifPresent(it -> result.addAll(toQualifiers(it)));
//        service.interfaceTypeInfo().forEach(it -> result.addAll(toQualifiers(it)));

        return result;
    }

    private Optional<DependenciesInfo> toInjectionDependencies(TypeInfo service,
                                                               List<TypedElementName> allElementsOfInterest) {
        Dependencies.BuilderContinuation builder = Dependencies.builder(service.typeName().name());
        gatherInjectionPoints(builder, service, allElementsOfInterest);
        return Optional.of(builder.build());
    }

    private void gatherInjectionPoints(Dependencies.BuilderContinuation builder,
                                       TypeInfo service,
                                       List<TypedElementName> allElementsOfInterest) {
        List<TypedElementName> injectableElementsForThisService = allElementsOfInterest.stream()
                .filter(it -> it.enclosingTypeName().orElseThrow().equals(service.typeName()))
                .filter(it -> {
                    Optional<? extends AnnotationAndValue> anno = findFirst(Inject.class, it.annotations());
                    return anno.isPresent();
                }).toList();
        if (!injectableElementsForThisService.isEmpty()) {
            injectableElementsForThisService.forEach(it -> {
                System.out.println("it: " + it);

                // TO
            });
        }

        // recursive up the hierarchy
        service.superTypeInfo().ifPresent(it -> gatherInjectionPoints(builder, it, allElementsOfInterest));
    }

    private void gatherElementsOfInterestInThisModule(List<TypedElementName> result) {
        Elements elementUtils = processingEnv.getElementUtils();
        for (String annoType : supportedElementTargetAnnotations()) {
            // annotation may not be on the classpath, in such a case just ignore it
            TypeElement annoTypeElement = elementUtils.getTypeElement(annoType);
            if (annoTypeElement != null) {
                Set<? extends Element> typesToProcess = utils.roundEnv().getElementsAnnotatedWith(annoTypeElement);
                typesToProcess.forEach(it -> result.add(createTypedElementNameFromElement(it, elementUtils)));
            }
        }
    }

    private void gatherTypeInfosToProcessInThisModule(Map<TypeName, TypeInfo> result,
                                                      List<TypedElementName> elementsOfInterest) {
        // this section gathers based upon the class-level annotations in order to discover what to process
        for (String annoType : supportedServiceClassTargetAnnotations()) {
            // annotation may not be on the classpath, in such a case just ignore it
            TypeElement annoTypeElement = processingEnv.getElementUtils().getTypeElement(annoType);
            if (annoTypeElement != null) {
                Set<? extends Element> typesToProcess = utils.roundEnv().getElementsAnnotatedWith(annoTypeElement);
                typesToProcess.forEach(it -> {
                    TypeName typeName = createTypeNameFromElement(it).orElseThrow();
                    if (!result.containsKey(typeName)) {
                        Optional<TypeInfo> typeInfo = toTypeInfo((TypeElement) it, typeName, elementsOfInterest);
                        typeInfo.ifPresent(it2 -> result.put(typeName, it2));
                    }
                });
            }
        }

        // this section gathers based upon the element-level annotations in order to discover what to process
        Set<TypeName> enclosingElementsOfInterest = elementsOfInterest.stream()
                .map(TypedElementName::enclosingTypeName)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toSet());
        enclosingElementsOfInterest.removeAll(result.keySet());
        enclosingElementsOfInterest.forEach(it -> {
            TypeElement element = Objects.requireNonNull(processingEnv.getElementUtils().getTypeElement(it.name()), it.name());
            result.put(it, toTypeInfo(element, it, elementsOfInterest).orElseThrow());
        });
    }

    private Optional<TypeInfo> toTypeInfo(TypeElement element,
                                          TypeName typeName,
                                          List<TypedElementName> elementsOfInterest) {
        if (typeName.name().equals(Object.class.getName())) {
            return Optional.empty();
        }

        try {
            Elements elementUtils = processingEnv.getElementUtils();
            Set<AnnotationAndValue> annotations = createAnnotationAndValueSet(elementUtils.getTypeElement(typeName.name()));
            Map<TypeName, List<AnnotationAndValue>> referencedAnnotations = toReferencedTypeNamesToAnnotations(annotations);
            List<TypedElementName> elements = toElementsOfInterestEnclosedInThisType(typeName, elementsOfInterest);

            List<TypeName> allTypeNames = new ArrayList<>();
            allTypeNames.add(typeName);
            allTypeNames.addAll(referencedAnnotations.keySet());

            DefaultTypeInfo.Builder builder = DefaultTypeInfo.builder()
                    .typeName(typeName)
                    .typeKind(String.valueOf(element.getKind()))
                    .annotations(annotations)
                    .referencedTypeNamesToAnnotations(referencedAnnotations)
                    .modifierNames(toModifierNames(element.getModifiers()))
                    .elementInfo(elements);

            // add all of the element's and parameters to the references annotation set
            elements.forEach(it -> {
                List<AnnotationAndValue> annos = it.annotations();
                Map<TypeName, List<AnnotationAndValue>> resolved = toReferencedTypeNamesToAnnotations(annos);
                resolved.forEach(builder::addReferencedTypeNamesToAnnotations);

                it.parameterArguments().forEach(arg -> {
                    List<AnnotationAndValue> argAnnos = arg.annotations();
                    Map<TypeName, List<AnnotationAndValue>> resolvedArgAnnos = toReferencedTypeNamesToAnnotations(argAnnos);
                    resolvedArgAnnos.forEach(builder::addReferencedTypeNamesToAnnotations);
                });
            });

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

    private Map<TypeName, String> toReferencedModuleNames(List<TypeName> allTypeNames) {
        Map<TypeName, String> result = new LinkedHashMap<>();
        Elements elementUtils = processingEnv.getElementUtils();
        AtomicReference<String> moduleName = new AtomicReference<>();
        allTypeNames.forEach(it -> {
            TypeElement typeElement = elementUtils.getTypeElement(it.name());
            if (typeElement == null || !utils.isTypeInThisModule(typeElement, moduleName)) {
                if (hasValue(moduleName.get())) {
                    result.put(it, moduleName.get());
                }
            }
        });
        return result;
    }

    private List<TypedElementName> toElementsOfInterestEnclosedInThisType(TypeName typeName,
                                                                          List<TypedElementName> allElementsOfInterest) {
        return allElementsOfInterest.stream()
                .filter(it -> typeName.equals(it.enclosingTypeName().orElseThrow()))
                .collect(Collectors.toList());
    }

}
