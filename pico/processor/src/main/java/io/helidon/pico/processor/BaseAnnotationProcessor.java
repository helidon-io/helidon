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

package io.helidon.pico.processor;

import java.io.File;
import java.lang.System.Logger.Level;
import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
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

import io.helidon.common.HelidonServiceLoader;
import io.helidon.common.LazyValue;
import io.helidon.common.Weight;
import io.helidon.pico.Contract;
import io.helidon.pico.ExternalContracts;
import io.helidon.pico.RunLevel;
import io.helidon.pico.types.AnnotationAndValue;
import io.helidon.pico.types.DefaultAnnotationAndValue;
import io.helidon.pico.DefaultServiceInfo;
import io.helidon.pico.types.DefaultTypeName;
import io.helidon.pico.InjectionPointInfo;
import io.helidon.pico.QualifierAndValue;
import io.helidon.pico.ServiceInfoBasics;
import io.helidon.pico.types.TypeName;
import io.helidon.pico.tools.ToolsException;
import io.helidon.pico.tools.creator.ActivatorCreatorResponse;
import io.helidon.pico.tools.creator.InterceptionPlan;
import io.helidon.pico.tools.creator.InterceptorCreator;
import io.helidon.pico.tools.creator.impl.AbstractCreator;
import io.helidon.pico.tools.creator.impl.AbstractFilerMsgr;
import io.helidon.pico.tools.creator.impl.CodeGenFiler;
import io.helidon.pico.tools.creator.impl.DefaultActivatorCreator;
import io.helidon.pico.tools.creator.impl.DefaultActivatorCreatorCodeGen;
import io.helidon.pico.tools.creator.impl.DefaultActivatorCreatorConfigOptions;
import io.helidon.pico.tools.creator.impl.DefaultActivatorCreatorRequest;
import io.helidon.pico.tools.creator.impl.Msgr;
import io.helidon.pico.tools.processor.JavaxTypeTools;
import io.helidon.pico.tools.processor.Options;
import io.helidon.pico.tools.processor.ServicesToProcess;
import io.helidon.pico.tools.processor.TypeTools;
import io.helidon.pico.tools.utils.CommonUtils;
import io.helidon.pico.tools.utils.ModuleUtils;

import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import jakarta.annotation.Priority;
import jakarta.inject.Provider;
import jakarta.inject.Scope;
import jakarta.inject.Singleton;

import static io.helidon.pico.tools.creator.impl.DefaultInterceptorCreator.InterceptorProcessor;
import static io.helidon.pico.tools.creator.impl.DefaultInterceptorCreator.createInterceptorProcessorFromProcessor;
import static io.helidon.pico.tools.processor.TypeTools.createQualifierAndValueSet;
import static io.helidon.pico.tools.processor.TypeTools.createTypeNameFromElement;
import static io.helidon.pico.tools.processor.TypeTools.createTypeNameFromMirror;
import static io.helidon.pico.tools.processor.TypeTools.isAbstract;
import static io.helidon.pico.tools.processor.TypeTools.isProviderType;
import static io.helidon.pico.tools.processor.TypeTools.toAccess;
import static javax.tools.Diagnostic.Kind;

/**
 * Abstract base for all Helidon Pico annotation processing.
 *
 * @param <B> the type(s) handled by this anno processor
 */
@SuppressWarnings("checkstyle:VisibilityModifier")
public abstract class BaseAnnotationProcessor<B> extends AbstractProcessor implements Msgr {
    /**
     * Logger.
     */
    protected final System.Logger logger = System.getLogger(getClass().getName());

    protected static final String CONFIGURED_BY_TYPENAME = "io.helidon.pico.config.api.ConfiguredBy";

    static final boolean MAYBE_ANNOTATIONS_CLAIMED_BY_THIS_PROCESSOR = false;

    static final String TARGET_DIR = "/target/";
    static final String SRC_MAIN_JAVA_DIR = "src/main/java";

    boolean processed;
    RoundEnvironment roundEnv;
    final ServicesToProcess services = ServicesToProcess.getServicesInstance();
    ActivatorCreatorResponse result;

