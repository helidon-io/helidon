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
import io.helidon.testing.junit5.Testing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Testing.Test
public class InterfaceAnnotationsTest {

    private final JsonBinding jsonBinding;

    InterfaceAnnotationsTest(JsonBinding jsonBinding) {
        this.jsonBinding = jsonBinding;
    }

    @Test
    public void testJsonbPropertyIfcInheritance() {
        InterfacedPojoB pojo = new InterfacedPojoImpl();
        pojo.setPropertyA("AA");
        pojo.setPropertyB("BB");

        String json = "{\"propA\":\"AA\",\"propB\":\"BB\"}";
        assertEquals(json, jsonBinding.serialize(pojo));
    }

    interface InterfacedPojoA {

        @Json.Property("propA")
        String getPropertyA();

        @Json.Property("propA")
        void setPropertyA(String property);

    }

    interface InterfacedPojoB extends InterfacedPojoA {

        @Json.Property("propB")
        String getPropertyB();

        @Json.Property("propB")
        void setPropertyB(String propertyB);

    }

    @Json.Entity
    static class InterfacedPojoImpl implements InterfacedPojoB {

        private String propertyA;
        private String propertyB;

        @Override
        public String getPropertyA() {
            return propertyA;
        }

        @Override
        public void setPropertyA(String propertyA) {
            this.propertyA = propertyA;
        }

        @Override
        public String getPropertyB() {
            return propertyB;
        }

        @Override
        public void setPropertyB(String propertyB) {
            this.propertyB = propertyB;
        }

    }

}
