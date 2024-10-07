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

import io.helidon.common.types.TypeName;
import io.helidon.service.codegen.InjectCodegenTypes;
import io.helidon.service.inject.api.GeneratedInjectService;
import io.helidon.service.inject.api.InjectServiceDescriptor;
import io.helidon.service.inject.api.Injection;
import io.helidon.service.inject.api.Interception;
import io.helidon.service.inject.api.InterceptionException;
import io.helidon.service.inject.api.InterceptionInvoker;
import io.helidon.service.inject.api.InterceptionMetadata;
import io.helidon.service.inject.api.Ip;
import io.helidon.service.inject.api.ProviderType;
import io.helidon.service.inject.api.Qualifier;
import io.helidon.service.inject.api.ServiceInstance;

import org.hamcrest.CoreMatchers;
import org.hamcrest.collection.IsEmptyCollection;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

class InjectCodegenTypesTest {
    @Test
    void testTypes() {
        // it is really important to test ALL constants on the class, so let's use reflection
        Field[] declaredFields = InjectCodegenTypes.class.getDeclaredFields();

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

        // api.Injection.*
        checkField(toCheck, checked, fields, "INJECTION_INJECT", Injection.Inject.class);
        checkField(toCheck, checked, fields, "INJECTION_SINGLETON", Injection.Singleton.class);
        checkField(toCheck, checked, fields, "INJECTION_NAMED", Injection.Named.class);
        checkField(toCheck, checked, fields, "INJECTION_NAMED_BY_CLASS", Injection.NamedByType.class);
        checkField(toCheck, checked, fields, "INJECTION_QUALIFIER", Injection.Qualifier.class);
        checkField(toCheck, checked, fields, "INJECTION_DESCRIBE", Injection.Describe.class);
        checkField(toCheck, checked, fields, "INJECTION_SCOPE", Injection.Scope.class);
        checkField(toCheck, checked, fields, "INJECTION_INSTANCE", Injection.PerLookup.class);
        checkField(toCheck, checked, fields, "INJECTION_CREATE_FOR", Injection.PerInstance.class);
        checkField(toCheck, checked, fields, "INJECTION_RUN_LEVEL", Injection.RunLevel.class);
        checkField(toCheck, checked, fields, "INJECTION_POINT_PROVIDER", Injection.InjectionPointProvider.class);
        checkField(toCheck, checked, fields, "INJECTION_SCOPE_HANDLER", Injection.ScopeHandler.class);
        checkField(toCheck, checked, fields, "INJECTION_SERVICES_PROVIDER", Injection.ServicesProvider.class);
        checkField(toCheck, checked, fields, "INJECTION_QUALIFIED_PROVIDER", Injection.QualifiedProvider.class);

        // api.Interception.*
        checkField(toCheck, checked, fields, "INTERCEPTION_TRIGGER", Interception.Trigger.class);
        checkField(toCheck, checked, fields, "INTERCEPTION_DELEGATE", Interception.Delegate.class);
        checkField(toCheck, checked, fields, "INTERCEPTION_EXTERNAL_DELEGATE", Interception.ExternalDelegate.class);

        // api.* except for interception types
        checkField(toCheck, checked, fields, "INJECT_PROVIDER_TYPE", ProviderType.class);
        checkField(toCheck, checked, fields, "INJECT_QUALIFIER", Qualifier.class);
        checkField(toCheck, checked, fields, "INJECT_INJECTION_POINT", Ip.class);
        checkField(toCheck, checked, fields, "INJECT_SERVICE_INSTANCE", ServiceInstance.class);
        checkField(toCheck, checked, fields, "INJECT_SERVICE_DESCRIPTOR", InjectServiceDescriptor.class);

        // api.* interception types
        checkField(toCheck, checked, fields, "INTERCEPT_EXCEPTION", InterceptionException.class);
        checkField(toCheck, checked, fields, "INTERCEPT_METADATA", InterceptionMetadata.class);
        checkField(toCheck, checked, fields, "INTERCEPT_INVOKER", InterceptionInvoker.class);

        // generated inject service types
        checkField(toCheck, checked, fields, "INJECT_G_CREATE_FOR_DESCRIPTOR",
                   GeneratedInjectService.PerInstanceDescriptor.class);
        checkField(toCheck, checked, fields, "INJECT_G_QUALIFIED_PROVIDER_DESCRIPTOR",
                   GeneratedInjectService.QualifiedProviderDescriptor.class);
        checkField(toCheck, checked, fields, "INJECT_G_SCOPE_HANDLER_DESCRIPTOR",
                   GeneratedInjectService.ScopeHandlerDescriptor.class);
        checkField(toCheck, checked, fields, "INJECT_G_IP_SUPPORT",
                   GeneratedInjectService.IpSupport.class);

        // generated interception types
        checkField(toCheck, checked, fields, "INTERCEPT_G_WRAPPER_SUPPLIER_PROVIDER",
                   GeneratedInjectService.SupplierProviderInterceptionWrapper.class);
        checkField(toCheck, checked, fields, "INTERCEPT_G_WRAPPER_SERVICES_PROVIDER",
                   GeneratedInjectService.ServicesProviderInterceptionWrapper.class);
        checkField(toCheck, checked, fields, "INTERCEPT_G_WRAPPER_IP_PROVIDER",
                   GeneratedInjectService.IpProviderInterceptionWrapper.class);
        checkField(toCheck, checked, fields, "INTERCEPT_G_WRAPPER_QUALIFIED_PROVIDER",
                   GeneratedInjectService.QualifiedProviderInterceptionWrapper.class);

        assertThat("If the collection is not empty, please add appropriate checkField line to this test",
                   toCheck,
                   IsEmptyCollection.empty());
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