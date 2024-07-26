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

package io.helidon.service.maven.plugin;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.codegen.CodegenException;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.classmodel.Method;
import io.helidon.codegen.compiler.Compiler;
import io.helidon.codegen.compiler.CompilerOptions;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.service.codegen.GeneratedMainCodegen;
import io.helidon.service.inject.api.InjectServiceInfo;
import io.helidon.service.registry.DescriptorMetadata;
import io.helidon.service.registry.GeneratedService;
import io.helidon.service.registry.ServiceDiscovery;
import io.helidon.service.registry.ServiceLoader__ServiceDescriptor;

import static io.helidon.service.codegen.ServiceCodegenTypes.INJECT_APPLICATION;

class MainClassCreator {
    private static final TypeName GENERATOR = TypeName.create(MainClassCreator.class);

    private final MavenCodegenContext ctx;
    private final boolean failOnError;

    MainClassCreator(MavenCodegenContext scanContext, boolean failOnError) {
        this.ctx = scanContext;
        this.failOnError = failOnError;
    }

    void create(CompilerOptions compilerOptions, WrappedServices services, TypeName generatedType) {
        try {
            codegen(compilerOptions, services, generatedType);
        } catch (CodegenException ce) {
            handleError(ce);
        } catch (Throwable te) {
            handleError(new CodegenException("Failed to code generate application class", te));
        }
    }

    private void codegen(CompilerOptions compilerOptions, WrappedServices services, TypeName generatedType) {
        ClassModel classModel =
                GeneratedMainCodegen.generate(GENERATOR,
                                              generatedType,
                                              false,
                                              (classBuilder, methodModel, paramName) -> serviceDescriptors(classBuilder,
                                                                                                           methodModel,
                                                                                                           paramName,
                                                                                                           services))
                        .build();

        Path generated = ctx.filer()
                .writeSourceFile(classModel);

        Compiler.compile(compilerOptions, generated);
    }

    private void serviceDescriptors(ClassModel.Builder classModel,
                                    Method.Builder method,
                                    String paramName,
                                    WrappedServices services) {
        List<ServiceLoader__ServiceDescriptor> serviceLoaded = new ArrayList<>();
        List<TypeName> serviceDescriptors = new ArrayList<>();

        // applications must be added directly from service discovery, as they are not part of the registry
        addApplications(serviceDescriptors);

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
                .forEach(it -> addServiceLoader(classModel, method, paramName, providerConstants, constantCounter, it));

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

    private void addApplications(List<TypeName> serviceDescriptors) {
        ServiceDiscovery.create()
                .allMetadata()
                .stream()
                .filter(it -> it.contracts().contains(INJECT_APPLICATION))
                .map(DescriptorMetadata::descriptorType)
                .forEach(serviceDescriptors::add);
    }

    private void addServiceLoader(ClassModel.Builder classModel,
                                  Method.Builder main,
                                  String paramName,
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
                .thenComparing(GeneratedService.Descriptor::serviceType);
    }

    private void handleError(CodegenException ce) {
        if (failOnError) {
            throw ce;
        } else {
            ctx.logger().log(ce.toEvent(System.Logger.Level.WARNING));
        }
    }
}
