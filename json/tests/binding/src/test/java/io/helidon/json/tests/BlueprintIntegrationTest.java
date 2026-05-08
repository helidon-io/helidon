/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.json.tests;

import java.util.Optional;

import io.helidon.builder.api.Prototype;
import io.helidon.json.binding.JsonBinding;
import io.helidon.testing.junit5.Testing;

import org.hamcrest.Matcher;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

@Testing.Test
public class BlueprintIntegrationTest {

    private final JsonBinding jsonBinding;

    BlueprintIntegrationTest(JsonBinding jsonBinding) {
        this.jsonBinding = jsonBinding;
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testBlueprintIntegrationParameterized(BindingMethod bindingMethod) {
        String expectedJson = "{\"firstName\":\"Test\",\"lastName\":\"Name\"}";
        MyTestPerson expectedPerson = MyTestPerson.builder().firstName("Test").lastName("Name").build();

        String json = bindingMethod.serialize(jsonBinding, expectedPerson);
        assertThat(json, is(expectedJson));

        MyTestPerson actualPerson = bindingMethod.deserialize(jsonBinding, json, MyTestPerson.class);

        assertThat(actualPerson, notNullValue());
        /*
        assertThat(actualPerson, is(expectedPerson));
        This fails with
        Compilation failure
        [ERROR] error: incompatible types: no instance(s) of type variable(s) T exist so that Matcher<T> conforms to boolean
         */
        assertThat(actualPerson, (Matcher<MyTestPerson>)equalTo(expectedPerson));
    }

    @ParameterizedTest
    @EnumSource(BindingMethod.class)
    public void testBlueprintIntegrationWithOptionalEmptyParameterized(BindingMethod bindingMethod) {
        String expectedJson = "{\"firstName\":\"Test\"}";
        MyTestPerson expectedPerson = MyTestPerson.builder().firstName("Test").build();

        String json = bindingMethod.serialize(jsonBinding, expectedPerson);
        assertThat(json, is(expectedJson));

        MyTestPerson actualPerson = bindingMethod.deserialize(jsonBinding, json, MyTestPerson.class);

        assertThat(actualPerson, notNullValue());
        // see above - cannot use assertThat(actualPerson, is(expectedPerson)) due to compiler issue
        assertThat(actualPerson, (Matcher<MyTestPerson>)equalTo(expectedPerson));
    }

    @Prototype.Blueprint
    @Prototype.Annotated("io.helidon.json.binding.Json.Entity")
    interface MyTestPersonBlueprint {

        String firstName();

        Optional<String> lastName();

    }
}
