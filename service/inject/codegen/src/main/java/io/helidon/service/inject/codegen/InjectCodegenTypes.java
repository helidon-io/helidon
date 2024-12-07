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

package io.helidon.service.inject.codegen;

import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;

/**
 * Types for code generation from Helidon Service Inject API and Helidon Service Inject.
 */
public class InjectCodegenTypes {
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.inject.api.Injection.Point}.
     */
    public static final TypeName INJECTION_INJECT = TypeName.create("io.helidon.service.inject.api.Injection.Inject");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.inject.api.Injection.Singleton}.
     */
    public static final TypeName INJECTION_SINGLETON = TypeName.create("io.helidon.service.inject.api.Injection.Singleton");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.inject.api.Injection.Named}.
     */
    public static final TypeName INJECTION_NAMED = TypeName.create("io.helidon.service.inject.api.Injection.Named");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.inject.api.Injection.NamedByType}.
     */
    public static final TypeName INJECTION_NAMED_BY_TYPE =
            TypeName.create("io.helidon.service.inject.api.Injection.NamedByType");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.inject.api.Injection.Qualifier}.
     */
    public static final TypeName INJECTION_QUALIFIER = TypeName.create("io.helidon.service.inject.api.Injection.Qualifier");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.inject.api.Injection.Describe}.
     */
    public static final TypeName INJECTION_DESCRIBE = TypeName.create("io.helidon.service.inject.api.Injection.Describe");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.inject.api.Injection.Scope}.
     */
    public static final TypeName INJECTION_SCOPE = TypeName.create("io.helidon.service.inject.api.Injection.Scope");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.inject.api.Injection.PerLookup}.
     */
    public static final TypeName INJECTION_PER_LOOKUP = TypeName.create("io.helidon.service.inject.api.Injection.PerLookup");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.inject.api.Injection.PerInstance}.
     */
    public static final TypeName INJECTION_PER_INSTANCE = TypeName.create("io.helidon.service.inject.api.Injection.PerInstance");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.inject.api.Injection.RunLevel}.
     */
    public static final TypeName INJECTION_RUN_LEVEL = TypeName.create("io.helidon.service.inject.api.Injection.RunLevel");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.inject.api.Injection.Main}.
     */
    public static final TypeName INJECTION_MAIN = TypeName.create("io.helidon.service.inject.api.Injection.Main");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.inject.api.InjectionPointFactory}.
     */
    public static final TypeName INJECTION_POINT_FACTORY = TypeName.create(
            "io.helidon.service.inject.api.Injection.InjectionPointFactory");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.inject.api.Injection.ScopeHandler}.
     */
    public static final TypeName INJECTION_SCOPE_HANDLER =
            TypeName.create("io.helidon.service.inject.api.Injection.ScopeHandler");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.ServicesFactory}.
     */
    public static final TypeName INJECTION_SERVICES_FACTORY =
            TypeName.create("io.helidon.service.inject.api.Injection.ServicesFactory");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.QualifiedFactory}.
     */
    public static final TypeName INJECTION_QUALIFIED_FACTORY =
            TypeName.create("io.helidon.service.inject.api.Injection.QualifiedFactory");

    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.Interception.Intercepted}.
     */
    public static final TypeName INTERCEPTION_INTERCEPTED =
            TypeName.create("io.helidon.service.inject.api.Interception.Intercepted");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.Interception.Delegate}.
     */
    public static final TypeName INTERCEPTION_DELEGATE = TypeName.create("io.helidon.service.inject.api.Interception.Delegate");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.Interception.ExternalDelegates}.
     */
    public static final TypeName INTERCEPTION_EXTERNAL_DELEGATE =
            TypeName.create("io.helidon.service.inject.api.Interception.ExternalDelegate");

    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.inject.api.FactoryType}.
     */
    public static final TypeName INJECT_FACTORY_TYPE = TypeName.create("io.helidon.service.inject.api.FactoryType");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.Qualifier}.
     */
    public static final TypeName INJECT_QUALIFIER = TypeName.create("io.helidon.service.inject.api.Qualifier");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.Ip}.
     */
    public static final TypeName INJECT_INJECTION_POINT = TypeName.create("io.helidon.service.inject.api.Ip");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.inject.api.ServiceInstance}.
     */
    public static final TypeName INJECT_SERVICE_INSTANCE = TypeName.create("io.helidon.service.inject.api.ServiceInstance");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.inject.api.InjectServiceDescriptor}.
     */
    public static final TypeName INJECT_SERVICE_DESCRIPTOR =
            TypeName.create("io.helidon.service.inject.api.InjectServiceDescriptor");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.inject.InjectConfig.Builder}.
     */
    public static final TypeName INJECT_CONFIG_BUILDER =
            TypeName.create("io.helidon.service.inject.InjectConfig.Builder");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.inject.InjectConfig}.
     */
    public static final TypeName INJECT_CONFIG =
            TypeName.create("io.helidon.service.inject.InjectConfig");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.inject.api.InjectRegistry}.
     */
    public static final TypeName INJECT_REGISTRY =
            TypeName.create("io.helidon.service.inject.api.InjectRegistry");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.inject.InjectRegistryManager}.
     */
    public static final TypeName INJECT_REGISTRY_MANAGER =
            TypeName.create("io.helidon.service.inject.InjectRegistryManager");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.inject.InjectionMain}.
     */
    public static final TypeName INJECT_MAIN =
            TypeName.create("io.helidon.service.inject.InjectionMain");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.inject.Binding}.
     */
    public static final TypeName INJECT_BINDING =
            TypeName.create("io.helidon.service.inject.Binding");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.inject.InjectionPlanBinder}.
     */
    public static final TypeName INJECT_PLAN_BINDER =
            TypeName.create("io.helidon.service.inject.InjectionPlanBinder");

    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.inject.api.InvocationException}.
     */
    public static final TypeName INTERCEPT_EXCEPTION =
            TypeName.create("io.helidon.service.inject.api.InterceptionException");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.InterceptionMetadata}.
     */
    public static final TypeName INTERCEPT_METADATA = TypeName.create(
            "io.helidon.service.inject.api.InterceptionMetadata");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.Invoker}.
     */
    public static final TypeName INTERCEPT_INVOKER =
            TypeName.create("io.helidon.service.inject.api.InterceptionInvoker");
    /**
     * {@link io.helidon.common.types.TypeName} for
     * {@code io.helidon.service.inject.api.GeneratedInjectService.PerInstanceDescriptor}.
     */
    public static final TypeName INJECT_G_PER_INSTANCE_DESCRIPTOR = TypeName.create(
            "io.helidon.service.inject.api.GeneratedInjectService.PerInstanceDescriptor");
    /**
     * {@link io.helidon.common.types.TypeName} for
     * {@code io.helidon.service.inject.api.GeneratedInjectService.QualifiedFactoryDescriptor}.
     */
    public static final TypeName INJECT_G_QUALIFIED_FACTORY_DESCRIPTOR = TypeName.create(
            "io.helidon.service.inject.api.GeneratedInjectService.QualifiedFactoryDescriptor");
    /**
     * {@link io.helidon.common.types.TypeName} for
     * {@code io.helidon.service.inject.api.GeneratedInjectService.ScopeHandlerDescriptor}.
     */
    public static final TypeName INJECT_G_SCOPE_HANDLER_DESCRIPTOR = TypeName.create(
            "io.helidon.service.inject.api.GeneratedInjectService.ScopeHandlerDescriptor");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code "io.helidon.service.inject.api.GeneratedInjectService.IpSupport"}.
     */
    public static final TypeName INJECT_G_IP_SUPPORT = TypeName.create(
            "io.helidon.service.inject.api.GeneratedInjectService.IpSupport");
    /**
     * {@link io.helidon.common.types.TypeName} for
     * {@code io.helidon.service.inject.api.GeneratedInjectService.SupplierFactoryInterceptionWrapper}.
     */
    public static final TypeName INTERCEPT_G_WRAPPER_SUPPLIER_FACTORY =
            TypeName.create("io.helidon.service.inject.api.GeneratedInjectService.SupplierFactoryInterceptionWrapper");
    /**
     * {@link io.helidon.common.types.TypeName} for
     * {@code io.helidon.service.inject.api.GeneratedInjectService.ServicesFactoryInterceptionWrapper}.
     */
    public static final TypeName INTERCEPT_G_WRAPPER_SERVICES_FACTORY =
            TypeName.create("io.helidon.service.inject.api.GeneratedInjectService.ServicesFactoryInterceptionWrapper");
    /**
     * {@link io.helidon.common.types.TypeName} for
     * {@code io.helidon.service.inject.api.GeneratedInjectService.IpFactoryInterceptionWrapper}.
     */
    public static final TypeName INTERCEPT_G_WRAPPER_IP_FACTORY =
            TypeName.create("io.helidon.service.inject.api.GeneratedInjectService.IpFactoryInterceptionWrapper");
    /**
     * {@link io.helidon.common.types.TypeName} for
     * {@code io.helidon.service.inject.api.GeneratedInjectService.QualifiedFactoryInterceptionWrapper}.
     */
    public static final TypeName INTERCEPT_G_WRAPPER_QUALIFIED_FACTORY =
            TypeName.create("io.helidon.service.inject.api.GeneratedInjectService.QualifiedFactoryInterceptionWrapper");

    /**
     * {@link io.helidon.common.types.TypeName} for
     * {@code io.helidon.service.inject.api.Event.Observer}.
     */
    public static final TypeName EVENT_OBSERVER = TypeName.create("io.helidon.service.inject.api.Event.Observer");
    /**
     * {@link io.helidon.common.types.TypeName} for
     * {@code io.helidon.service.inject.api.Event.AsyncObserver}.
     */
    public static final TypeName EVENT_OBSERVER_ASYNC = TypeName.create("io.helidon.service.inject.api.Event.AsyncObserver");
    /**
     * {@link io.helidon.common.types.TypeName} for
     * {@code io.helidon.service.inject.api.Event.Emitter}.
     */
    public static final TypeName EVENT_EMITTER = TypeName.create("io.helidon.service.inject.api.Event.Emitter");
    /**
     * {@link io.helidon.common.types.TypeName} for
     * {@code io.helidon.service.inject.api.EventManager}.
     */
    public static final TypeName EVENT_MANAGER = TypeName.create("io.helidon.service.inject.api.EventManager");
    /**
     * {@link io.helidon.common.types.TypeName} for
     * {@code io.helidon.service.inject.api.GeneratedInjectService.EventObserverRegistration}.
     */
    public static final TypeName INJECT_G_EVENT_OBSERVER_REGISTRATION =
            TypeName.create("io.helidon.service.inject.api.GeneratedInjectService.EventObserverRegistration");

    /**
     * {@link io.helidon.common.types.TypeName} for String array.
     */
    public static final TypeName STRING_ARRAY = TypeName.builder()
            .from(TypeNames.STRING)
            .array(true)
            .build();
    /**
     * {@link io.helidon.common.types.TypeName} for primitive double array.
     */
    public static final TypeName DOUBLE_ARRAY = TypeName.builder()
            .from(TypeNames.PRIMITIVE_DOUBLE)
            .array(true)
            .build();


    private InjectCodegenTypes() {
    }
}
