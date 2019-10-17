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
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Logger;

import io.helidon.dbclient.DbClientException;
import io.helidon.dbclient.DbInterceptorContext;
import io.helidon.dbclient.DbStatement;
import io.helidon.dbclient.common.AbstractStatement;

/**
 * Common JDBC statement builder.
 *
 * @param <S> subclass of this class
 * @param <R> Statement execution result type
 */
abstract class JdbcStatement<S extends DbStatement<S, R>, R> extends AbstractStatement<S, R> {

    private static final Logger LOGGER = Logger.getLogger(JdbcStatement.class.getName());

    private final ExecutorService executorService;
    private final String dbType;
    private final CompletionStage<Connection> connection;
    private final JdbcExecuteContext executeContext;

    JdbcStatement(JdbcExecuteContext executeContext, JdbcStatementContext statementContext) {
        super(statementContext.statementType(),
              statementContext.statementName(),
              statementContext.statement(),
              executeContext.dbMapperManager(),
              executeContext.mapperManager(),
              executeContext.interceptors());

        this.executeContext = executeContext;
        this.dbType = executeContext.dbType();
        this.connection = executeContext.connection();
        this.executorService = executeContext.executorService();
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

    CompletionStage<Connection> connection() {
        return connection;
    }

    ExecutorService executorService() {
        return executorService;
    }

    JdbcExecuteContext executeContext() {
        return executeContext;
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
            Parser parser = new Parser(statement);
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
     * Before replacement:
     * {@code SELECT * FROM table WHERE name = :name AND type = :type}
     * After replacement:
     * {@code SELECT * FROM table WHERE name = ? AND type = ?}
     * Expected list of parameters:
     * {@code "name", "type"}
     */
    static final class Parser {
                @FunctionalInterface

        private interface Action extends Consumer<Parser> {}

        /**
         * Character classes used in state machine.
         */
        private enum CharClass {
            LT,  // Letter (any unicode letter)
            NUM, // Number (any unicode digit)
            LF,  // Line feed / new line (\n), terminates line alone or in CR LF sequence
            CR,  // Carriage return (\r), terminates line in CR LF sequence
            SQ,  // Single quote ('), begins string in SQL
            ST,  // Star (*), part of multiline comment beginning "/*" and ending "*/" sequence
            DA,  // Dash (-), part of single line comment beginning sequence "--"
            SL,  // Slash (/), part of multiline comment beginning "/*" and ending "*/" sequence
            CL,  // Colon (:), begins named parameter
            OTH; // Other characters

            /**
             * Returns character class corresponding to provided character.
             *
             * @param c character to determine its character class
             * @return character class corresponding to provided character
             */
            private static CharClass charClass(char c) {
                switch(c) {
                    case '\r': return CR;
                    case '\n': return LF;
                    case '\'': return SQ;
                    case '*': return ST;
                    case '-': return DA;
                    case '/': return SL;
                    case ':': return CL;
                    default:
                        return Character.isLetter(c)
                                ? LT
                                : (Character.isDigit(c) ? NUM : OTH);
                }
            }

        }

        /**
         * States used in state machine.
         */
        private enum State {
            STMT, // Common statement processing
            STR,  // SQL string processing after 1st SQ was recieved
            COL,  // Symbolic name processing after opening CL (colon) was recieved
            PAR,  // Symbolic name processing after 1st LT or later LT or NUM of parameter name was recieved
            MCB,  // Multiline comment processing after opening slash was recieved from the "/*" sequence
            MCE,  // Multiline comment processing after closing star was recieved from the "*/" sequence
            MLC,  // Multiline comment processing of the comment itself
            SCB,  // Single line comment processing after opening dash was recieved from the "--" sequence
            SCE,  // Single line comment processing after closing CR was recieved from the CR LF sequence
            SLC;  // Single line comment processing of the comment itself

            /** States transition table. */
            private static final State[][] TRANSITION = {
            //   LT    NUM   LF    CR    SQ    ST     DA    SL    CL    OTH
                {STMT, STMT, STMT, STMT,  STR, STMT,  SCB,  MCB,  COL, STMT}, // Transitions from STMT state
                { STR,  STR,  STR,  STR, STMT,  STR,  STR,  STR,  STR,  STR}, // Transitions from STR state
                { PAR, STMT, STMT, STMT,  STR, STMT,  SCB,  MCB,  COL, STMT}, // Transitions from COL state
                { PAR,  PAR, STMT, STMT,  STR, STMT,  SCB,  MCB,  COL, STMT}, // Transitions from PAR state
                {STMT, STMT, STMT, STMT,  STR,  MLC,  SCB,  MCB,  COL, STMT}, // Transitions from MCB state
                { MLC,  MLC,  MLC,  MLC,  MLC,  MCE,  MLC, STMT,  MLC, MLC}, // Transitions from MCE state
                { MLC,  MLC,  MLC,  MLC,  MLC,  MCE,  MLC,  MLC,  MLC, MLC}, // Transitions from MLC state
                {STMT, STMT, STMT, STMT,  STR, STMT,  SLC,  MCB,  COL, STMT}, // Transitions from SCB state
                { SLC,  SLC, STMT,  SCE,  SLC,  SLC,  SLC,  SLC,  SLC,  SLC}, // Transitions from SCE state
                { SLC,  SLC, STMT,  SCE,  SLC,  SLC,  SLC,  SLC,  SLC,  SLC} // Transitions from SLC state
            };
        }

        /**
         * State automaton action table.
         */
        private static final Action[][] ACTION = {
            //     LT                 NUM                  LF                  CR                  SQ                 ST                  DA                   SL                 CL                  OTH
                {  Parser::copyChar,  Parser::copyChar,   Parser::copyChar,   Parser::copyChar,   Parser::copyChar,  Parser::copyChar,    Parser::copyChar,    Parser::copyChar, Parser::doNothing,  Parser::copyChar},  // STMT actions
                {  Parser::copyChar,  Parser::copyChar,   Parser::copyChar,   Parser::copyChar,   Parser::copyChar,  Parser::copyChar,    Parser::copyChar,    Parser::copyChar,  Parser::copyChar,  Parser::copyChar},  // STR actions
                {   Parser::firstLt, Parser::clAndChar,  Parser::clAndChar,  Parser::clAndChar,  Parser::clAndChar,  Parser::clAndChar,  Parser::clAndChar,    Parser::clAndChar, Parser::addColon,  Parser::clAndChar},  // COL actions
                { Parser::nextLtNum, Parser::nextLtNum, Parser::endParChar, Parser::endParChar, Parser::endParChar, Parser::endParChar,  Parser::endParChar,  Parser::endParChar,   Parser::endPar,  Parser::endParChar}, // PAR actions
                {  Parser::copyChar,  Parser::copyChar,   Parser::copyChar,   Parser::copyChar,   Parser::copyChar,  Parser::copyChar,    Parser::copyChar,    Parser::copyChar,  Parser::copyChar,  Parser::copyChar},  // MCB actions
                {  Parser::copyChar,  Parser::copyChar,   Parser::copyChar,   Parser::copyChar,   Parser::copyChar,  Parser::copyChar,    Parser::copyChar,    Parser::copyChar,  Parser::copyChar,  Parser::copyChar},  // MCE actions
                {  Parser::copyChar,  Parser::copyChar,   Parser::copyChar,   Parser::copyChar,   Parser::copyChar,  Parser::copyChar,    Parser::copyChar,    Parser::copyChar,  Parser::copyChar,  Parser::copyChar},  // MLC actions
                {  Parser::copyChar,  Parser::copyChar,   Parser::copyChar,   Parser::copyChar,   Parser::copyChar,  Parser::copyChar,    Parser::copyChar,    Parser::copyChar,  Parser::copyChar,  Parser::copyChar},  // SCB actions
                {  Parser::copyChar,  Parser::copyChar,   Parser::copyChar,   Parser::copyChar,   Parser::copyChar,  Parser::copyChar,    Parser::copyChar,    Parser::copyChar,  Parser::copyChar,  Parser::copyChar},  // SCE actions
                {  Parser::copyChar,  Parser::copyChar,   Parser::copyChar,   Parser::copyChar,   Parser::copyChar,  Parser::copyChar,    Parser::copyChar,    Parser::copyChar,  Parser::copyChar,  Parser::copyChar},  // SLC actions
        };

        /**
         * Do nothing.
         *
         * @param parser parser instance
         */
        private static void doNothing(Parser parser) {
        }

        /**
         * Copy character from input string to output as is.
         *
         * @param parser parser instance
         */
        private static void copyChar(Parser parser) {
            parser.sb.append(parser.c);
        }

        /**
         * Add previous colon character to output.
         *
         * @param parser parser instance
         */
        private static void addColon(Parser parser) {
            parser.sb.append(':');
        }

        /**
         * Copy previous colon and current input string character to output.
         *
         * @param parser parser instance
         */
        private static void clAndChar(Parser parser) {
            parser.sb.append(':');
            parser.sb.append(parser.c);
        }

        /**
         * Store 1st named parameter letter.
         *
         * @param parser parser instance
         */
        private static void firstLt(Parser parser) {
            parser.nap.setLength(0);
            parser.nap.append(parser.c);
        }

        /**
         * Store next named parameter letter or number.
         *
         * @param parser parser instance
         */
        private static void nextLtNum(Parser parser) {
            parser.nap.append(parser.c);
        }

        /**
         * Finish stored named parameter and copy current character from input string to output as is.
         *
         * @param parser parser instance
         */
        private static void endParChar(Parser parser) {
            String parName = parser.nap.toString();
            parser.names.add(parName);
            parser.sb.append('?');
            parser.sb.append(parser.c);
        }

        /**
         * Finish stored named parameter without copying current character from input string to output as is.
         *
         * @param parser parser instance
         */
        private static void endPar(Parser parser) {
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

        private final List<String>names;

        /**
         * Character being currently processed.
         */
        private char c;

        /**
         * Character class of character being currently processed.
         */
        private CharClass cl;

        Parser(String statement) {
            this.sb = new StringBuilder(statement.length());
            this.nap = new StringBuilder(32);
            this.names = new LinkedList<>();
            this.statement = statement;
            this.c = '\0';
            this.cl = null;

        }

        String convert() {
            State state = State.STMT;  // Initial state: common statement processing
            int len = statement.length();
            for (int i = 0; i < len; i++) {
                c = statement.charAt(i);
                cl = CharClass.charClass(c);
                ACTION[state.ordinal()][cl.ordinal()].accept(this);
                state = State.TRANSITION[state.ordinal()][cl.ordinal()];
            }
            // Process end of statement
            if (state == State.PAR) {
                String parName = nap.toString();
                names.add(parName);
                sb.append('?');
            }
            return sb.toString();
        }

        List<String> namesOrder() {
            return names;
        }

    }

}
