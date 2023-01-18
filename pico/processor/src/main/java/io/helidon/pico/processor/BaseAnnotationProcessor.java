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

import java.io.File;
import java.lang.System.Logger.Level;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.tools.JavaFileObject;

import io.helidon.common.Weight;
import io.helidon.pico.Contract;
import io.helidon.pico.DefaultServiceInfo;
import io.helidon.pico.ExternalContracts;
import io.helidon.pico.InjectionPointInfo;
import io.helidon.pico.QualifierAndValue;
import io.helidon.pico.RunLevel;
import io.helidon.pico.ServiceInfoBasics;
import io.helidon.pico.tools.AbstractFilerMsgr;
import io.helidon.pico.tools.ActivatorCreator;
import io.helidon.pico.tools.ActivatorCreatorCodeGen;
import io.helidon.pico.tools.ActivatorCreatorRequest;
import io.helidon.pico.tools.ActivatorCreatorResponse;
import io.helidon.pico.tools.CodeGenFiler;
import io.helidon.pico.tools.CommonUtils;
import io.helidon.pico.tools.DefaultActivatorCreator;
import io.helidon.pico.tools.DefaultActivatorCreatorConfigOptions;
import io.helidon.pico.tools.DefaultGeneralCreatorRequest;
import io.helidon.pico.tools.GeneralCreatorRequest;
import io.helidon.pico.tools.InterceptionPlan;
import io.helidon.pico.tools.InterceptorCreator;
import io.helidon.pico.tools.JavaxTypeTools;
import io.helidon.pico.tools.ModuleUtils;
import io.helidon.pico.tools.Msgr;
import io.helidon.pico.tools.Options;
import io.helidon.pico.tools.ServicesToProcess;
import io.helidon.pico.tools.ToolsException;
import io.helidon.pico.tools.TypeTools;
import io.helidon.pico.tools.spi.ActivatorCreatorProvider;
import io.helidon.pico.tools.spi.InterceptorCreatorProvider;
import io.helidon.pico.types.AnnotationAndValue;
import io.helidon.pico.types.DefaultAnnotationAndValue;
import io.helidon.pico.types.DefaultTypeName;
import io.helidon.pico.types.TypeName;

import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import jakarta.annotation.Priority;
import jakarta.inject.Provider;
import jakarta.inject.Scope;
import jakarta.inject.Singleton;

import static io.helidon.builder.processor.tools.BuilderTypeTools.createTypeNameFromElement;
import static io.helidon.pico.tools.TypeTools.createAnnotationAndValueListFromElement;
import static io.helidon.pico.tools.TypeTools.createAnnotationAndValueSet;
import static io.helidon.pico.tools.TypeTools.createQualifierAndValueSet;
import static io.helidon.pico.tools.TypeTools.createTypeNameFromMirror;
import static io.helidon.pico.tools.TypeTools.isAbstract;
import static io.helidon.pico.tools.TypeTools.isProviderType;
import static io.helidon.pico.tools.TypeTools.needToDeclareModuleUsage;
import static io.helidon.pico.tools.TypeTools.oppositeOf;
import static io.helidon.pico.tools.TypeTools.toAccess;
import static javax.tools.Diagnostic.Kind;

/**
 * Abstract base for all Helidon Pico annotation processing.
 *
 * @param <B> the type handled by this anno processor
 */
//@SuppressWarnings("checkstyle:VisibilityModifier")
abstract class BaseAnnotationProcessor<B> extends AbstractProcessor implements Msgr {
    private final System.Logger logger = System.getLogger(getClass().getName());

    static final boolean MAYBE_ANNOTATIONS_CLAIMED_BY_THIS_PROCESSOR = false;
    static final String CONFIGURED_BY_TYPENAME = "io.helidon.pico.config.api.ConfiguredBy";
    static final String TARGET_DIR = "/target/";
    static final String SRC_MAIN_JAVA_DIR = "src/main/java";

    boolean processed;
    RoundEnvironment roundEnv;
    ActivatorCreatorResponse result;
    final ServicesToProcess services = ServicesToProcess.servicesInstance();
    final InterceptorCreator interceptorCreator = InterceptorCreatorProvider.instance();

