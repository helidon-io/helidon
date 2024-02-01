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

package io.helidon.service.codegen;

import io.helidon.common.types.TypeName;

/**
 * Types used in code generation of Helidon Inject.
 */
public final class ServiceCodegenTypes {
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.Injection.Point}.
     */
    public static final TypeName INJECTION_INJECT = TypeName.create("io.helidon.service.registry.Injection.Inject");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.Injection.Singleton}.
     */
    public static final TypeName INJECTION_SINGLETON = TypeName.create("io.helidon.service.registry.Injection.Singleton");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.Injection.RequestScope}.
     */
    public static final TypeName INJECTION_REQUEST_SCOPE = TypeName.create("io.helidon.service.registry.Injection.RequestScope");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.Injection.Named}.
     */
    public static final TypeName INJECTION_NAMED = TypeName.create("io.helidon.service.registry.Injection.Named");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.Injection.ClassNamed}.
     */
    public static final TypeName INJECTION_CLASS_NAMED = TypeName.create("io.helidon.service.registry.Injection.ClassNamed");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.Injection.Qualifier}.
     */
    public static final TypeName INJECTION_QUALIFIER = TypeName.create("io.helidon.service.registry.Injection.Qualifier");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.Injection.PostConstruct}.
     */
    public static final TypeName INJECTION_POST_CONSTRUCT = TypeName.create("io.helidon.service.registry.Injection.PostConstruct");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.Injection.PreDestroy}.
     */
    public static final TypeName INJECTION_PRE_DESTROY = TypeName.create("io.helidon.service.registry.Injection.PreDestroy");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.Service.Provider}.
     */
    public static final TypeName SERVICE_ANNOTATION_PROVIDER = TypeName.create("io.helidon.service.registry.Service.Provider");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.Injection.Contract}.
     */
    public static final TypeName SERVICE_ANNOTATION_CONTRACT = TypeName.create("io.helidon.service.registry.Service.Contract");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.Injection.ExternalContracts}.
     */
    public static final TypeName SERVICE_ANNOTATION_EXTERNAL_CONTRACTS = TypeName.create("io.helidon.service.registry.Service"
                                                                                                 + ".ExternalContracts");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.Injection.Scope}.
     */
    public static final TypeName INJECTION_SCOPE = TypeName.create("io.helidon.service.registry.Injection.Scope");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.Injection.Service}.
     */
    public static final TypeName INJECTION_SERVICE = TypeName.create("io.helidon.service.registry.Injection.Service");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.Service.Descriptor}.
     */
    public static final TypeName SERVICE_ANNOTATION_DESCRIPTOR = TypeName.create("io.helidon.service.registry.Service.Descriptor");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.Injection.DrivenBy}.
     */
    public static final TypeName INJECTION_DRIVEN_BY = TypeName.create("io.helidon.service.registry.Injection.DrivenBy");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.Injection.Eager}.
     */
    public static final TypeName INJECTION_EAGER = TypeName.create("io.helidon.service.registry.Injection.Eager");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.Injection.RunLevel}.
     */
    public static final TypeName RUN_LEVEL = TypeName.create("io.helidon.service.registry.Injection.RunLevel");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.ServiceInfo}.
     */
    public static final TypeName SERVICE_INFO = TypeName.create("io.helidon.service.registry.ServiceInfo");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.Lookup}.
     */
    public static final TypeName SERVICE_LOOKUP = TypeName.create("io.helidon.service.registry.Lookup");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.ServiceDescriptor}.
     */
    public static final TypeName SERVICE_DESCRIPTOR = TypeName.create("io.helidon.service.registry.ServiceDescriptor");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.GeneratedService.Descriptor}.
     */
    public static final TypeName GENERATED_SERVICE_DESCRIPTOR = TypeName.create("io.helidon.service.registry.GeneratedService.Descriptor");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.Invoker}.
     */
    public static final TypeName INVOKER = TypeName.create("io.helidon.service.registry.Invoker");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.Interception.Trigger}.
     */
    public static final TypeName INTERCEPTED_TRIGGER = TypeName.create("io.helidon.service.registry.Interception.Trigger");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.Qualifier}.
     */
    public static final TypeName QUALIFIER = TypeName.create("io.helidon.service.registry.Qualifier");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.Ip}.
     */
    public static final TypeName INJECTION_POINT = TypeName.create("io.helidon.service.registry.Ip");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.Dependency}.
     */
    public static final TypeName SERVICE_DEPENDENCY = TypeName.create("io.helidon.service.registry.Dependency");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.ServicesProvider}.
     */
    public static final TypeName SERVICES_PROVIDER = TypeName.create("io.helidon.service.registry.ServicesProvider");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.QualifiedInstance}.
     */
    public static final TypeName QUALIFIED_INSTANCE = TypeName.create("io.helidon.service.registry.QualifiedInstance");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.InjectionPointProvider}.
     */
    public static final TypeName INJECTION_POINT_PROVIDER = TypeName.create("io.helidon.service.registry.InjectionPointProvider");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.QualifiedProvider}.
     */
    public static final TypeName QUALIFIED_PROVIDER = TypeName.create("io.helidon.service.registry.QualifiedProvider");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.InjectionContext}.
     */
    public static final TypeName INJECTION_CONTEXT = TypeName.create("io.helidon.service.registry.InjectionContext");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.DependencyContext}.
     */
    public static final TypeName DEPENDENCY_CONTEXT = TypeName.create("io.helidon.service.registry.DependencyContext");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.InterceptionMetadata}.
     */
    public static final TypeName INTERCEPTION_METADATA = TypeName.create("io.helidon.service.registry.InterceptionMetadata");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.ModuleComponent}.
     */
    public static final TypeName MODULE_COMPONENT = TypeName.create("io.helidon.service.registry.ModuleComponent");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.inject.Application}.
     */
    public static final TypeName APPLICATION = TypeName.create("io.helidon.inject.Application");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.ServiceBinder}.
     */
    public static final TypeName SERVICE_BINDER = TypeName.create("io.helidon.service.registry.ServiceBinder");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.inject.InvocationException}.
     */
    public static final TypeName INVOCATION_EXCEPTION = TypeName.create("io.helidon.inject.InvocationException");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registryInjectionPlanBinder}.
     */
    public static final TypeName SERVICE_INJECTION_PLAN_BINDER = TypeName.create("io.helidon.service.registryInjectionPlanBinder");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.inject.InjectionConfig}.
     */
    public static final TypeName INJECTION_CONFIG = TypeName.create("io.helidon.inject.InjectionConfig");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.inject.Phase}.
     */
    public static final TypeName INJECT_PHASE = TypeName.create("io.helidon.inject.Phase");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.inject.InjectionServices}.
     */
    public static final TypeName MANAGED_REGISTRY = TypeName.create("io.helidon.inject.ManagedRegistry");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registrys}.
     */
    public static final TypeName SERVICE_REGISTRY = TypeName.create("io.helidon.service.registry.ServiceRegistry");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.builder.api.Prototype.Blueprint}.
     */
    public static final TypeName PROTOTYPE_BLUEPRINT = TypeName.create("io.helidon.builder.api.Prototype.Blueprint");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.builder.api.Prototype.Configured}.
     */
    public static final TypeName PROTOTYPE_CONFIGURED = TypeName.create("io.helidon.builder.api.Prototype.Configured");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.config.metadata.Configured}.
     */
    public static final TypeName CONFIG_META_CONFIGURED = TypeName.create("io.helidon.config.metadata.Configured");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.common.config.Config}.
     */
    public static final TypeName COMMON_CONFIG = TypeName.create("io.helidon.common.config.Config");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.common.config.ConfigException}.
     */
    public static final TypeName COMMON_CONFIG_EXCEPTION = TypeName.create("io.helidon.common.config.ConfigException");

    private ServiceCodegenTypes() {
    }
}
