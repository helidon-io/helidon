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
package io.helidon.dbclient.json;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.helidon.common.mapper.MapperManager;
import io.helidon.dbclient.DbColumn;
import io.helidon.dbclient.DbColumnBase;
import io.helidon.dbclient.DbMapperManager;
import io.helidon.dbclient.DbRow;
import io.helidon.dbclient.DbRowBase;
import io.helidon.json.JsonArray;
import io.helidon.json.JsonNull;
import io.helidon.json.JsonObject;
import io.helidon.json.JsonString;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

/**
 * Tests {@link io.helidon.dbclient.json.HelidonJsonMapper}.
 */
class HelidonJsonMapperTest {

    private static final DbMapperManager DB_MAPPER_MANAGER = DbMapperManager.builder()
            .addMapperProvider(new HelidonJsonMapperProvider())
            .build();

    @Test
    void testRowAsJsonObject() {
        DbRow row = row(column("name", "Ada"), column("active", true), column("age", 42));
        JsonObject jsonObject = row.as(JsonObject.class);
        assertThat(jsonObject.stringValue("name").orElseThrow(), is("Ada"));
        assertThat(jsonObject.booleanValue("active").orElseThrow(), is(true));
        assertThat(jsonObject.intValue("age").orElseThrow(), is(42));
    }

    @Test
    void testRowAsJsonObjectPreservesHelidonJsonValue() {
        JsonObject address = JsonObject.builder()
                .set("city", "Prague")
                .build();
        DbRow row = row(column("address", address));
        JsonObject jsonObject = row.as(JsonObject.class);
        assertThat(jsonObject.objectValue("address").orElseThrow(), is(address));
    }

    @Test
    void testRowAsJsonObjectConvertsNestedMapAndList() {
        List<Object> rawAliases = new ArrayList<>();
        rawAliases.add("home");
        rawAliases.add(null);
        rawAliases.add(Map.of("primary", true));

        Map<String, Object> address = new LinkedHashMap<>();
        address.put("city", "Prague");
        address.put("zip", new PreciseNumber("120.50"));
        address.put("aliases", rawAliases);

        DbRow row = row(column("address", address));
        JsonObject jsonObject = row.as(JsonObject.class);

        JsonObject addressObject = jsonObject.objectValue("address").orElseThrow();
        assertThat(addressObject.stringValue("city").orElseThrow(), is("Prague"));
        assertThat(addressObject.numberValue("zip").orElseThrow(), is(new BigDecimal("120.50")));

        JsonArray aliases = addressObject.arrayValue("aliases").orElseThrow();
        assertThat(aliases.get(0, JsonNull.instance()).asString().value(), is("home"));
        assertThat(aliases.get(1, JsonNull.instance()), is(JsonNull.instance()));
        assertThat(aliases.get(2, JsonNull.instance()).asObject().booleanValue("primary").orElseThrow(), is(true));
    }

    @Test
    void testRowAsJsonObjectPreservesArbitraryNumberPrecision() {
        DbRow row = row(column("price", new PreciseNumber("12.34")));
        JsonObject jsonObject = row.as(JsonObject.class);
        assertThat(jsonObject.numberValue("price").orElseThrow(), is(new BigDecimal("12.34")));
    }

    @Test
    void testRowAsJsonObjectPreservesSpecialFloatingPointValuesAsStrings() {
        DbRow row = row(column("nan", Double.NaN),
                        column("positiveInfinity", Double.POSITIVE_INFINITY),
                        column("negativeInfinity", Float.NEGATIVE_INFINITY));
        JsonObject jsonObject = row.as(JsonObject.class);

        assertThat(jsonObject.stringValue("nan").orElseThrow(), is("NaN"));
        assertThat(jsonObject.stringValue("positiveInfinity").orElseThrow(), is("Infinity"));
        assertThat(jsonObject.stringValue("negativeInfinity").orElseThrow(), is("-Infinity"));
    }

    @Test
    void testJsonObjectToNamedParameters() {
        JsonObject address = JsonObject.builder()
                .set("city", "Prague")
                .set("flags", JsonArray.createStrings(List.of("capital", "eu")))
                .build();
        JsonArray tags = JsonArray.createStrings(List.of("json", "dbclient"));
        JsonObject jsonObject = JsonObject.builder()
                .set("name", "Ada")
                .set("active", true)
                .set("age", 42)
                .set("address", address)
                .set("tags", tags)
                .setNull("note")
                .build();

        Map<String, Object> parameters = HelidonJsonMapper.create().toNamedParameters(jsonObject);

        assertThat(parameters.get("name"), is("Ada"));
        assertThat(parameters.get("active"), is(true));
        assertThat(parameters.get("age"), is(new BigDecimal("42")));
        assertThat(parameters.get("address"), is(Map.of("city", "Prague", "flags", List.of("capital", "eu"))));
        assertThat(parameters.get("tags"), is(List.of("json", "dbclient")));
        assertThat(parameters.containsKey("note"), is(true));
        assertThat(parameters.get("note"), is(nullValue()));
    }

    @Test
    void testJsonObjectToIndexedParameters() {
        JsonObject jsonObject = JsonObject.builder()
                .set("name", "Ada")
                .set("active", true)
                .set("age", 42)
                .set("tags", JsonArray.create(List.of(JsonString.create("json"),
                                                      JsonObject.builder()
                                                              .set("type", "dbclient")
                                                              .build())))
                .build();

        List<Object> parameters = HelidonJsonMapper.create().toIndexedParameters(jsonObject);

        assertThat(parameters,
                   contains("Ada",
                            true,
                            new BigDecimal("42"),
                            List.of("json", Map.of("type", "dbclient"))));
    }

    private static DbRow row(DbColumnBase... columns) {
        return new DbRowBase(columns, DB_MAPPER_MANAGER) {
            @Override
            public DbColumn column(String name) {
                return super.column(name);
            }
        };
    }

    @SuppressWarnings("removal")
    private static DbColumnBase column(String name, Object value) {
        return new DbColumnBase(value, MapperManager.create()) {

            @Override
            public Class<?> javaType() {
                return value.getClass();
            }

            @Override
            public String dbType() {
                return "test";
            }

            @Override
            public String name() {
                return name;
            }
        };
    }

    private static final class PreciseNumber extends Number {
        private final BigDecimal value;

        private PreciseNumber(String value) {
            this.value = new BigDecimal(value);
        }

        @Override
        public int intValue() {
            return value.intValue();
        }

        @Override
        public long longValue() {
            return value.longValue();
        }

        @Override
        public float floatValue() {
            return value.floatValue();
        }

        @Override
        public double doubleValue() {
            return value.doubleValue();
        }

        @Override
        public String toString() {
            return value.toString();
        }
    }
}
