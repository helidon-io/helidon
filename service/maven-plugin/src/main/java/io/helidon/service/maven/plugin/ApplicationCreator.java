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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import io.helidon.codegen.CodegenException;
import io.helidon.codegen.CodegenOptions;
import io.helidon.codegen.CodegenUtil;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.classmodel.ContentBuilder;
import io.helidon.codegen.classmodel.Method;
import io.helidon.codegen.compiler.Compiler;
import io.helidon.codegen.compiler.CompilerOptions;
import io.helidon.common.types.AccessModifier;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.Annotations;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.service.codegen.DescriptorClassCode;
import io.helidon.service.codegen.GenerateServiceDescriptor;
import io.helidon.service.codegen.HelidonMetaInfServices;
import io.helidon.service.codegen.HelidonMetaInfServices.DescriptorMeta;
import io.helidon.service.codegen.RegistryCodegenContext;
import io.helidon.service.inject.api.InjectServiceInfo;
import io.helidon.service.inject.api.Interception;
import io.helidon.service.inject.api.Ip;
import io.helidon.service.inject.api.Lookup;
import io.helidon.service.inject.api.Qualifier;
import io.helidon.service.registry.ServiceLoader__ServiceDescriptor;

import static io.helidon.service.codegen.ServiceCodegenTypes.INJECTION_PLAN_BINDER;
import static io.helidon.service.codegen.ServiceCodegenTypes.INJECTION_POINT_PROVIDER;
import static io.helidon.service.codegen.ServiceCodegenTypes.INJECT_APPLICATION;
import static io.helidon.service.codegen.ServiceCodegenTypes.QUALIFIED_PROVIDER;
import static io.helidon.service.codegen.ServiceCodegenTypes.SERVICES_PROVIDER;
import static io.helidon.service.codegen.ServiceCodegenTypes.SERVICE_ANNOTATION_CONTRACT;
import static io.helidon.service.codegen.ServiceCodegenTypes.SERVICE_ANNOTATION_PROVIDER;

/**
 * The default implementation for {@link ApplicationCreator}.
 */
class ApplicationCreator {
    private static final TypeName GENERATOR = TypeName.create(ApplicationCreator.class);

    private final MavenCodegenContext ctx;
    private final boolean failOnError;
    private final PermittedProviderType permittedProviderType;
    private final Set<TypeName> permittedProviderTypes;
    private final Set<TypeName> permittedProviderQualifierTypes;

    ApplicationCreator(MavenCodegenContext scanContext, boolean failOnError) {
        this.ctx = scanContext;
        this.failOnError = failOnError;

        CodegenOptions options = scanContext.options();
        this.permittedProviderType = ApplicationOptions.PERMITTED_PROVIDER_TYPE.value(options);
        this.permittedProviderTypes = ApplicationOptions.PERMITTED_PROVIDER_TYPES.value(options);
        this.permittedProviderQualifierTypes = ApplicationOptions.PERMITTED_PROVIDER_QUALIFIER_TYPES.value(options);
    }

    /**
     * Generates the source and class file for {@code io.helidon.inject.Application} using the current classpath.
     *
     * @param injectionServices injection services to use
     * @param serviceTypes      types to process
     * @param typeName          generated application type name
     * @param moduleName        name of the module of this maven module
     * @param compilerOptions   compilation options
     */
    void createApplication(WrappedServices injectionServices,
                           Set<TypeName> serviceTypes,
                           TypeName typeName,
                           String moduleName,
                           CompilerOptions compilerOptions) {
        Objects.requireNonNull(injectionServices);
        Objects.requireNonNull(serviceTypes);

        Set<TypeName> providersInUseThatAreNotAllowed = providersNotAllowed(injectionServices);
        if (!providersInUseThatAreNotAllowed.isEmpty()) {
            handleError(new CodegenException("There are dynamic Providers being used that are not allow-listed: "
                                                     + providersInUseThatAreNotAllowed
                                                     + "; see the documentation for examples of allow-listing."));
        }

        try {
            codegen(injectionServices, serviceTypes, typeName, moduleName, compilerOptions);
        } catch (CodegenException ce) {
            handleError(ce);
        } catch (Throwable te) {
            handleError(new CodegenException("Failed to code generate application class", te));
        }
    }

