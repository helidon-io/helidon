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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;

import io.helidon.common.types.AnnotationAndValue;
import io.helidon.common.types.AnnotationAndValueDefault;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementName;
import io.helidon.pico.api.Contract;
import io.helidon.pico.api.DependenciesInfo;
import io.helidon.pico.api.ElementInfo;
import io.helidon.pico.api.ExternalContracts;
import io.helidon.pico.api.PicoServicesConfig;
import io.helidon.pico.api.QualifierAndValue;
import io.helidon.pico.api.ServiceInfoBasics;
import io.helidon.pico.api.ServiceInfoDefault;
import io.helidon.pico.runtime.Dependencies;
import io.helidon.pico.tools.ActivatorCreatorCodeGen;
import io.helidon.pico.tools.ActivatorCreatorConfigOptionsDefault;
import io.helidon.pico.tools.ActivatorCreatorDefault;
import io.helidon.pico.tools.ActivatorCreatorRequest;
import io.helidon.pico.tools.ActivatorCreatorResponse;
import io.helidon.pico.tools.InterceptionPlan;
import io.helidon.pico.tools.InterceptorCreatorProvider;
import io.helidon.pico.tools.Options;
import io.helidon.pico.tools.ServicesToProcess;
import io.helidon.pico.tools.ToolsException;
import io.helidon.pico.tools.TypeNames;
import io.helidon.pico.tools.spi.ActivatorCreator;
import io.helidon.pico.tools.spi.InterceptorCreator;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;

import static io.helidon.builder.processor.tools.BuilderTypeTools.createTypeNameFromElement;
import static io.helidon.common.types.TypeNameDefault.createFromTypeName;
import static io.helidon.pico.processor.ActiveProcessorUtils.MAYBE_ANNOTATIONS_CLAIMED_BY_THIS_PROCESSOR;
import static io.helidon.pico.processor.GeneralProcessorUtils.findFirst;
import static io.helidon.pico.processor.GeneralProcessorUtils.isProviderType;
import static io.helidon.pico.processor.GeneralProcessorUtils.rootStackTraceElementOf;
import static io.helidon.pico.processor.GeneralProcessorUtils.toPostConstructMethod;
import static io.helidon.pico.processor.GeneralProcessorUtils.toPreDestroyMethod;
import static io.helidon.pico.processor.GeneralProcessorUtils.toQualifiers;
import static io.helidon.pico.processor.GeneralProcessorUtils.toRunLevel;
import static io.helidon.pico.processor.GeneralProcessorUtils.toScopeNames;
import static io.helidon.pico.processor.GeneralProcessorUtils.toServiceTypeHierarchy;
import static io.helidon.pico.processor.GeneralProcessorUtils.toWeight;
import static io.helidon.pico.tools.TypeTools.createTypedElementNameFromElement;
import static io.helidon.pico.tools.TypeTools.toAccess;
import static java.util.Objects.requireNonNull;

/**
 * An annotation processor that will find everything needing to be processed related to core Pico conde generation.
 */
public class PicoAnnotationProcessor extends AbstractProcessor {
    private static boolean disableBaseProcessing;

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

    private ActiveProcessorUtils utils;
    private CreatorHandler creator;
    private boolean autoAddInterfaces;

    /**
     * Service loader based constructor.
     *
     * @deprecated this is a Java ServiceLoader implementation and the constructor should not be used directly
     */
    @Deprecated
    public PicoAnnotationProcessor() {
    }

