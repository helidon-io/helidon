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
import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import io.helidon.common.mapper.MapperManager;
import io.helidon.dbclient.DbColumn;
import io.helidon.dbclient.DbColumnBase;
import io.helidon.dbclient.DbMapperManager;
import io.helidon.dbclient.DbRow;
import io.helidon.dbclient.DbRowBase;
import io.helidon.json.JsonArray;
import io.helidon.json.JsonObject;
import io.helidon.json.JsonString;
import io.helidon.json.JsonValueType;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * Tests {@link io.helidon.dbclient.json.JsonMapper}.
 */
class JsonMapperTest {

    private static final DbMapperManager DB_MAPPER_MANAGER = DbMapperManager.create();

    @Test
    void testRowAsJsonObject() {
        DbRow row = row(column("name", String.class, "Helidon"),
                        column("age", Integer.class, 7),
                        column("active", Boolean.class, true),
                        column("missing", Object.class, null));

        JsonObject jsonObject = row.as(JsonObject.class);

        assertAll("JSON object values",
                  () -> assertThat(jsonObject.stringValue("name").orElseThrow(), is("Helidon")),
                  () -> assertThat(jsonObject.numberValue("age").orElseThrow(), is(new BigDecimal("7"))),
                  () -> assertThat(jsonObject.booleanValue("active").orElseThrow(), is(true)),
                  () -> assertThat(jsonObject.value("missing").orElseThrow().type(), is(JsonValueType.NULL)));
    }

    @Test
    void testRowAsJsonObjectAdditionalValues() {
        JsonString jsonValue = JsonString.create("json");
        DbRow row = row(column("json", JsonString.class, jsonValue),
                        column("decimal", BigDecimal.class, new BigDecimal("123.45")),
                        column("integer", BigInteger.class, new BigInteger("12345678901234567890")),
                        column("double", Double.class, 1.25),
                        column("other", Object.class, new StringBuilder("fallback")));

        JsonObject jsonObject = row.as(JsonObject.class);

        assertAll("JSON object values",
                  () -> assertThat(jsonObject.value("json").orElseThrow(), is(jsonValue)),
                  () -> assertThat(jsonObject.numberValue("decimal").orElseThrow(), is(new BigDecimal("123.45"))),
                  () -> assertThat(jsonObject.numberValue("integer").orElseThrow(),
                                   is(new BigDecimal("12345678901234567890"))),
                  () -> assertThat(jsonObject.numberValue("double").orElseThrow(), is(new BigDecimal("1.25"))),
                  () -> assertThat(jsonObject.stringValue("other").orElseThrow(), is("fallback")));
    }

    @Test
    void testToNamedParameters() {
        JsonObject nested = JsonObject.builder()
                .set("value", "nested")
                .build();
        JsonObject jsonObject = JsonObject.builder()
                .set("name", "Helidon")
                .set("age", 7)
                .set("active", true)
                .setNull("missing")
                .set("nested", nested)
                .setValues("items", List.of(JsonString.create("one"), JsonString.create("two")))
                .build();

        Map<String, ?> parameters = DB_MAPPER_MANAGER.toNamedParameters(jsonObject, JsonObject.class);

        assertAll("named parameters",
                  () -> assertThat(parameters.get("name"), is("Helidon")),
                  () -> assertThat(parameters.get("age"), is(new BigDecimal("7"))),
                  () -> assertThat(parameters.get("active"), is(true)),
                  () -> assertThat(parameters.containsKey("missing"), is(true)),
                  () -> assertThat(parameters.get("missing"), is((Object) null)),
                  () -> assertThat(parameters.get("nested"), is(nested)),
                  () -> assertThat(parameters.get("items"), instanceOf(JsonArray.class)));
    }

    @Test
    void testToIndexedParametersKeepsObjectOrder() {
        JsonObject jsonObject = JsonObject.builder()
                .set("first", "one")
                .set("second", 2)
                .set("third", true)
                .build();

        List<?> parameters = DB_MAPPER_MANAGER.toIndexedParameters(jsonObject, JsonObject.class);

        assertThat(parameters, contains("one", new BigDecimal("2"), true));
    }

    private static DbRow row(DbColumnBase... columns) {
        return new DbRowBase(columns, DB_MAPPER_MANAGER) {
            @Override
            public DbColumn column(String name) {
                return super.column(name);
            }
        };
    }

    private static DbColumnBase column(String name, Class<?> javaType, Object value) {
        return new DbColumnBase(value, MapperManager.create()) {
            @Override
            public Class<?> javaType() {
                return javaType;
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
}