    void codegen(WrappedServices injectionServices,
                 Set<TypeName> serviceTypes,
                 TypeName typeName,
                 String moduleName,
                 CompilerOptions compilerOptions) {
        ClassModel.Builder classModel = ClassModel.builder()
                .accessModifier(AccessModifier.PACKAGE_PRIVATE)
                .copyright(CodegenUtil.copyright(GENERATOR,
                                                 GENERATOR,
                                                 typeName))
                .description("Generated Application to provide explicit bindings for known services.")
                .type(typeName)
                .addAnnotation(CodegenUtil.generatedAnnotation(GENERATOR,
                                                               GENERATOR,
                                                               typeName,
                                                               "1",
                                                               ""))
                .addInterface(INJECT_APPLICATION);

        // deprecated default constructor - application should always be service loaded
        classModel.addConstructor(ctr -> ctr
                .accessModifier(AccessModifier.PACKAGE_PRIVATE));

        // public String name()
        classModel.addMethod(nameMethod -> nameMethod
                .addAnnotation(Annotations.OVERRIDE)
                .returnType(io.helidon.common.types.TypeNames.STRING)
                .name("name")
                .addContentLine("return \"" + moduleName + "\";"));

        // public void configure(ServiceInjectionPlanBinder binder)
        classModel.addMethod(configureMethod -> configureMethod
                .addAnnotation(Annotations.OVERRIDE)
                .name("configure")
                .addParameter(binderParam -> binderParam
                        .name("binder")
                        .type(INJECTION_PLAN_BINDER))
                .update(it -> createConfigureMethodBody(injectionServices,
                                                        serviceTypes,
                                                        it)));

        Path generated = ctx.filer()
                .writeSourceFile(classModel.build());

        TypeInfo appTypeInfo = createAppTypeInfo(typeName);
        RegistryCodegenContext registryCodegenContext = RegistryCodegenContext.create(ctx);
        GenerateServiceDescriptor.generate(GENERATOR,
                                           registryCodegenContext,
                                           List.of(appTypeInfo),
                                           appTypeInfo);

        List<Path> toCompile = new ArrayList<>();
        toCompile.add(generated);

        HelidonMetaInfServices services = HelidonMetaInfServices.create(ctx.filer(), GENERATOR, GENERATOR, moduleName);

        for (DescriptorClassCode descriptor : registryCodegenContext.descriptors()) {
            Path path = ctx.filer().writeSourceFile(descriptor.classCode().classModel().build(),
                                                    descriptor.classCode().originatingElements());
            toCompile.add(path);

            services.add(new DescriptorMeta(descriptor.registryType(),
                                            descriptor.classCode().newType(),
                                            descriptor.weight(),
                                            descriptor.contracts()));
        }

        services.write();
        Compiler.compile(compilerOptions, toCompile.toArray(new Path[0]));
    }

