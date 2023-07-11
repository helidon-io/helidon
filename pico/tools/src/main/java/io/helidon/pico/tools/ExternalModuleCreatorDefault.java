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

package io.helidon.pico.tools;

import java.net.URI;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import io.helidon.common.LazyValue;
import io.helidon.common.Weight;
import io.helidon.common.types.TypeName;
import io.helidon.pico.api.DependenciesInfo;
import io.helidon.pico.api.ElementKind;
import io.helidon.pico.api.InjectionPointInfo;
import io.helidon.pico.api.Qualifier;
import io.helidon.pico.runtime.Dependencies;
import io.helidon.pico.tools.spi.ExternalModuleCreator;

import io.github.classgraph.ClassInfo;
import io.github.classgraph.ClassInfoList;
import io.github.classgraph.ClassMemberInfo;
import io.github.classgraph.FieldInfo;
import io.github.classgraph.MethodInfo;
import io.github.classgraph.MethodParameterInfo;
import io.github.classgraph.ModuleInfo;
import io.github.classgraph.PackageInfo;
import io.github.classgraph.ScanResult;
import jakarta.inject.Singleton;

import static io.helidon.pico.api.ServiceInfoBasics.DEFAULT_PICO_WEIGHT;
import static io.helidon.pico.tools.TypeTools.createInjectionPointInfo;
import static io.helidon.pico.tools.TypeTools.createQualifierSet;
import static io.helidon.pico.tools.TypeTools.createTypeNameFromClassInfo;
import static io.helidon.pico.tools.TypeTools.extractScopeTypeName;
import static io.helidon.pico.tools.TypeTools.hasAnnotation;
import static io.helidon.pico.tools.TypeTools.isAbstract;
import static io.helidon.pico.tools.TypeTools.isPrivate;
import static io.helidon.pico.tools.TypeTools.isStatic;
import static io.helidon.pico.tools.TypeTools.methodsAnnotatedWith;
import static io.helidon.pico.tools.TypeTools.providesContractType;
import static io.helidon.pico.tools.TypeTools.toAccess;
import static java.util.function.Predicate.not;

/**
 * The default implementation of {@link io.helidon.pico.tools.spi.ExternalModuleCreator}.
 */
@Singleton
@Weight(DEFAULT_PICO_WEIGHT)
public class ExternalModuleCreatorDefault extends AbstractCreator implements ExternalModuleCreator {
    private static final Set<String> SERVICE_DEFINING_ANNOTATIONS = Set.of(TypeNames.JAKARTA_SINGLETON,
                                                                           TypeNames.JAKARTA_APPLICATION_SCOPED);
    private final LazyValue<ScanResult> scan = LazyValue.create(ReflectionHandler.INSTANCE.scan());
    private final ServicesToProcess services = ServicesToProcess.servicesInstance();

    /**
     * Service loader based constructor.
     *
     * @deprecated this is a Java ServiceLoader implementation and the constructor should not be used directly
     */
    @Deprecated
    public ExternalModuleCreatorDefault() {
        super(TemplateHelper.DEFAULT_TEMPLATE_NAME);
    }

    static boolean isPicoSupported(TypeName serviceTypeName,
                                   MethodInfo methodInfo,
                                   System.Logger logger) {
        return PicoSupported.isSupportedInjectionPoint(logger, serviceTypeName, methodInfo.toString(),
                                                       isPrivate(methodInfo.getModifiers()), methodInfo.isStatic());
    }

