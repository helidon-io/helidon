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

package io.helidon.inject.tests.codegen;

import io.helidon.builder.api.Prototype;
import io.helidon.common.config.Config;
import io.helidon.config.metadata.Configured;
import io.helidon.inject.Application;
import io.helidon.inject.InjectionConfig;
import io.helidon.inject.InjectionServices;
import io.helidon.inject.InvocationException;
import io.helidon.inject.Phase;
import io.helidon.inject.ServiceInjectionPlanBinder;
import io.helidon.inject.Services;
import io.helidon.inject.codegen.InjectCodegenTypes;
import io.helidon.inject.service.Injection;
import io.helidon.inject.service.InjectionContext;
import io.helidon.inject.service.InjectionPointProvider;
import io.helidon.inject.service.Interception;
import io.helidon.inject.service.InterceptionMetadata;
import io.helidon.inject.service.Invoker;
import io.helidon.inject.service.Ip;
import io.helidon.inject.service.Lookup;
import io.helidon.inject.service.ModuleComponent;
import io.helidon.inject.service.QualifiedInstance;
import io.helidon.inject.service.QualifiedProvider;
import io.helidon.inject.service.ServiceBinder;
import io.helidon.inject.service.ServiceDescriptor;
import io.helidon.inject.service.ServiceInfo;
import io.helidon.inject.service.ServicesProvider;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class InjectCodegenTypesTest {
    @Test
    void testTypes() {
        assertThat(InjectCodegenTypes.INJECTION_INJECT.fqName(),
                   is(Injection.Inject.class.getCanonicalName()));
        assertThat(InjectCodegenTypes.INJECTION_SINGLETON.fqName(),
                   is(Injection.Singleton.class.getCanonicalName()));
        assertThat(InjectCodegenTypes.INJECTION_NAMED.fqName(),
                   is(Injection.Named.class.getCanonicalName()));
        assertThat(InjectCodegenTypes.INJECTION_CLASS_NAMED.fqName(),
                   is(Injection.ClassNamed.class.getCanonicalName()));
        assertThat(InjectCodegenTypes.INJECTION_QUALIFIER.fqName(),
                   is(Injection.Qualifier.class.getCanonicalName()));
        assertThat(InjectCodegenTypes.INJECTION_POST_CONSTRUCT.fqName(),
                   is(Injection.PostConstruct.class.getCanonicalName()));
        assertThat(InjectCodegenTypes.INJECTION_PRE_DESTROY.fqName(),
                   is(Injection.PreDestroy.class.getCanonicalName()));
        assertThat(InjectCodegenTypes.INJECTION_CONTRACT.fqName(),
                   is(Injection.Contract.class.getCanonicalName()));
        assertThat(InjectCodegenTypes.INJECTION_EXTERNAL_CONTRACTS.fqName(),
                   is(Injection.ExternalContracts.class.getCanonicalName()));
        assertThat(InjectCodegenTypes.INJECTION_SERVICE.fqName(),
                   is(Injection.Service.class.getCanonicalName()));
        assertThat(InjectCodegenTypes.INJECTION_DESCRIPTOR.fqName(),
                   is(Injection.Descriptor.class.getCanonicalName()));
        assertThat(InjectCodegenTypes.INJECTION_DRIVEN_BY.fqName(),
                   is(Injection.DrivenBy.class.getCanonicalName()));
        assertThat(InjectCodegenTypes.INJECTION_EAGER.fqName(),
                   is(Injection.Eager.class.getCanonicalName()));
        assertThat(InjectCodegenTypes.RUN_LEVEL.fqName(),
                   is(Injection.RunLevel.class.getCanonicalName()));
        assertThat(InjectCodegenTypes.SERVICE_INFO.fqName(),
                   is(ServiceInfo.class.getCanonicalName()));
        assertThat(InjectCodegenTypes.SERVICE_LOOKUP.fqName(),
                   is(Lookup.class.getCanonicalName()));
        assertThat(InjectCodegenTypes.SERVICE_DESCRIPTOR.fqName(),
                   is(ServiceDescriptor.class.getCanonicalName()));
        assertThat(InjectCodegenTypes.INVOKER.fqName(),
                   is(Invoker.class.getCanonicalName()));
        assertThat(InjectCodegenTypes.INTERCEPTED_TRIGGER.fqName(),
                   is(Interception.Trigger.class.getCanonicalName()));
        assertThat(InjectCodegenTypes.QUALIFIER.fqName(),
                   is(io.helidon.inject.service.Qualifier.class.getCanonicalName()));
        assertThat(InjectCodegenTypes.IP_ID.fqName(),
                   is(Ip.class.getCanonicalName()));
        assertThat(InjectCodegenTypes.SERVICES_PROVIDER.fqName(),
                   is(ServicesProvider.class.getCanonicalName()));
        assertThat(InjectCodegenTypes.QUALIFIED_INSTANCE.fqName(),
                   is(QualifiedInstance.class.getCanonicalName()));
        assertThat(InjectCodegenTypes.INJECTION_POINT_PROVIDER.fqName(),
                   is(InjectionPointProvider.class.getCanonicalName()));
        assertThat(InjectCodegenTypes.INJECTION_CONTEXT.fqName(),
                   is(InjectionContext.class.getCanonicalName()));
        assertThat(InjectCodegenTypes.INTERCEPTION_METADATA.fqName(),
                   is(InterceptionMetadata.class.getCanonicalName()));
        assertThat(InjectCodegenTypes.MODULE_COMPONENT.fqName(),
                   is(ModuleComponent.class.getCanonicalName()));
        assertThat(InjectCodegenTypes.APPLICATION.fqName(),
                   is(Application.class.getCanonicalName()));
        assertThat(InjectCodegenTypes.SERVICE_BINDER.fqName(),
                   is(ServiceBinder.class.getCanonicalName()));
        assertThat(InjectCodegenTypes.INVOCATION_EXCEPTION.fqName(),
                   is(InvocationException.class.getCanonicalName()));
        assertThat(InjectCodegenTypes.SERVICE_INJECTION_PLAN_BINDER.fqName(),
                   is(ServiceInjectionPlanBinder.class.getCanonicalName()));
        assertThat(InjectCodegenTypes.INJECTION_CONFIG.fqName(),
                   is(InjectionConfig.class.getCanonicalName()));
        assertThat(InjectCodegenTypes.INJECT_PHASE.fqName(),
                   is(Phase.class.getCanonicalName()));
        assertThat(InjectCodegenTypes.INJECT_INJECTION_SERVICES.fqName(),
                   is(InjectionServices.class.getCanonicalName()));
        assertThat(InjectCodegenTypes.INJECT_SERVICES.fqName(),
                   is(Services.class.getCanonicalName()));
        assertThat(InjectCodegenTypes.PROTOTYPE_BLUEPRINT.fqName(),
                   is(Prototype.Blueprint.class.getCanonicalName()));
        assertThat(InjectCodegenTypes.PROTOTYPE_CONFIGURED.fqName(),
                   is(Prototype.Configured.class.getCanonicalName()));
        assertThat(InjectCodegenTypes.CONFIG_META_CONFIGURED.fqName(),
                   is(Configured.class.getCanonicalName()));
        assertThat(InjectCodegenTypes.COMMON_CONFIG.fqName(),
                   is(Config.class.getCanonicalName()));
        assertThat(InjectCodegenTypes.QUALIFIED_PROVIDER.fqName(),
                   is(QualifiedProvider.class.getCanonicalName()));
    }
}