/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

package io.helidon.service.inject.maven.plugin;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import io.helidon.codegen.CodegenException;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.classmodel.Method;
import io.helidon.codegen.compiler.Compiler;
import io.helidon.codegen.compiler.CompilerOptions;
import io.helidon.codegen.scan.ScanContext;
import io.helidon.codegen.scan.ScanTypeInfoFactory;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.ElementSignature;
import io.helidon.common.types.ResolvedType;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.service.inject.api.InjectServiceInfo;
import io.helidon.service.inject.api.Injection.RunLevel;
import io.helidon.service.inject.codegen.ApplicationMainGenerator;
import io.helidon.service.metadata.DescriptorMetadata;
import io.helidon.service.registry.ServiceDescriptor;
import io.helidon.service.registry.ServiceDiscovery;
import io.helidon.service.registry.ServiceLoader__ServiceDescriptor;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ClassInfoList;
import io.github.classgraph.ScanResult;

import static io.helidon.service.inject.codegen.InjectCodegenTypes.INJECTION_MAIN;
import static io.helidon.service.inject.codegen.InjectCodegenTypes.INJECTION_RUN_LEVEL;
import static io.helidon.service.inject.codegen.InjectCodegenTypes.INJECT_BINDING;
import static io.helidon.service.inject.codegen.InjectCodegenTypes.INJECT_MAIN;

class MainClassCreator {
    private static final TypeName GENERATOR = TypeName.create(MainClassCreator.class);
    private static final String INJECTION_MAIN_ANNOTATION = INJECTION_MAIN.packageName()
            + "."
            + INJECTION_MAIN.classNameWithEnclosingNames().replace('.', '$');

    private final MavenCodegenContext ctx;
    private final boolean failOnError;

    MainClassCreator(MavenCodegenContext scanContext, boolean failOnError) {
        this.ctx = scanContext;
        this.failOnError = failOnError;
    }

    void create(MavenCodegenContext scanContext,
                CompilerOptions compilerOptions,
                WrappedServices services,
                TypeName generatedType) {
        try {
            codegen(scanContext, compilerOptions, services, generatedType);
        } catch (CodegenException ce) {
            handleError(ce);
        } catch (Throwable te) {
            handleError(new CodegenException("Failed to code generate application main class", te));
        }
    }

    private void codegen(ScanContext scanContext,
                         CompilerOptions compilerOptions,
                         WrappedServices services,
                         TypeName generatedType) {
        // if there is a custom main class present, we must honor it
        Optional<TypeInfo> foundCustomMain = findCustomMain(scanContext, compilerOptions.outputDirectory());
        Set<ElementSignature> declaredSignatures;
        TypeName superType;

        if (foundCustomMain.isPresent()) {
            TypeInfo customMain = foundCustomMain.get();
            ApplicationMainGenerator.validate(customMain);
            declaredSignatures = ApplicationMainGenerator.declaredSignatures(customMain);
            superType = customMain.typeName();
        } else {
            declaredSignatures = Set.of();
            superType = INJECT_MAIN;
        }

        ClassModel classModel =
                ApplicationMainGenerator.generate(GENERATOR,
                                                  declaredSignatures,
                                                  superType,
                                                  generatedType,
                                                  false,
                                                  true,
                                                  (classBuilder, methodModel, paramName) -> serviceDescriptors(classBuilder,
                                                                                                               methodModel,
                                                                                                               paramName,
                                                                                                               services),
                                                  (classBuilder, methodModel, paramName) -> runLevels(
                                                          methodModel,
                                                          services))
                        .build();

        Path generated = ctx.filer()
                .writeSourceFile(classModel);

        Compiler.compile(compilerOptions, generated);
    }

    private Optional<TypeInfo> findCustomMain(ScanContext scanContext, Path targetPath) {
        // we do not want to search the whole application, just the current module. Main class MUST be in the module that
        // uses the maven plugin
        try (ScanResult scan = new ClassGraph()
                .overrideClasspath(Set.of(targetPath))
                .enableAllInfo()
                .scan()) {
            ClassInfoList customMainClasses = scan.getClassesWithAnnotation(INJECTION_MAIN_ANNOTATION);
            if (customMainClasses.isEmpty()) {
                return Optional.empty();
            }
            if (customMainClasses.size() > 1) {
                String names = customMainClasses.stream()
                        .map(ClassInfo::getName)
                        .collect(Collectors.joining(", "));
                throw new CodegenException("There can only be one class annotated with " + INJECTION_MAIN.fqName() + ", "
                                                   + "but discovered more than one: " + names);
            }
            return ScanTypeInfoFactory.create(scanContext, customMainClasses.getFirst());
        }
    }

