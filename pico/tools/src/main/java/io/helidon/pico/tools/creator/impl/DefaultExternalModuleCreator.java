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

package io.helidon.pico.tools.creator.impl;

import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import io.helidon.common.LazyValue;
import io.helidon.pico.InjectionPointInfo;
import io.helidon.pico.QualifierAndValue;
import io.helidon.pico.spi.ext.Dependencies;
import io.helidon.pico.spi.impl.DefaultInjectionPointInfo;
import io.helidon.pico.tools.ToolsException;
import io.helidon.pico.tools.creator.ActivatorCreatorCodeGen;
import io.helidon.pico.tools.creator.ActivatorCreatorRequest;
import io.helidon.pico.tools.creator.ExternalModuleCreator;
import io.helidon.pico.tools.creator.ExternalModuleCreatorRequest;
import io.helidon.pico.tools.creator.ExternalModuleCreatorResponse;
import io.helidon.pico.tools.processor.PicoSupported;
import io.helidon.pico.tools.processor.ServicesToProcess;
import io.helidon.pico.tools.processor.TypeTools;
import io.helidon.pico.tools.utils.ModuleUtils;
import io.helidon.pico.types.TypeName;

import io.github.classgraph.ClassInfo;
import io.github.classgraph.ClassInfoList;
import io.github.classgraph.FieldInfo;
import io.github.classgraph.MethodInfo;
import io.github.classgraph.MethodParameterInfo;
import io.github.classgraph.ModuleInfo;
import io.github.classgraph.PackageInfo;
import io.github.classgraph.ScanResult;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import static io.helidon.pico.types.DefaultTypeName.createFromTypeName;
import static io.helidon.pico.tools.processor.TypeTools.createInjectionPointInfo;
import static io.helidon.pico.tools.processor.TypeTools.createQualifierAndValueSet;
import static io.helidon.pico.tools.processor.TypeTools.createTypeNameFromClassInfo;
import static io.helidon.pico.tools.processor.TypeTools.extractScopeTypeName;
import static io.helidon.pico.tools.processor.TypeTools.hasAnnotation;
import static io.helidon.pico.tools.processor.TypeTools.isAbstract;
import static io.helidon.pico.tools.processor.TypeTools.isPrivate;
import static io.helidon.pico.tools.processor.TypeTools.isStatic;
import static io.helidon.pico.tools.processor.TypeTools.methodsAnnotatedWith;
import static io.helidon.pico.tools.processor.TypeTools.providesContractType;
import static io.helidon.pico.tools.processor.TypeTools.toAccess;
import static java.util.Objects.nonNull;

/**
 * The default implementation of {@link io.helidon.pico.tools.creator.ExternalModuleCreator}.
 */
@Singleton
public class DefaultExternalModuleCreator implements ExternalModuleCreator {
    private static final System.Logger LOGGER = System.getLogger(DefaultExternalModuleCreator.class.getName());

    private final LazyValue<ScanResult> scan = LazyValue.create(ReflectionHandler.INSTANCE::getScan);
    private final ServicesToProcess services = ServicesToProcess.getServicesInstance();
    private boolean failOnWarning;