    @Override
    public void init(ProcessingEnvironment processingEnv) {
        this.processingEnv = processingEnv;
        Options.init(processingEnv);
        debug("*** Processing " + getClass().getSimpleName() + " ***");
        super.init(processingEnv);
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return annoTypes().stream().map(Class::getName).collect(Collectors.toSet());
    }

    /**
     * The annotation types we handle.
     *
     * @return annotation types we handle
     */
    abstract Set<Class<? extends Annotation>> annoTypes();

    /**
     * If these annotation type names are found on the target then do not process.
     *
     * @return the set of annotation type names that should result in skipped processing for this processor type
     */
    Set<String> contraAnnotations() {
        return Set.of();
    }

    @Override
    public boolean process(
            Set<? extends TypeElement> annotations,
            RoundEnvironment roundEnv) {
        this.processed = true;
        this.roundEnv = roundEnv;

        try {
            ServicesToProcess.onBeginProcessing(this, annotations, roundEnv);

            if (!roundEnv.processingOver()) {
                for (Class<? extends Annotation> annoType : annoTypes()) {
                    Set<? extends Element> typesToProcess = roundEnv.getElementsAnnotatedWith(annoType);

                    Set<String> contraAnnotations = contraAnnotations();
                    if (!contraAnnotations.isEmpty()) {
                        // filter out the ones we will not do...
                        typesToProcess = typesToProcess.stream()
                                .filter(it -> !containsAnyAnnotation(it, contraAnnotations))
                                .collect(Collectors.toSet());
                    }

                    doBulkInner(typesToProcess, null, null);
                }
            }

            boolean claimedResult = doFiler(roundEnv);
            ServicesToProcess.onEndProcessing(this, annotations, roundEnv);
            return claimedResult;
        } catch (Throwable t) {
            error(getClass().getSimpleName() + " error during processing; " + t + " @ "
                          + CommonUtils.rootStackTraceElementOf(t), t);
            // we typically will not even get to this next line since the messager.error() call will trigger things to halt
            throw new ToolsException("error during processing: " + t + " @ "
                                             + CommonUtils.rootStackTraceElementOf(t), t);
        }
    }

    private boolean containsAnyAnnotation(
            Element element,
            Set<String> contraAnnotations) {
        List<AnnotationAndValue> annotationAndValues = createAnnotationAndValueListFromElement(element, processingEnv.getElementUtils());
        Optional<AnnotationAndValue> annotation = annotationAndValues.stream()
                .filter(it -> contraAnnotations.contains(it.typeName().name()))
                .findFirst();
        return annotation.isPresent();
    }

    int doBulkInner(
            Set<? extends Element> typesToProcess,
            TypeName serviceTypeName,
            B builder) {
        int injectedCtorCount = 0;

        try {
            for (Element typeToProcess : typesToProcess) {
                assert (Objects.nonNull(typeToProcess));
                if (serviceTypeName != null && !typeToProcess.getEnclosingElement().toString()
                        .equals(serviceTypeName.name())) {
                    continue;
                }

                if (typeToProcess instanceof TypeElement) {
                    doInner((TypeElement) typeToProcess, builder);
                } else if (typeToProcess instanceof ExecutableElement) {
                    doInner((ExecutableElement) typeToProcess, builder);
                    if (typeToProcess.getKind() == ElementKind.CONSTRUCTOR) {
                        injectedCtorCount++;
                    }
                } else if (typeToProcess instanceof VariableElement) {
                    doInner(null,
                            (VariableElement) typeToProcess,
                            builder,
                            null,
                            0,
                            null,
                            null,
                            null,
                            null);
                }
            }
        } catch (Throwable t) {
            throw new ToolsException("handling: " + typesToProcess + " for " + serviceTypeName + ": " + t, t);
        }

        return injectedCtorCount;
    }

