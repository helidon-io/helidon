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
import java.util.List;
import java.util.Map;

import io.helidon.common.mapper.Mappers;
import io.helidon.dbclient.DbColumn;
import io.helidon.dbclient.DbColumnBase;
import io.helidon.dbclient.DbMapperManager;
import io.helidon.dbclient.DbRow;
import io.helidon.dbclient.DbRowBase;
import io.helidon.json.JsonArray;
import io.helidon.json.JsonObject;
import io.helidon.json.JsonString;
import io.helidon.service.registry.Services;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

/**
 * Tests {@link JsonMapper}.
 */
class JsonMapperTest {

    private static final DbMapperManager DB_MAPPER_MANAGER = DbMapperManager.builder()
            .addMapperProvider(new JsonMapperProvider())
            .build();

    @Test
    void testRowAsJsonObject() {
        DbRow row = row(column("foo", String.class, "bar"),
                        column("count", Integer.class, 42),
                        column("enabled", Boolean.class, true));

        JsonObject jsonObject = row.as(JsonObject.class);

        assertThat(jsonObject.stringValue("foo").orElseThrow(), is("bar"));
        assertThat(jsonObject.numberValue("count").orElseThrow(), is(new BigDecimal("42")));
        assertThat(jsonObject.booleanValue("enabled").orElseThrow(), is(true));
    }

    @Test
    void testToNamedParameters() {
        JsonObject jsonObject = JsonObject.builder()
                .set("name", "Ada")
                .set("count", new BigDecimal("42.5"))
                .set("enabled", true)
                .setNull("missing")
                .set("nested", nested -> nested.set("value", "inside"))
                .setValues("items", List.of(JsonString.create("first"), JsonString.create("second")))
                .build();

        Map<String, Object> parameters = JsonMapper.create().toNamedParameters(jsonObject);

        assertThat(parameters.get("name"), is("Ada"));
        assertThat(parameters.get("count"), is(new BigDecimal("42.5")));
        assertThat(parameters.get("enabled"), is(true));
        assertThat(parameters.get("missing"), nullValue());
        assertThat(parameters.get("nested"), instanceOf(JsonObject.class));
        assertThat(parameters.get("items"), instanceOf(JsonArray.class));
    }

    @Test
    void testToIndexedParameters() {
        JsonObject jsonObject = JsonObject.builder()
                .set("first", "alpha")
                .set("second", 7)
                .set("third", false)
                .build();

        List<Object> parameters = JsonMapper.create().toIndexedParameters(jsonObject);

        assertThat(parameters, contains("alpha", new BigDecimal("7"), false));
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
        return new DbColumnBase(value, Services.get(Mappers.class)) {

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
