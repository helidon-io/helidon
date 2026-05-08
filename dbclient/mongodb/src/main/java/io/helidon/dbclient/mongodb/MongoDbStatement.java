/*
 * Copyright (c) 2019, 2026 Oracle and/or its affiliates.
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
package io.helidon.dbclient.mongodb;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.helidon.common.GenericType;
import io.helidon.common.mapper.MapperException;
import io.helidon.dbclient.DbClientServiceContext;
import io.helidon.dbclient.DbExecuteContext;
import io.helidon.dbclient.DbIndexedStatementParameters;
import io.helidon.dbclient.DbMapperManager;
import io.helidon.dbclient.DbNamedStatementParameters;
import io.helidon.dbclient.DbStatement;
import io.helidon.dbclient.DbStatementBase;
import io.helidon.dbclient.DbStatementParameters;
import io.helidon.dbclient.DbStatementType;

import com.mongodb.client.MongoDatabase;
import jakarta.json.Json;
import org.bson.Document;

import static io.helidon.dbclient.mongodb.MongoDbStatement.MongoOperation.COMMAND;
import static io.helidon.dbclient.mongodb.MongoDbStatement.MongoOperation.DELETE;
import static io.helidon.dbclient.mongodb.MongoDbStatement.MongoOperation.INSERT;
import static io.helidon.dbclient.mongodb.MongoDbStatement.MongoOperation.QUERY;
import static io.helidon.dbclient.mongodb.MongoDbStatement.MongoOperation.UPDATE;

/**
 * Common MongoDB statement builder.
 *
 * @param <S> type of subclass
 */
abstract class MongoDbStatement<S extends DbStatement<S>> extends DbStatementBase<S> {

    private static final String ERROR_NO_MAPPER_FOUND = "Failed to find DB mapper.";

    /**
     * Empty JSON object.
     */
    static final Document EMPTY = Document.parse(Json.createObjectBuilder().build().toString());

    /**
     * Operation JSON parameter name.
     */
    protected static final String JSON_OPERATION = "operation";

    /**
     * Collection JSON parameter name.
     */
    protected static final String JSON_COLLECTION = "collection";

    /**
     * Query JSON parameter name.
     */
    protected static final String JSON_QUERY = "query";

    /**
     * Value JSON parameter name.
     */
    protected static final String JSON_VALUE = "value";

    /**
     * Projection JSON parameter name: Defines projection to restrict returned fields.
     */
    protected static final String JSON_PROJECTION = "projection";

    private final MongoDatabase db;

    /**
     * Create a new instance.
     *
     * @param db      MongoDb instance
     * @param context context
     */
    MongoDbStatement(MongoDatabase db, DbExecuteContext context) {
        super(context);
        this.db = db;
    }

    /**
     * Get the mongo db instance.
     *
     * @return MongoDatabase
     */
    MongoDatabase db() {
        return db;
    }

    /**
     * Prepare the statement string.
     *
     * @return prepared statement string
     */
    String prepareStatement(DbClientServiceContext serviceContext) {
        String statement = serviceContext.statement();
        DbStatementParameters stmtParams = serviceContext.statementParameters();
        if (stmtParams instanceof DbIndexedStatementParameters indexed) {
            List<Object> params = indexed.parameters().stream()
                    .map(this::normalizeParameter)
                    .toList();
            return StatementParsers.indexedParser(statement, params).convert();
        } else if (stmtParams instanceof DbNamedStatementParameters named) {
            return StatementParsers.namedParser(statement, prepareNamedParameters(named)).convert();
        }
        return statement;
    }

    private Map<String, Object> prepareNamedParameters(DbNamedStatementParameters named) {
        Map<String, Object> params = new LinkedHashMap<>(named.parameters().size());
        Map<String, Object> flattened = new LinkedHashMap<>();
        named.parameters().forEach((key, value) -> {
            NamedParameter namedParameter = normalizeNamedParameter(value);
            params.put(key, namedParameter.value());
            namedParameter.flattenedValues().forEach(flattened::putIfAbsent);
        });
        flattened.forEach(params::putIfAbsent);
        return params;
    }

    private Object normalizeParameter(Object value) {
        if ((value == null) || isScalar(value)) {
            return value;
        }
        if (value instanceof Map<?, ?> valueMap) {
            return normalizeMap(valueMap);
        }
        if (value instanceof Iterable<?> iterable) {
            return normalizeList(iterable);
        }
        if (value.getClass().isArray()) {
            return normalizeArray(value);
        }
        Map<String, Object> mappedValue = mappedValue(value);
        return mappedValue == null ? value : mappedValue;
    }

    private NamedParameter normalizeNamedParameter(Object value) {
        if ((value == null) || isScalar(value)) {
            return NamedParameter.create(value);
        }
        if (value instanceof Map<?, ?> valueMap) {
            return NamedParameter.create(normalizeMap(valueMap));
        }
        if (value instanceof Iterable<?> iterable) {
            return NamedParameter.create(normalizeList(iterable));
        }
        if (value.getClass().isArray()) {
            return NamedParameter.create(normalizeArray(value));
        }
        Map<String, Object> mappedValue = mappedValue(value);
        if (mappedValue == null) {
            return NamedParameter.create(value);
        }
        return new NamedParameter(mappedValue, mappedValue);
    }

    private Map<String, Object> normalizeMap(Map<?, ?> valueMap) {
        Map<String, Object> normalized = new LinkedHashMap<>(valueMap.size());
        valueMap.forEach((key, nestedValue) -> normalized.put(String.valueOf(key), normalizeParameter(nestedValue)));
        return normalized;
    }

