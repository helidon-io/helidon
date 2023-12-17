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

package io.helidon.inject.maven.plugin;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
import io.helidon.inject.InjectionPointProvider;
import io.helidon.inject.InjectionResolver;
import io.helidon.inject.InjectionServices;
import io.helidon.inject.Lookup;
import io.helidon.inject.ServiceInjectionPlanBinder;
import io.helidon.inject.ServiceProvider;
import io.helidon.inject.Services;
import io.helidon.inject.codegen.InjectCodegenTypes;
import io.helidon.inject.service.Interception;
import io.helidon.inject.service.Ip;
import io.helidon.inject.service.ModuleComponent;
import io.helidon.inject.service.Qualifier;

/**
 * The default implementation for {@link io.helidon.inject.maven.plugin.ApplicationCreator}.
 */
class ApplicationCreator {
    private static final TypeName MODULE_COMPONENT = TypeName.create(ModuleComponent.class);
    private static final TypeName APPLICATION = TypeName.create(ModuleComponent.class);
    private static final TypeName CREATOR = TypeName.create(ApplicationCreator.class);
    /**
     * Helidon {link io.helidon.inject.InjectionPointProvider}.
     */
    private static final TypeName INJECTION_POINT_PROVIDER = TypeName.create(InjectionPointProvider.class);
    /**
     * Helidon {@link io.helidon.inject.ServiceProvider}.
     */
    private static final TypeName SERVICE_PROVIDER = TypeName.create(ServiceProvider.class);
    private static final TypeName BINDER_TYPE = TypeName.create(ServiceInjectionPlanBinder.class);

    private final MavenCodegenContext ctx;
    private final boolean failOnError;
    private final PermittedProviderType permittedProviderType;
    private final Set<TypeName> permittedProviderTypes;
    private final Set<TypeName> permittedProviderQualifierTypes;

    ApplicationCreator(MavenCodegenContext scanContext, boolean failOnError) {
        this.ctx = scanContext;
        this.failOnError = failOnError;

        CodegenOptions options = scanContext.options();
        this.permittedProviderType = options.option(ApplicationOptions.PERMITTED_PROVIDER_TYPE,
                                                    PermittedProviderType.NAMED,
                                                    PermittedProviderType.class);
        this.permittedProviderTypes = options.asList(ApplicationOptions.PERMITTED_PROVIDER_TYPE_NAMES)
                .stream()
                .map(TypeName::create)
                .collect(Collectors.toSet());

        this.permittedProviderQualifierTypes = options.asList(ApplicationOptions.PERMITTED_PROVIDER_QUALIFIER_TYPE_NAMES)
                .stream()
                .map(TypeName::create)
                .collect(Collectors.toSet());
    }

    /**
     * Generates the source and class file for {@link io.helidon.inject.Application} using the current classpath.
     *
     * @param injectionServices injection services to use
     * @param serviceTypes      types to process
     * @param typeName          generated application type name
     * @param moduleName        name of the module of this maven module
     * @param compilerOptions   compilation options
     */
    public void createApplication(InjectionServices injectionServices,
                                  Set<TypeName> serviceTypes,
                                  TypeName typeName,
                                  String moduleName,
                                  CompilerOptions compilerOptions) {
        Objects.requireNonNull(injectionServices);
        Objects.requireNonNull(serviceTypes);

        List<TypeName> providersInUseThatAreNotAllowed = providersNotAllowed(injectionServices, serviceTypes);
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

    void codegen(InjectionServices injectionServices,
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
                .addInterface(InjectCodegenTypes.APPLICATION);

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
                        .type(BINDER_TYPE))
                .update(it -> createConfigureMethodBody(injectionServices,
                                                        serviceTypes,
                                                        it)));

        Path generated = ctx.filer()
                .writeSourceFile(classModel.build());

