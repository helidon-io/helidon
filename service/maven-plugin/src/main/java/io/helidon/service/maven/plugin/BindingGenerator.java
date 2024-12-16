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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import io.helidon.codegen.CodegenException;
import io.helidon.codegen.CodegenUtil;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.classmodel.ContentBuilder;
import io.helidon.codegen.classmodel.Method;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.Annotations;
import io.helidon.common.types.ResolvedType;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.service.codegen.DescriptorClassCode;
import io.helidon.service.codegen.HelidonMetaInfServices;
import io.helidon.service.metadata.DescriptorMetadata;
import io.helidon.service.registry.Dependency;
import io.helidon.service.registry.Interception;
import io.helidon.service.registry.Lookup;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.Service.RunLevel;
import io.helidon.service.registry.ServiceDescriptor;
import io.helidon.service.registry.ServiceInfo;
import io.helidon.service.registry.ServiceLoader__ServiceDescriptor;

import static io.helidon.service.codegen.ServiceCodegenTypes.LIST_OF_DOUBLES;
import static io.helidon.service.codegen.ServiceCodegenTypes.SERVICE_ANNOTATION_RUN_LEVEL;
import static io.helidon.service.codegen.ServiceCodegenTypes.SERVICE_BINDING;
import static io.helidon.service.codegen.ServiceCodegenTypes.SERVICE_CONFIG_BUILDER;
import static io.helidon.service.codegen.ServiceCodegenTypes.SERVICE_INJECTION_POINT_FACTORY;
import static io.helidon.service.codegen.ServiceCodegenTypes.SERVICE_LOADER_DESCRIPTOR;
import static io.helidon.service.codegen.ServiceCodegenTypes.SERVICE_PLAN_BINDER;

/**
 * The default implementation for {@link BindingGenerator}.
 */
class BindingGenerator {
    private static final TypeName GENERATOR = TypeName.create(BindingGenerator.class);

    private final MavenCodegenContext ctx;
    private final boolean failOnError;

    BindingGenerator(MavenCodegenContext scanContext, boolean failOnError) {
        this.ctx = scanContext;
        this.failOnError = failOnError;
    }

    /**
     * Generates the source and class file for {@code Binding} using the current classpath.
     *
     * @param injectionServices injection services to use
     * @param serviceTypes      types to process
     * @param typeName          generated binding type name
     * @param moduleName        name of the module of this maven module
     * @param sourcesToCompile  list of sources to compile
     */
    void createBinding(WrappedServices injectionServices,
                       Set<TypeName> serviceTypes,
                       TypeName typeName,
                       String moduleName,
                       List<Path> sourcesToCompile) {
        Objects.requireNonNull(injectionServices);
        Objects.requireNonNull(serviceTypes);

        try {
            codegen(injectionServices, serviceTypes, typeName, moduleName, sourcesToCompile);
        } catch (CodegenException ce) {
            handleError(ce);
        } catch (Throwable te) {
            handleError(new CodegenException("Failed to code generate binding class", te));
        }
    }

