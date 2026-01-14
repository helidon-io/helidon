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

import java.math.BigInteger;
import java.util.UUID;

import io.helidon.json.binding.Json;
import io.helidon.json.binding.JsonBinding;
import io.helidon.testing.junit5.Testing;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@Testing.Test
public class MiscConvertersTest {

    private final JsonBinding jsonBinding;

    MiscConvertersTest(JsonBinding jsonBinding) {
        this.jsonBinding = jsonBinding;
    }

    @Test
    public void testUuidConverter() {
        UUID original = UUID.randomUUID();
        String json = jsonBinding.serialize(original);
        assertThat(json, is("\"" + original + "\""));
        UUID deserialized = jsonBinding.deserialize(json, UUID.class);
        assertThat(deserialized, is(original));
    }

    @Test
    public void testBigIntegerConverter() {
        BigInteger original = new BigInteger("123456789012345678901234567890");
        String json = jsonBinding.serialize(original);
        assertThat(json, is("\"123456789012345678901234567890\""));
        BigInteger deserialized = jsonBinding.deserialize(json, BigInteger.class);
        assertThat(deserialized, is(original));
    }

    @Test
    public void testUuidInBean() {
        UuidBean bean = new UuidBean();
        bean.setId(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"));
        bean.setName("Test Bean");

        String json = jsonBinding.serialize(bean);
        assertThat(json, is("{\"id\":\"550e8400-e29b-41d4-a716-446655440000\",\"name\":\"Test Bean\"}"));

        UuidBean deserialized = jsonBinding.deserialize(json, UuidBean.class);
        assertThat(deserialized.getId(), is(bean.getId()));
        assertThat(deserialized.getName(), is(bean.getName()));
    }

    @Test
    public void testBigIntegerInBean() {
        BigIntegerBean bean = new BigIntegerBean();
        bean.setValue(new BigInteger("999999999999999999999999999999"));
        bean.setDescription("Large number");

        String json = jsonBinding.serialize(bean);
        BigIntegerBean deserialized = jsonBinding.deserialize(json, BigIntegerBean.class);

        assertThat(deserialized.getValue(), is(bean.getValue()));
        assertThat(deserialized.getDescription(), is(bean.getDescription()));
    }

    @Json.Entity
    static class UuidBean {
        private UUID id;
        private String name;

        public UUID getId() {
            return id;
        }

        public void setId(UUID id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    @Json.Entity
    static class BigIntegerBean {
        private BigInteger value;
        private String description;

        public BigInteger getValue() {
            return value;
        }

        public void setValue(BigInteger value) {
            this.value = value;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }
}