    private void runLevels(Method.Builder method,
                           WrappedServices services) {
        Set<Double> runLevels = new TreeSet<>();

        for (InjectServiceInfo serviceInfo : services.all()) {
            if (serviceInfo.runLevel().isPresent()) {
                runLevels.add(serviceInfo.runLevel().get());
            }
        }

        List<Double> runLevelList = List.copyOf(runLevels);

        for (int i = 0; i < runLevelList.size(); i++) {
            double current = runLevelList.get(i);
            if (Double.compare(RunLevel.STARTUP, current) == 0) {
                method.addContent(INJECTION_RUN_LEVEL)
                        .addContent(".STARTUP");
            } else if (Double.compare(RunLevel.SERVER, current) == 0) {
                method.addContent(INJECTION_RUN_LEVEL)
                        .addContent(".SERVER");
            } else if (Double.compare(RunLevel.NORMAL, current) == 0) {
                method.addContent(INJECTION_RUN_LEVEL)
                        .addContent(".NORMAL");
            } else {
                method.addContent(String.valueOf(current))
                        .addContent("D");
            }
            if (i == runLevelList.size() - 1) {
                method.addContentLine("");
            } else {
                method.addContentLine(",");
            }
        }
    }

    private void serviceDescriptors(ClassModel.Builder classModel,
                                    Method.Builder method,
                                    String paramName,
                                    WrappedServices services) {
        List<ServiceLoader__ServiceDescriptor> serviceLoaded = new ArrayList<>();
        List<TypeName> serviceDescriptors = new ArrayList<>();

        // bindings must be added directly from service discovery, as they are not part of the registry
        addBindings(serviceDescriptors);

        // for each discovered service, add it to the configuration
        for (InjectServiceInfo serviceInfo : services.all()) {
            if (serviceInfo.coreInfo() instanceof ServiceLoader__ServiceDescriptor sl) {
                serviceLoaded.add(sl);
            } else {
                serviceDescriptors.add(serviceInfo.descriptorType());
            }
        }

        Map<TypeName, String> providerConstants = new HashMap<>();
        AtomicInteger constantCounter = new AtomicInteger();

        serviceLoaded.stream()
                .sorted(serviceLoaderComparator())
                .forEach(it -> addServiceLoader(classModel, method, providerConstants, constantCounter, it));

        if (!serviceLoaded.isEmpty() && !serviceDescriptors.isEmpty()) {
            // visually separate service loaded services from service descriptors
            method.addContentLine("");
        }

        // config.addServiceDescriptor(ImperativeFeature__ServiceDescriptor.INSTANCE);
        serviceDescriptors.stream().sorted()
                .forEach(it -> method.addContent(paramName)
                        .addContent(".addServiceDescriptor(")
                        .addContent(it)
                        .addContentLine(".INSTANCE);"));
    }

    private void addBindings(List<TypeName> serviceDescriptors) {
        ResolvedType binding = ResolvedType.create(INJECT_BINDING);

        ServiceDiscovery.create()
                .allMetadata()
                .stream()
                .filter(it -> it.contracts().contains(binding))
                .map(DescriptorMetadata::descriptorType)
                .forEach(serviceDescriptors::add);
    }

    private void addServiceLoader(ClassModel.Builder classModel,
                                  Method.Builder main,
                                  Map<TypeName, String> providerConstants,
                                  AtomicInteger constantCounter,
                                  ServiceLoader__ServiceDescriptor sl) {
        // Generated code:
        // config.addServiceDescriptor(serviceLoader(PROVIDER_1,
        //                                           YamlConfigParser.class,
        //                                           () -> new io.helidon.config.yaml.YamlConfigParser(),
        //                                           90.0));
        TypeName providerInterface = sl.providerInterface();
        String constantName = providerConstants.computeIfAbsent(providerInterface, it -> {
            int i = constantCounter.getAndIncrement();
            String constant = "PROVIDER_" + i;
            classModel.addField(field -> field
                    .accessModifier(AccessModifier.PRIVATE)
                    .isStatic(true)
                    .isFinal(true)
                    .type(TypeNames.TYPE_NAME)
                    .name(constant)
                    .addContentCreate(providerInterface));
            return constant;
        });

        main.addContent("config")
                .addContent(".addServiceDescriptor(serviceLoader(")
                .addContent(constantName)
                .addContentLine(",")
                .increaseContentPadding()
                .increaseContentPadding()
                .increaseContentPadding()
                .addContent(sl.serviceType()).addContentLine(".class,")
                .addContent("() -> new ").addContent(sl.serviceType()).addContentLine("(),")
                .addContent(String.valueOf(sl.weight()))
                .addContentLine("));")
                .decreaseContentPadding()
                .decreaseContentPadding()
                .decreaseContentPadding();
    }

    private Comparator<ServiceLoader__ServiceDescriptor> serviceLoaderComparator() {
        return Comparator.comparing(ServiceLoader__ServiceDescriptor::providerInterface)
                .thenComparing(ServiceDescriptor::serviceType);
    }

    private void handleError(CodegenException ce) {
        if (failOnError) {
            throw ce;
        } else {
            ctx.logger().log(ce.toEvent(System.Logger.Level.WARNING));
        }
    }
}