    // note: it is important to use this class' CL since maven will not give us the "right" one.
    LazyValue<InterceptorCreator> interceptorCreator = LazyValue.create(() ->
        HelidonServiceLoader.create(ServiceLoader.load(InterceptorCreator.class,
                                                       InterceptorCreator.class.getClassLoader())).iterator().next());

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
        return getAnnoTypes().stream().map(Class::getName).collect(Collectors.toSet());
    }

    /**
     * If these annotation type names are found on the target then do not process.
     *
     * @return the set of annotation type names that should result in skipped processing for this processor type
     */
    public Set<String> getContraAnnotations() {
        return Collections.emptySet();
    }

    /**
     * @return Indicators to signal need to process this type.
     */
    protected abstract Set<Class<? extends Annotation>> getAnnoTypes();

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        this.processed = true;
        this.roundEnv = roundEnv;

        try {
            ServicesToProcess.onBeginProcessing(this, annotations, roundEnv);

            if (!roundEnv.processingOver()) {
                for (Class<? extends Annotation> annoType : getAnnoTypes()) {
                    Set<? extends Element> typesToProcess = roundEnv.getElementsAnnotatedWith(annoType);

                    Set<String> contraAnnotations = getContraAnnotations();
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
                          + CommonUtils.rootErrorCoordinateOf(t), t);
            // we typically will not even get to this next line since the messager.error() call will trigger things to halt
            throw new ToolsException("error during processing: " + t + " @ "
                                             + CommonUtils.rootErrorCoordinateOf(t), t);
        }
    }

    private boolean containsAnyAnnotation(Element element, Set<String> contraAnnotations) {
        List<AnnotationAndValue> annotationAndValues = TypeTools
                .createAnnotationAndValueListFromElement(element, processingEnv.getElementUtils());
        Optional<AnnotationAndValue> annotation = annotationAndValues.stream()
                .filter(it -> contraAnnotations.contains(it.typeName().name()))
                .findFirst();
        return annotation.isPresent();
    }

    protected int doBulkInner(Set<? extends Element> typesToProcess,
                           TypeName serviceTypeName,
                           B builder) {
        int injectedCtorCount = 0;

        try {
            for (Element typeToProcess : typesToProcess) {
                assert (Objects.nonNull(typeToProcess));
                if (Objects.nonNull(serviceTypeName) && !typeToProcess.getEnclosingElement().toString()
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

    protected void doInner(ExecutableElement method, B builder) {
        // NOP
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    protected void doInner(String serviceTypeName,
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

    protected void doInner(TypeElement type, B builder) {
        TypeName serviceTypeName = createTypeNameFromElement(type);
        if (!Objects.isNull(serviceTypeName)) {
            processServiceType(serviceTypeName, type);
        }
    }

    protected void processServiceType(TypeName serviceTypeName, TypeElement type) {
        maybeSetBasicsForServiceType(serviceTypeName, type);
        maybeSetContractsAndModulesForServiceType(serviceTypeName, type);
        maybeSetInterceptorPlanForServiceType(serviceTypeName, type);
    }

    protected void maybeSetInterceptorPlanForServiceType(TypeName serviceTypeName, TypeElement ignoredType) {
        if (services.hasVisitedInterceptorPlanFor(serviceTypeName)) {
            return;
        }

        // note: it is important to use this class' CL since maven will not give us the "right" one.
        InterceptorProcessor processor = createInterceptorProcessorFromProcessor(
                interceptedServiceInfoFor(serviceTypeName), interceptorCreator.get(), processingEnv);
        Set<String> annotationTypeTriggers = processor.getAllAnnotationTypeTriggers();
        if (annotationTypeTriggers.isEmpty()) {
            services.interceptorPlanFor(serviceTypeName, null);
            return;
        }

        InterceptionPlan plan = processor.createInterceptorPlan(annotationTypeTriggers);
        if (Objects.isNull(plan)) {
            warn("expected to see an interception plan for: " + serviceTypeName, null);
            services.interceptorPlanFor(serviceTypeName, null);
            return;
        }

        services.interceptorPlanFor(serviceTypeName, plan);
    }

    protected ServiceInfoBasics interceptedServiceInfoFor(TypeName serviceTypeName) {
        return DefaultServiceInfo.builder()
                .serviceTypeName(serviceTypeName.name())
                .weight(services.getServicesToWeightedPriorities().get(serviceTypeName))
                .scopeTypeNames(services.getServicesToScopeTypeNames().get(serviceTypeName))
                .build();
    }

    protected void maybeSetContractsAndModulesForServiceType(TypeName serviceTypeName, TypeElement type) {
        if (services.hasContractsFor(serviceTypeName)) {
            return;
        }

        Set<TypeName> providerForSet = new LinkedHashSet<>();
        Set<String> externalModuleNamesRequired = new LinkedHashSet<>();
        Set<TypeName> contracts = getContracts(type, providerForSet);
        Set<TypeName> externalContracts = getExternalContracts(type, externalModuleNamesRequired);
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
            services.setProviderFor(serviceTypeName, providerForSet);
        }
        if (!externalModuleNamesRequired.isEmpty()) {
            services.addExternalRequiredModules(serviceTypeName, externalModuleNamesRequired);
        }
    }

    protected void maybeSetBasicsForServiceType(TypeName serviceTypeName, TypeElement type) {
        if (services.hasHierarchyFor(serviceTypeName)) {
            return;
        }

        debug("processing service type basics for " + serviceTypeName);
        if (Objects.isNull(type)) {
            type = processingEnv.getElementUtils().getTypeElement(serviceTypeName.name());
            if (Objects.isNull(type)) {
                warn("expected to find a typeElement for " + serviceTypeName, null);
                return;
            }
        }

        services.setServiceTypeHierarchy(serviceTypeName, toServiceTypeHierarchy(type, true));

        TypeName parentServiceTypeName = createTypeNameFromElement(TypeTools.toTypeElement(type.getSuperclass()));
        boolean acceptedParent = services.setParentServiceType(serviceTypeName, parentServiceTypeName);
//        ByteArrayOutputStream baos = new ByteArrayOutputStream();
//        PrintStream ps = new PrintStream(baos);
//        new PicoException(getClass().getSimpleName() + ": " + serviceTypeName + " parent here: "
//                + "accepted = " + acceptedParent + "; " + " map = " + services.getServiceTypeToParentServiceTypes()
//                                  + "; " + parentServiceTypeName).printStackTrace(ps);
//        ps.close();
//        processingEnv.getMessager().printMessage(Kind.NOTE, baos.toString());

        services.setServiceTypeAccessLevel(serviceTypeName, toAccess(type));
        services.setServiceTypeIsAbstract(serviceTypeName, isAbstract(type));

        RunLevel runLevel = type.getAnnotation(RunLevel.class);
        if (Objects.nonNull(runLevel)) {
            services.setRunLevel(serviceTypeName, runLevel.value());
        }

        List<String> scopeAnnotations = getAnnotationsWithAnnotation(type, Scope.class);
        scopeAnnotations.forEach(scope -> services.addScopeTypeName(serviceTypeName, scope));
        if (scopeAnnotations.contains(UnsupportedConstructsProcessor.APPLICATION_SCOPED_TYPE_NAME)
                    && Options.isOptionEnabled(Options.TAG_MAP_APPLICATION_TO_SINGLETON_SCOPE)) {
            services.addScopeTypeName(serviceTypeName, Singleton.class.getName());
        }

        Set<QualifierAndValue> qualifiers = createQualifierAndValueSet(type);
        if (!qualifiers.isEmpty()) {
            services.setQualifiers(serviceTypeName, qualifiers);
        }

        Weight weight = type.getAnnotation(Weight.class);
        if (Objects.nonNull(weight)) {
            services.setWeightedPriority(serviceTypeName, weight.value());
        } else {
            processPriority(serviceTypeName, type);
        }
    }

    protected boolean processPriority(TypeName serviceTypeName, TypeElement type) {
        Priority priority = type.getAnnotation(Priority.class);
        if (Objects.nonNull(priority)) {
            services.setWeightedPriority(serviceTypeName, (double) priority.value());
            return true;
        } else if (Objects.nonNull(JavaxTypeTools.INSTANCE.get())) {
            Integer priorityVal = JavaxTypeTools.INSTANCE.get().getPriority(serviceTypeName, type);
            if (Objects.nonNull(priorityVal)) {
                services.setWeightedPriority(serviceTypeName, (double) priorityVal);
                return true;
            }
        }
        return false;
    }

    protected List<TypeName> toServiceTypeHierarchy(TypeElement type, boolean includeSelf) {
        List<TypeName> result = new LinkedList<>();
        if (!includeSelf) {
            TypeMirror mirror = type.getSuperclass();
            type = TypeTools.toTypeElement(mirror);
        }
        while (Objects.nonNull(type)) {
            result.add(0, createTypeNameFromElement(type));
            TypeMirror mirror = type.getSuperclass();
            type = TypeTools.toTypeElement(mirror);
        }
        return result;
    }

    protected void adjustContractsForExternals(Set<TypeName> contracts,
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

    protected void maybeAddExternalModule(String externalModuleName, Set<String> externalModuleNamesRequired) {
        if (AbstractCreator.needToDeclareModuleUsage(externalModuleName)) {
            externalModuleNamesRequired.add(externalModuleName);
        }
    }

    protected boolean isInThisModule(TypeElement type, AtomicReference<String> moduleName) {
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
            File file = ModuleUtils.toFile(sourceFile.toUri());
            File srcPath = ModuleUtils.toSourcePath(file, type);
            services.setLastKnownSourcePathBeingProcessed(srcPath);
            return true;
        } catch (Exception e) {
            debug("unable to determine if contract is external: " + type + "; " + e.getMessage(), null);
        }

        ModuleElement module = processingEnv.getElementUtils().getModuleOf(type);
        if (!module.isUnnamed()) {
            String name = module.getQualifiedName().toString();
            moduleName.set(name);
        }

        // assumed external, but unknown module name
        return false;
    }

    protected List<String> getAnnotationsWithAnnotation(TypeElement type, Class<? extends Annotation> annotation) {
        List<String> list = new LinkedList<>();
        type.getAnnotationMirrors()
                .forEach(am -> {
                    if (Objects.nonNull(am.getAnnotationType().asElement().getAnnotation(annotation))) {
                        list.add(am.getAnnotationType().asElement().toString());
                    }
                });

        if (list.isEmpty() && Objects.nonNull(JavaxTypeTools.INSTANCE.get())) {
            List<String> list2 = JavaxTypeTools.INSTANCE.get()
                    .getAnnotationsWithAnnotation(type, TypeTools.oppositeOf(annotation.getName()));
            if (Objects.nonNull(list2)) {
                return list2;
            }
        }

        return list;
    }

    protected CodeGenFiler createCodeGenFiler() {
        AbstractFilerMsgr filer = AbstractFilerMsgr.createAnnotationBasedFiler(processingEnv, this);
        return new CodeGenFiler(filer);
    }

    protected boolean doFiler(RoundEnvironment roundEnv) {
        // don't do filer until very end of the round
        boolean isProcessingOver = roundEnv.processingOver();
        DefaultActivatorCreator creator = null;
        CodeGenFiler filer;

        Map<TypeName, InterceptionPlan> interceptionPlanMap = services.getInterceptorPlans();
        if (!interceptionPlanMap.isEmpty()) {
            filer = createCodeGenFiler();
            creator = new DefaultActivatorCreator(filer);
            creator.codegenInterceptors(interceptionPlanMap);
            services.clearInterceptorPlans();
        }

        if (!isProcessingOver) {
            return MAYBE_ANNOTATIONS_CLAIMED_BY_THIS_PROCESSOR;
        }

        DefaultActivatorCreatorCodeGen codeGen = DefaultActivatorCreatorCodeGen.toActivatorCreatorCodeGen(services);
        if (Objects.isNull(codeGen)) {
            return MAYBE_ANNOTATIONS_CLAIMED_BY_THIS_PROCESSOR;
        }

        DefaultActivatorCreatorConfigOptions configOptions = DefaultActivatorCreatorConfigOptions.builder()
                .applicationPreCreated(Options.isOptionEnabled(Options.TAG_APPLICATION_PRE_CREATE))
                .moduleCreated(isProcessingOver)
                .build();
        DefaultActivatorCreatorRequest req = DefaultActivatorCreatorRequest
                .toActivatorCreatorRequest(services, codeGen, configOptions, false);

        try {
            if (Objects.isNull(creator)) {
                filer = createCodeGenFiler();
                creator = new DefaultActivatorCreator(filer);
            }
            services.setLastGeneratedPackageName(req.getPackageName());
            ActivatorCreatorResponse res = creator.createModuleActivators(req);
            this.result = res;
            if (!res.isSuccess()) {
                throw new ToolsException("error during codegen", res.getError());
            }
        } catch (Exception te) {
            Object hierarchy = codeGen.getServiceTypeHierarchy();
            if (Objects.isNull(hierarchy)) {
                warn("expected to have a known service type hierarchy in the context", null);
            } else {
                log("service type hierarchy is " + hierarchy);
            }

            ToolsException revisedTe = new ToolsException("error in annotation processing round for "
                                                        + req.getServiceTypeNames(), te);
            error(revisedTe.getMessage(), revisedTe);
        } finally {
            if (isProcessingOver) {
                processingEnv.getMessager().printMessage(Kind.NOTE, getClass().getSimpleName()
                        + ": processing is over - resetting");
                services.clear();
            }
        }

        // allow other processors to also process these same annotations?
        return MAYBE_ANNOTATIONS_CLAIMED_BY_THIS_PROCESSOR;
    }

    protected Set<TypeName> getContracts(TypeElement type, Set<TypeName> providerForSet) {
        Set<TypeName> result = new LinkedHashSet<>();

        Set<TypeMirror> processed = new LinkedHashSet<>();
        gatherContractsToBeProcessed(processed, type);

        for (TypeMirror possibleContract : processed) {
            TypeElement teContract = TypeTools.toTypeElement(possibleContract);
            if (Objects.isNull(teContract)) {
                continue;
            }

            TypeName parentTe = createTypeNameFromElement(teContract);
            if (Objects.nonNull(teContract.getAnnotation(Contract.class))) {
                result.add(parentTe);
                continue;
            } else if (Options.isOptionEnabled(Options.TAG_AUTO_ADD_NON_CONTRACT_INTERFACES)
                            && (ElementKind.INTERFACE == teContract.getKind())) {
                result.add(parentTe);
                // fall in the next section, skip continue here
            } else if (services.getServiceTypeNames().contains(parentTe)) {
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

            TypeName gTypeName = createTypeNameFromMirror(gType);
            if (Objects.isNull(gTypeName)) {
                continue;
            }

            // if we made it here then this provider qualifies, and we take what it provides into the result
            TypeName teContractName = createTypeNameFromElement(teContract);
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

    protected void gatherContractsToBeProcessed(Set<TypeMirror> processed, TypeElement typeElement) {
        if (Objects.isNull(typeElement)) {
            return;
        }

        typeElement.getInterfaces().forEach((tm) -> {
            processed.add(tm);
            gatherContractsToBeProcessed(processed, TypeTools.toTypeElement(tm));
        });

        toServiceTypeHierarchy(typeElement, false).stream()
                .map((te) -> toTypeElement(te).asType())
                .forEach((tm) -> {
                    processed.add(tm);
                    gatherContractsToBeProcessed(processed, TypeTools.toTypeElement(tm));
                });
    }

    protected Set<TypeName> getExternalContracts(TypeElement type, Set<String> externalModulesRequired) {
        Set<TypeName> result = new LinkedHashSet<>();

        Stack<TypeMirror> stack = new Stack<>();
        stack.push(type.asType());
        stack.addAll(type.getInterfaces());
        stack.add(type.getSuperclass());

        TypeMirror iface;
        while (!stack.isEmpty()) {
            iface = stack.pop();
            TypeElement teContract = TypeTools.toTypeElement(iface);
            if (Objects.isNull(teContract)) {
                continue;
            }

            stack.addAll(teContract.getInterfaces());
            stack.add(teContract.getSuperclass());

            ExternalContracts externalContracts = teContract.getAnnotation(ExternalContracts.class);
            if (Objects.nonNull(externalContracts)) {
                Collection<AnnotationAndValue> annotations = TypeTools.createAnnotationAndValueSet(teContract);
                AnnotationAndValue annotation = DefaultAnnotationAndValue
                        .findFirst(ExternalContracts.class.getName(), annotations, true, true);
                List<String> values = CommonUtils.toList(annotation.value().orElse(null));
                for (String externalContract : values) {
                    result.add(DefaultTypeName.createFromTypeName(externalContract));
                }
                if (Objects.nonNull(externalContracts.moduleNames())) {
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

    protected TypeElement toTypeElement(TypeName typeName) {
        return Objects.requireNonNull(processingEnv.getElementUtils().getTypeElement(typeName.name()));
    }

    protected Level getLevel() {
        return (Options.isOptionEnabled(Options.TAG_DEBUG)) ? Level.INFO : Level.DEBUG;
    }

    @Override
    public void debug(String message, Throwable t) {
        if (Options.isOptionEnabled(Options.TAG_DEBUG)) {
            logger.log(getLevel(), getClass().getSimpleName() + ": Debug: " + message, t);
        }
        if (Objects.nonNull(processingEnv) && Objects.nonNull(processingEnv.getMessager())) {
            processingEnv.getMessager().printMessage(Kind.OTHER, message);
        }
    }

    @Override
    public void log(String message) {
        //        logger.log(getLevel(), getClass().getSimpleName() + ": Note: " + message);
        if (Objects.nonNull(processingEnv) && Objects.nonNull(processingEnv.getMessager())) {
            processingEnv.getMessager().printMessage(Kind.NOTE, message);
        }
    }

    @Override
    public void warn(String message, Throwable t) {
        if (Options.isOptionEnabled(Options.TAG_DEBUG) && Objects.nonNull(t)) {
            logger.log(Level.WARNING, getClass().getSimpleName() + ": Warning: " + message);
            t.printStackTrace();
        }
        if (Objects.nonNull(processingEnv) && Objects.nonNull(processingEnv.getMessager())) {
            processingEnv.getMessager().printMessage(Kind.WARNING, message);
        }
    }

    @Override
    public void error(String message, Throwable t) {
        logger.log(Level.ERROR, getClass().getSimpleName() + ": Error: " + message, t);
        if (Objects.nonNull(processingEnv) && Objects.nonNull(processingEnv.getMessager())) {
            processingEnv.getMessager().printMessage(Kind.ERROR, message);
        }
    }

}
