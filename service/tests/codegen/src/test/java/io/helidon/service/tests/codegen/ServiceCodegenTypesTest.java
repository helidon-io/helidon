/*
 * Copyright (c) 2024, 2025 Oracle and/or its affiliates.
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
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.helidon.builder.api.Prototype;
import io.helidon.common.Generated;
import io.helidon.common.GenericType;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.ResolvedType;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.service.codegen.ServiceCodegenTypes;
import io.helidon.service.registry.Binding;
import io.helidon.service.registry.Dependency;
import io.helidon.service.registry.DependencyCardinality;
import io.helidon.service.registry.DependencyContext;
import io.helidon.service.registry.DependencyPlanBinder;
import io.helidon.service.registry.EmptyBinding;
import io.helidon.service.registry.Event;
import io.helidon.service.registry.EventManager;
import io.helidon.service.registry.FactoryType;
import io.helidon.service.registry.GeneratedService;
import io.helidon.service.registry.Interception;
import io.helidon.service.registry.InterceptionException;
import io.helidon.service.registry.InterceptionInvoker;
import io.helidon.service.registry.InterceptionMetadata;
import io.helidon.service.registry.Lookup;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.RegistryStartupProvider;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceDescriptor;
import io.helidon.service.registry.ServiceInstance;
import io.helidon.service.registry.ServiceLoader__ServiceDescriptor;
import io.helidon.service.registry.ServiceRegistry;
import io.helidon.service.registry.ServiceRegistryConfig;
import io.helidon.service.registry.ServiceRegistryManager;

import org.hamcrest.CoreMatchers;
import org.hamcrest.collection.IsEmptyCollection;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

class ServiceCodegenTypesTest {
    @SuppressWarnings("removal")
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
        checkField(toCheck, checked, fields, "SERVICE_ANNOTATION_PRE_DESTROY", Service.PreDestroy.class);
        checkField(toCheck, checked, fields, "SERVICE_ANNOTATION_POST_CONSTRUCT", Service.PostConstruct.class);
        checkField(toCheck, checked, fields, "SERVICE_ANNOTATION_CONTRACT", Service.Contract.class);
        checkField(toCheck, checked, fields, "SERVICE_ANNOTATION_EXTERNAL_CONTRACTS", Service.ExternalContracts.class);
        checkField(toCheck, checked, fields, "SERVICE_ANNOTATION_DESCRIPTOR", Service.Descriptor.class);
        checkField(toCheck, checked, fields, "SERVICE_ANNOTATION_GENERATE_BINDING", Service.GenerateBinding.class);
        checkField(toCheck, checked, fields, "SERVICE_ANNOTATION_ENTRY_POINT", Service.EntryPoint.class);
        checkField(toCheck, checked, fields, "SERVICE_DEPENDENCY", Dependency.class);
        checkField(toCheck, checked, fields, "SERVICE_DEPENDENCY_CONTEXT", DependencyContext.class);
        checkField(toCheck, checked, fields, "SERVICE_DESCRIPTOR", ServiceDescriptor.class);
        checkField(toCheck, checked, fields, "REGISTRY_STARTUP_PROVIDER", RegistryStartupProvider.class);

        checkField(toCheck, checked, fields, "BUILDER_BLUEPRINT", Prototype.Blueprint.class);
        checkField(toCheck, checked, fields, "GENERATED_ANNOTATION", Generated.class);

        // api.Service.*
        checkField(toCheck, checked, fields, "SERVICE_ANNOTATION_INJECT", Service.Inject.class);
        checkField(toCheck, checked, fields, "SERVICE_ANNOTATION_SINGLETON", Service.Singleton.class);
        checkField(toCheck, checked, fields, "SERVICE_ANNOTATION_NAMED", Service.Named.class);
        checkField(toCheck, checked, fields, "SERVICE_ANNOTATION_NAMED_BY_TYPE", Service.NamedByType.class);
        checkField(toCheck, checked, fields, "SERVICE_ANNOTATION_QUALIFIER", Service.Qualifier.class);
        checkField(toCheck, checked, fields, "SERVICE_ANNOTATION_DESCRIBE", Service.Describe.class);
        checkField(toCheck, checked, fields, "SERVICE_ANNOTATION_SCOPE", Service.Scope.class);
        checkField(toCheck, checked, fields, "SERVICE_ANNOTATION_PER_LOOKUP", Service.PerLookup.class);
        checkField(toCheck, checked, fields, "SERVICE_ANNOTATION_PER_INSTANCE", Service.PerInstance.class);
        checkField(toCheck, checked, fields, "SERVICE_ANNOTATION_RUN_LEVEL", Service.RunLevel.class);
        checkField(toCheck, checked, fields, "SERVICE_INJECTION_POINT_FACTORY", Service.InjectionPointFactory.class);
        checkField(toCheck, checked, fields, "SERVICE_SCOPE_HANDLER", Service.ScopeHandler.class);
        checkField(toCheck, checked, fields, "SERVICE_SERVICES_FACTORY", Service.ServicesFactory.class);
        checkField(toCheck, checked, fields, "SERVICE_QUALIFIED_FACTORY", Service.QualifiedFactory.class);

        checkField(toCheck, checked, fields, "SERVICE_CONFIG", ServiceRegistryConfig.class);
        checkField(toCheck, checked, fields, "SERVICE_CONFIG_BUILDER", ServiceRegistryConfig.Builder.class);
        checkField(toCheck, checked, fields, "SERVICE_REGISTRY", ServiceRegistry.class);
        checkField(toCheck, checked, fields, "SERVICE_REGISTRY_MANAGER", ServiceRegistryManager.class);
        checkField(toCheck, checked, fields, "DEPENDENCY_CARDINALITY", DependencyCardinality.class);
        checkField(toCheck, checked, fields, "SERVICE_LOADER_DESCRIPTOR", ServiceLoader__ServiceDescriptor.class);
        checkField(toCheck, checked, fields, "SERVICE_LOOKUP", Lookup.class);
        checkField(toCheck, checked, fields, "SERVICE_QUALIFIED_INSTANCE", Service.QualifiedInstance.class);

        // api.Interception.*
        checkField(toCheck, checked, fields, "INTERCEPTION_INTERCEPTED", Interception.Intercepted.class);
        checkField(toCheck, checked, fields, "INTERCEPTION_DELEGATE", Interception.Delegate.class);
        checkField(toCheck, checked, fields, "INTERCEPTION_EXTERNAL_DELEGATE", Interception.ExternalDelegate.class);

        // api.* except for interception types
        checkField(toCheck, checked, fields, "SERVICE_FACTORY_TYPE", FactoryType.class);
        checkField(toCheck, checked, fields, "SERVICE_QUALIFIER", Qualifier.class);
        checkField(toCheck, checked, fields, "SERVICE_SERVICE_INSTANCE", ServiceInstance.class);
        checkField(toCheck, checked, fields, "SERVICE_BINDING", Binding.class);
        checkField(toCheck, checked, fields, "SERVICE_BINDING_EMPTY", EmptyBinding.class);
        checkField(toCheck, checked, fields, "SERVICE_PLAN_BINDER", DependencyPlanBinder.class);

        // api.* interception types
        checkField(toCheck, checked, fields, "INTERCEPT_EXCEPTION", InterceptionException.class);
        checkField(toCheck, checked, fields, "INTERCEPT_METADATA", InterceptionMetadata.class);
        checkField(toCheck, checked, fields, "INTERCEPT_INVOKER", InterceptionInvoker.class);

        // api.* event types
        checkField(toCheck, checked, fields, "EVENT_OBSERVER", Event.Observer.class);
        checkField(toCheck, checked, fields, "EVENT_OBSERVER_ASYNC", Event.AsyncObserver.class);
        checkField(toCheck, checked, fields, "EVENT_EMITTER", Event.Emitter.class);
        checkField(toCheck, checked, fields, "EVENT_MANAGER", EventManager.class);

        // generated inject service types
        checkField(toCheck, checked, fields, "SERVICE_G_PER_INSTANCE_DESCRIPTOR",
                   GeneratedService.PerInstanceDescriptor.class);
        checkField(toCheck, checked, fields, "SERVICE_G_QUALIFIED_FACTORY_DESCRIPTOR",
                   GeneratedService.QualifiedFactoryDescriptor.class);
        checkField(toCheck, checked, fields, "SERVICE_G_SCOPE_HANDLER_DESCRIPTOR",
                   GeneratedService.ScopeHandlerDescriptor.class);
        checkField(toCheck, checked, fields, "SERVICE_G_DEPENDENCY_SUPPORT",
                   GeneratedService.DependencySupport.class);
        checkField(toCheck, checked, fields, "SERVICE_G_EVENT_OBSERVER_REGISTRATION",
                   GeneratedService.EventObserverRegistration.class);

        // generated interception types
        checkField(toCheck, checked, fields, "INTERCEPT_G_WRAPPER_SUPPLIER_FACTORY",
                   GeneratedService.SupplierFactoryInterceptionWrapper.class);
        checkField(toCheck, checked, fields, "INTERCEPT_G_WRAPPER_SERVICES_FACTORY",
                   GeneratedService.ServicesFactoryInterceptionWrapper.class);
        checkField(toCheck, checked, fields, "INTERCEPT_G_WRAPPER_IP_FACTORY",
                   GeneratedService.IpFactoryInterceptionWrapper.class);
        checkField(toCheck, checked, fields, "INTERCEPT_G_WRAPPER_QUALIFIED_FACTORY",
                   GeneratedService.QualifiedFactoryInterceptionWrapper.class);
        checkField(toCheck, checked, fields, "ANY_GENERIC_TYPE", TypeName.builder()
                .type(GenericType.class)
                .addTypeArgument(TypeNames.WILDCARD)
                .build());
        checkField(toCheck, checked, fields, "LIST_OF_DEPENDENCIES", TypeName.builder()
                .type(List.class)
                .addTypeArgument(TypeName.create(Dependency.class))
                .build());
        checkField(toCheck, checked, fields, "LIST_OF_DOUBLES", TypeName.builder()
                .type(List.class)
                .addTypeArgument(TypeName.create(Double.class))
                .build());
        checkField(toCheck, checked, fields, "SET_OF_QUALIFIERS", TypeName.builder()
                .type(Set.class)
                .addTypeArgument(TypeName.create(Qualifier.class))
                .build());
        checkField(toCheck, checked, fields, "LIST_OF_ANNOTATIONS", TypeName.builder()
                .type(List.class)
                .addTypeArgument(TypeName.create(Annotation.class))
                .build());
        checkField(toCheck, checked, fields, "SET_OF_STRINGS", TypeName.builder()
                .type(Set.class)
                .addTypeArgument(TypeName.create(String.class))
                .build());
        checkField(toCheck, checked, fields, "SET_OF_RESOLVED_TYPES", TypeName.builder()
                .type(Set.class)
                .addTypeArgument(TypeName.create(ResolvedType.class))
                .build());
        checkField(toCheck, checked, fields, "GENERIC_T_TYPE", TypeName.builder()
                .className("T")
                .generic(true)
                .build());

        assertThat("If the collection is not empty, please add appropriate checkField line to this test",
                   toCheck,
                   IsEmptyCollection.empty());
    }

    private void checkField(Set<String> namesToCheck,
                            Set<String> checkedNames,
                            Map<String, Field> namesToFields,
                            String name,
                            TypeName expectedType) {
        Field field = namesToFields.get(name);
        assertThat("Field " + name + " does not exist in the class", field, notNullValue());
        try {
            namesToCheck.remove(name);
            if (checkedNames.add(name)) {
                TypeName value = (TypeName) field.get(null);
                assertThat("Field " + name, value.resolvedName(), is(expectedType.resolvedName()));
            } else {
                fail("Field " + name + " is checked more than once");
            }
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
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