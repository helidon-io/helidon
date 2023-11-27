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

package io.helidon.inject.maven.plugin;

import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import io.helidon.codegen.CodegenEvent;
import io.helidon.codegen.CodegenException;
import io.helidon.codegen.CodegenOptions;
import io.helidon.codegen.CodegenUtil;
import io.helidon.codegen.classmodel.ClassModel;
import io.helidon.codegen.classmodel.ContentBuilder;
import io.helidon.codegen.classmodel.Javadoc;
import io.helidon.codegen.classmodel.Method;
import io.helidon.codegen.compiler.Compiler;
import io.helidon.codegen.compiler.CompilerOptions;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.Annotations;
import io.helidon.common.types.TypeName;
import io.helidon.inject.codegen.InjectCodegenTypes;
import io.helidon.inject.service.Interception;
import io.helidon.inject.service.Ip;
import io.helidon.inject.service.Lookup;
import io.helidon.inject.service.Qualifier;
import io.helidon.inject.service.ServiceInfo;

import static io.helidon.inject.codegen.InjectCodegenTypes.APPLICATION;
import static io.helidon.inject.codegen.InjectCodegenTypes.MODULE_COMPONENT;
import static io.helidon.inject.codegen.InjectCodegenTypes.SERVICE_INJECTION_PLAN_BINDER;

/**
 * The default implementation for {@link io.helidon.inject.maven.plugin.ApplicationCreator}.
 */
class ApplicationCreator {
    private static final TypeName CREATOR = TypeName.create(ApplicationCreator.class);

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
                .copyright(CodegenUtil.copyright(CREATOR,
                                                 CREATOR,
                                                 typeName))
                .description("Generated Application to provide explicit bindings for known services.")
                .type(typeName)
                .addAnnotation(CodegenUtil.generatedAnnotation(CREATOR,
                                                               CREATOR,
                                                               typeName,
                                                               "1",
                                                               ""))
                .addInterface(APPLICATION);

