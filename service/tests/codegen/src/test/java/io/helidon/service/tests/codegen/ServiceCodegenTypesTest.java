/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.service.tests.codegen;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import io.helidon.builder.api.Prototype;
import io.helidon.common.config.Config;
import io.helidon.common.config.ConfigException;
import io.helidon.common.types.TypeName;
import io.helidon.config.metadata.Configured;
import io.helidon.service.codegen.ServiceCodegenTypes;
import io.helidon.service.inject.Application;
import io.helidon.service.inject.ApplicationMain;
import io.helidon.service.inject.InjectConfig;
import io.helidon.service.inject.InjectRegistryManager;
import io.helidon.service.inject.InjectionPlanBinder;
import io.helidon.service.inject.api.GeneratedInjectService;
import io.helidon.service.inject.api.GeneratedInjectService.CreateForDescriptor;
import io.helidon.service.inject.api.GeneratedInjectService.IpSupport;
import io.helidon.service.inject.api.GeneratedInjectService.QualifiedProviderDescriptor;
import io.helidon.service.inject.api.GeneratedInjectService.ScopeHandlerDescriptor;
import io.helidon.service.inject.api.InjectRegistry;
import io.helidon.service.inject.api.Injection;
import io.helidon.service.inject.api.Interception;
import io.helidon.service.inject.api.InvocationException;
import io.helidon.service.inject.api.Invoker;
import io.helidon.service.inject.api.Ip;
import io.helidon.service.inject.api.Lookup;
import io.helidon.service.inject.api.Qualifier;
import io.helidon.service.registry.Dependency;
import io.helidon.service.registry.DependencyContext;
import io.helidon.service.registry.GeneratedService;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceInfo;

import org.hamcrest.CoreMatchers;
import org.hamcrest.collection.IsEmptyCollection;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