    /**
     * Any overriding APT processor can optionally pass {@code false} in order to prevent duuplicate base processing.
     *
     * @param disableBaseProcessing set to true to disable base processing
     */
    protected PicoAnnotationProcessor(boolean disableBaseProcessing) {
        if (disableBaseProcessing) {
            PicoAnnotationProcessor.disableBaseProcessing = true;
        }
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
        this.creator = new CreatorHandler(getClass().getSimpleName(), processingEnv, utils);
        this.creator.activateSimulationMode();
        super.init(processingEnv);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations,
                           RoundEnvironment roundEnv) {
        utils.roundEnv(roundEnv);

        if (disableBaseProcessing && getClass() == PicoAnnotationProcessor.class) {
            return false;
        }

        ServicesToProcess.onBeginProcessing(utils, getSupportedAnnotationTypes(), roundEnv);
        ServicesToProcess.addOnDoneRunnable(CreatorHandler.reporting());

        try {
            if (roundEnv.processingOver()) {
                return MAYBE_ANNOTATIONS_CLAIMED_BY_THIS_PROCESSOR;
            }

            // build the model
            List<TypedElementName> elementsOfInterest = new ArrayList<>();
            gatherElementsOfInterestInThisModule(elementsOfInterest);
            validate(elementsOfInterest);

            // cumulatively collect the types to process in the module
            Map<TypeName, TypeInfo> typeInfoToCreateActivatorsForInThisModule = new LinkedHashMap<>();
            gatherTypeInfosToProcessInThisModule(typeInfoToCreateActivatorsForInThisModule, elementsOfInterest);

            // optionally intercept and validate the model
            Set<TypeInfo> filtered = interceptorAndValidate(typeInfoToCreateActivatorsForInThisModule.values());

            // code generate the model
            if (!filtered.isEmpty()) {
                ServicesToProcess services = toServicesToProcess(filtered, elementsOfInterest);
                doFiler(services);
            }

            return MAYBE_ANNOTATIONS_CLAIMED_BY_THIS_PROCESSOR;
        } catch (Throwable t) {
            ToolsException exc = new ToolsException("Error while processing: " + t
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
     * Returns the activator creator in use.
     *
     * @return the activator creator in use
     */
    protected ActivatorCreator activatorCreator() {
        return creator;
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
        ActivatorCreatorCodeGen codeGen = ActivatorCreatorDefault.createActivatorCreatorCodeGen(services).orElse(null);
        if (codeGen == null) {
            return;
        }

        ActivatorCreatorConfigOptionsDefault configOptions = ActivatorCreatorConfigOptionsDefault.builder()
                .applicationPreCreated(Options.isOptionEnabled(Options.TAG_APPLICATION_PRE_CREATE))
                .moduleCreated(true)
                .build();
        ActivatorCreatorRequest req = ActivatorCreatorDefault
                .createActivatorCreatorRequest(services, codeGen, configOptions, creator.filer(), false);
        ActivatorCreatorResponse res = creator.createModuleActivators(req);
        if (!res.success()) {
            ToolsException exc = new ToolsException("Error during codegen", res.error().orElse(null));
            utils.error(exc.getMessage(), exc);
            // should not get here since the error above should halt further processing
            throw exc;
        }
    }

    /**
     * These are all of the elements (methods, constructors, methods) that are "interesting" (i.e., has {@code @Inject}, etc.).
     *
     * @param elementsOfInterest the elements that are eligible for some form of Pico processing
     */
    protected void validate(List<TypedElementName> elementsOfInterest) {
        validatePerClass(elementsOfInterest,
                            "There can be max of one injectable constructor per class",
                            1,
                            (it) -> it.elementTypeKind().equals(TypeInfo.KIND_CONSTRUCTOR)
                                    && findFirst(Inject.class, it.annotations()).isPresent());
        validatePerClass(elementsOfInterest,
                            "There can be max of one PostConstruct method per class",
                            1,
                            (it) -> it.elementTypeKind().equals(TypeInfo.KIND_METHOD)
                                    && findFirst(PostConstruct.class, it.annotations()).isPresent());
        validatePerClass(elementsOfInterest,
                            "There can be max of one PreDestroy method per class",
                            1,
                            (it) -> it.elementTypeKind().equals(TypeInfo.KIND_METHOD)
                                    && findFirst(PreDestroy.class, it.annotations()).isPresent());
        validatePerClass(elementsOfInterest,
                         PicoServicesConfig.NAME + " does not currently support static or private elements",
                         0,
                         (it) -> it.modifierNames().contains(TypeInfo.MODIFIER_PRIVATE)
                                 || it.modifierNames().contains(TypeInfo.MODIFIER_STATIC));
    }

    private void validatePerClass(List<TypedElementName> elementsOfInterest,
                                  String msg,
                                  int maxAllowed,
                                  Predicate<TypedElementName> matcher) {
        Map<TypeName, List<TypedElementName>> allTypeNamesToMatchingElements = new LinkedHashMap<>();
        elementsOfInterest.stream()
                .filter(matcher)
                .forEach(it -> allTypeNamesToMatchingElements
                        .computeIfAbsent(it.enclosingTypeName().orElseThrow(), (n) -> new ArrayList<>()).add(it));
        allTypeNamesToMatchingElements.values().stream()
                .filter(list -> list.size() > maxAllowed)
                .forEach(it -> utils.error(msg + " for " + it.get(0).enclosingTypeName(), null));
    }

    /**
     * Provides a means for anyone to validate and intercept the collection of types to process.
     *
     * @param typesToCreateActivatorsFor the map of types to process (where key is the proposed generated name)
     * @return the (possibly revised) set of types to process
     */
    protected Set<TypeInfo> interceptorAndValidate(Collection<TypeInfo> typesToCreateActivatorsFor) {
        return new LinkedHashSet<>(Objects.requireNonNull(typesToCreateActivatorsFor));
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
        if (service.modifierNames().contains(TypeInfo.MODIFIER_ABSTRACT)) {
            return;
        }

        utils.debug("Processing: " + service.typeName());
        processBasics(services, service, serviceTypeNamesToCodeGenerate, allElementsOfInterest);
        processInterceptors(services, service, serviceTypeNamesToCodeGenerate, allElementsOfInterest);
        processExtensions(services, service, serviceTypeNamesToCodeGenerate, allElementsOfInterest);
    }

    /**
     * Processes the basic Pico service type - its contracts, run level, weight, dependencies, etc.
     *
     * @param services                          the services to process builder
     * @param service                           the service type info to process right now
     * @param serviceTypeNamesToCodeGenerate    the entire set of types that are planned to be code-generated
     * @param allElementsOfInterest             all of the elements of interest that pico "knows" about
     */
    @SuppressWarnings("unused")
    protected void processBasics(ServicesToProcess services,
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
        toInjectionDependencies(service, allElementsOfInterest).ifPresent(services::addDependencies);
        services.addAccessLevel(serviceTypeName,
                                toAccess(service.modifierNames()));
        services.addIsAbstract(serviceTypeName,
                               service.modifierNames().contains(TypeInfo.MODIFIER_ABSTRACT));
        services.addServiceTypeHierarchy(serviceTypeName,
                                         toServiceTypeHierarchy(service));
        services.addQualifiers(serviceTypeName,
                               toQualifiers(service));
        gatherContractsIntoServicesToProcess(services, service);
    }

    /**
     * Process any interception plans.
     *
     * @param services                          the services to process builder
     * @param service                           the service type info to process right now
     * @param serviceTypeNamesToCodeGenerate    the entire set of types that are planned to be code-generated
     * @param allElementsOfInterest             all of the elements of interest that pico "knows" about
     */
    @SuppressWarnings("unused")
    private void processInterceptors(ServicesToProcess services,
                                     TypeInfo service,
                                     Set<TypeName> serviceTypeNamesToCodeGenerate,
                                     List<TypedElementName> allElementsOfInterest) {
        TypeName serviceTypeName = service.typeName();
        InterceptorCreator interceptorCreator = InterceptorCreatorProvider.instance();
        ServiceInfoBasics interceptedServiceInfo = toServiceInfo(service);
        InterceptorCreator.InterceptorProcessor processor = interceptorCreator.createInterceptorProcessor(
                interceptedServiceInfo,
                interceptorCreator,
                Optional.of(processingEnv));
        Set<String> annotationTypeTriggers = processor.allAnnotationTypeTriggers();
        if (annotationTypeTriggers.isEmpty()) {
            services.addInterceptorPlanFor(serviceTypeName, Optional.empty());
            return;
        }

        Optional<InterceptionPlan> plan = processor.createInterceptorPlan(annotationTypeTriggers);
        if (plan.isEmpty()) {
            utils.log("unable to produce an interception plan for: " + serviceTypeName);
        }
        services.addInterceptorPlanFor(serviceTypeName, plan);
    }

    /**
     * Process any extensions (e.g., config-driven) requiring extra processing or any modifications to {@link ServicesToProcess}.
     *
     * @param services                          the services to process builder
     * @param service                           the service type info to process right now
     * @param serviceTypeNamesToCodeGenerate    the entire set of types that are planned to be code-generated
     * @param allElementsOfInterest             all of the elements of interest that pico "knows" about
     */
    @SuppressWarnings("unused")
    protected void processExtensions(ServicesToProcess services,
                                     TypeInfo service,
                                     Set<TypeName> serviceTypeNamesToCodeGenerate,
                                     List<TypedElementName> allElementsOfInterest) {
        // NOP;
    }

    private ServicesToProcess toServicesToProcess(Set<TypeInfo> typesToCodeGenerate,
                                                  List<TypedElementName> allElementsOfInterest) {
        ServicesToProcess services = ServicesToProcess.create();
        utils.relayModuleInfoToServicesToProcess(services);

        Set<TypeName> typesNamesToCodeGenerate = typesToCodeGenerate.stream().map(TypeInfo::typeName).collect(Collectors.toSet());
        typesToCodeGenerate.forEach(service -> {
            try {
                process(services, service, typesNamesToCodeGenerate, allElementsOfInterest);
            } catch (Throwable t) {
                throw new ToolsException("Error while processing: " + service.typeName(), t);
            }
        });

        return services;
    }

    private void gatherContractsIntoServicesToProcess(ServicesToProcess services,
                                                      TypeInfo service) {
        Set<TypeName> contracts = new LinkedHashSet<>();
        Set<TypeName> externalContracts = new LinkedHashSet<>();
        Set<TypeName> providerForSet = new LinkedHashSet<>();
        Set<String> externalModuleNames = new LinkedHashSet<>();

        gatherContracts(contracts,
                        externalContracts,
                        providerForSet,
                        externalModuleNames,
                        service,
                        false);

        TypeName serviceTypeName = service.typeName();
        contracts.forEach(it -> services.addTypeForContract(serviceTypeName, it, false));
        externalContracts.forEach(it -> services.addTypeForContract(serviceTypeName, it, true));
        services.addProviderFor(serviceTypeName, providerForSet);
        services.addExternalRequiredModules(serviceTypeName, externalModuleNames);

        utils.debug(serviceTypeName
                            + ": contracts=" + contracts
                            + ", providers=" + providerForSet
                            + ", externalContracts=" + externalContracts
                            + ", externalModuleNames=" + externalModuleNames);
    }

    private void gatherContracts(Set<TypeName> contracts,
                                 Set<TypeName> externalContracts,
                                 Set<TypeName> providerForSet,
                                 Set<String> externalModuleNamesRequired,
                                 TypeInfo typeInfo,
                                 boolean isThisTypeEligibleToBeAContract) {
        TypeName fqTypeName = typeInfo.typeName();
        TypeName fqProviderTypeName = null;
        if (isProviderType(fqTypeName)) {
            fqProviderTypeName = fqTypeName.genericTypeName();
            fqTypeName = requireNonNull(fqTypeName.typeArguments().get(0), fqTypeName.toString());
        }
        TypeName genericTypeName = fqTypeName.genericTypeName();

        if (isThisTypeEligibleToBeAContract) {
            if (fqProviderTypeName != null) {
                if (!genericTypeName.generic()) {
                    providerForSet.add(genericTypeName);

                    Optional<String> moduleName = filterModuleName(typeInfo.moduleNameOf(genericTypeName));
                    moduleName.ifPresent(externalModuleNamesRequired::add);
                    if (moduleName.isPresent()) {
                        externalContracts.add(genericTypeName);
                    } else {
                        contracts.add(genericTypeName);
                    }
                }

                // if we are dealing with a Provider<> then we should add those too as module dependencies
                TypeName genericProviderTypeName = fqProviderTypeName.genericTypeName();
                externalContracts.add(genericProviderTypeName);
                filterModuleName(typeInfo.moduleNameOf(genericProviderTypeName)).ifPresent(externalModuleNamesRequired::add);
                if (genericProviderTypeName.name().equals(TypeNames.PICO_INJECTION_POINT_PROVIDER)) {
                    TypeName jakartaProviderTypeName = createFromTypeName(TypeNames.JAKARTA_PROVIDER);
                    externalContracts.add(jakartaProviderTypeName);
                    filterModuleName(typeInfo.moduleNameOf(jakartaProviderTypeName)).ifPresent(externalModuleNamesRequired::add);
                }
            } else {
                boolean isTypeAnInterface = typeInfo.typeKind().equals(TypeInfo.KIND_INTERFACE);
                boolean isContractType = (autoAddInterfaces && isTypeAnInterface)
                        || AnnotationAndValueDefault.findFirst(Contract.class, typeInfo.annotations()).isPresent();
                if (isContractType) {
                    Optional<String> moduleName = filterModuleName(typeInfo.moduleNameOf(genericTypeName));
                    moduleName.ifPresent(externalModuleNamesRequired::add);
                    if (moduleName.isPresent()) {
                        externalContracts.add(genericTypeName);
                    } else {
                        contracts.add(genericTypeName);
                    }
                }
            }
        }

        AnnotationAndValue externalContractAnno = AnnotationAndValueDefault
                .findFirst(ExternalContracts.class, typeInfo.annotations())
                .orElse(null);
        if (externalContractAnno != null) {
            String[] externalContractNames = externalContractAnno.value("value").orElse("").split(",[ \t]*");
            for (String externalContractName : externalContractNames) {
                TypeName externalContractTypeName = createFromTypeName(externalContractName);
                externalContracts.add(externalContractTypeName);
                filterModuleName(typeInfo.moduleNameOf(externalContractTypeName)).ifPresent(externalModuleNamesRequired::add);
            }

            String[] moduleNames = externalContractAnno.value("moduleNames").orElse("").split(",[ \t]*");
            for (String externalModuleName : moduleNames) {
                if (!externalModuleName.isBlank()) {
                    externalModuleNamesRequired.add(externalModuleName);
                }
            }
        }

        // process parent hierarchy
        typeInfo.superTypeInfo().ifPresent(it -> gatherContracts(contracts,
                                                                 externalContracts,
                                                                 providerForSet,
                                                                 externalModuleNamesRequired,
                                                                 it,
                                                                 true));
        typeInfo.interfaceTypeInfo().forEach(it -> gatherContracts(contracts,
                                                                   externalContracts,
                                                                   providerForSet,
                                                                   externalModuleNamesRequired,
                                                                   it,
                                                                   true));
    }

    private Optional<String> filterModuleName(Optional<String> moduleName) {
        String name = moduleName.orElse(null);
        if (name != null && (name.startsWith("java.") || name.startsWith("jdk"))) {
            return Optional.empty();
        }
        return moduleName;
    }

    private ServiceInfoBasics toServiceInfo(TypeInfo service) {
        return ServiceInfoDefault.builder()
                .serviceTypeName(service.typeName().name())
                .declaredWeight(toWeight(service))
                .declaredRunLevel(toRunLevel(service))
                .scopeTypeNames(toScopeNames(service))
                .build();
    }

    private Optional<DependenciesInfo> toInjectionDependencies(TypeInfo service,
                                                               List<TypedElementName> allElementsOfInterest) {
        Dependencies.BuilderContinuation builder = Dependencies.builder(service.typeName().name());
        gatherInjectionPoints(builder, service, allElementsOfInterest);
        DependenciesInfo deps = builder.build();
        return deps.serviceInfoDependencies().isEmpty() ? Optional.empty() : Optional.of(deps);
    }

    private void gatherInjectionPoints(Dependencies.BuilderContinuation builder,
                                       TypeInfo service,
                                       List<TypedElementName> allElementsOfInterest) {
        List<TypedElementName> injectableElementsForThisService = allElementsOfInterest.stream()
                .filter(it -> it.enclosingTypeName().orElseThrow().equals(service.typeName()))
                .filter(it -> findFirst(Inject.class, it.annotations()).isPresent())
                .toList();
        injectableElementsForThisService.forEach(elem -> gatherInjectionPoints(builder, elem, service));

        // recursive up the hierarchy
        service.superTypeInfo().ifPresent(it -> gatherInjectionPoints(builder, it, allElementsOfInterest));
    }

    /**
     * Processes all of the injection points for the provided typed element, accumulating the result in the provided builder
     * continuation instance.
     *
     * @param builder  the builder continuation instance
     * @param typedElement the typed element to convert
     * @param service the type info of the backing service
     */
    private static void gatherInjectionPoints(Dependencies.BuilderContinuation builder,
                                              TypedElementName typedElement,
                                              TypeInfo service) {
        String elemName = typedElement.elementName();
        int elemArgs = typedElement.parameterArguments().size();
        AtomicInteger elemOffset = new AtomicInteger();
        typedElement.parameterArguments().forEach(it -> {
            TypeName typeName = it.typeName();
            boolean isOptional = typeName.isOptional();
            typeName = (isOptional) ? typeName.typeArguments().get(0) : typeName;
            boolean isList = typeName.isList();
            typeName = (isList) ? typeName.typeArguments().get(0) : typeName;
            boolean isProviderType = isProviderType(typeName);
            typeName = (isProviderType) ? typeName.typeArguments().get(0) : typeName;

            int pos = elemOffset.incrementAndGet();
            ElementInfo.Access access = toAccess(typedElement.modifierNames());
            Set<QualifierAndValue> qualifiers = toQualifiers(it, service);

            builder.add(service.typeName().name(),
                        elemName,
                        typeName.name(),
                        ElementInfo.ElementKind.valueOf(typedElement.elementTypeKind()),
                        elemArgs,
                        access)
                    .qualifiers(qualifiers)
                    .elemOffset(pos)
                    .listWrapped(isList)
                    .providerWrapped(isProviderType)
                    .optionalWrapped(isOptional);
        });
    }

    private void gatherElementsOfInterestInThisModule(List<TypedElementName> result) {
        Elements elementUtils = processingEnv.getElementUtils();
        for (String annoType : supportedElementTargetAnnotations()) {
            // annotation may not be on the classpath, in such a case just ignore it
            TypeElement annoTypeElement = elementUtils.getTypeElement(annoType);
            if (annoTypeElement != null) {
                Set<? extends Element> typesToProcess = utils.roundEnv().getElementsAnnotatedWith(annoTypeElement);
                typesToProcess.forEach(it -> result.add(createTypedElementNameFromElement(it, elementUtils).orElseThrow()));
            }
        }
    }

    private void gatherTypeInfosToProcessInThisModule(Map<TypeName, TypeInfo> result,
                                                      List<TypedElementName> elementsOfInterest) {
        // this section gathers based upon the class-level annotations in order to discover what to process
        for (String annoType : supportedServiceClassTargetAnnotations()) {
            // annotation may not be on the classpath, in such a case just ignore it
            Elements elements = processingEnv.getElementUtils();
            TypeElement annoTypeElement = elements.getTypeElement(annoType);
            if (annoTypeElement != null) {
                Set<? extends Element> typesToProcess = utils.roundEnv().getElementsAnnotatedWith(annoTypeElement);
                typesToProcess.forEach(it -> {
                    TypeName typeName = createTypeNameFromElement(it).orElseThrow().genericTypeName();
                    if (!result.containsKey(typeName)) {
                        TypeElement typeElement = (TypeElement) it;
                        Optional<TypeInfo> typeInfo =
                                utils.toTypeInfo(typeElement, typeElement.asType(), elementsOfInterest::contains);
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

        Elements elementUtils = processingEnv.getElementUtils();
        enclosingElementsOfInterest.forEach(it -> {
            TypeName typeName = it.genericTypeName();
            if (!result.containsKey(typeName)) {
                TypeElement element = requireNonNull(elementUtils.getTypeElement(it.name()), it.name());
                result.put(creator.toActivatorImplTypeName(typeName),
                           utils.toTypeInfo(element, element.asType(), elementsOfInterest::contains).orElseThrow());
            }
        });
    }

}
