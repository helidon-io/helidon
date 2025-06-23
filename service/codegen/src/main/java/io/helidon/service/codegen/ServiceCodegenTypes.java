/*
 * Copyright (c) 2023, 2025 Oracle and/or its affiliates.
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

package io.helidon.service.codegen;

import io.helidon.common.Generated;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;

/**
 * Types used in code generation of Helidon Service.
 */
public final class ServiceCodegenTypes {
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.Service.Provider}.
     */
    public static final TypeName SERVICE_ANNOTATION_PROVIDER = TypeName.create("io.helidon.service.registry.Service.Provider");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.Service.Singleton}.
     */
    public static final TypeName SERVICE_ANNOTATION_SINGLETON = TypeName.create("io.helidon.service.registry.Service.Singleton");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.Service.Singleton}.
     */
    public static final TypeName SERVICE_ANNOTATION_PER_LOOKUP = TypeName.create("io.helidon.service.registry.Service.PerLookup");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.Service.Inject}.
     */
    public static final TypeName SERVICE_ANNOTATION_INJECT = TypeName.create("io.helidon.service.registry.Service.Inject");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.Service.RunLevel}.
     */
    public static final TypeName SERVICE_ANNOTATION_RUN_LEVEL =
            TypeName.create("io.helidon.service.registry.Service.RunLevel");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.Service.EntryPoint}.
     */
    public static final TypeName SERVICE_ANNOTATION_ENTRY_POINT =
            TypeName.create("io.helidon.service.registry.Service.EntryPoint");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.Service.ScopeHandler}.
     */
    public static final TypeName SERVICE_SCOPE_HANDLER =
            TypeName.create("io.helidon.service.registry.Service.ScopeHandler");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.Service.PreDestroy}.
     */
    public static final TypeName SERVICE_ANNOTATION_PRE_DESTROY =
            TypeName.create("io.helidon.service.registry.Service.PreDestroy");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.Service.PostConstruct}.
     */
    public static final TypeName SERVICE_ANNOTATION_POST_CONSTRUCT =
            TypeName.create("io.helidon.service.registry.Service.PostConstruct");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.Service.Describe}.
     */
    public static final TypeName SERVICE_ANNOTATION_DESCRIBE =
            TypeName.create("io.helidon.service.registry.Service.Describe");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.Service.PerInstance}.
     */
    public static final TypeName SERVICE_ANNOTATION_PER_INSTANCE =
            TypeName.create("io.helidon.service.registry.Service.PerInstance");

    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.Service.Scope}.
     */
    public static final TypeName SERVICE_ANNOTATION_SCOPE =
            TypeName.create("io.helidon.service.registry.Service.Scope");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.Service.Qualifier}.
     */
    public static final TypeName SERVICE_ANNOTATION_QUALIFIER =
            TypeName.create("io.helidon.service.registry.Service.Qualifier");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.Service.Named}.
     */
    public static final TypeName SERVICE_ANNOTATION_NAMED =
            TypeName.create("io.helidon.service.registry.Service.Named");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.Service.NamedByType}.
     */
    public static final TypeName SERVICE_ANNOTATION_NAMED_BY_TYPE =
            TypeName.create("io.helidon.service.registry.Service.NamedByType");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.Service.Contract}.
     */
    public static final TypeName SERVICE_ANNOTATION_CONTRACT = TypeName.create("io.helidon.service.registry.Service.Contract");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.Service.ExternalContracts}.
     */
    public static final TypeName SERVICE_ANNOTATION_EXTERNAL_CONTRACTS = TypeName.create("io.helidon.service.registry.Service"
                                                                                                 + ".ExternalContracts");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.Service.GenerateBinding}.
     */
    public static final TypeName SERVICE_ANNOTATION_GENERATE_BINDING =
            TypeName.create("io.helidon.service.registry.Service.GenerateBinding");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.Service.Descriptor}.
     */
    public static final TypeName SERVICE_ANNOTATION_DESCRIPTOR =
            TypeName.create("io.helidon.service.registry.Service.Descriptor");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.ServiceDescriptor}.
     */
    public static final TypeName SERVICE_DESCRIPTOR =
            TypeName.create("io.helidon.service.registry.ServiceDescriptor");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.Service.ServiceFactory}.
     */
    public static final TypeName SERVICE_SERVICES_FACTORY = TypeName.create("io.helidon.service.registry.Service"
                                                                                    + ".ServicesFactory");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.Service.InjectionPointFactory}.
     */
    public static final TypeName SERVICE_INJECTION_POINT_FACTORY = TypeName.create(
            "io.helidon.service.registry.Service.InjectionPointFactory");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.Service.QualifiedFactory}.
     */
    public static final TypeName SERVICE_QUALIFIED_FACTORY = TypeName.create(
            "io.helidon.service.registry.Service.QualifiedFactory");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.Service.QualifiedInstance}.
     */
    public static final TypeName SERVICE_QUALIFIED_INSTANCE = TypeName.create(
            "io.helidon.service.registry.Service.QualifiedInstance");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.Dependency}.
     */
    public static final TypeName SERVICE_DEPENDENCY = TypeName.create("io.helidon.service.registry.Dependency");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.DependencyContext}.
     */
    public static final TypeName SERVICE_DEPENDENCY_CONTEXT = TypeName.create("io.helidon.service.registry.DependencyContext");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.Qualifier}.
     */
    public static final TypeName SERVICE_QUALIFIER = TypeName.create("io.helidon.service.registry.Qualifier");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.FactoryType}.
     */
    public static final TypeName SERVICE_FACTORY_TYPE = TypeName.create("io.helidon.service.registry.FactoryType");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.ServiceInstance}.
     */
    public static final TypeName SERVICE_SERVICE_INSTANCE = TypeName.create("io.helidon.service.registry.ServiceInstance");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.Binding}.
     */
    public static final TypeName SERVICE_BINDING =
            TypeName.create("io.helidon.service.registry.Binding");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.EmptyBinding}.
     */
    public static final TypeName SERVICE_BINDING_EMPTY =
            TypeName.create("io.helidon.service.registry.EmptyBinding");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.DependencyPlanBinder}.
     */
    public static final TypeName SERVICE_PLAN_BINDER =
            TypeName.create("io.helidon.service.registry.DependencyPlanBinder");

    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.ServiceRegistryConfig}.
     */
    public static final TypeName SERVICE_CONFIG =
            TypeName.create("io.helidon.service.registry.ServiceRegistryConfig");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.ServiceRegistryConfig.Builder}.
     */
    public static final TypeName SERVICE_CONFIG_BUILDER =
            TypeName.create("io.helidon.service.registry.ServiceRegistryConfig.Builder");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.ServiceRegistry}.
     */
    public static final TypeName SERVICE_REGISTRY =
            TypeName.create("io.helidon.service.registry.ServiceRegistry");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.ServiceRegistryManager}.
     */
    public static final TypeName SERVICE_REGISTRY_MANAGER =
            TypeName.create("io.helidon.service.registry.ServiceRegistryManager");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.DependencyCardinality}.
     */
    public static final TypeName DEPENDENCY_CARDINALITY =
            TypeName.create("io.helidon.service.registry.DependencyCardinality");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.DependencyCardinality}.
     */
    public static final TypeName SERVICE_LOADER_DESCRIPTOR =
            TypeName.create("io.helidon.service.registry.ServiceLoader__ServiceDescriptor");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.Lookup}.
     */
    public static final TypeName SERVICE_LOOKUP =
            TypeName.create("io.helidon.service.registry.Lookup");
    /**
     * {@link io.helidon.common.types.TypeName} for
     * {@code io.helidon.service.registry.GeneratedService.PerInstanceDescriptor}.
     */
    public static final TypeName SERVICE_G_PER_INSTANCE_DESCRIPTOR = TypeName.create(
            "io.helidon.service.registry.GeneratedService.PerInstanceDescriptor");
    /**
     * {@link io.helidon.common.types.TypeName} for
     * {@code io.helidon.service.registry.GeneratedService.QualifiedFactoryDescriptor}.
     */
    public static final TypeName SERVICE_G_QUALIFIED_FACTORY_DESCRIPTOR = TypeName.create(
            "io.helidon.service.registry.GeneratedService.QualifiedFactoryDescriptor");
    /**
     * {@link io.helidon.common.types.TypeName} for
     * {@code io.helidon.service.registry.GeneratedService.ScopeHandlerDescriptor}.
     */
    public static final TypeName SERVICE_G_SCOPE_HANDLER_DESCRIPTOR = TypeName.create(
            "io.helidon.service.registry.GeneratedService.ScopeHandlerDescriptor");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code "io.helidon.service.registry.GeneratedService.DependencySupport"}.
     */
    public static final TypeName SERVICE_G_DEPENDENCY_SUPPORT = TypeName.create(
            "io.helidon.service.registry.GeneratedService.DependencySupport");

    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.builder.api.Prototype.Blueprint}.
     */
    public static final TypeName BUILDER_BLUEPRINT = TypeName.create("io.helidon.builder.api.Prototype.Blueprint");

    /**
     * {@link io.helidon.common.types.TypeName} for {@link io.helidon.common.Generated}.
     */
    public static final TypeName GENERATED_ANNOTATION = TypeName.create(Generated.class);

    /**
     * {@link io.helidon.common.types.TypeName} for
     * {@code io.helidon.service.registry.Event.Observer}.
     */
    public static final TypeName EVENT_OBSERVER = TypeName.create("io.helidon.service.registry.Event.Observer");
    /**
     * {@link io.helidon.common.types.TypeName} for
     * {@code io.helidon.service.registry.Event.AsyncObserver}.
     */
    public static final TypeName EVENT_OBSERVER_ASYNC = TypeName.create("io.helidon.service.registry.Event.AsyncObserver");
    /**
     * {@link io.helidon.common.types.TypeName} for
     * {@code io.helidon.service.registry.Event.Emitter}.
     */
    public static final TypeName EVENT_EMITTER = TypeName.create("io.helidon.service.registry.Event.Emitter");
    /**
     * {@link io.helidon.common.types.TypeName} for
     * {@code io.helidon.service.registry.EventManager}.
     */
    public static final TypeName EVENT_MANAGER = TypeName.create("io.helidon.service.registry.EventManager");
    /**
     * {@link io.helidon.common.types.TypeName} for
     * {@code io.helidon.service.registry.GeneratedService.EventObserverRegistration}.
     */
    public static final TypeName SERVICE_G_EVENT_OBSERVER_REGISTRATION =
            TypeName.create("io.helidon.service.registry.GeneratedService.EventObserverRegistration");

    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.Interception.Intercepted}.
     */
    public static final TypeName INTERCEPTION_INTERCEPTED =
            TypeName.create("io.helidon.service.registry.Interception.Intercepted");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.Interception.Delegate}.
     */
    public static final TypeName INTERCEPTION_DELEGATE = TypeName.create("io.helidon.service.registry.Interception.Delegate");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.Interception.ExternalDelegates}.
     */
    public static final TypeName INTERCEPTION_EXTERNAL_DELEGATE =
            TypeName.create("io.helidon.service.registry.Interception.ExternalDelegate");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.InvocationException}.
     */
    public static final TypeName INTERCEPT_EXCEPTION =
            TypeName.create("io.helidon.service.registry.InterceptionException");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.InterceptionMetadata}.
     */
    public static final TypeName INTERCEPT_METADATA = TypeName.create(
            "io.helidon.service.registry.InterceptionMetadata");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.Invoker}.
     */
    public static final TypeName INTERCEPT_INVOKER =
            TypeName.create("io.helidon.service.registry.InterceptionInvoker");
    /**
     * {@link io.helidon.common.types.TypeName} for
     * {@code io.helidon.service.registry.GeneratedService.SupplierFactoryInterceptionWrapper}.
     */
    public static final TypeName INTERCEPT_G_WRAPPER_SUPPLIER_FACTORY =
            TypeName.create("io.helidon.service.registry.GeneratedService.SupplierFactoryInterceptionWrapper");
    /**
     * {@link io.helidon.common.types.TypeName} for
     * {@code io.helidon.service.registry.GeneratedService.ServicesFactoryInterceptionWrapper}.
     */
    public static final TypeName INTERCEPT_G_WRAPPER_SERVICES_FACTORY =
            TypeName.create("io.helidon.service.registry.GeneratedService.ServicesFactoryInterceptionWrapper");
    /**
     * {@link io.helidon.common.types.TypeName} for
     * {@code io.helidon.service.registry.GeneratedService.IpFactoryInterceptionWrapper}.
     */
    public static final TypeName INTERCEPT_G_WRAPPER_IP_FACTORY =
            TypeName.create("io.helidon.service.registry.GeneratedService.IpFactoryInterceptionWrapper");
    /**
     * {@link io.helidon.common.types.TypeName} for
     * {@code io.helidon.service.registry.GeneratedService.QualifiedFactoryInterceptionWrapper}.
     */
    public static final TypeName INTERCEPT_G_WRAPPER_QUALIFIED_FACTORY =
            TypeName.create("io.helidon.service.registry.GeneratedService.QualifiedFactoryInterceptionWrapper");

    /**
     * {@link io.helidon.common.types.TypeName} for
     * {@code io.helidon.service.registry.RegistryStartupProvider}.
     */
    public static final TypeName REGISTRY_STARTUP_PROVIDER =
            TypeName.create("io.helidon.service.registry.RegistryStartupProvider");
    /**
     * A Set of Qualifier.
     */
    public static final TypeName SET_OF_QUALIFIERS = TypeName.builder(TypeNames.SET)
            .addTypeArgument(SERVICE_QUALIFIER)
            .build();

    /**
     * A list of Qualifier.
     */
    public static final TypeName LIST_OF_ANNOTATIONS = TypeName.builder(TypeNames.LIST)
            .addTypeArgument(TypeNames.ANNOTATION)
            .build();

    /**
     * A set of ResolvedType.
     */
    public static final TypeName SET_OF_RESOLVED_TYPES = TypeName.builder(TypeNames.SET)
            .addTypeArgument(TypeNames.RESOLVED_TYPE_NAME)
            .build();
    /**
     * A set of String.
     */
    public static final TypeName SET_OF_STRINGS = TypeName.builder(TypeNames.SET)
            .addTypeArgument(TypeNames.STRING)
            .build();
    /**
     * A list of Double.
     */
    public static final TypeName LIST_OF_DOUBLES = TypeName.builder(TypeNames.LIST)
            .addTypeArgument(TypeNames.BOXED_DOUBLE)
            .build();
    /**
     * A list of Dependency.
     */
    public static final TypeName LIST_OF_DEPENDENCIES = TypeName.builder(TypeNames.LIST)
            .addTypeArgument(ServiceCodegenTypes.SERVICE_DEPENDENCY)
            .build();

    /**
     * A {@code GenericType<T>}.
     */
    public static final TypeName GENERIC_T_TYPE = TypeName.createFromGenericDeclaration("T");

    /**
     * A {@code GenericType<?>}.
     */
    public static final TypeName ANY_GENERIC_TYPE = TypeName.builder(TypeNames.GENERIC_TYPE)
            .addTypeArgument(TypeNames.WILDCARD)
            .build();

    private ServiceCodegenTypes() {
    }
}