        // now we have the source generated, we add the META-INF/service, and compile the class
        ctx.filer()
                .services(CREATOR,
                          InjectCodegenTypes.APPLICATION,
                          List.of(typeName));

        Compiler.compile(compilerOptions, generated);
    }

    BindingPlan bindingPlan(InjectionServices services,
                            TypeName serviceTypeName) {

        Lookup si = toServiceInfoCriteria(serviceTypeName);
        ServiceProvider<?> sp = services.services().getProvider(si);
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
            // type of the result that satisfies the injection point (full generic type)
            TypeName ipType = dependency.typeName();

            InjectionPlan iPlan = injectionPlan(services, sp, dependency);
            List<ServiceProvider<Object>> qualified = iPlan.qualifiedProviders();
            List<?> unqualified = iPlan.unqualifiedProviders();

            BindingType type = bindingType(ipType);
            boolean isProvider = isProvider(ipType);

            if (qualified.isEmpty() && !unqualified.isEmpty()) {
                Object target = unqualified.getFirst();
                TypeName targetType;
                if (target instanceof Class<?> aClass) {
                    targetType = TypeName.create(aClass);
                } else if (target instanceof TypeName tn) {
                    targetType = tn;
                } else {
                    // the actual class name may be some generated type, we are interested in the contract required
                    targetType = dependency.contract();
                }
                bindings.add(new Binding(BindingTime.RUNTIME, type, dependency, isProvider, List.of(targetType)));
            } else {
                bindings.add(new Binding(BindingTime.BUILD, type, dependency, isProvider, qualified.stream()
                        .map(ServiceProvider::infoType)
                        .toList()));
            }
        }

        return new BindingPlan(serviceDescriptorType, bindings);
    }

    private static boolean isProvider(TypeName typeName,
                                      Services services) {
        ServiceProvider<?> sp = toServiceProvider(typeName, services);
        return sp.isProvider();
    }

    private static Lookup toServiceInfoCriteria(TypeName typeName) {
        return Lookup.builder()
                .serviceType(typeName)
                .build();
    }

    private static ServiceProvider<?> toServiceProvider(TypeName typeName,
                                                        Services services) {
        return services.getProvider(toServiceInfoCriteria(typeName));
    }

    /**
     * Determines if the service provider is valid to receive injections.
     *
     * @param sp the service provider
     * @return true if the service provider can receive injection
     */
    private static boolean isQualifiedInjectionTarget(ServiceProvider<?> sp) {
        Set<TypeName> contractsImplemented = sp.contracts();
        List<Ip> dependencies = sp.dependencies();

        return (!dependencies.isEmpty())
                || (
                !contractsImplemented.isEmpty()
                        && !contractsImplemented.contains(MODULE_COMPONENT)
                        && !contractsImplemented.contains(APPLICATION));
    }

    private boolean isAllowListedProviderQualifierTypeName(TypeName typeName,
                                                           Services services) {

        if (permittedProviderQualifierTypes.isEmpty()) {
            return false;
        }

        ServiceProvider<?> sp = toServiceProvider(typeName, services);
        Set<TypeName> spQualifierTypeNames = sp.qualifiers().stream()
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

    @SuppressWarnings("rawtypes")
    private List<TypeName> providersNotAllowed(InjectionServices injectionServices,
                                               Set<TypeName> serviceTypes) {
        Services services = injectionServices.services();

        List<ServiceProvider<Supplier>> providers = services.allProviders(Lookup.builder()
                                                                                      .addContract(Supplier.class)
                                                                                      .build());
        if (providers.isEmpty()) {
            return List.of();
        }

        List<TypeName> providersInUseThatAreNotAllowed = new ArrayList<>();
        for (TypeName typeName : serviceTypes) {
            if (!isAllowListedProviderName(typeName)
                    && isProvider(typeName, services)
                    && !isAllowListedProviderQualifierTypeName(typeName, services)) {
                providersInUseThatAreNotAllowed.add(typeName);
            }
        }
        return providersInUseThatAreNotAllowed;
    }

    private boolean isProvider(TypeName ipType) {
        if (ipType.isOptional() || ipType.isList() && !ipType.typeArguments().isEmpty()) {
            return isProvider(ipType.typeArguments().getFirst());
        }

        return ipType.isSupplier()
                || INJECTION_POINT_PROVIDER.equals(ipType)
                || SERVICE_PROVIDER.equals(ipType);
    }

    private BindingType bindingType(TypeName ipType) {
        if (ipType.isList()) {
            return BindingType.MANY;
        }
        if (ipType.isOptional()) {
            return BindingType.OPTIONAL;
        }
        return BindingType.SINGLE;
    }

    private InjectionPlan injectionPlan(InjectionServices injectionServices,
                                        ServiceProvider<?> self,
                                        Ip dependency) {
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

        if (self instanceof InjectionResolver ir) {
            Optional<Object> resolved = ir.resolve(dependency, injectionServices.services(), self, false);
            Object target = resolved.orElse(null);
            if (target != null) {
                return new InjectionPlan(toUnqualified(target).toList(), toQualified(target).toList());
            }
        }

        Services services = injectionServices.services();
        List<ServiceProvider<Object>> qualifiedProviders = services.allProviders(dependencyTo);
        List<ServiceProvider<Object>> unqualifiedProviders = List.of();

        if (qualifiedProviders.isEmpty()) {
            if (dependency.typeName().isOptional()) {
                return new InjectionPlan(List.of(), List.of());
            } else {
                unqualifiedProviders = injectionPointProvidersFor(services, dependency);
            }
        }

        // remove current service provider from matches
        qualifiedProviders = qualifiedProviders.stream()
                .filter(it -> !it.serviceInfo().equals(self.serviceInfo()))
                .toList();

        unqualifiedProviders = unqualifiedProviders.stream()
                .filter(it -> !it.serviceInfo().equals(self.serviceInfo()))
                .toList();

        // the list now contains all providers that match the processed injection points
        return new InjectionPlan(unqualifiedProviders, qualifiedProviders);
    }

    @SuppressWarnings("unchecked")
    private Stream<ServiceProvider<Object>> toQualified(Object target) {
        if (target instanceof Collection<?> collection) {
            return collection.stream()
                    .flatMap(this::toQualified);
        }
        return (target instanceof ServiceProvider<?> sp)
                ? Stream.of((ServiceProvider<Object>) sp)
                : Stream.of();
    }

    private Stream<?> toUnqualified(Object target) {
        if (target instanceof Collection<?> collection) {
            return collection.stream()
                    .flatMap(this::toUnqualified);
        }
        return (target instanceof ServiceProvider<?>)
                ? Stream.of()
                : Stream.of(target);
    }

    private List<ServiceProvider<Object>> injectionPointProvidersFor(Services services, Ip ipoint) {
        if (ipoint.qualifiers().isEmpty()) {
            return List.of();
        }
        Lookup criteria = Lookup.builder(Lookup.create(ipoint))
                .qualifiers(Set.of())
                .addContract(InjectCodegenTypes.INJECTION_POINT_PROVIDER)
                .build();
        return services.allProviders(criteria);
    }

    private void createConfigureMethodBody(InjectionServices services, Set<TypeName> serviceTypes, Method.Builder method) {

        // find all interceptors and bind them
        List<ServiceProvider<Interception.Interceptor>> interceptors = services.services()
                .allProviders(Lookup.builder()
                                          .addContract(Interception.Interceptor.class)
                                          .addQualifier(Qualifier.WILDCARD_NAMED)
                                          .build());
        method.addContent("binder.interceptors(");
        boolean multiline = interceptors.size() > 2;
        if (multiline) {
            method.addContentLine("");
        }

        Iterator<ServiceProvider<Interception.Interceptor>> interceptorIterator = interceptors.iterator();
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

        method.addContentLine(");");

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
                        .addContent(binding.ipInfo().descriptor().genericTypeName())
                        .addContent(".")
                        .addContent(binding.ipInfo.field());

                switch (binding.time()) {
                case RUNTIME -> runtimeBinding(method, binding, ipId, supportNulls);
                case BUILD -> buildTimeBinding(method, binding, ipId, supportNulls);
                default -> throw new IllegalArgumentException("Unsupported binding time: " + binding.time());
                }
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
        switch (binding.type()) {
        case SINGLE -> {
            if (binding.typeNames().isEmpty()) {
                if (supportNulls) {
                    method.addContent(".bindNull(")
                            .update(ipId::accept)
                            .addContentLine(")");
                } else {
                    throw new CodegenException("Injection point requires a value, but no provider discovered: "
                                                       + binding.ipInfo() + " for "
                                                       + binding.ipInfo().service().fqName());
                }
            } else {
                method.addContent(".bind(")
                        .update(ipId::accept)
                        .addContent(",")
                        .addContent(binding.useProvider() + ", ")
                        .addContent(binding.typeNames.getFirst().genericTypeName())
                        .addContentLine(".INSTANCE)");
            }
        }
        case OPTIONAL -> {
            method.addContent(".bindOptional(")
                    .update(ipId::accept)
                    .addContent(", ")
                    .addContent(String.valueOf(binding.useProvider()));
            if (binding.typeNames.isEmpty()) {
                method.addContentLine(")");
            } else {
                method.addContent(", ")
                        .addContent(binding.typeNames.getFirst().genericTypeName())
                        .addContentLine(".INSTANCE)");
            }
        }
        case MANY -> {
            method.addContent(".bindMany(")
                    .update(ipId::accept)
                    .addContent(", ")
                    .addContent(String.valueOf(binding.useProvider()));
            if (binding.typeNames.isEmpty()) {
                method.addContentLine(")");
            } else {
                method.addContent(", ")
                        .update(it -> {
                            Iterator<TypeName> iterator = binding.typeNames.iterator();
                            while (iterator.hasNext()) {
                                it.addContent(iterator.next())
                                        .addContent(".INSTANCE");
                                if (iterator.hasNext()) {
                                    it.addContent(", ");
                                }
                            }
                        })
                        .addContentLine(")");
            }
        }
        default -> throw new IllegalArgumentException("Unsupported binding type: " + binding.type());
        }
    }

    private void runtimeBinding(Method.Builder method, Binding binding, Consumer<ContentBuilder<?>> ipId, boolean supportNulls) {
        switch (binding.type()) {
        case SINGLE -> {
            if (supportNulls) {
                method.addContent(".runtimeBindNullable");
            } else {
                method.addContent(".runtimeBind");
            }
        }
        case OPTIONAL -> method.addContent(".runtimeBindOptional");
        case MANY -> method.addContent(".runtimeBindMany");
        default -> throw new IllegalArgumentException("Unsupported binding type: " + binding.type());
        }
        // such as .runtimeBind(MyDescriptor.IP_ID_0, false, ConfigBean.class)
        method.addContent("(")
                .update(ipId::accept)
                .addContent(", ")
                .addContent(binding.useProvider + ", ")
                .addContent(binding.typeNames.getFirst().genericTypeName())
                .addContentLine(".class)");
    }

    enum BindingType {
        SINGLE,
        OPTIONAL,
        MANY
    }

    enum BindingTime {
        BUILD,
        RUNTIME
    }

    record InjectionPlan(List<?> unqualifiedProviders,
                         List<ServiceProvider<Object>> qualifiedProviders) {
    }

    record BindingPlan(TypeName descriptorType,
                       Set<Binding> bindings) {
    }

    record Binding(BindingTime time,
                   BindingType type,
                   Ip ipInfo,
                   boolean useProvider,
                   List<TypeName> typeNames) {
    }
}
