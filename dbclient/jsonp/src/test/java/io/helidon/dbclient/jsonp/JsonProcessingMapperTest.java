/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
package io.helidon.dbclient.jsonp;

import io.helidon.common.mapper.MapperManager;
import io.helidon.dbclient.DbColumn;
import io.helidon.dbclient.DbColumnBase;
import io.helidon.dbclient.DbMapperManager;
import io.helidon.dbclient.DbRow;
import io.helidon.dbclient.DbRowBase;

import jakarta.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Tests {@link io.helidon.dbclient.jsonp.JsonProcessingMapper}.
 */
class JsonProcessingMapperTest {

    private static final DbMapperManager DB_MAPPER_MANAGER = DbMapperManager.builder()
            .addMapperProvider(new JsonProcessingMapperProvider())
            .build();

    @Test
    void testRowAsJsonObject() {
        DbRow row = row(column("foo", "bar"), column("bob", "alice"));
        JsonObject jsonObject = row.as(JsonObject.class);
        assertThat(jsonObject.getString("foo"), is("bar"));
    }

    private static DbRow row(DbColumnBase... columns) {
        return new DbRowBase(columns, DB_MAPPER_MANAGER) {
            @Override
            public DbColumn column(String name) {
                return super.column(name);
            }
        };
    }

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
}
