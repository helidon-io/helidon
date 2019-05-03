/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.db.mongodb;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import javax.json.Json;
import javax.json.JsonReaderFactory;

import io.helidon.common.mapper.MapperManager;
import io.helidon.db.DbMapperManager;
import io.helidon.db.StatementType;
import io.helidon.db.common.AbstractStatement;
import io.helidon.db.common.InterceptorSupport;

import com.mongodb.reactivestreams.client.MongoDatabase;
import org.bson.Document;

/**
 * Common MongoDB statement builder.
 *
 * @param <S> MongoDB statement type
 * @param <R> Statement execution result type
 */
public abstract class MongoDbStatement<S extends MongoDbStatement<S, R>, R> extends AbstractStatement<S, R> {

    /**
     * Empty JSON object.
     */
    protected static final Document EMPTY = Document.parse(Json.createObjectBuilder().build().toString());

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
     * JSON reader factory.
     */
    protected static final JsonReaderFactory READER_FACTORY = Json.createReaderFactory(Collections.emptyMap());

    private final MongoDatabase db;

    MongoDbStatement(StatementType statementType,
                     MongoDatabase db,
                     String statementName,
                     String statement,
                     DbMapperManager dbMapperManager,
                     MapperManager mapperManager,
                     InterceptorSupport interceptors) {

        super(statementType,
              statementName,
              statement,
              dbMapperManager,
              mapperManager,
              interceptors);

        this.db = db;
    }

    String build() {
        switch (paramType()) {
        // Statement shall not contain any parameters, no conversion is needed.
        case UNKNOWN:
            return statement();
        case INDEXED:
            return new JdbcParser(statement(), indexedParams()).convert();
        // Replace parameter identifiers with values from name to value map
        case NAMED:
            return new MappingParser(statement(), namedParams()).convert();
        default:
            throw new IllegalStateException("Unknown SQL statement type: " + paramType());
        }
    }

    /**
     * Statement name.
     *
     * @return name of this statement (never null, may be generated)
     */
    public String statementName() {
        return super.statementName();
    }

    MongoDatabase db() {
        return db;
    }

    @Override
    protected String dbType() {
        return MongoDbProvider.DB_TYPE;
    }

    /**
     * Mongo operation enumeration.
     */
    enum MongoOperation {
        QUERY("query", "find", "select"),
        INSERT("insert"),
        UPDATE("update"),
        DELETE("delete");

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

    static class MongoStatement {
        private final String preparedStmt;

        private static Document/*JsonObject*/ readStmt(JsonReaderFactory jrf, String preparedStmt) {
            return Document.parse(preparedStmt);
        }

        private final Document/*JsonObject*/ jsonStmt;
        private final MongoOperation operation;
        private final String collection;
        private final Document query;
        private final Document value;

        MongoStatement(StatementType statementType, JsonReaderFactory jrf, String preparedStmt) {
            this.preparedStmt = preparedStmt;
            this.jsonStmt = readStmt(jrf, preparedStmt);

            MongoOperation operation;
            if (jsonStmt.containsKey(JSON_OPERATION)) {
                operation = MongoOperation.operationByName(jsonStmt.getString(JSON_OPERATION));
                // make sure we have alignment between statement type and operation
                switch (statementType) {
                case QUERY:
                case GET:
                    validateOperation(statementType, operation, MongoOperation.QUERY);
                    break;
                case INSERT:
                    validateOperation(statementType, operation, MongoOperation.INSERT);
                    break;
                case UPDATE:
                    validateOperation(statementType, operation, MongoOperation.UPDATE);
                    break;
                case DELETE:
                    validateOperation(statementType, operation, MongoOperation.DELETE);
                    break;
                case DML:
                    validateOperation(statementType, operation, MongoOperation.INSERT,
                                      MongoOperation.UPDATE, MongoOperation.DELETE);
                    break;
                case UNKNOWN:
                    // any operation is OK for this type
                    break;
                }
            } else {
                switch (statementType) {
                case QUERY:
                    operation = MongoOperation.QUERY;
                    break;
                case GET:
                    operation = MongoOperation.QUERY;
                    break;
                case INSERT:
                    operation = MongoOperation.INSERT;
                    break;
                case UPDATE:
                    operation = MongoOperation.UPDATE;
                    break;
                case DELETE:
                    operation = MongoOperation.DELETE;
                    break;
                case DML:
                case UNKNOWN:
                default:
                    throw new IllegalStateException(
                            "Operation type is not defined in statement, and cannot be inferred from statement type: "
                                    + statementType);
                }
            }
            this.operation = operation;
            this.collection = jsonStmt.getString(JSON_COLLECTION);
            this.value = jsonStmt.get(JSON_VALUE, Document.class);
            this.query = jsonStmt.get(JSON_QUERY, Document.class);
        }

        private static void validateOperation(StatementType statementType,
                                              MongoOperation actual,
                                              MongoOperation... expected) {

            for (MongoOperation operation : expected) {
                if (actual == operation) {
                    return;
                }
            }

            throw new IllegalStateException("Statement type is "
                                                    + statementType
                                                    + ", yet operation in statement is: "
                                                    + actual);
        }

