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

import io.helidon.common.Weight;
import io.helidon.common.types.TypeName;

/**
 * Types used in code generation of Helidon Service.
 */
public final class ServiceCodegenTypes {
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.inject.api.Injection.Point}.
     */
    public static final TypeName INJECTION_INJECT = TypeName.create("io.helidon.service.inject.api.Injection.Inject");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.inject.api.Injection.Singleton}.
     */
    public static final TypeName INJECTION_SINGLETON = TypeName.create("io.helidon.service.inject.api.Injection.Singleton");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.inject.api.Injection.RequestScope}.
     */
    public static final TypeName INJECTION_REQUEST_SCOPE = TypeName.create("io.helidon.service.inject.api.Injection"
                                                                                   + ".RequestScope");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.inject.api.Injection.Named}.
     */
    public static final TypeName INJECTION_NAMED = TypeName.create("io.helidon.service.inject.api.Injection.Named");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.inject.api.Injection.NamedByClass}.
     */
    public static final TypeName INJECTION_NAMED_BY_CLASS =
            TypeName.create("io.helidon.service.inject.api.Injection.NamedByClass");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.inject.api.Injection.Qualifier}.
     */
    public static final TypeName INJECTION_QUALIFIER = TypeName.create("io.helidon.service.inject.api.Injection.Qualifier");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.inject.api.Injection.Main}.
     */
    public static final TypeName INJECTION_MAIN = TypeName.create("io.helidon.service.inject.api.Injection.Main");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.inject.api.Injection.Describe}.
     */
    public static final TypeName INJECTION_DESCRIBE = TypeName.create("io.helidon.service.inject.api.Injection.Describe");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.Service.Provider}.
     */
    public static final TypeName SERVICE_ANNOTATION_PROVIDER = TypeName.create("io.helidon.service.registry.Service.Provider");
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
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.Service.Contract}.
     */
    public static final TypeName SERVICE_ANNOTATION_CONTRACT = TypeName.create("io.helidon.service.registry.Service.Contract");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.Service.ExternalContracts}.
     */
    public static final TypeName SERVICE_ANNOTATION_EXTERNAL_CONTRACTS = TypeName.create("io.helidon.service.registry.Service"
                                                                                                 + ".ExternalContracts");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.inject.api.Injection.Scope}.
     */
    public static final TypeName INJECTION_SCOPE = TypeName.create("io.helidon.service.inject.api.Injection.Scope");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.inject.api.Injection.Instance}.
     */
    public static final TypeName INJECTION_INSTANCE = TypeName.create("io.helidon.service.inject.api.Injection.Instance");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.Service.Descriptor}.
     */
    public static final TypeName SERVICE_ANNOTATION_DESCRIPTOR =
            TypeName.create("io.helidon.service.registry.Service.Descriptor");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.inject.api.Injection.CreateFor}.
     */
    public static final TypeName INJECTION_CREATE_FOR = TypeName.create("io.helidon.service.inject.api.Injection.CreateFor");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.inject.api.Injection.RunLevel}.
     */
    public static final TypeName INJECTION_RUN_LEVEL = TypeName.create("io.helidon.service.inject.api.Injection.RunLevel");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.ServiceInfo}.
     */
    public static final TypeName SERVICE_INFO = TypeName.create("io.helidon.service.registry.ServiceInfo");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.Lookup}.
     */
    public static final TypeName INJECT_LOOKUP = TypeName.create("io.helidon.service.inject.api.Lookup");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.inject.api.ServiceInstance}.
     */
    public static final TypeName INJECT_SERVICE_INSTANCE = TypeName.create("io.helidon.service.inject.api.ServiceInstance");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.inject.api.InjectServiceDescriptor}.
     */
    public static final TypeName INJECT_SERVICE_DESCRIPTOR = TypeName.create(
            "io.helidon.service.inject.api.InjectServiceDescriptor");
    /**
     * {@link io.helidon.common.types.TypeName} for
     * {@code io.helidon.service.inject.api.GeneratedInjectService.CreateForDescriptor}.
     */
    public static final TypeName INJECT_CREATE_FOR_DESCRIPTOR = TypeName.create(
            "io.helidon.service.inject.api.GeneratedInjectService.CreateForDescriptor");
    /**
     * {@link io.helidon.common.types.TypeName} for
     * {@code io.helidon.service.inject.api.GeneratedInjectService.QualifiedProviderDescriptor}.
     */
    public static final TypeName INJECT_QUALIFIED_PROVIDER_DESCRIPTOR = TypeName.create(
            "io.helidon.service.inject.api.GeneratedInjectService.QualifiedProviderDescriptor");
    /**
     * {@link io.helidon.common.types.TypeName} for
     * {@code io.helidon.service.inject.api.GeneratedInjectService.ScopeHandlerDescriptor}.
     */
    public static final TypeName INJECT_SCOPE_HANDLER_DESCRIPTOR = TypeName.create(
            "io.helidon.service.inject.api.GeneratedInjectService.ScopeHandlerDescriptor");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.inject.api.Injection.ScopeHandler}.
     */
    public static final TypeName INJECT_SCOPE_HANDLER = TypeName.create("io.helidon.service.inject.api.Injection.ScopeHandler");

    /**
     * {@link io.helidon.common.types.TypeName} for {@code "io.helidon.service.inject.api.GeneratedInjectService.IpSupport"}.
     */
    public static final TypeName INJECT_IP_SUPPORT = TypeName.create(
            "io.helidon.service.inject.api.GeneratedInjectService.IpSupport");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.ServiceDescriptor}.
     */
    public static final TypeName SERVICE_DESCRIPTOR = TypeName.create("io.helidon.service.registry.ServiceDescriptor");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.Invoker}.
     */
    public static final TypeName INVOKER = TypeName.create("io.helidon.service.inject.api.Invoker");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.Interception.Trigger}.
     */
    public static final TypeName INTERCEPTION_TRIGGER = TypeName.create("io.helidon.service.inject.api.Interception.Trigger");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.Interception.Delegate}.
     */
    public static final TypeName INTERCEPTION_DELEGATE = TypeName.create("io.helidon.service.inject.api.Interception.Delegate");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.Interception.ExternalDelegates}.
     */
    public static final TypeName INTERCEPTION_EXTERNAL_DELEGATES =
            TypeName.create("io.helidon.service.inject.api.Interception.ExternalDelegates");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.Qualifier}.
     */
    public static final TypeName INJECT_QUALIFIER = TypeName.create("io.helidon.service.inject.api.Qualifier");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.Ip}.
     */
    public static final TypeName INJECTION_POINT = TypeName.create("io.helidon.service.inject.api.Ip");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.Dependency}.
     */
    public static final TypeName SERVICE_DEPENDENCY = TypeName.create("io.helidon.service.registry.Dependency");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.ServicesProvider}.
     */
    public static final TypeName SERVICES_PROVIDER = TypeName.create("io.helidon.service.inject.api.Injection.ServicesProvider");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.inject.api.ProviderType}.
     */
    public static final TypeName SERVICE_PROVIDER_TYPE = TypeName.create("io.helidon.service.inject.api.ProviderType");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.QualifiedInstance}.
     */
    public static final TypeName QUALIFIED_INSTANCE = TypeName.create("io.helidon.service.inject.api.Injection"
                                                                              + ".QualifiedInstance");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.inject.api.InjectionPointProvider}.
     */
    public static final TypeName INJECTION_POINT_PROVIDER = TypeName.create(
            "io.helidon.service.inject.api.Injection.InjectionPointProvider");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.QualifiedProvider}.
     */
    public static final TypeName QUALIFIED_PROVIDER = TypeName.create("io.helidon.service.inject.api.Injection"
                                                                              + ".QualifiedProvider");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.DependencyContext}.
     */
    public static final TypeName SERVICE_DEPENDENCY_CONTEXT = TypeName.create("io.helidon.service.registry.DependencyContext");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.InterceptionMetadata}.
     */
    public static final TypeName INTERCEPTION_METADATA = TypeName.create(
            "io.helidon.service.inject.api.InterceptionMetadata");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.inject.api.Application}.
     */
    public static final TypeName INJECT_BINDING = TypeName.create("io.helidon.service.inject.Binding");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.inject.api.InvocationException}.
     */
    public static final TypeName INVOCATION_EXCEPTION = TypeName.create("io.helidon.service.inject.api.InvocationException");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registryInjectionPlanBinder}.
     */
    public static final TypeName INJECTION_PLAN_BINDER = TypeName.create("io.helidon.service.inject.InjectionPlanBinder");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.inject.InjectConfig}.
     */
    public static final TypeName INJECT_CONFIG = TypeName.create("io.helidon.service.inject.InjectConfig");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.inject.InjectConfig.Builder}.
     */
    public static final TypeName INJECT_CONFIG_BUILDER = TypeName.create("io.helidon.service.inject.InjectConfig.Builder");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.inject.InjectRegistryManager}.
     */
    public static final TypeName INJECT_REGISTRY_MANAGER =
            TypeName.create("io.helidon.service.inject.InjectRegistryManager");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.inject.api.InjectRegistry"}.
     */
    public static final TypeName INJECT_REGISTRY = TypeName.create("io.helidon.service.inject.api.InjectRegistry");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.inject.InjectionMain"}.
     */
    public static final TypeName INJECT_APPLICATION_MAIN = TypeName.create("io.helidon.service.inject.InjectionMain");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.builder.api.Prototype.Blueprint}.
     */
    public static final TypeName BUILDER_BLUEPRINT = TypeName.create("io.helidon.builder.api.Prototype.Blueprint");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.builder.api.Prototype.Configured}.
     */
    public static final TypeName BUILDER_CONFIGURED = TypeName.create("io.helidon.builder.api.Prototype.Configured");
    /**
     * {@link io.helidon.common.types.TypeName} for {@link io.helidon.common.Weight}.
     */
    public static final TypeName WEIGHT = TypeName.create(Weight.class);
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.config.metadata.Configured}.
     */
    public static final TypeName CONFIG_META_CONFIGURED = TypeName.create("io.helidon.config.metadata.Configured");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.common.config.Config}.
     */
    public static final TypeName CONFIG_COMMON_CONFIG = TypeName.create("io.helidon.common.config.Config");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.common.config.ConfigException}.
     */
    public static final TypeName CONFIG_EXCEPTION = TypeName.create("io.helidon.common.config.ConfigException");

    private ServiceCodegenTypes() {
    }
}