class ServiceCodegenTypesTest {
    @Test
    void testTypes() {
        // it is really important to test ALL constants on the class, so let's use reflection
        Field[] declaredFields = ServiceCodegenTypes.class.getDeclaredFields();

        Set<String> toCheck = new HashSet<>();
        Set<String> checked = new HashSet<>();
        Map<String, Field> fields = new HashMap<>();

        for (Field declaredField : declaredFields) {
            String name = declaredField.getName();

            assertThat(name + " must be a TypeName", declaredField.getType(), CoreMatchers.sameInstance(TypeName.class));
            assertThat(name + " must be static", Modifier.isStatic(declaredField.getModifiers()), is(true));
            assertThat(name + " must be public", Modifier.isPublic(declaredField.getModifiers()), is(true));
            assertThat(name + " must be final", Modifier.isFinal(declaredField.getModifiers()), is(true));

            toCheck.add(name);
            fields.put(name, declaredField);
        }

        checkField(toCheck, checked, fields, "SERVICE_ANNOTATION_PROVIDER", Service.Provider.class);
        checkField(toCheck, checked, fields, "SERVICE_ANNOTATION_CONTRACT", Service.Contract.class);
        checkField(toCheck, checked, fields, "SERVICE_ANNOTATION_EXTERNAL_CONTRACTS", Service.ExternalContracts.class);
        checkField(toCheck, checked, fields, "SERVICE_ANNOTATION_DESCRIPTOR", Service.Descriptor.class);
        checkField(toCheck, checked, fields, "SERVICE_DESCRIPTOR", GeneratedService.Descriptor.class);
        checkField(toCheck, checked, fields, "SERVICE_DEPENDENCY", Dependency.class);
        checkField(toCheck, checked, fields, "SERVICE_DEPENDENCY_CONTEXT", DependencyContext.class);
        checkField(toCheck, checked, fields, "SERVICE_INFO", ServiceInfo.class);

        checkField(toCheck, checked, fields, "INJECTION_INJECT", Injection.Inject.class);
        checkField(toCheck, checked, fields, "INJECTION_SCOPE", Injection.Scope.class);
        checkField(toCheck, checked, fields, "INJECTION_SINGLETON", Injection.Singleton.class);
        checkField(toCheck, checked, fields, "INJECTION_REQUEST_SCOPE", Injection.RequestScope.class);
        checkField(toCheck, checked, fields, "INJECTION_NAMED", Injection.Named.class);
        checkField(toCheck, checked, fields, "INJECTION_NAMED_BY_CLASS", Injection.NamedByClass.class);
        checkField(toCheck, checked, fields, "INJECTION_QUALIFIER", Injection.Qualifier.class);
        checkField(toCheck, checked, fields, "INJECTION_POST_CONSTRUCT", Injection.PostConstruct.class);
        checkField(toCheck, checked, fields, "INJECTION_PRE_DESTROY", Injection.PreDestroy.class);
        checkField(toCheck, checked, fields, "INJECTION_INSTANCE", Injection.Instance.class);
        checkField(toCheck, checked, fields, "INJECTION_CREATE_FOR", Injection.CreateFor.class);
        checkField(toCheck, checked, fields, "INJECTION_RUN_LEVEL", Injection.RunLevel.class);
        checkField(toCheck, checked, fields, "INJECTION_MAIN", Injection.Main.class);
        checkField(toCheck, checked, fields, "INJECT_SERVICE_DESCRIPTOR", GeneratedInjectService.Descriptor.class);
        checkField(toCheck, checked, fields, "INJECT_LOOKUP", Lookup.class);
        checkField(toCheck, checked, fields, "INJECT_QUALIFIER", Qualifier.class);
        checkField(toCheck, checked, fields, "INJECTION_POINT", Ip.class);
        checkField(toCheck, checked, fields, "SERVICES_PROVIDER", Injection.ServicesProvider.class);
        checkField(toCheck, checked, fields, "QUALIFIED_INSTANCE", Injection.QualifiedInstance.class);
        checkField(toCheck, checked, fields, "INJECTION_POINT_PROVIDER", Injection.InjectionPointProvider.class);
        checkField(toCheck, checked, fields, "QUALIFIED_PROVIDER", Injection.QualifiedProvider.class);
        checkField(toCheck, checked, fields, "INJECTION_PLAN_BINDER", InjectionPlanBinder.class);
        checkField(toCheck, checked, fields, "INJECT_SCOPE_HANDLER", Injection.ScopeHandler.class);
        checkField(toCheck, checked, fields, "INJECT_APPLICATION", Application.class);
        checkField(toCheck, checked, fields, "INJECT_CONFIG_BUILDER", InjectConfig.Builder.class);
        checkField(toCheck, checked, fields, "INJECT_QUALIFIED_PROVIDER_DESCRIPTOR", QualifiedProviderDescriptor.class);
        checkField(toCheck, checked, fields, "INJECT_SCOPE_HANDLER_DESCRIPTOR", ScopeHandlerDescriptor.class);
        checkField(toCheck, checked, fields, "INJECT_CREATE_FOR_DESCRIPTOR", CreateForDescriptor.class);
        checkField(toCheck, checked, fields, "INJECT_IP_SUPPORT", IpSupport.class);
        checkField(toCheck, checked, fields, "INJECT_CONFIG", InjectConfig.class);
        checkField(toCheck, checked, fields, "INJECT_REGISTRY_MANAGER", InjectRegistryManager.class);
        checkField(toCheck, checked, fields, "INJECT_REGISTRY", InjectRegistry.class);
        checkField(toCheck, checked, fields, "INJECT_APPLICATION_MAIN", ApplicationMain.class);

        checkField(toCheck, checked, fields, "INVOKER", Invoker.class);
        checkField(toCheck, checked, fields, "INVOCATION_EXCEPTION", InvocationException.class);
        checkField(toCheck, checked, fields, "INTERCEPTION_TRIGGER", Interception.Trigger.class);
        checkField(toCheck, checked, fields, "INTERCEPTION_METADATA", GeneratedInjectService.InterceptionMetadata.class);

        checkField(toCheck, checked, fields, "BUILDER_BLUEPRINT", Prototype.Blueprint.class);
        checkField(toCheck, checked, fields, "BUILDER_CONFIGURED", Prototype.Configured.class);

        checkField(toCheck, checked, fields, "CONFIG_COMMON_CONFIG", Config.class);
        checkField(toCheck, checked, fields, "CONFIG_EXCEPTION", ConfigException.class);
        checkField(toCheck, checked, fields, "CONFIG_META_CONFIGURED", Configured.class);

        assertThat(toCheck, IsEmptyCollection.empty());
    }

    private void checkField(Set<String> namesToCheck,
                            Set<String> checkedNames,
                            Map<String, Field> namesToFields,
                            String name,
                            Class<?> expectedType) {
        Field field = namesToFields.get(name);
        assertThat("Field " + name + " does not exist in the class", field, notNullValue());
        try {
            namesToCheck.remove(name);
            if (checkedNames.add(name)) {
                TypeName value = (TypeName) field.get(null);
                assertThat("Field " + name, value.fqName(), is(expectedType.getCanonicalName()));
            } else {
                fail("Field " + name + " is checked more than once");
            }
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}