        public Document/*JsonObject*/ getJsonStmt() {
            return jsonStmt;
        }

        public MongoOperation getOperation() {
            return operation;
        }

        public String getCollection() {
            return collection;
        }

        public Document getQuery() {
            return query;
        }

        public Document getValue() {
            return value;
        }

        @Override
        public String toString() {
            return preparedStmt;
        }
    }

    abstract static class Parser {
        /**
         * Parser ACTION to be performed.
         */
        @FunctionalInterface
        interface Action {
            void process(Parser parser);
        }

        /**
         * States used in state machine.
         */
        enum State {
            STMT, // Common statement
            STR,  // SQL string
            COL,  // After colon, expecting parameter name
            PAR  // Processing parameter name
        }

        /**
         * Do nothing.
         *
         * @param parser parser instance
         */
        static void noop(Parser parser) {
        }

        /**
         * Copy character from input string to output as is.
         *
         * @param parser parser instance
         */
        static void copy(Parser parser) {
            parser.sb.append(parser.c);
        }

        // Should be done much better!
        static String toJson(Object value) {
            if (value == null) {
                return "null";
            }
            if (value instanceof Number) {
                if (value instanceof Integer) {
                    return Json.createValue((Integer) value).toString();
                }
                if (value instanceof Long) {
                    return Json.createValue((Long) value).toString();
                }
                if (value instanceof Double) {
                    return Json.createValue((Double) value).toString();
                }
                if (value instanceof BigInteger) {
                    return Json.createValue((BigInteger) value).toString();
                }
                if (value instanceof BigDecimal) {
                    return Json.createValue((BigDecimal) value).toString();
                }
            }
            if (value instanceof String) {
                return Json.createValue((String) value).toString();
            }
            if (value instanceof Boolean) {
                return ((Boolean) value) ? "true" : "false";
            }
            return '"' + value.toString() + '"';
        }

        /**
         * SQL statement to be parsed.
         */
        final String statement;
        /**
         * Target SQL statement builder.
         */
        final StringBuilder sb;
        /**
         * Temporary string storage.
         */
        final StringBuilder nap;

        /**
         * Character being currently processed.
         */
        char c;

        private Parser(String statement) {
            this.sb = new StringBuilder(statement.length());
            this.nap = new StringBuilder(32);
            this.statement = statement;
            this.c = '\0';
        }

    }

    // Slightly modified copy-paste from JDBC statement parser
    // Replaces "$name" named parameters with value from mappings if exists

    /**
     * Mapping parser state machine.
     */
    private static final class MappingParser extends Parser {

        /**
         * First character of named parameter identifier.
         */
        private static final char PAR_BEG = '$';

        /**
         * Character classes used in state machine.
         */
        private enum CharClass {
            LT,  // Letter
            NUM, // Number
            DQ,  // Double quote, begins/ends string in JSON
            DOL, // Dollar sign, begins named parameter
            OTH; // Other character// Other character

            // This is far from optimal code, but direct translation table would be too large for Java char
            private static CharClass charClass(char c) {
                return Character.isLetter(c) ? LT :
                        Character.isDigit(c) ? NUM :
                                c == '"' ? DQ :
                                        c == PAR_BEG ? DOL : OTH;
            }

        }

        /**
         * States TRANSITION table.
         */
        private static final State[][] TRANSITION = {
                // LT          NUM         DQ          DOL        OTH
                {State.STMT, State.STMT, State.STR, State.COL, State.STMT}, // Transition from STMT
                {State.STR, State.STR, State.STMT, State.STR, State.STR}, // Transition from STR
                {State.PAR, State.PAR, State.STR, State.COL, State.STMT}, // Transition from COL
                {State.PAR, State.PAR, State.STMT, State.COL, State.STMT}  // Transition from PAR
        };

        static final Action COPY_ACTION = Parser::copy;
        static final Action FNAP_ACTION = MappingParser::fnap;
        static final Action NNAP_ACTION = MappingParser::nnap;
        static final Action NOOP_ACTION = Parser::noop;
        static final Action DOCH_ACTION = MappingParser::doch;
        static final Action CPDO_ACTION = MappingParser::cpdo;
        static final Action ENAP_ACTION = MappingParser::enap;

        /**
         * States TRANSITION ACTION table.
         */
        private static final Action[][] ACTION = {
                // LT         NUM          DQ           DOL          OTH
                {COPY_ACTION, COPY_ACTION, COPY_ACTION, NOOP_ACTION, COPY_ACTION}, // STMT
                {COPY_ACTION, COPY_ACTION, COPY_ACTION, COPY_ACTION, COPY_ACTION}, // STR
                {FNAP_ACTION, FNAP_ACTION, DOCH_ACTION, CPDO_ACTION, DOCH_ACTION}, // COL
                {NNAP_ACTION, NNAP_ACTION, ENAP_ACTION, ENAP_ACTION, ENAP_ACTION}  // PAR
        };