    @Override
    public ExternalModuleCreatorResponse prepareToCreateExternalModule(ExternalModuleCreatorRequest req) {
        Objects.requireNonNull(req);

        ExternalModuleCreatorResponse.Builder responseBuilder =
                ExternalModuleCreatorResponse.builder();
        Collection<String> packageNames = req.packageNamesToScan();
        if (packageNames.isEmpty()) {
            return handleError(req, new ToolsException("Package names to scan are required"), responseBuilder);
        }

        Collection<Path> targetExternalJars = identifyExternalJars(packageNames);
        if (1 != targetExternalJars.size()) {
            return handleError(req, new ToolsException("The package names provided " + packageNames
                                                               + " must map to a single jar file, but instead found: "
                                                               + targetExternalJars), responseBuilder);
        }

        try {
            // handle the explicit qualifiers passed in
            req.serviceTypeToQualifiersMap().forEach((serviceTypeName, qualifiers) ->
                                                             services.addQualifiers(TypeName.create(serviceTypeName),
                                                                                    qualifiers));

            // process each found service type
            scan.get().getAllStandardClasses()
                    .stream()
                    .filter(classInfo -> packageNames.contains(classInfo.getPackageName()))
                    .filter(not(ClassInfo::isInterface))
                    .filter(not(ClassInfo::isExternalClass))
                    .filter(classInfo -> !isPrivate(classInfo.getModifiers()))
                    .filter(classInfo -> !classInfo.isInnerClass() || req.innerClassesProcessed())
                    .forEach(this::processServiceType);

            ActivatorCreatorCodeGen activatorCreatorCodeGen = ActivatorCreatorDefault
                    .createActivatorCreatorCodeGen(services).orElseThrow();
            ActivatorCreatorRequest activatorCreatorRequest = ActivatorCreatorDefault
                    .createActivatorCreatorRequest(services,
                                                   activatorCreatorCodeGen,
                                                   req.activatorCreatorConfigOptions(),
                                                   req.filer(),
                                                   req.throwIfError());
            return responseBuilder
                    .activatorCreatorRequest(activatorCreatorRequest)
                    .serviceTypeNames(services.serviceTypeNames())
                    .moduleName(services.moduleName())
                    .packageName(activatorCreatorRequest.packageName())
                    .build();
        } catch (Throwable t) {
            return handleError(req, new ToolsException("failed to analyze / prepare external module", t), responseBuilder);
        } finally {
            services.reset(false);
        }
    }

    ExternalModuleCreatorResponse handleError(ExternalModuleCreatorRequest request,
                                              ToolsException e,
                                              ExternalModuleCreatorResponse.Builder builder) {
        if (request == null || request.throwIfError()) {
            throw e;
        }

        logger().log(System.Logger.Level.ERROR, e.getMessage(), e);

        return builder.error(e).success(false).build();
    }

    private Collection<Path> identifyExternalJars(Collection<String> packageNames) {
        Set<Path> classpath = new LinkedHashSet<>();
        for (String packageName : packageNames) {
            PackageInfo packageInfo = scan.get().getPackageInfo(packageName);
            if (packageInfo != null) {
                for (ClassInfo classInfo : packageInfo.getClassInfo()) {
                    URI uri = classInfo.getClasspathElementURI();
                    classpath.add(Path.of(uri));
                }
            }
        }
        return classpath;
    }

    private void processServiceType(ClassInfo classInfo) {
        logger().log(System.Logger.Level.DEBUG, "processing " + classInfo);

        TypeName serviceTypeName = createTypeNameFromClassInfo(classInfo);

        ModuleInfo moduleInfo = classInfo.getModuleInfo();
        Collection<String> requiresModule = null;
        if (moduleInfo != null) {
            requiresModule = Collections.singleton(moduleInfo.getName());
            services.moduleName(moduleInfo.getName());
        }

        processTypeAndContracts(classInfo, serviceTypeName, requiresModule);
        processScopeAndQualifiers(classInfo, serviceTypeName);
        processPostConstructAndPreDestroy(classInfo, serviceTypeName);
        processDependencies(classInfo, serviceTypeName);
    }