    void doInner(
            ExecutableElement method,
            B builder) {
        // NOP
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    void doInner(
            String serviceTypeName,
            VariableElement var,
            B builder,
            String elemName,
            int elemArgs,
            Integer elemOffset,
            InjectionPointInfo.ElementKind elemKind,
            InjectionPointInfo.Access access,
            Boolean isStaticAlready) {
        // NOP
    }

    void doInner(
            TypeElement type,
            B builder) {
        TypeName serviceTypeName = createTypeNameFromElement(type).orElse(null);
        if (serviceTypeName != null) {
            processServiceType(serviceTypeName, type);
        }
    }

    void processServiceType(
            TypeName serviceTypeName,
            TypeElement type) {
        maybeSetBasicsForServiceType(serviceTypeName, type);
        maybeSetContractsAndModulesForServiceType(serviceTypeName, type);
        maybeSetInterceptorPlanForServiceType(serviceTypeName, type);
    }

    void maybeSetInterceptorPlanForServiceType(
            TypeName serviceTypeName,
            TypeElement ignoredType) {
        if (services.hasVisitedInterceptorPlanFor(serviceTypeName)) {
            return;
        }

        // note: it is important to use this class' CL since maven will not give us the "right" one.
        InterceptorCreator.InterceptorProcessor processor = interceptorCreator.createInterceptorProcessor(
                toInterceptedServiceInfoFor(serviceTypeName), interceptorCreator, Optional.of(processingEnv));
        Set<String> annotationTypeTriggers = processor.allAnnotationTypeTriggers();
        if (annotationTypeTriggers.isEmpty()) {
            services.addInterceptorPlanFor(serviceTypeName, Optional.empty());
            return;
        }

        Optional<InterceptionPlan> plan = processor.createInterceptorPlan(annotationTypeTriggers);
        if (plan.isEmpty()) {
            warn("expected to see an interception plan for: " + serviceTypeName);
        }
        services.addInterceptorPlanFor(serviceTypeName, plan);
    }

    ServiceInfoBasics toInterceptedServiceInfoFor(
            TypeName serviceTypeName) {
        return DefaultServiceInfo.builder()
                .serviceTypeName(serviceTypeName.name())
                .weight(services.weightedPriorities().get(serviceTypeName))
                .scopeTypeNames(services.scopeTypeNames().get(serviceTypeName))
                .build();
    }

    void maybeSetContractsAndModulesForServiceType(
            TypeName serviceTypeName,
            TypeElement type) {
        if (services.hasContractsFor(serviceTypeName)) {
            return;
        }

        Set<TypeName> providerForSet = new LinkedHashSet<>();
        Set<String> externalModuleNamesRequired = new LinkedHashSet<>();
        Set<TypeName> contracts = toContracts(type, providerForSet);
        Set<TypeName> externalContracts = toExternalContracts(type, externalModuleNamesRequired);
        adjustContractsForExternals(contracts, externalContracts, externalModuleNamesRequired);
        Set<TypeName> allContracts = new LinkedHashSet<>();
        allContracts.addAll(contracts);
        allContracts.addAll(externalContracts);
        allContracts.addAll(providerForSet);

        debug("found contracts " + allContracts + " for " + serviceTypeName);
        for (TypeName contract : allContracts) {
            boolean isExternal = externalContracts.contains(contract);
            services.addTypeForContract(serviceTypeName, contract, isExternal);
        }
        if (!providerForSet.isEmpty()) {
            services.addProviderFor(serviceTypeName, providerForSet);
        }
        if (!externalModuleNamesRequired.isEmpty()) {
            services.addExternalRequiredModules(serviceTypeName, externalModuleNamesRequired);
        }
    }

    void maybeSetBasicsForServiceType(
            TypeName serviceTypeName,
            TypeElement type) {
        if (services.hasHierarchyFor(serviceTypeName)) {
            return;
        }

        debug("processing service type basics for " + serviceTypeName);
        if (type == null) {
            type = processingEnv.getElementUtils().getTypeElement(serviceTypeName.name());
            if (type == null) {
                warn("expected to find a typeElement for " + serviceTypeName);
                return;
            }
        }

        services.addServiceTypeHierarchy(serviceTypeName, toServiceTypeHierarchy(type, true));

        TypeElement superTypeElement = TypeTools.toTypeElement(type.getSuperclass()).orElseThrow();
        TypeName parentServiceTypeName = createTypeNameFromElement(superTypeElement).orElse(null);
        boolean acceptedParent = services.addParentServiceType(serviceTypeName, parentServiceTypeName);
        services.addAccessLevel(serviceTypeName, toAccess(type));
        services.addIsAbstract(serviceTypeName, isAbstract(type));

        RunLevel runLevel = type.getAnnotation(RunLevel.class);
        if (Objects.nonNull(runLevel)) {
            services.addDeclaredRunLevel(serviceTypeName, runLevel.value());
        }

        List<String> scopeAnnotations = annotationsWithAnnotationOf(type, Scope.class);
        scopeAnnotations.forEach(scope -> services.addScopeTypeName(serviceTypeName, scope));
        if (scopeAnnotations.contains(UnsupportedConstructsProcessor.APPLICATION_SCOPED_TYPE_NAME)
                    && Options.isOptionEnabled(Options.TAG_MAP_APPLICATION_TO_SINGLETON_SCOPE)) {
            services.addScopeTypeName(serviceTypeName, Singleton.class.getName());
        }

        Set<QualifierAndValue> qualifiers = createQualifierAndValueSet(type);
        if (!qualifiers.isEmpty()) {
            services.addQualifiers(serviceTypeName, qualifiers);
        }

        Weight weight = type.getAnnotation(Weight.class);
        if (weight != null) {
            services.addDeclaredWeight(serviceTypeName, weight.value());
        } else {
            processPriority(serviceTypeName, type);
        }
    }

    boolean processPriority(
            TypeName serviceTypeName,
            TypeElement type) {
        Priority priority = type.getAnnotation(Priority.class);
        if (priority != null) {
            services.addDeclaredWeight(serviceTypeName, (double) priority.value());
            return true;
        } else if (JavaxTypeTools.INSTANCE.get() != null) {
            Integer priorityVal = JavaxTypeTools.INSTANCE.get().priorityOf(serviceTypeName, type).orElse(null);
            if (priorityVal != null) {
                services.addDeclaredWeight(serviceTypeName, (double) priorityVal);
                return true;
            }
        }
        return false;
    }

    List<TypeName> toServiceTypeHierarchy(
            TypeElement type,
            boolean includeSelf) {
        List<TypeName> result = new LinkedList<>();
        if (!includeSelf) {
            TypeMirror mirror = type.getSuperclass();
            type = TypeTools.toTypeElement(mirror).orElse(null);
        }
        while (type != null) {
            result.add(0, createTypeNameFromElement(type).orElseThrow());
            TypeMirror mirror = type.getSuperclass();
            type = TypeTools.toTypeElement(mirror).orElse(null);
        }
        return result;
    }

    void adjustContractsForExternals(
            Set<TypeName> contracts,
            Set<TypeName> externalContracts,
            Set<String> externalModuleNamesRequired) {
        AtomicReference<String> externalModuleName = new AtomicReference<>();
        for (TypeName contract : contracts) {
            if (!isInThisModule(toTypeElement(contract), externalModuleName)) {
                maybeAddExternalModule(externalModuleName.get(), externalModuleNamesRequired);
                externalContracts.add(contract);
            }
        }

        for (TypeName externalContract : externalContracts) {
            if (isInThisModule(toTypeElement(externalContract), externalModuleName)) {
                warn(externalContract + " is actually in this module and therefore should not be labelled as external.", null);
                maybeAddExternalModule(externalModuleName.get(), externalModuleNamesRequired);
            }
        }

        contracts.removeAll(externalContracts);
    }

    void maybeAddExternalModule(
            String externalModuleName,
            Set<String> externalModuleNamesRequired) {
        if (needToDeclareModuleUsage(externalModuleName)) {
            externalModuleNamesRequired.add(externalModuleName);
        }
    }

    boolean isInThisModule(
            TypeElement type,
            AtomicReference<String> moduleName) {
        if (roundEnv.getRootElements().contains(type)) {
            return true;
        }

        moduleName.set(null);
        // if there is no module-info in use we need to try to find the type is in our source path and if
        // not found then assume it is external
        try {
            Trees trees = Trees.instance(processingEnv);
            TreePath path = trees.getPath(type);
            if (Objects.isNull(path)) {
                return false;
            }
            JavaFileObject sourceFile = path.getCompilationUnit().getSourceFile();
            Optional<File> file = ModuleUtils.toFile(sourceFile.toUri());
            Optional<File> srcPath = ModuleUtils.toSourcePath(file, type);
            srcPath.ifPresent(services::lastKnownSourcePathBeingProcessed);
            return true;
        } catch (Throwable t) {
            debug("unable to determine if contract is external: " + type + "; " + t.getMessage(), t);
        }

        ModuleElement module = processingEnv.getElementUtils().getModuleOf(type);
        if (!module.isUnnamed()) {
            String name = module.getQualifiedName().toString();
            moduleName.set(name);
        }

        // assumed external, but unknown module name
        return false;
    }

    List<String> annotationsWithAnnotationOf(
            TypeElement type,
            Class<? extends Annotation> annotation) {
        List<String> list = new ArrayList<>();
        type.getAnnotationMirrors()
                .forEach(am -> {
                    if (Objects.nonNull(am.getAnnotationType().asElement().getAnnotation(annotation))) {
                        list.add(am.getAnnotationType().asElement().toString());
                    }
                });

        if (list.isEmpty() && Objects.nonNull(JavaxTypeTools.INSTANCE.get())) {
            List<String> list2 = JavaxTypeTools.INSTANCE.get()
                    .annotationsWithAnnotationOf(type, oppositeOf(annotation.getName()));
            if (!list2.isEmpty()) {
                return list2;
            }
        }

        return list;
    }

    CodeGenFiler createCodeGenFiler() {
        AbstractFilerMsgr filer = AbstractFilerMsgr.createAnnotationBasedFiler(processingEnv, this);
        return new CodeGenFiler(filer);
    }

    boolean doFiler(
            RoundEnvironment roundEnv) {
        // don't do filer until very end of the round
        boolean isProcessingOver = roundEnv.processingOver();
        ActivatorCreator creator = ActivatorCreatorProvider.instance();
        CodeGenFiler filer = createCodeGenFiler();

        Map<TypeName, InterceptionPlan> interceptionPlanMap = services.interceptorPlans();
        if (!interceptionPlanMap.isEmpty()) {
            GeneralCreatorRequest req = DefaultGeneralCreatorRequest.builder().filer(filer);
            creator.codegenInterceptors(req, interceptionPlanMap);
            services.clearInterceptorPlans();
        }

        if (!isProcessingOver) {
            return MAYBE_ANNOTATIONS_CLAIMED_BY_THIS_PROCESSOR;
        }

        ActivatorCreatorCodeGen codeGen = DefaultActivatorCreator.createActivatorCreatorCodeGen(services).orElse(null);
        if (codeGen == null) {
            return MAYBE_ANNOTATIONS_CLAIMED_BY_THIS_PROCESSOR;
        }

        DefaultActivatorCreatorConfigOptions configOptions = DefaultActivatorCreatorConfigOptions.builder()
                .applicationPreCreated(Options.isOptionEnabled(Options.TAG_APPLICATION_PRE_CREATE))
                .moduleCreated(isProcessingOver)
                .build();
        ActivatorCreatorRequest req = DefaultActivatorCreator
                .createActivatorCreatorRequest(services, codeGen, configOptions, filer, false);

        try {
            services.lastGeneratedPackageName(req.packageName().orElseThrow());
            ActivatorCreatorResponse res = creator.createModuleActivators(req);
            this.result = res;
            if (!res.success()) {
                throw new ToolsException("error during codegen", res.error().orElse(null));
            }
        } catch (Exception te) {
            Object hierarchy = codeGen.serviceTypeHierarchy();
            if (hierarchy == null) {
                warn("expected to have a known service type hierarchy in the context");
            } else {
                debug("service type hierarchy is " + hierarchy);
            }

            ToolsException revisedTe = new ToolsException("error in annotation processing round for "
                                                        + req.serviceTypeNames(), te);
            error(revisedTe.getMessage(), revisedTe);
        } finally {
            if (isProcessingOver) {
                processingEnv.getMessager().printMessage(Kind.NOTE, getClass().getSimpleName()
                        + ": processing is over - resetting");
                services.reset(false);
            }
        }

        // allow other processors to also process these same annotations?
        return MAYBE_ANNOTATIONS_CLAIMED_BY_THIS_PROCESSOR;
    }

    Set<TypeName> toContracts(
            TypeElement type,
            Set<TypeName> providerForSet) {
        Set<TypeName> result = new LinkedHashSet<>();

        Set<TypeMirror> processed = new LinkedHashSet<>();
        gatherContractsToBeProcessed(processed, type);

        for (TypeMirror possibleContract : processed) {
            TypeElement teContract = TypeTools.toTypeElement(possibleContract).orElse(null);
            if (teContract == null) {
                continue;
            }

            TypeName parentTe = createTypeNameFromElement(teContract).orElse(null);
            if (Objects.nonNull(teContract.getAnnotation(Contract.class))) {
                result.add(parentTe);
                continue;
            } else if (Options.isOptionEnabled(Options.TAG_AUTO_ADD_NON_CONTRACT_INTERFACES)
                            && (ElementKind.INTERFACE == teContract.getKind())) {
                result.add(parentTe);
                // fall in the next section, skip continue here
            } else if (services.serviceTypeNames().contains(parentTe)) {
                result.add(parentTe);
            }

            String potentialProviderClassName = (1 == teContract.getTypeParameters().size())
                    ? teContract.getQualifiedName().toString() : null;
            boolean isProviderType = Objects.nonNull(potentialProviderClassName)
                    && isProviderType(potentialProviderClassName);
            if (!isProviderType) {
                continue;
            }

            TypeParameterElement tpe = teContract.getTypeParameters().get(0);
            if (1 != tpe.getBounds().size()) {
                continue;
            }

            TypeMirror gType = ((DeclaredType) possibleContract).getTypeArguments().get(0);
            if (Objects.isNull(gType)) {
                continue;
            }

            TypeName gTypeName = createTypeNameFromMirror(gType).orElse(null);
            if (gTypeName == null) {
                continue;
            }

            // if we made it here then this provider qualifies, and we take what it provides into the result
            TypeName teContractName = createTypeNameFromElement(teContract).orElseThrow();
            result.add(teContractName);
            if (isProviderType(teContractName.name())) {
                result.add(DefaultTypeName.create(Provider.class));
            }
            providerForSet.add(gTypeName);
        }

        if (!result.isEmpty()) {
            debug("Contracts for " + type + " was " + result + " w/ providerSet " + providerForSet);
        }
        return result;
    }

    void gatherContractsToBeProcessed(
            Set<TypeMirror> processed,
            TypeElement typeElement) {
        if (typeElement == null) {
            return;
        }

        typeElement.getInterfaces().forEach((tm) -> {
            processed.add(tm);
            gatherContractsToBeProcessed(processed, TypeTools.toTypeElement(tm).orElse(null));
        });

        toServiceTypeHierarchy(typeElement, false).stream()
                .map((te) -> toTypeElement(te).asType())
                .forEach((tm) -> {
                    processed.add(tm);
                    gatherContractsToBeProcessed(processed, TypeTools.toTypeElement(tm).orElse(null));
                });
    }

    Set<TypeName> toExternalContracts(
            TypeElement type,
            Set<String> externalModulesRequired) {
        Set<TypeName> result = new LinkedHashSet<>();

        Stack<TypeMirror> stack = new Stack<>();
        stack.push(type.asType());
        stack.addAll(type.getInterfaces());
        stack.add(type.getSuperclass());

        TypeMirror iface;
        while (!stack.isEmpty()) {
            iface = stack.pop();
            TypeElement teContract = TypeTools.toTypeElement(iface).orElse(null);
            if (teContract == null) {
                continue;
            }

            stack.addAll(teContract.getInterfaces());
            stack.add(teContract.getSuperclass());

            ExternalContracts externalContracts = teContract.getAnnotation(ExternalContracts.class);
            if (Objects.nonNull(externalContracts)) {
                Collection<AnnotationAndValue> annotations = createAnnotationAndValueSet(teContract);
                Optional<? extends AnnotationAndValue> annotation = DefaultAnnotationAndValue
                        .findFirst(ExternalContracts.class.getName(), annotations);
                List<String> values = (annotation.isPresent() && annotation.get().value().isPresent())
                        ? CommonUtils.toList(annotation.get().value().get()) : List.of();
                for (String externalContract : values) {
                    result.add(DefaultTypeName.createFromTypeName(externalContract));
                }
                if (externalContracts.moduleNames() != null) {
                    externalModulesRequired.addAll(Arrays.asList(externalContracts.moduleNames()));
                }
                continue;
            }

            String potentialProviderClassName = (1 == teContract.getTypeParameters().size())
                    ? teContract.getQualifiedName().toString() : null;
            boolean isProviderType = Objects.nonNull(potentialProviderClassName)
                    && isProviderType(potentialProviderClassName);
            if (!isProviderType) {
                continue;
            }

            TypeParameterElement tpe = teContract.getTypeParameters().get(0);
            if (1 != tpe.getBounds().size()) {
                continue;
            }

            TypeMirror gType = ((DeclaredType) iface).getTypeArguments().get(0);
            if (Objects.nonNull(gType)) {
                stack.add(gType);
            }
        }

        if (!result.isEmpty()) {
            debug("ExternalContracts for " + type + " was " + result + " w/ modulesRequired " + externalModulesRequired);
        }
        return result;
    }

    TypeElement toTypeElement(
            TypeName typeName) {
        return Objects.requireNonNull(processingEnv.getElementUtils().getTypeElement(typeName.name()));
    }

    System.Logger logger() {
        return logger;
    }

    Level loggerLevel() {
        return (Options.isOptionEnabled(Options.TAG_DEBUG)) ? Level.INFO : Level.DEBUG;
    }

    @Override
    public void debug(
            String message,
            Throwable t) {
        if (Options.isOptionEnabled(Options.TAG_DEBUG)) {
            logger.log(loggerLevel(), getClass().getSimpleName() + ": Debug: " + message, t);
        }
        if (processingEnv != null && processingEnv.getMessager() != null) {
            processingEnv.getMessager().printMessage(Kind.OTHER, message);
        }
    }

    @Override
    public void debug(
            String message) {
        if (Options.isOptionEnabled(Options.TAG_DEBUG)) {
            logger.log(loggerLevel(), getClass().getSimpleName() + ": Debug: " + message);
        }
        if (processingEnv != null && processingEnv.getMessager() != null) {
            processingEnv.getMessager().printMessage(Kind.OTHER, message);
        }
    }

    @Override
    public void log(
            String message) {
        //        logger.log(getLevel(), getClass().getSimpleName() + ": Note: " + message);
        if (processingEnv != null && processingEnv.getMessager() != null) {
            processingEnv.getMessager().printMessage(Kind.NOTE, message);
        }
    }

    @Override
    public void warn(
            String message,
            Throwable t) {
        if (Options.isOptionEnabled(Options.TAG_DEBUG) && t != null) {
            logger.log(Level.WARNING, getClass().getSimpleName() + ": Warning: " + message);
            t.printStackTrace();
        }
        if (processingEnv != null && processingEnv.getMessager() != null) {
            processingEnv.getMessager().printMessage(Kind.WARNING, message);
        }
    }

    @Override
    public void warn(
            String message) {
        if (Options.isOptionEnabled(Options.TAG_DEBUG)) {
            logger.log(Level.WARNING, getClass().getSimpleName() + ": Warning: " + message);
        }
        if (processingEnv != null && processingEnv.getMessager() != null) {
            processingEnv.getMessager().printMessage(Kind.WARNING, message);
        }
    }

    @Override
    public void error(
            String message,
            Throwable t) {
        logger.log(Level.ERROR, getClass().getSimpleName() + ": Error: " + message, t);
        if (processingEnv != null && processingEnv.getMessager() != null) {
            processingEnv.getMessager().printMessage(Kind.ERROR, message);
        }
    }

    static boolean hasValue(
            String val) {
        return (val != null && !val.isBlank());
    }

}