        /**
         * Copy previous colon character to output.
         *
         * @param parser parser instance
         */
        private static void cpdo(Parser parser) {
            parser.sb.append(PAR_BEG);
        }

        /**
         * Copy previous colon and current input string character to output.
         *
         * @param parser parser instance
         */
        private static void doch(Parser parser) {
            parser.sb.append(PAR_BEG);
            parser.sb.append(parser.c);
        }

        /**
         * Store 1st named parameter letter.
         *
         * @param parser parser instance
         */
        private static void fnap(Parser parser) {
            parser.nap.setLength(0);
            parser.nap.append(parser.c);
        }

        /**
         * Store next named parameter letter.
         *
         * @param parser parser instance
         */
        private static void nnap(Parser parser) {
            parser.nap.append(parser.c);
        }

        /**
         * Finish stored named parameter and copy current character from input string to output as is.
         *
         * @param parser parser instance
         */
        private static void enap(Parser parser) {
            String parName = parser.nap.toString();
            if (((MappingParser) parser).mappings.containsKey(parName)) {
                parser.sb.append(toJson(((MappingParser) parser).mappings.get(parName)));
            } else {
                parser.sb.append(PAR_BEG);
                parser.sb.append(parName);
            }
            parser.sb.append(parser.c);
        }

        private final Map<String, Object> mappings;
        /**
         * Character class of character being currently processed.
         */
        protected CharClass cl;

        private MappingParser(String statement, Map<String, Object> mappings) {
            super(statement);
            this.mappings = mappings;
            this.cl = null;
        }

        private String convert() {
            State state = State.STMT;  // Initial state: common statement processing
            int len = statement.length();
            for (int i = 0; i < len; i++) {
                c = statement.charAt(i);
                cl = CharClass.charClass(c);
                ACTION[state.ordinal()][cl.ordinal()].process(this);
                state = TRANSITION[state.ordinal()][cl.ordinal()];
            }
            // Process end of statement
            if (state == State.PAR) {
                String parName = nap.toString();
                if (mappings.containsKey(parName)) {
                    sb.append(toJson(mappings.get(parName)));
                } else {
                    sb.append(PAR_BEG);
                    sb.append(parName);
                }
            }
            return sb.toString();
        }

    }

    private static final class JdbcParser extends Parser {

        /**
         * First character of named parameter identifier.
         */
        private static final char PAR_LT = '?';

        /**
         * Character classes used in state machine.
         */
        private static enum CharClass {
            LT,  // Letter
            NUM, // Number
            DQ,  // Double quote, begins/ends string in JSON
            QST, // Question mark, parameter
            OTH; // Other character

            // This is far from optimal code, but direct translation table would be too large for Java char
            private static CharClass charClass(char c) {
                return Character.isLetter(c) ? LT :
                        Character.isDigit(c) ? NUM :
                                c == '"' ? DQ :
                                        c == PAR_LT ? QST : OTH;
            }

        }

        /**
         * States TRANSITION table.
         */
        private static final State[][] TRANSITION = {
                // LT                      NUM                    DQ                     QST                   OTH
                {State.STMT, State.STMT, State.STR, State.STMT, State.STMT}, // Transition from STMT
                {State.STR, State.STR, State.STMT, State.STR, State.STR} // Transition from STR
        };

        private static final Action COPY_ACTION = Parser::copy;
        private static final Action VAAP_ACTION = JdbcParser::vaap;
        /**
         * States TRANSITION ACTION table.
         */
        private static final Action[][] ACTION = {
                // LT         NUM          DQ           QST          OTH
                {COPY_ACTION, COPY_ACTION, COPY_ACTION, VAAP_ACTION, COPY_ACTION}, // STMT
                {COPY_ACTION, COPY_ACTION, COPY_ACTION, COPY_ACTION, COPY_ACTION} // STR
        };

        /**
         * Append next parameter from parameters list.
         *
         * @param parser parser instance
         */
        private static void vaap(Parser parser) {
            if (((JdbcParser) parser).parIt.hasNext()) {
                parser.sb.append(toJson(((JdbcParser) parser).parIt.next()));
            } else {
                parser.sb.append(parser.c);
            }
        }

        private final List<Object> parameters;
        private final ListIterator<Object> parIt;
        /**
         * Character class of character being currently processed.
         */
        protected CharClass cl;

        private JdbcParser(String statement, List<Object> parameters) {
            super(statement);
            this.parameters = parameters;
            this.parIt = parameters.listIterator();
            this.cl = null;
        }

        private String convert() {
            State state = State.STMT;  // Initial state: common statement processing
            int len = statement.length();
            for (int i = 0; i < len; i++) {
                c = statement.charAt(i);
                cl = CharClass.charClass(c);
                ACTION[state.ordinal()][cl.ordinal()].process(this);
                state = TRANSITION[state.ordinal()][cl.ordinal()];
            }
            return sb.toString();
        }

    }

}
