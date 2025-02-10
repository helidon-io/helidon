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

package io.helidon.builder.codegen;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import io.helidon.builder.api.Description;
import io.helidon.builder.api.GeneratedBuilder;
import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.builder.api.RuntimeType;
import io.helidon.common.Generated;
import io.helidon.common.config.Config;
import io.helidon.common.config.ConfigBuilderSupport;
import io.helidon.common.types.TypeName;
import io.helidon.service.registry.GlobalServiceRegistry;
import io.helidon.service.registry.RegistryBuilderSupport;
import io.helidon.service.registry.ServiceRegistry;
import io.helidon.service.registry.Services;

import org.hamcrest.CoreMatchers;
import org.hamcrest.collection.IsEmptyCollection;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

public class TypesTest {
    @Test
    void testTypes() {
        // it is really important to test ALL constants on the class, so let's use reflection
        Field[] declaredFields = Types.class.getDeclaredFields();

        Set<String> toCheck = new HashSet<>();
        Set<String> checked = new HashSet<>();
        Map<String, Field> fields = new HashMap<>();

        for (Field declaredField : declaredFields) {
            String name = declaredField.getName();

            assertThat(name + " must be a TypeName", declaredField.getType(), CoreMatchers.sameInstance(TypeName.class));
            assertThat(name + " must be static", Modifier.isStatic(declaredField.getModifiers()), is(true));
            assertThat(name + " must be package local, not public",
                       Modifier.isPublic(declaredField.getModifiers()),
                       is(false));
            assertThat(name + " must be package local, not private",
                       Modifier.isPrivate(declaredField.getModifiers()),
                       is(false));
            assertThat(name + " must be package local, not protected",
                       Modifier.isProtected(declaredField.getModifiers()),
                       is(false));
            assertThat(name + " must be final", Modifier.isFinal(declaredField.getModifiers()), is(true));

            toCheck.add(name);
            fields.put(name, declaredField);
        }

        checkField(toCheck, checked, fields, "COMMON_CONFIG", Config.class);
        checkField(toCheck, checked, fields, "GENERATED", Generated.class);
        checkField(toCheck, checked, fields, "DEPRECATED", Deprecated.class);
        checkField(toCheck, checked, fields, "LINKED_HASH_MAP", LinkedHashMap.class);
        checkField(toCheck, checked, fields, "ARRAY_LIST", ArrayList.class);
        checkField(toCheck, checked, fields, "LINKED_HASH_SET", LinkedHashSet.class);
        checkField(toCheck, checked, fields, "CHAR_ARRAY", char[].class);
        checkField(toCheck, checked, fields, "PATH", Path.class);
        checkField(toCheck, checked, fields, "URI", URI.class);
        checkField(toCheck, checked, fields, "SERVICE_REGISTRY", ServiceRegistry.class);
        checkField(toCheck, checked, fields, "GLOBAL_SERVICE_REGISTRY", GlobalServiceRegistry.class);
        checkField(toCheck, checked, fields, "BUILDER_DESCRIPTION", Description.class);
        checkField(toCheck, checked, fields, "PROTOTYPE_BLUEPRINT", Prototype.Blueprint.class);
        checkField(toCheck, checked, fields, "PROTOTYPE_IMPLEMENT", Prototype.Implement.class);
        checkField(toCheck, checked, fields, "PROTOTYPE_API", Prototype.Api.class);
        checkField(toCheck, checked, fields, "PROTOTYPE_ANNOTATED", Prototype.Annotated.class);
        checkField(toCheck, checked, fields, "PROTOTYPE_FACTORY", Prototype.Factory.class);
        checkField(toCheck, checked, fields, "PROTOTYPE_CONFIGURED", Prototype.Configured.class);
        checkField(toCheck, checked, fields, "PROTOTYPE_PROVIDES", Prototype.Provides.class);
        checkField(toCheck, checked, fields, "PROTOTYPE_BUILDER", Prototype.Builder.class);
        checkField(toCheck, checked, fields, "PROTOTYPE_CUSTOM_METHODS", Prototype.CustomMethods.class);
        checkField(toCheck, checked, fields, "PROTOTYPE_FACTORY_METHOD", Prototype.FactoryMethod.class);
        checkField(toCheck, checked, fields, "PROTOTYPE_BUILDER_METHOD", Prototype.BuilderMethod.class);
        checkField(toCheck, checked, fields, "PROTOTYPE_PROTOTYPE_METHOD", Prototype.PrototypeMethod.class);
        checkField(toCheck, checked, fields, "PROTOTYPE_BUILDER_DECORATOR", Prototype.BuilderDecorator.class);
        checkField(toCheck, checked, fields, "PROTOTYPE_CONSTANT", Prototype.Constant.class);
        checkField(toCheck, checked, fields, "PROTOTYPE_SERVICE_REGISTRY", Prototype.RegistrySupport.class);
        checkField(toCheck, checked, fields, "GENERATED_EQUALITY_UTIL", GeneratedBuilder.EqualityUtil.class);
        checkField(toCheck, checked, fields, "RUNTIME_PROTOTYPE", RuntimeType.PrototypedBy.class);
        checkField(toCheck, checked, fields, "RUNTIME_PROTOTYPED_BY", RuntimeType.PrototypedBy.class);
        checkField(toCheck, checked, fields, "RUNTIME_API", RuntimeType.Api.class);
        checkField(toCheck, checked, fields, "OPTION_SAME_GENERIC", Option.SameGeneric.class);
        checkField(toCheck, checked, fields, "OPTION_SINGULAR", Option.Singular.class);
        checkField(toCheck, checked, fields, "OPTION_CONFIDENTIAL", Option.Confidential.class);
        checkField(toCheck, checked, fields, "OPTION_REDUNDANT", Option.Redundant.class);
        checkField(toCheck, checked, fields, "OPTION_CONFIGURED", Option.Configured.class);
        checkField(toCheck, checked, fields, "OPTION_ACCESS", Option.Access.class);
        checkField(toCheck, checked, fields, "OPTION_REQUIRED", Option.Required.class);
        checkField(toCheck, checked, fields, "OPTION_PROVIDER", Option.Provider.class);
        checkField(toCheck, checked, fields, "OPTION_ALLOWED_VALUES", Option.AllowedValues.class);
        checkField(toCheck, checked, fields, "OPTION_ALLOWED_VALUE", Option.AllowedValue.class);
        checkField(toCheck, checked, fields, "OPTION_DEFAULT", Option.Default.class);
        checkField(toCheck, checked, fields, "OPTION_DEFAULT_INT", Option.DefaultInt.class);
        checkField(toCheck, checked, fields, "OPTION_DEFAULT_DOUBLE", Option.DefaultDouble.class);
        checkField(toCheck, checked, fields, "OPTION_DEFAULT_BOOLEAN", Option.DefaultBoolean.class);
        checkField(toCheck, checked, fields, "OPTION_DEFAULT_LONG", Option.DefaultLong.class);
        checkField(toCheck, checked, fields, "OPTION_DEFAULT_METHOD", Option.DefaultMethod.class);
        checkField(toCheck, checked, fields, "OPTION_DEFAULT_CODE", Option.DefaultCode.class);
        checkField(toCheck, checked, fields, "OPTION_DEPRECATED", Option.Deprecated.class);
        checkField(toCheck, checked, fields, "OPTION_TYPE", Option.Type.class);
        checkField(toCheck, checked, fields, "OPTION_DECORATOR", Option.Decorator.class);
        checkField(toCheck, checked, fields, "OPTION_REGISTRY_SERVICE", Option.RegistryService.class);

        checkField(toCheck, checked, fields, "SERVICES", Services.class);

        checkField(toCheck, checked, fields, "CONFIG_BUILDER_SUPPORT", ConfigBuilderSupport.class);
        checkField(toCheck, checked, fields, "CONFIG_CONFIGURED_BUILDER", ConfigBuilderSupport.ConfiguredBuilder.class);

        checkField(toCheck, checked, fields, "REGISTRY_BUILDER_SUPPORT", RegistryBuilderSupport.class);

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
                fail("Field " + name + " is checked more than once.class");
            }
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