    void codegen(WrappedServices injectionServices,
                 Set<TypeName> serviceTypes,
                 TypeName typeName,
                 String moduleName,
                 List<Path> sourcesToCompile) {
        ClassModel.Builder classModel = ClassModel.builder()
                .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                .copyright(CodegenUtil.copyright(GENERATOR,
                                                 GENERATOR,
                                                 typeName))
                .description("Generated Binding to provide explicit bindings for known services.")
                .type(typeName)
                .addAnnotation(CodegenUtil.generatedAnnotation(GENERATOR,
                                                               GENERATOR,
                                                               typeName,
                                                               "1",
                                                               ""))
                .addInterface(SERVICE_BINDING);

        // deprecated default constructor - binding should always be service loaded
        classModel.addConstructor(ctr -> ctr
                .accessModifier(AccessModifier.PRIVATE));

        // public String name()
        classModel.addMethod(nameMethod -> nameMethod
                .addAnnotation(Annotations.OVERRIDE)
                .returnType(io.helidon.common.types.TypeNames.STRING)
                .name("name")
                .addContentLine("return \"" + moduleName + "\";"));

        // public void binding(DependencyPlanBinder binder)
        classModel.addMethod(bindingMethod -> bindingMethod
                .addAnnotation(Annotations.OVERRIDE)
                .name("binding")
                // constructors of services for service loader are usually deprecated in Helidon
                .addAnnotation(Annotation.create(SuppressWarnings.class, "deprecation"))
                .addParameter(binderParam -> binderParam
                        .name("binder")
                        .type(SERVICE_PLAN_BINDER))
                .update(it -> createBindingMethodBody(injectionServices,
                                                      serviceTypes,
                                                      it)));

        // public void configure(ServiceRegistryConfig.Builder builder)
        classModel.addMethod(configureMethod -> configureMethod
                .addAnnotation(Annotations.OVERRIDE)
                .name("configure")
                .addParameter(configBuilder -> configBuilder
                        .name("builder")
                        .type(SERVICE_CONFIG_BUILDER)
                )
                .update(it -> createConfigureMethodBody(injectionServices,
                                                        classModel,
                                                        it))
        );
        // static Application__Binding create()
        classModel.addMethod(create -> create
                .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                .returnType(typeName)
                .name("create")
                .isStatic(true)
                .description("Create a new application binding instance.")
                .addContent("return new ")
                .addContent(typeName.className())
                .addContentLine("();")
        );

        Path generated = ctx.filer()
                .writeSourceFile(classModel.build());

        MavenRoundContext roundContext = new MavenRoundContext(ctx);

        sourcesToCompile.add(generated);

        HelidonMetaInfServices services = HelidonMetaInfServices.create(ctx.filer(), moduleName);

        for (DescriptorClassCode descriptor : roundContext.descriptors()) {
            Path path = ctx.filer().writeSourceFile(descriptor.classCode().classModel().build(),
                                                    descriptor.classCode().originatingElements());
            sourcesToCompile.add(path);

            services.add(DescriptorMetadata.create(descriptor.registryType(),
                                                   descriptor.classCode().newType(),
                                                   descriptor.weight(),
                                                   descriptor.contracts(),
                                                   Set.of()));
        }

        services.write();
    }

    BindingPlan bindingPlan(WrappedServices services,
                            TypeName serviceTypeName) {

        Lookup lookup = toLookup(serviceTypeName);
        ServiceInfo sp = services.get(lookup);
        TypeName serviceDescriptorType = sp.descriptorType();

        if (!isQualifiedInjectionTarget(sp)) {
            return new BindingPlan(serviceDescriptorType, Set.of());
        }

        List<Dependency> dependencies = sp.dependencies();
        if (dependencies.isEmpty()) {
            return new BindingPlan(serviceDescriptorType, Set.of());
        }

        Set<Binding> bindings = new LinkedHashSet<>();
        for (Dependency dependency : dependencies) {
            InjectionPlan iPlan = injectionPlan(services, sp, dependency);
            List<ServiceInfo> qualified = iPlan.qualifiedProviders();
            List<ServiceInfo> unqualified = iPlan.unqualifiedProviders();
            List<ServiceInfo> usedList;

            if (qualified.isEmpty() && !unqualified.isEmpty()) {
                usedList = unqualified;
            } else {
                usedList = qualified;
            }

            bindings.add(new Binding(dependency,
                                     usedList));
        }

        return new BindingPlan(serviceDescriptorType, bindings);
    }

