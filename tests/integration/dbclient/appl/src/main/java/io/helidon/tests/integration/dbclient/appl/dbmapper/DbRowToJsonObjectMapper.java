/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.dbclient.appl.dbmapper;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import javax.json.Json;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;

import io.helidon.dbclient.DbColumn;
import io.helidon.dbclient.DbMapper;
import io.helidon.dbclient.DbRow;

// This implementation is limited only to conversions required by jUnit tests.
/**
 * Mapper for DbRow to JsonObject conversion.
 */
public class DbRowToJsonObjectMapper implements DbMapper<JsonObject> {

    private static final DbRowToJsonObjectMapper INSTANCE = new DbRowToJsonObjectMapper();

    // Conversion is based on code used in EclipseLink:
    // https://github.com/eclipse-ee4j/eclipselink/blob/master/foundation/org.eclipse.persistence.core/src/main/java/org/eclipse/persistence/internal/helper/ConversionManager.java#L164
    // https://github.com/eclipse-ee4j/eclipselink/blob/master/foundation/org.eclipse.persistence.core/src/main/java/org/eclipse/persistence/internal/databaseaccess/DatabaseAccessor.java#L1347
    private static final Map<Class<?>, BiConsumer<JsonObjectBuilder, DbColumn>> MAPPERS = new HashMap<>();

    public static final SimpleDateFormat DATE_FORMATTER = new SimpleDateFormat("yyyy-MM-dd");
    public static final SimpleDateFormat TIME_FORMATTER = new SimpleDateFormat("HH:mm:ss");
    public static final SimpleDateFormat DATE_TIME_FORMATTER = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");

    static {
        MAPPERS.put(Byte.class, (job, column) -> job.add(column.name(), column.as(Byte.class).intValue()));
        MAPPERS.put(Short.class, (job, column) -> job.add(column.name(), column.as(Short.class).intValue()));
        MAPPERS.put(Integer.class, (job, column) -> job.add(column.name(), column.as(Integer.class).intValue()));
        MAPPERS.put(Long.class, (job, column) -> job.add(column.name(), column.as(Long.class).longValue()));
        MAPPERS.put(Float.class, (job, column) -> job.add(column.name(), column.as(Float.class).doubleValue()));
        MAPPERS.put(Double.class, (job, column) -> job.add(column.name(), column.as(Double.class).doubleValue()));
        MAPPERS.put(BigInteger.class, (job, column) -> job.add(column.name(), column.as(BigInteger.class)));
        MAPPERS.put(BigDecimal.class, (job, column) -> job.add(column.name(), column.as(BigDecimal.class)));
        MAPPERS.put(String.class, (job, column) -> job.add(column.name(), column.as(String.class)));
        MAPPERS.put(Character.class, (job, column) -> job.add(column.name(), column.as(Character.class).toString()));
        MAPPERS.put(Boolean.class, (job, column) -> job.add(column.name(), column.as(Boolean.class)));
        MAPPERS.put(java.sql.Date.class, (job, column) -> job.add(column.name(), DATE_FORMATTER.format(column.as(java.sql.Date.class))));
        MAPPERS.put(java.sql.Time.class, (job, column) -> job.add(column.name(), TIME_FORMATTER.format(column.as(java.sql.Time.class))));
        MAPPERS.put(java.sql.Timestamp.class, (job, column) -> job.add(column.name(), DATE_TIME_FORMATTER.format(column.as(java.sql.Timestamp.class))));
        MAPPERS.put(java.util.Date.class, (job, column) -> job.add(column.name(), DATE_TIME_FORMATTER.format(column.as(java.util.Date.class))));
    }

    static final DbRowToJsonObjectMapper getInstance() {
        return INSTANCE;
    }

    @Override
    public JsonObject read(DbRow dbRow) {
        JsonObjectBuilder job = Json.createObjectBuilder();
        dbRow.forEach((dbColumn) -> {
            BiConsumer<JsonObjectBuilder, DbColumn> mapper = MAPPERS.get(dbColumn.javaType());
            if (mapper != null) {
                mapper.accept(job, dbColumn);
            } else {
                job.add(dbColumn.name(), dbColumn.value().toString());
            }
        });
        return job.build();
    }

    @Override
    public Map<String, ?> toNamedParameters(JsonObject value) {
        Map<String, Object> params = new HashMap<>(value.size());
        for (Map.Entry<String, JsonValue> entry : value.entrySet()) {
            switch(entry.getValue().getValueType()) {
                case STRING:
                    params.put(entry.getKey(), entry.getValue().toString());
                    break;
                case NUMBER:
                    JsonNumber numberValue = (JsonNumber)entry.getValue();
                    if (numberValue.isIntegral()) {
                        params.put(entry.getKey(), numberValue.bigIntegerValue());
                    } else {
                        params.put(entry.getKey(), numberValue.bigDecimalValue());
                    }
                    break;
                case FALSE:
                    params.put(entry.getKey(), Boolean.FALSE);
                    break;
                case TRUE:
                    params.put(entry.getKey(), Boolean.TRUE);
                    break;
                case NULL:
                    params.put(entry.getKey(), null);
                    break;
            }
        }
        return params;
    }

    @Override
    public List<?> toIndexedParameters(JsonObject value) {
        List<Object> params = new ArrayList<>(value.size());
        for (Map.Entry<String, JsonValue> entry : value.entrySet()) {
            switch(entry.getValue().getValueType()) {
                case STRING:
                    params.add(entry.getValue().toString());
                    break;
                case NUMBER:
                    JsonNumber numberValue = (JsonNumber)entry.getValue();
                    if (numberValue.isIntegral()) {
                        params.add(numberValue.bigIntegerValue());
                    } else {
                        params.add(numberValue.bigDecimalValue());
                    }
                    break;
                case FALSE:
                    params.add(Boolean.FALSE);
                    break;
                case TRUE:
                    params.add(Boolean.TRUE);
                    break;
                case NULL:
                    params.add(null);
                    break;
            }
        }
        return params;
    }

}
