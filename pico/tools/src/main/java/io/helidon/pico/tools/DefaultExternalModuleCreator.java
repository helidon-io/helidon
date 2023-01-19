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
import io.helidon.pico.DependenciesInfo;
import io.helidon.pico.InjectionPointInfo;
import io.helidon.pico.QualifierAndValue;
import io.helidon.pico.services.Dependencies;
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

import static io.helidon.pico.tools.TypeTools.*;
import static io.helidon.pico.types.DefaultTypeName.*;

/**
 * The default implementation of {@link ExternalModuleCreator}.
 *
 * @deprecated
 */
@Singleton
public class DefaultExternalModuleCreator extends AbstractCreator implements ExternalModuleCreator {
    private final LazyValue<ScanResult> scan = LazyValue.create(ReflectionHandler.INSTANCE.scan());
    private final ServicesToProcess services = ServicesToProcess.servicesInstance();

    /**
     * Service loader based constructor.
     *
     * @deprecated
     */
    public DefaultExternalModuleCreator() {
        super(TemplateHelper.DEFAULT_TEMPLATE_NAME);
    }

    @Override
    public ExternalModuleCreatorResponse prepareToCreateExternalModule(
            ExternalModuleCreatorRequest req) {
        Objects.requireNonNull(req);

        DefaultExternalModuleCreatorResponse.Builder responseBuilder =
                DefaultExternalModuleCreatorResponse.builder();
        Collection<String> packageNames = req.packageNamesToScan();
        if (packageNames.isEmpty()) {
            return handleError(req, new ToolsException("Package names to scan are required"), responseBuilder);
        }

        Collection<Path> targetExternalJars = identifyExternalJars(packageNames);
        if (1 != targetExternalJars.size()) {
            return handleError(req, new ToolsException("the package names provided " + packageNames
                                                     + " must map to a single jar file, but instead found: "
                                                     + targetExternalJars), responseBuilder);
        }

        try {
            // handle the explicit qualifiers passed in
            req.serviceTypeToQualifiersMap().forEach((serviceTypeName, qualifiers) ->
                services.addQualifiers(createFromTypeName(serviceTypeName), qualifiers));

            // process each found service type
            scan.get().getAllStandardClasses()
                    .stream()
                    .filter((classInfo) -> packageNames.contains(classInfo.getPackageName()))
                    .filter((classInfo) -> !classInfo.isInterface())
                    .filter((classInfo) -> !classInfo.isExternalClass())
                    .filter((classInfo) -> !isPrivate(classInfo.getModifiers()))
                    .filter((classInfo) -> !classInfo.isInnerClass() || req.innerClassesProcessed())
                    .forEach(this::processServiceType);

            ActivatorCreatorCodeGen activatorCreatorCodeGen =
                    DefaultActivatorCreator.createActivatorCreatorCodeGen(services).orElseThrow();
            ActivatorCreatorRequest activatorCreatorRequest =
                    DefaultActivatorCreator
                            .createActivatorCreatorRequest(services, activatorCreatorCodeGen,
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

    private Collection<Path> identifyExternalJars(
            Collection<String> packageNames) {
        Set<Path> classpath = new LinkedHashSet<>();
        for (String packageName : packageNames) {
            PackageInfo packageInfo = scan.get().getPackageInfo(packageName);
            if (packageInfo != null) {
                for (ClassInfo classInfo : packageInfo.getClassInfo()) {
                    URI uri = classInfo.getClasspathElementURI();
                    File file = ModuleUtils.toFile(uri).orElse(null);
                    if (file != null) {
                        classpath.add(file.toPath());
                    }
                }
            }
        }
        return classpath;
    }

    private void processServiceType(
            ClassInfo classInfo) {
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

    private void processTypeAndContracts(
            ClassInfo classInfo,
            TypeName serviceTypeName,
            Collection<String> requiresModule) {
        services.addServiceTypeName(serviceTypeName);
        services.addTypeForContract(serviceTypeName, serviceTypeName, true);
        services.addExternalRequiredModules(serviceTypeName, requiresModule);
        services.addParentServiceType(serviceTypeName, createTypeNameFromClassInfo(classInfo.getSuperclass()));
        List<TypeName> hierarchy = DefaultActivatorCreator.serviceTypeHierarchy(serviceTypeName, scan);
        services.addServiceTypeHierarchy(serviceTypeName, hierarchy);
        if (Objects.nonNull(hierarchy)) {
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
                TypeName contract = createFromTypeName(cn);
                services.addTypeForContract(serviceTypeName, contract, true);
            }
            if (firstRound) {
                String cn = providesContractType(classInfo);
                if (Objects.nonNull(cn)) {
                    TypeName contract = createFromTypeName(cn);
                    services.addTypeForContract(serviceTypeName, contract, true);
                    services.addProviderFor(serviceTypeName, Collections.singleton(contract));
                }
            }
            classInfo = classInfo.getSuperclass();
            firstRound = false;
        }
    }

    private void processScopeAndQualifiers(
            ClassInfo classInfo,
            TypeName serviceTypeName) {
        services.addScopeTypeName(serviceTypeName, extractScopeTypeName(classInfo));

        Set<QualifierAndValue> qualifiers = createQualifierAndValueSet(classInfo);
        if (!qualifiers.isEmpty()) {
            services.addQualifiers(serviceTypeName, qualifiers);
        }
    }

    private void processPostConstructAndPreDestroy(
            ClassInfo classInfo,
            TypeName serviceTypeName) {
        MethodInfo postConstructMethod = methodsAnnotatedWith(classInfo, PostConstruct.class)
                .stream().findFirst().orElse(null);
        if (postConstructMethod != null) {
            services.addPostConstructMethod(serviceTypeName, postConstructMethod.getName());
        }

        MethodInfo preDestroyMethods = methodsAnnotatedWith(classInfo, PreDestroy.class)
                .stream().findFirst().orElse(null);
        if (preDestroyMethods != null) {
            services.addPreDestroyMethod(serviceTypeName, preDestroyMethods.getName());
        }
    }

    private void processDependencies(
            ClassInfo classInfo,
            TypeName serviceTypeName) {
        Dependencies.BuilderContinuation continuation = Dependencies.builder(serviceTypeName.name());
        for (FieldInfo fieldInfo : classInfo.getFieldInfo()) {
            continuation = continuationProcess(serviceTypeName, continuation, fieldInfo);
        }

        for (MethodInfo ctorInfo : classInfo.getDeclaredConstructorInfo()) {
            continuation = continuationProcess(serviceTypeName,
                                               continuation, InjectionPointInfo.ElementKind.CONSTRUCTOR, ctorInfo);
        }

        for (MethodInfo methodInfo : classInfo.getDeclaredMethodInfo()) {
            continuation = continuationProcess(serviceTypeName,
                                               continuation, InjectionPointInfo.ElementKind.METHOD, methodInfo);
        }

        DependenciesInfo dependencies = continuation.build();
        services.addDependencies(dependencies);
    }

    private Dependencies.BuilderContinuation continuationProcess(
            TypeName serviceTypeName,
            Dependencies.BuilderContinuation continuation,
            FieldInfo fieldInfo) {
        if (hasAnnotation(fieldInfo, Inject.class)) {
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

    private Dependencies.BuilderContinuation continuationProcess(
            TypeName serviceTypeName,
            Dependencies.BuilderContinuation continuation,
            InjectionPointInfo.ElementKind kind,
            MethodInfo methodInfo) {
        if (hasAnnotation(methodInfo, Inject.class)) {
            if (!isPicoSupported(serviceTypeName, methodInfo, logger())) {
                return continuation;
            }

            MethodParameterInfo[] params = methodInfo.getParameterInfo();
            if (params.length == 0) {
                continuation = continuation.add(methodInfo.getName(), Void.class, kind,
                                toAccess(methodInfo.getModifiers())).staticDeclaration(isStatic(methodInfo.getModifiers()));
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

    static boolean isPicoSupported(
            TypeName serviceTypeName,
            MethodInfo methodInfo,
            System.Logger logger) {
        return PicoSupported.isSupportedInjectionPoint(logger, serviceTypeName, methodInfo.toString(),
                                                       isPrivate(methodInfo.getModifiers()), methodInfo.isStatic());
    }

    ExternalModuleCreatorResponse handleError(
            ExternalModuleCreatorRequest request,
            ToolsException e,
            DefaultExternalModuleCreatorResponse.Builder builder) {
        if (Objects.isNull(request) || request.throwIfError()) {
            throw e;
        }

        return builder.error(e).success(false).build();
    }

}
