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

package io.helidon.inject.codegen;

import io.helidon.common.types.TypeName;

/**
 * Types used in code generation of Helidon Inject.
 */
public final class InjectCodegenTypes {
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.inject.service.Injection.Point}.
     */
    public static final TypeName INJECTION_INJECT = TypeName.create("io.helidon.inject.service.Injection.Inject");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.inject.service.Injection.Singleton}.
     */
    public static final TypeName INJECTION_SINGLETON = TypeName.create("io.helidon.inject.service.Injection.Singleton");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.inject.service.Injection.Named}.
     */
    public static final TypeName INJECTION_NAMED = TypeName.create("io.helidon.inject.service.Injection.Named");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.inject.service.Injection.ClassNamed}.
     */
    public static final TypeName INJECTION_CLASS_NAMED = TypeName.create("io.helidon.inject.service.Injection.ClassNamed");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.inject.service.Injection.Qualifier}.
     */
    public static final TypeName INJECTION_QUALIFIER = TypeName.create("io.helidon.inject.service.Injection.Qualifier");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.inject.service.Injection.PostConstruct}.
     */
    public static final TypeName INJECTION_POST_CONSTRUCT = TypeName.create("io.helidon.inject.service.Injection.PostConstruct");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.inject.service.Injection.PreDestroy}.
     */
    public static final TypeName INJECTION_PRE_DESTROY = TypeName.create("io.helidon.inject.service.Injection.PreDestroy");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.inject.service.Injection.Contract}.
     */
    public static final TypeName INJECTION_CONTRACT = TypeName.create("io.helidon.inject.service.Injection.Contract");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.inject.service.Injection.ExternalContracts}.
     */
    public static final TypeName INJECTION_EXTERNAL_CONTRACTS = TypeName.create("io.helidon.inject.service.Injection"
                                                                                        + ".ExternalContracts");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.inject.service.Injection.Service}.
     */
    public static final TypeName INJECTION_SERVICE = TypeName.create("io.helidon.inject.service.Injection.Service");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.inject.service.Injection.RunLevel}.
     */
    public static final TypeName RUN_LEVEL = TypeName.create("io.helidon.inject.service.Injection.RunLevel");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.inject.service.ServiceInfo}.
     */
    public static final TypeName SERVICE_INFO = TypeName.create("io.helidon.inject.service.ServiceInfo");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.inject.service.ServiceDescriptor}.
     */
    public static final TypeName SERVICE_DESCRIPTOR = TypeName.create("io.helidon.inject.service.ServiceDescriptor");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.inject.service.Invoker}.
     */
    public static final TypeName INVOKER = TypeName.create("io.helidon.inject.service.Invoker");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.inject.service.Interception.Trigger}.
     */
    public static final TypeName INTERCEPTED_TRIGGER = TypeName.create("io.helidon.inject.service.Interception.Trigger");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.inject.service.Qualifier}.
     */
    public static final TypeName QUALIFIER = TypeName.create("io.helidon.inject.service.Qualifier");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.inject.service.Ip}.
     */
    public static final TypeName IP_ID = TypeName.create("io.helidon.inject.service.Ip");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.inject.ServiceProvider}.
     */
    public static final TypeName SERVICE_PROVIDER = TypeName.create("io.helidon.inject.ServiceProvider");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.inject.InjectionPointProvider}.
     */
    public static final TypeName INJECTION_POINT_PROVIDER = TypeName.create("io.helidon.inject.InjectionPointProvider");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.inject.service.InjectionContext}.
     */
    public static final TypeName INJECTION_CONTEXT = TypeName.create("io.helidon.inject.service.InjectionContext");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.inject.service.InterceptionMetadata}.
     */
    public static final TypeName INTERCEPTION_METADATA = TypeName.create("io.helidon.inject.service.InterceptionMetadata");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.inject.service.ModuleComponent}.
     */
    public static final TypeName MODULE_COMPONENT = TypeName.create("io.helidon.inject.service.ModuleComponent");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.inject.Application}.
     */
    public static final TypeName APPLICATION = TypeName.create("io.helidon.inject.Application");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.inject.ContextualServiceQuery}.
     */
    public static final TypeName CONTEXT_QUERY = TypeName.create("io.helidon.inject.ContextualServiceQuery");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.inject.service.ServiceBinder}.
     */
    public static final TypeName SERVICE_BINDER = TypeName.create("io.helidon.inject.service.ServiceBinder");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.inject.InvocationException}.
     */
    public static final TypeName INVOCATION_EXCEPTION = TypeName.create("io.helidon.inject.InvocationException");
    /**
     * {@link io.helidon.common.types.TypeName} for {@code io.helidon.inject.ServiceInjectionPlanBinder}.
     */
    public static final TypeName SERVICE_INJECTION_PLAN_BINDER = TypeName.create("io.helidon.inject.ServiceInjectionPlanBinder");

    private InjectCodegenTypes() {
    }
}
