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

import io.helidon.json.binding.Json;
import io.helidon.json.binding.JsonBinding;
import io.helidon.json.binding.Order;
import io.helidon.testing.junit5.Testing;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@Testing.Test
public class PropertyOrderTest {

    private final JsonBinding jsonBinding;

    PropertyOrderTest(JsonBinding jsonBinding) {
        this.jsonBinding = jsonBinding;
    }

    @Test
    public void testDefaultPropertyOrder() {
        DefaultPropertyOrderRecord testRecord = new DefaultPropertyOrderRecord("Full value", "first name", "last name");
        assertThat(jsonBinding.serialize(testRecord),
                   is("{\"fullName\":\"Full value\",\"firstName\":\"first name\",\"lastName\":\"last name\"}"));
    }

    @Test
    public void testAnyPropertyOrder() {
        AnyOrderRecord testRecord = new AnyOrderRecord("Full value", "first name", "last name");
        assertThat(jsonBinding.serialize(testRecord),
                   is("{\"fullName\":\"Full value\",\"firstName\":\"first name\",\"lastName\":\"last name\"}"));
    }

    @Test
    public void testAlphabeticalPropertyOrder() {
        AlphabeticalOrderRecord testRecord = new AlphabeticalOrderRecord("Full value", "first name", "last name");
        assertThat(jsonBinding.serialize(testRecord),
                   is("{\"firstName\":\"first name\",\"fullName\":\"Full value\",\"lastName\":\"last name\"}"));
    }

    @Test
    public void testReversePropertyOrder() {
        ReverseOrderRecord testRecord = new ReverseOrderRecord("Full value", "first name", "last name");
        assertThat(jsonBinding.serialize(testRecord),
                   is("{\"lastName\":\"last name\",\"fullName\":\"Full value\",\"firstName\":\"first name\"}"));
    }

    @Json.Entity
    record DefaultPropertyOrderRecord(String fullName, String firstName, String lastName) {
    }

    @Json.Entity
    @Json.PropertyOrder(Order.UNDEFINED)
    record AnyOrderRecord(String fullName, String firstName, String lastName) {
    }

    @Json.Entity
    @Json.PropertyOrder(Order.ALPHABETICAL)
    record AlphabeticalOrderRecord(String fullName, String firstName, String lastName) {
    }

    @Json.Entity
    @Json.PropertyOrder(Order.REVERSE_ALPHABETICAL)
    record ReverseOrderRecord(String fullName, String firstName, String lastName) {
    }

}