    private static Consumer<ContentBuilder<?>> toContentBuilder(ServiceInfo serviceInfo) {
        if (serviceInfo instanceof ServiceLoader__ServiceDescriptor sl) {
            // we need to create a specific descriptor for interface and implementation
            TypeName providerInterface = sl.providerInterface();
            TypeName providerImpl = sl.serviceType();
            return it -> it.addContent(sl.descriptorType())
                    .addContent(".create(")
                    .addContentCreate(providerInterface)
                    .addContent(", ")
                    .addContent(providerImpl)
                    .addContent(".class, ")
                    .addContent(providerImpl)
                    .addContent("::new, ")
                    .addContent(String.valueOf(sl.weight()))
                    .addContent(")");
        } else {
            // the usual singleton instance
            return it -> it.addContent(serviceInfo.descriptorType())
                    .addContent(".INSTANCE");
        }
    }

    private static Lookup toLookup(TypeName typeName) {
        return Lookup.builder()
                .serviceType(typeName)
                .build();
    }

    /**
     * Determines if the service is valid to receive injections.
     *
     * @param sp the service provider
     * @return true if the service provider can receive injection
     */
    private static boolean isQualifiedInjectionTarget(ServiceInfo sp) {
        Set<ResolvedType> contractsImplemented = sp.contracts();
        List<Dependency> dependencies = sp.dependencies();

        if (contractsImplemented.contains(ResolvedType.create(SERVICE_BINDING))) {
            return false;
        }
        boolean hasDependencies = !dependencies.isEmpty();
        boolean hasContract = !contractsImplemented.isEmpty();

        return hasContract || hasDependencies;
    }

    private void createConfigureMethodBody(WrappedServices services,
                                           ClassModel.Builder classModel,
                                           Method.Builder method) {
        /*
        This method will:
        - configure all run levels
        - configure all services to avoid service discovery
         */

        configureRunLevels(services, classModel, method);
        registerServiceDescriptors(services, classModel, method);
    }

    private void configureRunLevels(WrappedServices services, ClassModel.Builder classModel, Method.Builder method) {

        /*
        private static final List<Double> RUN_LEVELS = List.of(12D, 100D);
        builder.runLevels(RUN_LEVELS);
         */
        runLevelsConstantField(classModel, services);
        method.addContentLine("builder.runLevels(RUN_LEVELS);");

    }

    static void runLevelsConstantField(ClassModel.Builder classModel, WrappedServices services) {
        Set<Double> runLevels = new TreeSet<>();

        for (ServiceInfo serviceInfo : services.all()) {
            if (serviceInfo.runLevel().isPresent()) {
                runLevels.add(serviceInfo.runLevel().get());
            }
        }

        List<Double> runLevelList = List.copyOf(runLevels);
        classModel.addField(runLevelsField -> runLevelsField
                .accessModifier(AccessModifier.PRIVATE)
                .isStatic(true)
                .isFinal(true)
                .type(LIST_OF_DOUBLES)
                .name("RUN_LEVELS")
                .addContent(List.class)
                .addContent(".of(")
                .update(fieldBuilder -> {
                    for (int i = 0; i < runLevelList.size(); i++) {
                        double current = runLevelList.get(i);
                        if (Double.compare(RunLevel.STARTUP, current) == 0) {
                            fieldBuilder.addContent(SERVICE_ANNOTATION_RUN_LEVEL)
                                    .addContent(".STARTUP");
                        } else if (Double.compare(RunLevel.SERVER, current) == 0) {
                            fieldBuilder.addContent(SERVICE_ANNOTATION_RUN_LEVEL)
                                    .addContent(".SERVER");
                        } else if (Double.compare(RunLevel.NORMAL, current) == 0) {
                            fieldBuilder.addContent(SERVICE_ANNOTATION_RUN_LEVEL)
                                    .addContent(".NORMAL");
                        } else {
                            fieldBuilder.addContent(String.valueOf(current))
                                    .addContent("D");
                        }
                        if (i == runLevelList.size() - 1) {
                            fieldBuilder.addContentLine("");
                        } else {
                            fieldBuilder.addContentLine(",");
                        }
                    }
                })
                .addContent(")")
        );
    }