    @SuppressWarnings("unchecked")
    @Override
    public ExternalModuleCreatorResponse prepareToCreateExternalModule(ExternalModuleCreatorRequest request) {
        DefaultExternalModuleCreatorResponse.DefaultExternalModuleCreatorResponseBuilder responseBuilder =
                DefaultExternalModuleCreatorResponse.builder();

        if (Objects.isNull(request)) {
            return handleError(request, new ToolsException("request is required"), responseBuilder);
        }

        failOnWarning = request.isFailOnWarning();

        final Collection<String> packageNames = request.getPackageNamesToScan();
        if (packageNames.isEmpty()) {
            return handleError(request, new ToolsException("Package names to scan are required"), responseBuilder);
        }

        final Collection<Path> targetExternalJars = identifyExternalJars(packageNames);
        if (1 != targetExternalJars.size()) {
            return handleError(request, new ToolsException("the package names provided " + packageNames
                                                     + " must map to a single jar file, but instead found: "
                                                     + targetExternalJars), responseBuilder);
        }

        try {
            // handle the explicit qualifiers passed in
            request.getServiceTypeToQualifiersMap().forEach((serviceTypeName, qualifiers) ->
                services.setQualifiers(createFromTypeName(serviceTypeName), qualifiers));

            // process each found service type
            scan.get().getAllStandardClasses()
                    .stream()
                    .filter((classInfo) -> packageNames.contains(classInfo.getPackageName()))
                    .filter((classInfo) -> !classInfo.isInterface())
                    .filter((classInfo) -> !classInfo.isExternalClass())
                    .filter((classInfo) -> !isPrivate(classInfo.getModifiers()))
                    .filter((classInfo) -> !classInfo.isInnerClass() || request.isInnerClassesProcessed())
                    .forEach(this::processServiceType);

            ActivatorCreatorCodeGen activatorCreatorCodeGen =
                    DefaultActivatorCreatorCodeGen.toActivatorCreatorCodeGen(services);
            ActivatorCreatorRequest activatorCreatorRequest =
                    DefaultActivatorCreatorRequest
                            .toActivatorCreatorRequest(services, activatorCreatorCodeGen,
                                                       request.getActivatorCreatorConfigOptions(),
                                                       request.isFailOnError());
            return (ExternalModuleCreatorResponse) responseBuilder
                    .activatorCreatorRequest(activatorCreatorRequest)
                    .serviceTypeNames(services.getServiceTypeNames())
                    .moduleName(services.getModuleName())
                    .packageName(activatorCreatorRequest.getPackageName())
                    .build();
        } catch (Throwable t) {
            return handleError(request, new ToolsException("failed to analyze / prepare external module", t), responseBuilder);
        } finally {
            services.clear();
        }
    }

    protected Collection<Path> identifyExternalJars(Collection<String> packageNames) {
        Set<Path> classpath = new LinkedHashSet<>();
        for (String packageName : packageNames) {
            PackageInfo packageInfo = scan.get().getPackageInfo(packageName);
            if (nonNull(packageInfo)) {
                for (ClassInfo classInfo : packageInfo.getClassInfo()) {
                    URI uri = classInfo.getClasspathElementURI();
                    File file = ModuleUtils.toFile(uri);
                    if (nonNull(file)) {
                        classpath.add(file.toPath());
                    }
                }
            }
        }
        return classpath;
    }