        // deprecated default constructor - application should always be service loaded
        classModel.addConstructor(ctr -> ctr.javadoc(Javadoc.builder()
                                                             .addLine(
                                                                     "Constructor only for use by {@link java.util"
                                                                             + ".ServiceLoader}.")
                                                             .addTag("deprecated", "to be used by Java Service Loader only")
                                                             .build())
                .addAnnotation(Annotations.DEPRECATED));

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
                        .type(SERVICE_INJECTION_PLAN_BINDER))
                .update(it -> createConfigureMethodBody(injectionServices,
                                                        serviceTypes,
                                                        it)));

        Path generated = ctx.filer()
                .writeSourceFile(classModel.build());

        Compiler.compile(compilerOptions, generated);

        if (ctx.module().isPresent()) {
            List<TypeName> provided = ctx.module().get()
                    .provides()
                    .get(APPLICATION);
            if (!provided.contains(typeName)) {
                throw new CodegenException("Please add \"provides " + APPLICATION.fqName()
                                                   + " with " + typeName.fqName() + ";\" to your module-info.java, as otherwise"
                                                   + " the "
                                                   + "application would not be discovered when running on module path.");
            }
        }
        // now we have the source generated, we add the META-INF/service, and compile the class
        if (ctx.module().isEmpty() || CodegenOptions.CREATE_META_INF_SERVICES.value(ctx.options())) {
            // only create meta-inf/services if we are not a JPMS module

            try {
                ctx.filer()
                        .services(CREATOR,
                                  APPLICATION,
                                  List.of(typeName));
            } catch (Exception e) {
                // ignore this exception, as the resource probably exists and was done by the user
                ctx.logger()
                        .log(CodegenEvent.builder()
                                     .level(System.Logger.Level.DEBUG)
                                     .message("Failed to create services, probably already exists")
                                     .throwable(e)
                                     .build());
            }
        }
    }

    BindingPlan bindingPlan(WrappedServices services,
                            TypeName serviceTypeName) {

        Lookup lookup = toLookup(serviceTypeName);
        ServiceInfo sp = services.get(lookup);
        TypeName serviceDescriptorType = sp.infoType();

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
    private static boolean isQualifiedInjectionTarget(ServiceInfo sp) {
        Set<TypeName> contractsImplemented = sp.contracts();
        List<Ip> dependencies = sp.dependencies();

        return (!dependencies.isEmpty())
                || (
                !contractsImplemented.isEmpty()
                        && !contractsImplemented.contains(MODULE_COMPONENT)
                        && !contractsImplemented.contains(APPLICATION));
    }

    private boolean isAllowListedProviderQualifierTypeName(ServiceInfo serviceInfo) {

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

        gatherProvidersNotAllowed(services, notAllowed, InjectCodegenTypes.INJECTION_POINT_PROVIDER);
        gatherProvidersNotAllowed(services, notAllowed, InjectCodegenTypes.SERVICES_PROVIDER);
        gatherProvidersNotAllowed(services, notAllowed, InjectCodegenTypes.QUALIFIED_PROVIDER);

        return notAllowed;
    }

    private void gatherProvidersNotAllowed(WrappedServices services,
                                           Set<TypeName> notAllowedProviders,
                                           TypeName contract) {
        List<ServiceInfo> providers = services.all(Lookup.builder()
                                                           .addContract(contract)
                                                           .build());
        for (ServiceInfo provider : providers) {
            TypeName typeName = provider.serviceType();
            if (!isAllowListedProviderName(typeName)
                    && !isAllowListedProviderQualifierTypeName(provider)) {
                notAllowedProviders.add(typeName);
            }
        }
    }

    private InjectionPlan injectionPlan(WrappedServices services,
                                        ServiceInfo self,
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

    private List<ServiceInfo> injectionPointProvidersFor(WrappedServices services, Ip injectionPoint) {
        if (injectionPoint.qualifiers().isEmpty()) {
            return List.of();
        }
        Lookup criteria = Lookup.builder(Lookup.create(injectionPoint))
                .qualifiers(Set.of()) // remove qualifier from lookup
                .addContract(InjectCodegenTypes.INJECTION_POINT_PROVIDER) // only search for injection point providers
                .build();
        return services.all(criteria);
    }

    private void createConfigureMethodBody(WrappedServices services,
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
            method.addContentLine("");
        }

        Iterator<ServiceInfo> interceptorIterator = interceptors.iterator();
        while (interceptorIterator.hasNext()) {
            method.addContent(interceptorIterator.next().infoType().genericTypeName())
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
                        .addContent(binding.injectionPoint.field());

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
        List<ServiceInfo> discovered = binding.descriptors();
        Iterator<TypeName> descriptors = discovered.stream()
                .map(ServiceInfo::infoType)
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
                                it.addContent(descriptors.next())
                                        .addContent(".INSTANCE");
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
                method.addContent(", ")
                        .addContent(descriptors.next().genericTypeName())
                        .addContentLine(".INSTANCE)");
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
                    method.addContent(", ")
                            .addContent(descriptors.next().genericTypeName())
                            .addContentLine(".INSTANCE)");
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
                                    it.addContent(descriptors.next())
                                            .addContent(".INSTANCE");
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
                method.addContent(", ")
                        .addContent(descriptors.next().genericTypeName())
                        .addContentLine(".INSTANCE)");
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
                        .addContent(descriptors.next().genericTypeName())
                        .addContentLine(".INSTANCE)");
            }
        }
    }

    record InjectionPlan(List<ServiceInfo> unqualifiedProviders,
                         List<ServiceInfo> qualifiedProviders) {
    }

    record BindingPlan(TypeName descriptorType,
                       Set<Binding> bindings) {
    }

    /**
     * @param injectionPoint to bind to
     * @param descriptors    matching descriptors
     */
    record Binding(Ip injectionPoint,
                   List<ServiceInfo> descriptors) {
    }
}