    BindingPlan bindingPlan(WrappedServices services,
                            TypeName serviceTypeName) {

        Lookup lookup = toLookup(serviceTypeName);
        InjectServiceInfo sp = services.get(lookup);
        TypeName serviceDescriptorType = sp.descriptorType();

        if (!isQualifiedInjectionTarget(sp)) {
            return new BindingPlan(serviceDescriptorType, Set.of());
        }

        List<Ip> dependencies = sp.dependencies();
        if (dependencies.isEmpty()) {
            return new BindingPlan(serviceDescriptorType, Set.of());
        }

        Set<Binding> bindings = new LinkedHashSet<>();
        for (Ip dependency : dependencies) {
            InjectionPlan iPlan = injectionPlan(services, sp, dependency);
            List<InjectServiceInfo> qualified = iPlan.qualifiedProviders();
            List<InjectServiceInfo> unqualified = iPlan.unqualifiedProviders();
            List<InjectServiceInfo> usedList;

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

    private static Consumer<ContentBuilder<?>> toContentBuilder(InjectServiceInfo serviceInfo) {
        if (serviceInfo.coreInfo() instanceof ServiceLoader__ServiceDescriptor sl) {
            // we need to create a specific descriptor for interface and implementation
            TypeName providerInterface = sl.providerInterface();
            TypeName providerImpl = sl.serviceType();
            return it -> it.addContent(sl.descriptorType())
                    .addContent(".create(")
                    .addContentCreate(providerInterface)
                    .addContent(", ")
                    .addContent(providerImpl)
                    .addContent(".class, () -> new ")
                    .addContent(providerImpl)
                    .addContent("(), ")
                    .addContent(String.valueOf(sl.weight()))
                    .addContent(")");
        } else {
            // the usual singleton instance
            return it -> it.addContent(serviceInfo.descriptorType().fqName())
                    .addContent(".INSTANCE");
        }
    }

    private static Lookup toLookup(TypeName typeName) {
        return Lookup.builder()
                .serviceType(typeName)
                .build();
    }

    /**
     * Determines if the service provider is valid to receive injections.
     *
     * @param sp the service provider
     * @return true if the service provider can receive injection
     */
    private static boolean isQualifiedInjectionTarget(InjectServiceInfo sp) {
        Set<TypeName> contractsImplemented = sp.contracts();
        List<Ip> dependencies = sp.dependencies();

        return (!dependencies.isEmpty())
                || (
                !contractsImplemented.isEmpty()
                        && !contractsImplemented.contains(INJECT_APPLICATION));
    }

    private TypeInfo createAppTypeInfo(TypeName typeName) {
        return TypeInfo.builder()
                .kind(ElementKind.CLASS)
                .typeName(typeName)
                // to trigger generation of descriptor
                .addAnnotation(Annotation.create(SERVICE_ANNOTATION_PROVIDER))
                .addInterfaceTypeInfo(TypeInfo.builder()
                                              .kind(ElementKind.INTERFACE)
                                              .typeName(INJECT_APPLICATION)
                                              .addAnnotation(Annotation.create(SERVICE_ANNOTATION_CONTRACT))
                                              .build())
                .build();
    }

    private boolean isAllowListedProviderQualifierTypeName(InjectServiceInfo serviceInfo) {

        if (permittedProviderQualifierTypes.isEmpty()) {
            return false;
        }

        Set<TypeName> spQualifierTypeNames = serviceInfo.qualifiers().stream()
                .map(Annotation::typeName)
                .collect(Collectors.toSet());
        spQualifierTypeNames.retainAll(permittedProviderQualifierTypes);
        return !spQualifierTypeNames.isEmpty();
    }

    private boolean isAllowListedProviderName(TypeName typeName) {
        return switch (permittedProviderType) {
            case ALL -> true;
            case NONE -> false;
            default -> permittedProviderTypes.contains(typeName);
        };
    }

    private void handleError(CodegenException ce) {
        if (failOnError) {
            throw ce;
        } else {
            ctx.logger().log(ce.toEvent(System.Logger.Level.WARNING));
        }
    }

    private Set<TypeName> providersNotAllowed(WrappedServices services) {

        Set<TypeName> notAllowed = new LinkedHashSet<>();

        gatherProvidersNotAllowed(services, notAllowed, INJECTION_POINT_PROVIDER);
        gatherProvidersNotAllowed(services, notAllowed, SERVICES_PROVIDER);
        gatherProvidersNotAllowed(services, notAllowed, QUALIFIED_PROVIDER);

        return notAllowed;
    }

    private void gatherProvidersNotAllowed(WrappedServices services,
                                           Set<TypeName> notAllowedProviders,
                                           TypeName contract) {
        List<InjectServiceInfo> providers = services.all(Lookup.builder()
                                                                 .addContract(contract)
                                                                 .build());
        for (InjectServiceInfo provider : providers) {
            TypeName typeName = provider.serviceType();
            if (!isAllowListedProviderName(typeName)
                    && !isAllowListedProviderQualifierTypeName(provider)) {
                notAllowedProviders.add(typeName);
            }
        }
    }

    private InjectionPlan injectionPlan(WrappedServices services,
                                        InjectServiceInfo self,
                                        Ip dependency) {
        /*
         very similar code is used in ServiceManager.planForIp
         make sure this is kept in sync!
         */
        Lookup dependencyTo = Lookup.create(dependency);
        Set<Qualifier> qualifiers = dependencyTo.qualifiers();
        if (self.contracts().containsAll(dependencyTo.contracts()) && self.qualifiers().equals(qualifiers)) {
            // criteria must have a single contract for each injection point
            // if this service implements the contracts actually required, we must look for services with lower weight
            // but only if we also have the same qualifiers
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

        List<InjectServiceInfo> qualifiedProviders = services.all(dependencyTo);
        List<InjectServiceInfo> unqualifiedProviders;

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

    private List<InjectServiceInfo> injectionPointProvidersFor(WrappedServices services, Ip injectionPoint) {
        if (injectionPoint.qualifiers().isEmpty()) {
            return List.of();
        }
        Lookup criteria = Lookup.builder(Lookup.create(injectionPoint))
                .qualifiers(Set.of()) // remove qualifier from lookup
                .addContract(INJECTION_POINT_PROVIDER) // only search for injection point providers
                .build();
        return services.all(criteria);
    }

    private void createConfigureMethodBody(WrappedServices services,
                                           Set<TypeName> serviceTypes,
                                           Method.Builder method) {
        // find all interceptors and bind them
        List<InjectServiceInfo> interceptors =
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

        Iterator<InjectServiceInfo> interceptorIterator = interceptors.iterator();
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
        method.addContentLine(");")
                .addContentLine("");

        // first collect required dependencies by descriptor
        Map<TypeName, Set<Binding>> injectionPlan = new LinkedHashMap<>();
        for (TypeName serviceType : serviceTypes) {
            BindingPlan plan = bindingPlan(services, serviceType);
            if (!plan.bindings.isEmpty()) {
                injectionPlan.put(plan.descriptorType(), plan.bindings());
            }
        }

        boolean supportNulls = false;
        // we group all bindings by descriptor they belong to
        injectionPlan.forEach((descriptorType, bindings) -> {
            method.addContent("binder.bindTo(")
                    .addContent(descriptorType.genericTypeName())
                    .addContentLine(".INSTANCE)")
                    .increaseContentPadding();

            for (Binding binding : bindings) {
                Consumer<ContentBuilder<?>> ipId = content -> content
                        .addContent(binding.injectionPoint().descriptor().genericTypeName())
                        .addContent(".")
                        .addContent(binding.injectionPoint.descriptorConstant());

                buildTimeBinding(method, binding, ipId, supportNulls);
            }

            /*
            Commit the dependencies
             */
            method.addContentLine(".commit();")
                    .decreaseContentPadding()
                    .addContentLine("");
        });
    }

    private void buildTimeBinding(Method.Builder method,
                                  Binding binding,
                                  Consumer<ContentBuilder<?>> ipId,
                                  boolean supportNulls) {

        /*
        Very similar code is used for runtime discovery in ServiceManager.planForIp
        make sure this is doing the same thing!
        Here we code generate the calls to the application class
         */

        Ip injectionPoint = binding.injectionPoint();
        List<InjectServiceInfo> discovered = binding.descriptors();
        Iterator<Consumer<ContentBuilder<?>>> descriptors = discovered.stream()
                .map(ApplicationCreator::toContentBuilder)
                .iterator();

        TypeName ipType = injectionPoint.typeName();

        // now there are a few options - optional, list, and single instance
        if (ipType.isList()) {
            TypeName typeOfList = ipType.typeArguments().getFirst();
            if (typeOfList.isSupplier()) {
                // inject List<Supplier<Contract>>
                method.addContent(".bindListOfSuppliers(");
            } else {
                // inject List<Contract>
                method.addContent(".bindList(");
            }
            method.update(ipId::accept);

            if (discovered.isEmpty()) {
                method.addContentLine(")");
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
                        .addContentLine(")");
            }
        } else if (ipType.isOptional()) {
            TypeName typeOfOptional = ipType.typeArguments().getFirst();
            if (typeOfOptional.isSupplier()) {
                // inject Optional<Supplier<Contract>>
                method.addContent(".bindOptionalOfSupplier(");
            } else {
                // inject Optional<Contract>
                method.addContent(".bindOptional(");
            }
            method.update(ipId::accept);

            if (discovered.isEmpty()) {
                method.addContentLine(")");
            } else {
                method.addContent(", ");
                descriptors.next().accept(method);
                method.addContentLine(")");
            }
        } else if (ipType.isSupplier()) {
            // one of the supplier options

            TypeName typeOfSupplier = ipType.typeArguments().getFirst();
            if (typeOfSupplier.isOptional()) {
                // inject Supplier<Optional<Contract>>
                method.addContent(".bindSupplierOfOptional(")
                        .update(ipId::accept);
                if (discovered.isEmpty()) {
                    method.addContentLine(")");
                } else {
                    method.addContent(", ");
                    descriptors.next().accept(method);
                    method.addContentLine(")");
                }
            } else if (typeOfSupplier.isList()) {
                // inject Supplier<List<Contract>>
                method.addContent(".bindSupplierOfList(")
                        .update(ipId::accept);
                if (discovered.isEmpty()) {
                    method.addContentLine(")");
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
                            .addContentLine(")");
                }
            } else {
                // inject Supplier<Contract>
                method.addContent(".bindSupplier(")
                        .update(ipId::accept);

                if (discovered.isEmpty()) {
                    // null binding is not supported at runtime
                    throw new CodegenException("Injection point requires a value, but no provider discovered: "
                                                       + injectionPoint);
                }
                method.addContent(", ");
                descriptors.next().accept(method);
                method.addContentLine(")");
            }
        } else {
            // inject Contract
            if (discovered.isEmpty()) {
                if (supportNulls) {
                    method.addContent(".bindNull(")
                            .update(ipId::accept)
                            .addContentLine(")");
                } else {
                    // null binding is not supported at runtime
                    throw new CodegenException("Injection point requires a value, but no provider discovered: "
                                                       + injectionPoint);
                }
            } else {
                method.addContent(".bind(")
                        .update(ipId::accept)
                        .addContent(", ")
                        .update(descriptors.next()::accept)
                        .addContentLine(")");
            }
        }
    }

    record InjectionPlan(List<InjectServiceInfo> unqualifiedProviders,
                         List<InjectServiceInfo> qualifiedProviders) {
    }

    record BindingPlan(TypeName descriptorType,
                       Set<Binding> bindings) {
    }

    /**
     * @param injectionPoint to bind to
     * @param descriptors    matching descriptors
     */
    record Binding(Ip injectionPoint,
                   List<InjectServiceInfo> descriptors) {
    }
}
