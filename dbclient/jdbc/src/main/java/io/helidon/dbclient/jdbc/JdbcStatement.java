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
package io.helidon.dbclient.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;
import java.util.logging.Logger;

import io.helidon.common.mapper.MapperManager;
import io.helidon.dbclient.DbClientException;
import io.helidon.dbclient.DbInterceptorContext;
import io.helidon.dbclient.DbMapperManager;
import io.helidon.dbclient.DbStatementType;
import io.helidon.dbclient.common.AbstractStatement;
import io.helidon.dbclient.common.InterceptorSupport;

/**
 * Common JDBC statement builder.
 *
 * @param <S> subclass of this class
 * @param <R> Statement execution result type
 */
abstract class JdbcStatement<S extends JdbcStatement<S, R>, R> extends AbstractStatement<S, R> {

    private static final Logger LOGGER = Logger.getLogger(JdbcStatement.class.getName());

    private final String dbType;
    private final ConnectionPool connectionPool;
    private final ExecutorService executorService;

    JdbcStatement(
            DbStatementType dbStatementType,
            ConnectionPool connectionPool,
            String statementName,
            String statement,
            DbMapperManager dbMapperManager,
            MapperManager mapperManager,
            ExecutorService executorService,
            InterceptorSupport interceptors) {

        super(dbStatementType,
              statementName,
              statement,
              dbMapperManager,
              mapperManager,
              interceptors);

        this.dbType = connectionPool.dbType();
        this.connectionPool = connectionPool;
        this.executorService = executorService;
    }

    PreparedStatement build(Connection conn, DbInterceptorContext dbContext) {
        LOGGER.fine(() -> String.format("Building SQL statement: %s", dbContext.statement()));
        String statement = dbContext.statement();
        String statementName = dbContext.statementName();

        Supplier<PreparedStatement> simpleStatementSupplier = () -> prepareStatement(conn, statementName, statement);

        if (dbContext.isIndexed()) {
            return dbContext.indexedParameters()
                    .map(params -> prepareIndexedStatement(conn, statementName, statement, params))
                    .orElseGet(simpleStatementSupplier);
        } else {
            return dbContext.namedParameters()
                    .map(params -> prepareNamedStatement(conn, statementName, statement, params))
                    .orElseGet(simpleStatementSupplier);
        }
    }

    /**
     * Switch to {@link #build(java.sql.Connection, io.helidon.dbclient.DbInterceptorContext)} and use interceptors.
     *
     * @param connection connection to use
     * @return prepared statement
     */
    @Deprecated
    protected PreparedStatement build(Connection connection) {
        LOGGER.fine(() -> String.format("Building SQL statement: %s", statement()));
        switch (paramType()) {
        // Statement may not contain any parameters, no conversion is needed.
        case UNKNOWN:
            return prepareStatement(connection, statementName(), statement());
        case INDEXED:
            return prepareIndexedStatement(connection, statementName(), statement(), indexedParams());
        case NAMED:
            return prepareNamedStatement(connection, statementName(), statement(), namedParams());
        default:
            throw new IllegalStateException("Unknown SQL statement type");
        }
    }

    @Override
    protected String dbType() {
        return dbType;
    }

    Connection connection() {
        return connectionPool.connection();
    }

    ExecutorService executorService() {
        return executorService;
    }

    private PreparedStatement prepareStatement(Connection conn, String statementName, String statement) {
        try {
            return conn.prepareStatement(statement);
        } catch (SQLException e) {
            throw new DbClientException("Failed to prepare statement: " + statementName, e);
        }
    }

    private PreparedStatement prepareNamedStatement(Connection connection,
                                                    String statementName,
                                                    String statement,
                                                    Map<String, Object> parameters) {

        try {
            // Parameters names must be replaced with ? and names occurence order must be stored.
            MappingParser parser = new MappingParser(statement);
            String jdbcStatement = parser.convert();
            LOGGER.finest(() -> String.format("Converted statement: %s", jdbcStatement));
            PreparedStatement preparedStatement = connection.prepareStatement(jdbcStatement);
            List<String> namesOrder = parser.namesOrder();
            int i = 1;
            for (String name : namesOrder) {
                Object value = parameters.get(name);
                LOGGER.info(String.format("Mapped parameter %d: %s -> %s", i, name, value));
                preparedStatement.setObject(i, value);
                i++;
            }
            return preparedStatement;
        } catch (SQLException e) {
            throw new DbClientException("Failed to prepare statement with named parameters: " + statementName, e);
        }
    }

    private PreparedStatement prepareIndexedStatement(Connection connection,
                                                      String statementName,
                                                      String statement,
                                                      List<Object> parameters) {

        try {
            PreparedStatement preparedStatement = connection.prepareStatement(statement);
            int i = 1; // JDBC set position parameter starts from 1.
            for (Object value : parameters) {
                LOGGER.info(String.format("Indexed parameter %d: %s", i, value));
                preparedStatement.setObject(i, value);
                // increase value for next iteration
                i++;
            }
            return preparedStatement;
        } catch (SQLException e) {
            throw new DbClientException("Failed to prepare statement with indexed params: " + statementName, e);
        }
    }

