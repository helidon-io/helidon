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
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.inject.api.Injection.NamedByClass}.
     */
    public static final TypeName INJECTION_NAMED_BY_CLASS =
            TypeName.create("io.helidon.service.inject.api.Injection.NamedByClass");
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
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.inject.api.Injection.Instance}.
     */
    public static final TypeName INJECTION_INSTANCE = TypeName.create("io.helidon.service.inject.api.Injection.Instance");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.inject.api.Injection.CreateFor}.
     */
    public static final TypeName INJECTION_CREATE_FOR = TypeName.create("io.helidon.service.inject.api.Injection.CreateFor");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.inject.api.Injection.RunLevel}.
     */
    public static final TypeName INJECTION_RUN_LEVEL = TypeName.create("io.helidon.service.inject.api.Injection.RunLevel");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.inject.api.InjectionPointProvider}.
     */
    public static final TypeName INJECTION_POINT_PROVIDER = TypeName.create(
            "io.helidon.service.inject.api.Injection.InjectionPointProvider");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.inject.api.Injection.ScopeHandler}.
     */
    public static final TypeName INJECTION_SCOPE_HANDLER =
            TypeName.create("io.helidon.service.inject.api.Injection.ScopeHandler");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.ServicesProvider}.
     */
    public static final TypeName INJECTION_SERVICES_PROVIDER =
            TypeName.create("io.helidon.service.inject.api.Injection.ServicesProvider");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.registry.QualifiedProvider}.
     */
    public static final TypeName INJECTION_QUALIFIED_PROVIDER =
            TypeName.create("io.helidon.service.inject.api.Injection.QualifiedProvider");

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
    public static final TypeName INTERCEPTION_EXTERNAL_DELEGATE =
            TypeName.create("io.helidon.service.inject.api.Interception.ExternalDelegate");

    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.service.inject.api.ProviderType}.
     */
    public static final TypeName INJECT_PROVIDER_TYPE = TypeName.create("io.helidon.service.inject.api.ProviderType");
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
     * {@code io.helidon.service.inject.api.GeneratedInjectService.CreateForDescriptor}.
     */
    public static final TypeName INJECT_G_CREATE_FOR_DESCRIPTOR = TypeName.create(
            "io.helidon.service.inject.api.GeneratedInjectService.CreateForDescriptor");
    /**
     * {@link io.helidon.common.types.TypeName} for
     * {@code io.helidon.service.inject.api.GeneratedInjectService.QualifiedProviderDescriptor}.
     */
    public static final TypeName INJECT_G_QUALIFIED_PROVIDER_DESCRIPTOR = TypeName.create(
            "io.helidon.service.inject.api.GeneratedInjectService.QualifiedProviderDescriptor");
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
     * {@code io.helidon.service.inject.api.GeneratedInjectService.SupplierProviderInterceptionWrapper}.
     */
    public static final TypeName INTERCEPT_G_WRAPPER_SUPPLIER_PROVIDER =
            TypeName.create("io.helidon.service.inject.api.GeneratedInjectService.SupplierProviderInterceptionWrapper");
    /**
     * {@link io.helidon.common.types.TypeName} for
     * {@code io.helidon.service.inject.api.GeneratedInjectService.ServicesProviderInterceptionWrapper}.
     */
    public static final TypeName INTERCEPT_G_WRAPPER_SERVICES_PROVIDER =
            TypeName.create("io.helidon.service.inject.api.GeneratedInjectService.ServicesProviderInterceptionWrapper");
    /**
     * {@link io.helidon.common.types.TypeName} for
     * {@code io.helidon.service.inject.api.GeneratedInjectService.IpProviderInterceptionWrapper}.
     */
    public static final TypeName INTERCEPT_G_WRAPPER_IP_PROVIDER =
            TypeName.create("io.helidon.service.inject.api.GeneratedInjectService.IpProviderInterceptionWrapper");
    /**
     * {@link io.helidon.common.types.TypeName} for
     * {@code io.helidon.service.inject.api.GeneratedInjectService.QualifiedProviderInterceptionWrapper}.
     */
    public static final TypeName INTERCEPT_G_WRAPPER_QUALIFIED_PROVIDER =
            TypeName.create("io.helidon.service.inject.api.GeneratedInjectService.QualifiedProviderInterceptionWrapper");

    private InjectCodegenTypes() {
    }
}