    private void processTypeAndContracts(ClassInfo classInfo,
                                         TypeName serviceTypeName,
                                         Collection<String> requiresModule) {
        services.addServiceTypeName(serviceTypeName);
        services.addTypeForContract(serviceTypeName, serviceTypeName, true);
        services.addExternalRequiredModules(serviceTypeName, requiresModule);
        ClassInfo superclass = classInfo.getSuperclass();
        if (superclass != null) {
            handleSuperClass(serviceTypeName, superclass);
        }
        List<TypeName> hierarchy = ActivatorCreatorDefault.serviceTypeHierarchy(serviceTypeName, scan);
        services.addServiceTypeHierarchy(serviceTypeName, hierarchy);
        if (hierarchy != null) {
            hierarchy.stream()
                    .filter((parentTypeName) -> !parentTypeName.equals(serviceTypeName))
                    .forEach((parentTypeName) -> services.addTypeForContract(serviceTypeName, parentTypeName, false));
        }
        services.addAccessLevel(serviceTypeName, toAccess(classInfo.getModifiers()));
        services.addIsAbstract(serviceTypeName, isAbstract(classInfo.getModifiers()));

        boolean firstRound = true;
        while (classInfo != null && !Object.class.getName().equals(classInfo.getName())) {
            ClassInfoList list = classInfo.getInterfaces();
            for (ClassInfo contractClassInfo : list) {
                String cn = contractClassInfo.getName();
                TypeName contract = TypeName.create(cn);
                services.addTypeForContract(serviceTypeName, contract, true);
            }
            if (firstRound) {
                String cn = providesContractType(classInfo);
                if (cn != null) {
                    TypeName contract = TypeName.create(cn);
                    services.addTypeForContract(serviceTypeName, contract, true);
                    services.addProviderFor(serviceTypeName, Collections.singleton(contract));
                }
            }
            classInfo = classInfo.getSuperclass();
            firstRound = false;
        }
    }

    private void handleSuperClass(TypeName serviceTypeName, ClassInfo superclass) {
        // looking for the closes type that is either a bean, or that has injection points (as that type MUST have an activator)
        findActivatedInHierarchy(superclass, new HashSet<>())
                .ifPresent(it -> services.addParentServiceType(serviceTypeName, TypeName.builder(it.genericTypeName())
                        .className(it.classNameWithEnclosingNames()
                                           .replace('.', '$') + ActivatorCreatorDefault.INNER_ACTIVATOR_CLASS_NAME)
                        .build()));
    }

    private Optional<TypeName> findActivatedInHierarchy(ClassInfo superClass, HashSet<ClassInfo> processed) {
        if (!processed.add(superClass)) {
            return Optional.empty();
        }
        // any type that has
        // - @Singleton on type (or any other "service defining annotation"), or has @Scope meta annotation
        // - @Inject on any field or constructor
        // same code in PicoAnnotationProcessor for TypeInfo
        for (ClassInfo annotation : superClass.getAnnotations()) {
            if (SERVICE_DEFINING_ANNOTATIONS.contains(annotation.getName())) {
                return Optional.of(TypeName.create(superClass.getName()));
            }
        }
        for (FieldInfo field : superClass.getDeclaredFieldInfo()) {
            if (hasInjectAnnotation(field)) {
                return Optional.of(TypeName.create(superClass.getName()));
            }
        }
        for (MethodInfo method : superClass.getDeclaredMethodAndConstructorInfo()) {
            if (hasInjectAnnotation(method)) {
                return Optional.of(TypeName.create(superClass.getName()));
            }
        }
        return Optional.ofNullable(superClass.getSuperclass())
                .flatMap(it -> findActivatedInHierarchy(it, processed));
    }

    private boolean hasInjectAnnotation(ClassMemberInfo member) {
        return member.hasAnnotation(TypeNames.JAKARTA_INJECT) || member.hasAnnotation(TypeNames.JAVAX_INJECT);
    }