    private List<Object> normalizeList(Iterable<?> iterable) {
        List<Object> normalized = new ArrayList<>();
        iterable.forEach(nestedValue -> normalized.add(normalizeParameter(nestedValue)));
        return normalized;
    }

    private List<Object> normalizeArray(Object value) {
        int length = Array.getLength(value);
        List<Object> normalized = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
            normalized.add(normalizeParameter(Array.get(value, i)));
        }
        return normalized;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mappedValue(Object value) {
        Class<Object> valueClass = (Class<Object>) value.getClass();
        try {
            return normalizeMap(context().dbMapperManager().toNamedParameters(value, valueClass));
        } catch (MapperException e) {
            if (isMissingMapper(e, valueClass)) {
                return null;
            }
            throw e;
        }
    }

    private boolean isMissingMapper(MapperException exception, Class<?> valueClass) {
        // DbMapperManager only exposes mapping operations, so we fall back
        // only when the manager reports its specific "no mapper found" error.
        String expectedMessage = "Failed to map "
                + GenericType.create(valueClass).getTypeName()
                + " to "
                + DbMapperManager.TYPE_NAMED_PARAMS.getTypeName()
                + ": "
                + ERROR_NO_MAPPER_FOUND;
        return expectedMessage.equals(exception.getMessage());
    }

    private static boolean isScalar(Object value) {
        return value instanceof CharSequence
                || value instanceof Number
                || value instanceof Boolean
                || value instanceof Character
                || value instanceof Enum<?>
                || value instanceof jakarta.json.JsonValue;
    }

    private record NamedParameter(Object value, Map<String, Object> flattenedValues) {
        private static NamedParameter create(Object value) {
            return new NamedParameter(value, Map.of());
        }
    }

    /**
     * Mongo operation enumeration.
     */
    enum MongoOperation {
        QUERY("query", "find", "select"),
        INSERT("insert"),
        UPDATE("update"),
        DELETE("delete"),
        // Database command not related to a specific collection
        // Only executable using generic statement
        COMMAND("command");

        private static final Map<String, MongoOperation> NAME_TO_OPERATION = new HashMap<>();

        static {
            for (MongoOperation value : MongoOperation.values()) {
                for (String name : value.names) {
                    NAME_TO_OPERATION.put(name.toLowerCase(), value);
                }
            }
        }

        static MongoOperation operationByName(String name) {
            if (name == null) {
                return null;
            }
            return NAME_TO_OPERATION.get(name.toLowerCase());
        }

        private final String[] names;

        MongoOperation(String... names) {
            this.names = names;
        }
    }

    /**
     * MongoDB statement.
     */
    static class MongoStatement {

        private static Document readStmt(String preparedStmt) {
            return Document.parse(preparedStmt);
        }

        private final String preparedStmt;
        private final MongoOperation operation;
        private final String collection;
        private final Document query;
        private final Document value;
        private final Document projection;

        /**
         * Create a new instance.
         *
         * @param stmtType     statement type
         * @param preparedStmt prepared statement
         */
        MongoStatement(DbStatementType stmtType, String preparedStmt) {
            this.preparedStmt = preparedStmt;
            Document jsonStmt = readStmt(preparedStmt);

            MongoOperation operation;
            if (jsonStmt.containsKey(JSON_OPERATION)) {
                operation = MongoOperation.operationByName(jsonStmt.getString(JSON_OPERATION));
                // make sure we have alignment between statement type and operation
                switch (stmtType) {
                    case QUERY, GET -> validateOperation(stmtType, operation, QUERY);
                    case INSERT -> validateOperation(stmtType, operation, INSERT);
                    case UPDATE -> validateOperation(stmtType, operation, UPDATE);
                    case DELETE -> validateOperation(stmtType, operation, DELETE);
                    case DML -> validateOperation(stmtType, operation, INSERT, UPDATE, DELETE);
                    case COMMAND -> validateOperation(stmtType, operation, COMMAND);
                    default -> throw new IllegalStateException(
                            "Operation type is not defined in statement, and cannot be inferred from statement type: "
                                    + stmtType);
                }
            } else {
                operation = switch (stmtType) {
                    case QUERY, GET -> QUERY;
                    case INSERT -> INSERT;
                    case UPDATE -> UPDATE;
                    case DELETE -> DELETE;
                    case COMMAND -> COMMAND;
                    default -> throw new IllegalStateException(
                            "Operation type is not defined in statement, and cannot be inferred from statement type: "
                                    + stmtType);
                };
            }
            this.operation = operation;
            this.collection = jsonStmt.getString(JSON_COLLECTION);
            this.value = jsonStmt.get(JSON_VALUE, Document.class);
            this.query = jsonStmt.get(JSON_QUERY, Document.class);
            this.projection = jsonStmt.get(JSON_PROJECTION, Document.class);
        }

        private static void validateOperation(DbStatementType dbStatementType,
                                              MongoOperation actual,
                                              MongoOperation... expected) {

            // PERF: time complexity of this check is terrible
            for (MongoOperation operation : expected) {
                if (actual == operation) {
                    return;
                }
            }

            throw new IllegalStateException("Statement type is "
                    + dbStatementType
                    + ", yet operation in statement is: "
                    + actual);
        }

        MongoOperation getOperation() {
            return operation;
        }

        String getCollection() {
            return collection;
        }

        Document getQuery() {
            return query != null ? query : EMPTY;
        }

        Document getValue() {
            return value;
        }

        Document getProjection() {
            return projection;
        }

        @Override
        public String toString() {
            return preparedStmt;
        }
    }

}