    /**
     * Mapping parser state machine.
     *
     * Is it really worth it to write a parser, when we could use a regular expression with a single restriction,
     * that the statement text must not contain :word except for parameters?
     * The implementation is then really simple:
     * <pre>
     *  Pattern pattern = Pattern.compile(":(\\w+)", Pattern.UNICODE_CHARACTER_CLASS);
     *  Matcher matcher = pattern.matcher(select);
     *  List names = new LinkedList<>();
     *  while(matcher.find()) {
     *      names.add(matcher.group(1));
     *  }
     *  matcher.reset();
     *  replaced = matcher.replaceAll("?");
     *  </pre>
     *
     * Before replacement:
     * {@code SELECT * FROM table WHERE name = :name AND type = :type}
     * After replacement:
     * {@code SELECT * FROM table WHERE name = ? AND type = ?}
     * Expected list of parameteres:
     * {@code "name", "type"}
     */
    private static final class MappingParser {

        /**
         * Parser ACTION to be performed.
         */
        @FunctionalInterface
        private interface Action {
            void process(MappingParser parser);
        }

        /**
         * Character classes used in state machine.
         */
        private enum CharClass {
            LT,  // Letter
            NUM, // Number
            SQ,  // Single quote, begins string in SQL
            COL, // Colon, begins named parameter
            OTH;  // Other character

            // This is far from optimal code, but direct translation table would be too large for Java char
            private static CharClass charClass(char c) {
                return Character.isLetter(c) ? LT : (Character.isDigit(c) ? NUM : ((c == '\'') ? SQ : ((c == ':') ? COL : OTH)));
            }

        }

        /**
         * States used in state machine.
         */
        private enum State {
            STMT, // Common statement
            STR,  // SQL string
            COL,  // After colon, expecting parameter name
            PAR,  // Processing parameter name
        }

        /**
         * States TRANSITION table.
         */
        private static final State[][] TRANSITION = {
                // LT          NUM         SQ          COL        OTH
                {State.STMT, State.STMT, State.STR, State.COL, State.STMT}, // Transition from STMT
                {State.STR, State.STR, State.STMT, State.STR, State.STR}, // Transition from STR
                {State.PAR, State.PAR, State.STR, State.COL, State.STMT}, // Transition from COL
                {State.PAR, State.PAR, State.STMT, State.COL, State.STMT}  // Transition from PAR
        };

        /**
         * States TRANSITION ACTION table.
         */
        private static final Action[][] ACTION = {
                // LT                   NUM                  SQ                    COL                  OTH
                {MappingParser::copy, MappingParser::copy, MappingParser::copy, MappingParser::noop, MappingParser::copy}, // STMT
                {MappingParser::copy, MappingParser::copy, MappingParser::copy, MappingParser::copy, MappingParser::copy}, // STR
                {MappingParser::fnap, MappingParser::fnap, MappingParser::clch, MappingParser::cpcl, MappingParser::clch},  // COL
                {MappingParser::nnap, MappingParser::nnap, MappingParser::enap, MappingParser::enap, MappingParser::enap}   // PAR
        };

        /**
         * Do nothing.
         *
         * @param parser parser instance
         */
        private static void noop(MappingParser parser) {
        }

        /**
         * Copy character from input string to output as is.
         *
         * @param parser parser instance
         */
        private static void copy(MappingParser parser) {
            parser.sb.append(parser.c);
        }

        /**
         * Copy previous colon character to output.
         *
         * @param parser parser instance
         */
        private static void cpcl(MappingParser parser) {
            parser.sb.append(':');
        }

        /**
         * Copy previous colon and current input string character to output.
         *
         * @param parser parser instance
         */
        private static void clch(MappingParser parser) {
            parser.sb.append(':');
            parser.sb.append(parser.c);
        }

        /**
         * Store 1st named parameter letter.
         *
         * @param parser parser instance
         */
        private static void fnap(MappingParser parser) {
            parser.nap.setLength(0);
            parser.nap.append(parser.c);
        }

        /**
         * Store next named parameter letter.
         *
         * @param parser parser instance
         */
        private static void nnap(MappingParser parser) {
            parser.nap.append(parser.c);
        }

        /**
         * Finish stored named parameter and copy current character from input string to output as is.
         *
         * @param parser parser instance
         */
        private static void enap(MappingParser parser) {
            String parName = parser.nap.toString();
            parser.names.add(parName);
            parser.sb.append('?');
            parser.sb.append(parser.c);
        }

        /**
         * SQL statement to be parsed.
         */
        private final String statement;
        /**
         * Target SQL statement builder.
         */
        private final StringBuilder sb;
        /**
         * Temporary string storage.
         */
        private final StringBuilder nap;
        /**
         * Ordered list of parameter names.
         */
        private final List<String> names;

        /**
         * Character being currently processed.
         */
        private char c;
        /**
         * Character class of character being currently processed.
         */
        private CharClass cl;

        private MappingParser(String statement) {
            this.sb = new StringBuilder(statement.length());
            this.nap = new StringBuilder(32);
            this.names = new LinkedList<>();
            this.statement = statement;
            this.c = '\0';
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
                names.add(parName);
                sb.append('?');
            }
            return sb.toString();
        }

        private List<String> namesOrder() {
            return names;
        }

    }

}