    private void processScopeAndQualifiers(ClassInfo classInfo,
                                           TypeName serviceTypeName) {
        TypeName scopeTypeName = extractScopeTypeName(classInfo);
        if (scopeTypeName != null) {
            services.addScopeTypeName(serviceTypeName, scopeTypeName);
        }

        Set<Qualifier> qualifiers = createQualifierSet(classInfo);
        if (!qualifiers.isEmpty()) {
            services.addQualifiers(serviceTypeName, qualifiers);
        }
    }

    private void processPostConstructAndPreDestroy(ClassInfo classInfo,
                                                   TypeName serviceTypeName) {
        MethodInfo postConstructMethod = methodsAnnotatedWith(classInfo, TypeNames.JAKARTA_POST_CONSTRUCT)
                .stream().findFirst().orElse(null);
        if (postConstructMethod != null) {
            services.addPostConstructMethod(serviceTypeName, postConstructMethod.getName());
        }

        MethodInfo preDestroyMethods = methodsAnnotatedWith(classInfo, TypeNames.JAKARTA_PRE_DESTROY)
                .stream().findFirst().orElse(null);
        if (preDestroyMethods != null) {
            services.addPreDestroyMethod(serviceTypeName, preDestroyMethods.getName());
        }
    }

    private void processDependencies(ClassInfo classInfo,
                                     TypeName serviceTypeName) {
        Dependencies.BuilderContinuation continuation = Dependencies.builder(serviceTypeName);
        for (FieldInfo fieldInfo : classInfo.getFieldInfo()) {
            continuation = continuationProcess(serviceTypeName, continuation, fieldInfo);
        }

        for (MethodInfo ctorInfo : classInfo.getDeclaredConstructorInfo()) {
            continuation = continuationProcess(serviceTypeName,
                                               continuation, ElementKind.CONSTRUCTOR, ctorInfo);
        }

        for (MethodInfo methodInfo : classInfo.getDeclaredMethodInfo()) {
            continuation = continuationProcess(serviceTypeName,
                                               continuation, ElementKind.METHOD, methodInfo);
        }

        DependenciesInfo dependencies = continuation.build();
        services.addDependencies(dependencies);
    }

    private Dependencies.BuilderContinuation continuationProcess(TypeName serviceTypeName,
                                                                 Dependencies.BuilderContinuation continuation,
                                                                 FieldInfo fieldInfo) {
        if (hasAnnotation(fieldInfo, TypeNames.JAKARTA_INJECT)) {
            if (!PicoSupported.isSupportedInjectionPoint(logger(),
                                                         serviceTypeName, fieldInfo.toString(),
                                                         isPrivate(fieldInfo.getModifiers()), fieldInfo.isStatic())) {
                return continuation;
            }

            InjectionPointInfo ipInfo = createInjectionPointInfo(serviceTypeName, fieldInfo);
            continuation = continuation.add(ipInfo);
        }

        return continuation;
    }

    private Dependencies.BuilderContinuation continuationProcess(TypeName serviceTypeName,
                                                                 Dependencies.BuilderContinuation continuation,
                                                                 ElementKind kind,
                                                                 MethodInfo methodInfo) {
        if (hasAnnotation(methodInfo, TypeNames.JAKARTA_INJECT)) {
            if (!isPicoSupported(serviceTypeName, methodInfo, logger())) {
                return continuation;
            }

            MethodParameterInfo[] params = methodInfo.getParameterInfo();
            if (params.length == 0) {
                continuation = continuation.add(methodInfo.getName(), Void.class, kind,
                                                toAccess(methodInfo.getModifiers()))
                        .ipName(methodInfo.getName())
                        .ipType(TypeName.create(Void.class))
                        .staticDeclaration(isStatic(methodInfo.getModifiers()));
            } else {
                int count = 0;
                for (MethodParameterInfo ignore : params) {
                    count++;
                    InjectionPointInfo ipInfo = createInjectionPointInfo(serviceTypeName, methodInfo, count);
                    continuation = continuation.add(ipInfo);
                }
            }
        }
        return continuation;
    }

}