    protected void processServiceType(ClassInfo classInfo) {
        LOGGER.log(System.Logger.Level.DEBUG, "processing " + classInfo);

        TypeName serviceTypeName = createTypeNameFromClassInfo(classInfo);

        ModuleInfo moduleInfo = classInfo.getModuleInfo();
        Collection<String> requiresModule = null;
        if (nonNull(moduleInfo)) {
            requiresModule = Collections.singleton(moduleInfo.getName());
            services.setModuleName(moduleInfo.getName());
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
        services.setParentServiceType(serviceTypeName, createTypeNameFromClassInfo(classInfo.getSuperclass()));
        List<TypeName> hierarchy = DefaultActivatorCreator.getServiceTypeHierarchy(serviceTypeName, scan);
        services.setServiceTypeHierarchy(serviceTypeName, hierarchy);
        if (Objects.nonNull(hierarchy)) {
            hierarchy.stream()
                    .filter((parentTypeName) -> !parentTypeName.equals(serviceTypeName))
                    .forEach((parentTypeName) -> services.addTypeForContract(serviceTypeName, parentTypeName, false));
        }
        services.setServiceTypeAccessLevel(serviceTypeName, toAccess(classInfo.getModifiers()));
        services.setServiceTypeIsAbstract(serviceTypeName, isAbstract(classInfo.getModifiers()));

        boolean firstRound = true;
        while (Objects.nonNull(classInfo) && !Object.class.getName().equals(classInfo.getName())) {
            ClassInfoList list = classInfo.getInterfaces();
            for (ClassInfo contractClassInfo : list) {
                String cn = contractClassInfo.getName();
                TypeName contract = createFromTypeName(cn);
                services.addTypeForContract(serviceTypeName, contract, true);
            }
            if (firstRound) {
                String cn = providesContractType(classInfo);
                if (Objects.nonNull(cn)) {
                    TypeName contract = createFromTypeName(cn);
                    services.addTypeForContract(serviceTypeName, contract, true);
                    services.setProviderFor(serviceTypeName, Collections.singleton(contract));
                }
            }
            classInfo = classInfo.getSuperclass();
            firstRound = false;
        }
    }

    private void processScopeAndQualifiers(ClassInfo classInfo,
                                           TypeName serviceTypeName) {
        services.addScopeTypeName(serviceTypeName, extractScopeTypeName(classInfo));

        Set<QualifierAndValue> qualifiers = createQualifierAndValueSet(classInfo);
        if (!qualifiers.isEmpty()) {
            services.setQualifiers(serviceTypeName, qualifiers);
        }
    }

    private void processPostConstructAndPreDestroy(ClassInfo classInfo,
                                                   TypeName serviceTypeName) {
        MethodInfo postConstructMethod = methodsAnnotatedWith(classInfo, PostConstruct.class)
                .stream().findFirst().orElse(null);
        if (Objects.nonNull(postConstructMethod)) {
            services.setPostConstructMethod(serviceTypeName, postConstructMethod.getName());
        }

        MethodInfo preDestroyMethods = methodsAnnotatedWith(classInfo, PreDestroy.class)
                .stream().findFirst().orElse(null);
        if (Objects.nonNull(preDestroyMethods)) {
            services.setPreDestroyMethod(serviceTypeName, preDestroyMethods.getName());
        }
    }

    private void processDependencies(ClassInfo classInfo,
                                     TypeName serviceTypeName) {
        Dependencies.BuilderContinuation continuation = Dependencies.builder()
                .forServiceTypeName(serviceTypeName.name());
        for (FieldInfo fieldInfo : classInfo.getFieldInfo()) {
            continuation = continuationProcess(serviceTypeName, continuation, fieldInfo);
        }

        for (MethodInfo ctorInfo : classInfo.getDeclaredConstructorInfo()) {
            continuation = continuationProcess(serviceTypeName,
                                               continuation, InjectionPointInfo.ElementKind.CTOR, ctorInfo);
        }

        for (MethodInfo methodInfo : classInfo.getDeclaredMethodInfo()) {
            continuation = continuationProcess(serviceTypeName,
                                               continuation, InjectionPointInfo.ElementKind.METHOD, methodInfo);
        }

        Dependencies dependencies = continuation.build().build();
        services.addDependencies(dependencies);
    }

    private Dependencies.BuilderContinuation continuationProcess(TypeName serviceTypeName,
                                                                 Dependencies.BuilderContinuation continuation,
                                                                 FieldInfo fieldInfo) {
        if (hasAnnotation(fieldInfo, Inject.class)) {
            if (!PicoSupported.isSupportedInjectionPoint(failOnWarning, LOGGER,
                                                         serviceTypeName, fieldInfo.toString(),
                                                         isPrivate(fieldInfo.getModifiers()), fieldInfo.isStatic())) {
                return continuation;
            }

            DefaultInjectionPointInfo ipInfo = createInjectionPointInfo(serviceTypeName, fieldInfo);
            continuation = continuation.add(ipInfo);
        }

        return continuation;
    }

    private Dependencies.BuilderContinuation continuationProcess(TypeName serviceTypeName,
                                                                 Dependencies.BuilderContinuation continuation,
                                                                 InjectionPointInfo.ElementKind kind,
                                                                 MethodInfo methodInfo) {
        if (hasAnnotation(methodInfo, Inject.class)) {
            if (!isPicoSupported(serviceTypeName, methodInfo, failOnWarning, LOGGER)) {
                return continuation;
            }

            MethodParameterInfo[] params = methodInfo.getParameterInfo();
            if (params.length == 0) {
                continuation = continuation.add(methodInfo.getName(), Void.class, kind,
                                toAccess(methodInfo.getModifiers())).setIsStatic(isStatic(methodInfo.getModifiers()));
            } else {
                int count = 0;
                for (MethodParameterInfo ignore : params) {
                    count++;
                    DefaultInjectionPointInfo ipInfo = TypeTools.createInjectionPointInfo(serviceTypeName, methodInfo, count);
                    continuation = continuation.add(ipInfo);
                }
            }
        }
        return continuation;
    }

    static boolean isPicoSupported(TypeName serviceTypeName,
                                   MethodInfo methodInfo,
                                   boolean failOnWarning,
                                   System.Logger logger) {
        return PicoSupported.isSupportedInjectionPoint(failOnWarning, logger,
                                                       serviceTypeName, methodInfo.toString(),
                                                       isPrivate(methodInfo.getModifiers()), methodInfo.isStatic());
    }

    protected ExternalModuleCreatorResponse handleError(ExternalModuleCreatorRequest request,
                                                   ToolsException e,
                                                   DefaultGeneralCreatorResponse.DefaultGeneralCreatorResponseBuilder builder) {
        if (Objects.isNull(request) || request.isFailOnError()) {
            throw e;
        }

        return (ExternalModuleCreatorResponse) builder.error(e).success(false).build();
    }

}