    private void registerServiceDescriptors(WrappedServices services, ClassModel.Builder classModel, Method.Builder method) {
        List<ServiceLoader__ServiceDescriptor> serviceLoaded = new ArrayList<>();
        List<TypeName> serviceDescriptors = new ArrayList<>();

        // for each discovered service, add it to the configuration
        for (ServiceInfo serviceInfo : services.all()) {
            if (serviceInfo instanceof ServiceLoader__ServiceDescriptor sl) {
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

        // viceDescriptor(ImperativeFeature__ServiceDescriptor.INSTANCE);
        serviceDescriptors.stream()
                .sorted()
                .forEach(it -> method
                        .addContent("builder.addServiceDescriptor(")
                        .addContent(it)
                        .addContentLine(".INSTANCE);"));
    }

    private void addServiceLoader(ClassModel.Builder classModel,
                                  Method.Builder main,
                                  Map<TypeName, String> providerConstants,
                                  AtomicInteger constantCounter,
                                  ServiceLoader__ServiceDescriptor sl) {
        // Generated code:
        // builder.addServiceDescriptor(serviceLoader(PROVIDER_1,
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

        main.addContent("builder.addServiceDescriptor(")
                .addContent(SERVICE_LOADER_DESCRIPTOR)
                .addContent(".create(")
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

    private void handleError(CodegenException ce) {
        if (failOnError) {
            throw ce;
        } else {
            ctx.logger().log(ce.toEvent(System.Logger.Level.WARNING));
        }
    }

    private InjectionPlan injectionPlan(WrappedServices services,
                                        ServiceInfo self,
                                        Dependency dependency) {
        /*
         very similar code is used in ServiceManager.planForIp
         make sure this is kept in sync!
         */
        Lookup dependencyTo = Lookup.create(dependency);
        Set<Qualifier> qualifiers = dependencyTo.qualifiers();
        if (self.contracts().containsAll(dependencyTo.contracts()) && self.qualifiers().equals(qualifiers)) {
            /*
            lookup must have a single contract for each injection point
            if this service implements the contracts actually required, we must look for services with lower weight
            but only if we also have the same qualifiers;
            this is to ensure that if an injection point in a service injects a contract the service implements itself,
            we do not end up in an infinite loop, but look for a service with a lower weight to satisfy that injection point
            this allows us to "chain" a single contract through multiple services
             */
            dependencyTo = Lookup.builder(dependencyTo)
                    .weight(self.weight())
                    .build();
        }

        /*
        An injection point can be satisfied by:
        1. a service that matches the type or contract, and qualifiers match
        2. a Supplier<T> service, where T matches service type or contract, and qualifiers match
        3. an InjectionPointProvider<T>, where T matches service type or contract, regardless of qualifiers
        4. an InjectionResolver, where the method resolve returns an information if this type can be resolved (config driven)
        */

        List<ServiceInfo> qualifiedProviders = services.all(dependencyTo);
        List<ServiceInfo> unqualifiedProviders;

        if (qualifiedProviders.isEmpty()) {
            unqualifiedProviders = injectionPointProvidersFor(services, dependency)
                    .stream()
                    .filter(it -> !it.serviceType().equals(self.serviceType()))
                    .toList();
        } else {
            unqualifiedProviders = List.of();
        }

        // remove current service provider from matches
        qualifiedProviders = qualifiedProviders.stream()
                .filter(it -> !it.serviceType().equals(self.serviceType()))
                .toList();

        // the list now contains all providers that match the processed injection points
        return new InjectionPlan(unqualifiedProviders, qualifiedProviders);
    }

    private List<ServiceInfo> injectionPointProvidersFor(WrappedServices services, Dependency injectionPoint) {
        if (injectionPoint.qualifiers().isEmpty()) {
            return List.of();
        }
        Lookup lookup = Lookup.builder(Lookup.create(injectionPoint))
                .qualifiers(Set.of()) // remove qualifier from lookup
                .addContract(SERVICE_INJECTION_POINT_FACTORY) // only search for injection point providers
                .build();
        return services.all(lookup);
    }

    private void createBindingMethodBody(WrappedServices services,
                                         Set<TypeName> serviceTypes,
                                         Method.Builder method) {
        // find all interceptors and bind them
        List<ServiceInfo> interceptors =
                services.all(Lookup.builder()
                                     .addContract(Interception.Interceptor.class)
                                     .addQualifier(Qualifier.WILDCARD_NAMED)
                                     .build());
        method.addContent("binder.interceptors(");
        boolean multiline = interceptors.size() > 2;
        if (multiline) {
            method.addContentLine("")
                    .increaseContentPadding();
        }

        Iterator<ServiceInfo> interceptorIterator = interceptors.iterator();
        while (interceptorIterator.hasNext()) {
            method.addContent(interceptorIterator.next().descriptorType())
                    .addContent(".INSTANCE");
            if (interceptorIterator.hasNext()) {
                method.addContent(",");
                if (multiline) {
                    method.addContentLine("");
                } else {
                    method.addContent(" ");
                }
            }
        }

        if (multiline) {
            method.addContentLine("")
                    .decreaseContentPadding();
        }
        method.addContentLine(");");

        // first collect required dependencies by descriptor
        Map<TypeName, Set<Binding>> injectionPlan = new LinkedHashMap<>();
        for (TypeName serviceType : serviceTypes) {
            BindingPlan plan = bindingPlan(services, serviceType);
            if (!plan.bindings.isEmpty()) {
                injectionPlan.put(plan.descriptorType(), plan.bindings());
            }
        }

        // we group all bindings by descriptor they belong to
        injectionPlan.forEach((descriptorType, bindings) -> {
            method.addContentLine("")
                    .addContent("binder.service(")
                    .addContent(descriptorType.genericTypeName())
                    .addContent(".INSTANCE)")
                    .increaseContentPadding();

            for (Binding binding : bindings) {
                Consumer<ContentBuilder<?>> ipId = content -> content
                        .addContent(binding.dependency().descriptor().genericTypeName())
                        .addContent(".")
                        .addContent(binding.dependency.descriptorConstant());

                buildTimeBinding(method, binding, ipId);
            }

            /*
            Commit the dependencies
             */
            method.addContentLine(";")
                    .decreaseContentPadding();
        });
    }

    /*
    Very similar code is used for runtime discovery in io.helidon.service.registry.Bindings.DependencyBinding.discoverBinding
    make sure this is doing the same thing!
    Here we code generate the calls to the binding class
    */
    private void buildTimeBinding(Method.Builder method,
                                  Binding binding,
                                  Consumer<ContentBuilder<?>> ipId) {

        List<ServiceInfo> discovered = binding.descriptors();
        Iterator<Consumer<ContentBuilder<?>>> descriptors = discovered.stream()
                .map(BindingGenerator::toContentBuilder)
                .iterator();

        method.addContentLine("")
                .addContent(".bind(")
                .update(ipId::accept);

        if (discovered.isEmpty()) {
            method.addContent(")");
        } else {
            method.addContent(", ")
                    .update(it -> {
                        while (descriptors.hasNext()) {
                            descriptors.next().accept(it);
                            if (descriptors.hasNext()) {
                                it.addContent(", ");
                            }
                        }
                    })
                    .addContent(")");
        }
    }

    private Comparator<ServiceLoader__ServiceDescriptor> serviceLoaderComparator() {
        return Comparator.comparing(ServiceLoader__ServiceDescriptor::providerInterface)
                .thenComparing(ServiceDescriptor::serviceType);
    }

    record InjectionPlan(List<ServiceInfo> unqualifiedProviders,
                         List<ServiceInfo> qualifiedProviders) {
    }

    record BindingPlan(TypeName descriptorType,
                       Set<Binding> bindings) {
    }

    /**
     * @param dependency  to bind to
     * @param descriptors matching descriptors
     */
    record Binding(Dependency dependency,
                   List<ServiceInfo> descriptors) {
    }
}
