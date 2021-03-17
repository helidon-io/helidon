/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates.
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
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.dbclient.DbClientException;
import io.helidon.dbclient.DbClientServiceContext;
import io.helidon.dbclient.DbStatement;
import io.helidon.dbclient.common.AbstractStatement;
import io.helidon.dbclient.common.DbStatementContext;

/**
 * Common JDBC statement builder.
 *
 * @param <S> subclass of this class
 * @param <R> Statement execution result type
 */
abstract class JdbcStatement<S extends DbStatement<S, R>, R> extends AbstractStatement<S, R> {

    /** Local logger instance. */
    private static final Logger LOGGER = Logger.getLogger(JdbcStatement.class.getName());

    private final ExecutorService executorService;
    private final String dbType;
    private final CompletionStage<Connection> connection;
    private final JdbcExecuteContext executeContext;

    JdbcStatement(JdbcExecuteContext executeContext, DbStatementContext statementContext) {
        super(statementContext);

        this.executeContext = executeContext;
        this.dbType = executeContext.dbType();
        this.connection = executeContext.connection();
        this.executorService = executeContext.executorService();
    }

    PreparedStatement build(Connection conn, DbClientServiceContext dbContext) {
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
            throw new DbClientException(String.format("Failed to prepare statement: %s", statementName), e);
        }
    }

    private PreparedStatement prepareNamedStatement(Connection connection,
                                                    String statementName,
                                                    String statement,
                                                    Map<String, Object> parameters) {

        PreparedStatement preparedStatement = null;
        try {
            // Parameters names must be replaced with ? and names occurence order must be stored.
            Parser parser = new Parser(statement);
            String jdbcStatement = parser.convert();
            LOGGER.finest(() -> String.format("Converted statement: %s", jdbcStatement));
            preparedStatement = connection.prepareStatement(jdbcStatement);
            List<String> namesOrder = parser.namesOrder();
            // Set parameters into prepared statement
            int i = 1;
            for (String name : namesOrder) {
                if (parameters.containsKey(name)) {
                    Object value = parameters.get(name);
                    LOGGER.finest(String.format("Mapped parameter %d: %s -> %s", i, name, value));
                    preparedStatement.setObject(i, value);
                    i++;
                } else {
                    throw new DbClientException(namedStatementErrorMessage(namesOrder, parameters));
                }
            }
            return preparedStatement;
        } catch (SQLException e) {
            closePreparedStatement(preparedStatement);
            throw new DbClientException("Failed to prepare statement with named parameters: " + statementName, e);
        }
    }

    private PreparedStatement prepareIndexedStatement(Connection connection,
                                                      String statementName,
                                                      String statement,
                                                      List<Object> parameters) {

        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = connection.prepareStatement(statement);
            int i = 1; // JDBC set position parameter starts from 1.
            for (Object value : parameters) {
                LOGGER.finest(String.format("Indexed parameter %d: %s", i, value));
                preparedStatement.setObject(i, value);
                // increase value for next iteration
                i++;
            }
            return preparedStatement;
        } catch (SQLException e) {
            closePreparedStatement(preparedStatement);
            throw new DbClientException(String.format("Failed to prepare statement with indexed params: %s", statementName), e);
        }
    }

    private void closePreparedStatement(final PreparedStatement preparedStatement) {
        if (preparedStatement != null) {
            try {
                preparedStatement.close();
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, String.format("Could not close PreparedStatement: %s", e.getMessage()), e);
            }
        }
    }

    private static String namedStatementErrorMessage(final List<String> namesOrder, final Map<String, Object> parameters) {
        // Parameters in query missing in parameters Map
        List<String> notInParams = new ArrayList<>(namesOrder.size());
        for (String name : namesOrder) {
            if (!parameters.containsKey(name)) {
                notInParams.add(name);
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Query parameters missing in Map: ");
        boolean first = true;
        for (String name : notInParams) {
            if (first) {
                first = false;
            } else {
                sb.append(", ");
            }
            sb.append(name);
        }
        return sb.toString();
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
            LETTER,       // Letter (any unicode letter)
            NUMBER,       // Number (any unicode digit)
            LF,           // Line feed / new line (\n), terminates line alone or in CR LF sequence
            CR,           // Carriage return (\r), terminates line in CR LF sequence
            APOSTROPHE,   // Single quote ('), begins string in SQL
            STAR,         // Star (*), part of multiline comment beginning "/*" and ending "*/" sequence
            DASH,         // Dash (-), part of single line comment beginning sequence "--"
            SLASH,        // Slash (/), part of multiline comment beginning "/*" and ending "*/" sequence
            COLON,        // Colon (:), begins named parameter
            OTHER;        // Other characters

            /**
             * Returns character class corresponding to provided character.
             *
             * @param c character to determine its character class
             * @return character class corresponding to provided character
             */
            private static CharClass charClass(char c) {
                switch (c) {
                    case '\r': return CR;
                    case '\n': return LF;
                    case '\'': return APOSTROPHE;
                    case '*': return STAR;
                    case '-': return DASH;
                    case '/': return SLASH;
                    case ':': return COLON;
                    default:
                        return Character.isLetter(c)
                                ? LETTER
                                : (Character.isDigit(c) ? NUMBER : OTHER);
                }
            }

        }

        /**
         * States used in state machine.
         */
        private enum State {
            STATEMENT,            // Common statement processing
            STRING,               // SQL string processing after 1st APOSTROPHE was recieved
            COLON,                // Symbolic name processing after opening COLON (colon) was recieved
            PARAMETER,            // Symbolic name processing after 1st LETTER or later LETTER
                                  // or NUMBER of parameter name was recieved
            MULTILN_COMMENT_BG,   // Multiline comment processing after opening slash was recieved from the "/*" sequence
            MULTILN_COMMENT_END,  // Multiline comment processing after closing star was recieved from the "*/" sequence
            MULTILN_COMMENT,      // Multiline comment processing of the comment itself
            SINGLELN_COMMENT_BG,  // Single line comment processing after opening dash was recieved from the "--" sequence
            SINGLELN_COMMENT_END, // Single line comment processing after closing CR was recieved from the CR LF sequence
            SINGLELN_COMMENT;     // Single line comment processing of the comment itself

            /** States transition table. */
            private static final State[][] TRANSITION = {
                // Transitions from STATEMENT state
                {
                    STATEMENT,           // LETTER: regular part of the statement, keep processing it
                    STATEMENT,           // NUMBER: regular part of the statement, keep processing it
                    STATEMENT,           // LF: regular part of the statement, keep processing it
                    STATEMENT,           // CR: regular part of the statement, keep processing it
                    STRING,              // APOSTROPHE: beginning of SQL string processing, switch to STRING state
                    STATEMENT,           // STAR: regular part of the statement, keep processing it
                    SINGLELN_COMMENT_BG, // DASH: possible starting sequence of single line comment,
                                         //       switch to SINGLELN_COMMENT_BG state
                    MULTILN_COMMENT_BG,  // SLASH: possible starting sequence of multi line comment,
                                         //        switch to MULTILN_COMMENT_BG state
                    COLON,               // COLON: possible beginning of named parameter, switch to COLON state
                    STATEMENT            // OTHER: regular part of the statement, keep processing it
                },
                // Transitions from STRING state
                {
                    STRING,              // LETTER: regular part of the SQL string, keep processing it
                    STRING,              // NUMBER: regular part of the SQL string, keep processing it
                    STRING,              // LF: regular part of the SQL string, keep processing it
                    STRING,              // CR: regular part of the SQL string, keep processing it
                    STATEMENT,           // APOSTROPHE: end of SQL string processing, go back to STATEMENT state
                    STRING,              // STAR: regular part of the SQL string, keep processing it
                    STRING,              // DASH: regular part of the SQL string, keep processing it
                    STRING,              // SLASH: regular part of the SQL string, keep processing it
                    STRING,              // COLON: regular part of the SQL string, keep processing it
                    STRING               // OTHER: regular part of the SQL string, keep processing it
                },
                // Transitions from COLON state
                {
                    PARAMETER,           // LETTER: first character of named parameter, switch to PARAMETER state
                    STATEMENT,           // NUMBER: can't be first character of named parameter, go back to STATEMENT state
                    STATEMENT,           // LF: can't be first character of named parameter, go back to STATEMENT state
                    STATEMENT,           // CR: can't be first character of named parameter, go back to STATEMENT state
                    STRING,              // APOSTROPHE: not a named parameter but beginning of SQL string processing,
                                         //             switch to STRING state
                    STATEMENT,           // STAR: can't be first character of named parameter, go back to STATEMENT state
                    SINGLELN_COMMENT_BG, // DASH: not a named parameter but possible starting sequence of single line comment,
                                         //       switch to SINGLELN_COMMENT_BG state
                    MULTILN_COMMENT_BG,  // SLASH: not a named parameter but possible starting sequence of multi line comment,
                                         //        switch to MULTILN_COMMENT_BG state
                    COLON,               // COLON: not a named parameter but possible beginning of another named parameter,
                                         //        retry named parameter processing
                    STATEMENT            // OTHER: can't be first character of named parameter, go back to STATEMENT state
                },
                // Transitions from PARAMETER state
                {
                    PARAMETER,           // LETTER: next character of named parameter, keep processing it
                    PARAMETER,           // NUMBER: next character of named parameter, keep processing it
                    STATEMENT,           // LF: can't be next character of named parameter, go back to STATEMENT state
                    STATEMENT,           // CR: can't be next character of named parameter, go back to STATEMENT state
                    STRING,              // APOSTROPHE: end of named parameter and beginning of SQL string processing,
                                         //             switch to STRING state
                    STATEMENT,           // STAR: can't be next character of named parameter, go back to STATEMENT state
                    SINGLELN_COMMENT_BG, // DASH: end of named parameter and possible starting sequence of single line comment,
                                         //       switch to SINGLELN_COMMENT_BG state
                    MULTILN_COMMENT_BG,  // SLASH: end of named parameter and possible starting sequence of multi line comment,
                                         //        switch to MULTILN_COMMENT_BG state
                    COLON,               // COLON: end of named parameter and possible beginning of another named parameter,
                                         //        switch to COLON state to restart named parameter processing
                    STATEMENT            // OTHER: can't be next character of named parameter, go back to STATEMENT state
                },
                // Transitions from MULTILN_COMMENT_BG state
                {
                    STATEMENT,           // LETTER: not starting sequence of multi line comment, go back to STATEMENT state
                    STATEMENT,           // NUMBER: not starting sequence of multi line comment, go back to STATEMENT state
                    STATEMENT,           // LF: not starting sequence of multi line comment, go back to STATEMENT state
                    STATEMENT,           // CR: not starting sequence of multi line comment, go back to STATEMENT state
                    STRING,              // APOSTROPHE: not starting sequence of multi line comment but beginning of SQL
                                         //             string processing, switch to STRING state
                    MULTILN_COMMENT,     // STAR: end of starting sequence of multi line comment,
                                         //       switch to MULTILN_COMMENT state
                    SINGLELN_COMMENT_BG, // DASH: not starting sequence of multi line comment but possible starting sequence
                                         //       of single line comment, switch to SINGLELN_COMMENT_BG state
                    MULTILN_COMMENT_BG,  // SLASH: not starting sequence of multi line comment but possible starting sequence
                                         //       of next multi line comment, retry multi line comment processing
                    COLON,               // COLON: not starting sequence of multi line comment but possible beginning
                                         //        of named parameter, switch to COLON state
                    STATEMENT            // OTHER: not starting sequence of multi line comment, go back to STATEMENT state
                },
                // Transitions from MULTILN_COMMENT_END state
                {
                    MULTILN_COMMENT,     // LETTER: not ending sequence of multi line comment, go back to MULTILN_COMMENT state
                    MULTILN_COMMENT,     // NUMBER: not ending sequence of multi line comment, go back to MULTILN_COMMENT state
                    MULTILN_COMMENT,     // LF: not ending sequence of multi line comment, go back to MULTILN_COMMENT state
                    MULTILN_COMMENT,     // CR: not ending sequence of multi line comment, go back to MULTILN_COMMENT state
                    MULTILN_COMMENT,     // APOSTROPHE: not ending sequence of multi line comment,
                                         //             go back to MULTILN_COMMENT state
                    MULTILN_COMMENT_END, // STAR: not ending sequence of multi line comment but possible ending sequence
                                         //       of next multi line comment, retry end of multi line comment processing
                    MULTILN_COMMENT,     // DASH: not ending sequence of multi line comment, go back to MULTILN_COMMENT state
                    STATEMENT,           // SLASH: end of ending sequence of multi line comment,
                                         //        switch to STATEMENT state
                    MULTILN_COMMENT,     // COLON: not ending sequence of multi line comment, go back to MULTILN_COMMENT state
                    MULTILN_COMMENT      // OTHER: not ending sequence of multi line comment, go back to MULTILN_COMMENT state
                },
                // Transitions from MULTILN_COMMENT state
                {
                    MULTILN_COMMENT,     // LETTER: regular multi line comment, keep processing it
                    MULTILN_COMMENT,     // NUMBER: regular multi line comment, keep processing it
                    MULTILN_COMMENT,     // LF: regular multi line comment, keep processing it
                    MULTILN_COMMENT,     // CR: regular multi line comment, keep processing it
                    MULTILN_COMMENT,     // APOSTROPHE: regular multi line comment, keep processing it
                    MULTILN_COMMENT_END, // STAR: possible ending sequence of multi line comment,
                                         //       switch to MULTILN_COMMENT_END state
                    MULTILN_COMMENT,     // DASH: regular multi line comment, keep processing it
                    MULTILN_COMMENT,     // SLASH: regular multi line comment, keep processing it
                    MULTILN_COMMENT,     // COLON: regular multi line comment, keep processing it
                    MULTILN_COMMENT      // OTHER: regular multi line comment, keep processing it
                },
                // Transitions from SINGLELN_COMMENT_BG state
                {
                    STATEMENT,           // LETTER: not starting sequence of single line comment, go back to STATEMENT state
                    STATEMENT,           // NUMBER: not starting sequence of single line comment, go back to STATEMENT state
                    STATEMENT,           // LF: not starting sequence of single line comment, go back to STATEMENT state
                    STATEMENT,           // CR: not starting sequence of single line comment, go back to STATEMENT state
                    STRING,              // APOSTROPHE: not starting sequence of single line comment but beginning of SQL
                                         //             string processing, switch to STRING state
                    STATEMENT,           // STAR: not starting sequence of single line comment, go back to STATEMENT state
                    SINGLELN_COMMENT,    // DASH: end of starting sequence of single line comment,
                                         //       switch to SINGLELN_COMMENT state
                    MULTILN_COMMENT_BG,  // SLASH: not starting sequence of single line comment but possible starting sequence
                                         //       of next multi line comment, switch to MULTILN_COMMENT_BG state
                    COLON,               // COLON: not starting sequence of single line comment but possible beginning
                                         //        of named parameter, switch to COLON state
                    STATEMENT            // OTHER: not starting sequence of single line comment, go back to STATEMENT state
                },
                // Transitions from SINGLELN_COMMENT_END state
                {
                    SINGLELN_COMMENT,     // LETTER: not ending sequence of single line comment, go back to SINGLELN_COMMENT state
                    SINGLELN_COMMENT,     // NUMBER: not ending sequence of single line comment, go back to SINGLELN_COMMENT state
                    STATEMENT,            // LF: end of single line comment, switch to STATEMENT state
                    SINGLELN_COMMENT_END, // CR: not ending sequence of single line comment but possible ending sequence
                                          //     of next single line comment, retry end of single line comment processing
                    SINGLELN_COMMENT,     // APOSTROPHE: not ending sequence of single line comment,
                                          //             go back to SINGLELN_COMMENT state
                    SINGLELN_COMMENT,     // STAR: not ending sequence of single line comment, go back to SINGLELN_COMMENT state
                    SINGLELN_COMMENT,     // DASH: not ending sequence of single line comment, go back to SINGLELN_COMMENT state
                    SINGLELN_COMMENT,     // SLASH: not ending sequence of single line comment, go back to SINGLELN_COMMENT state
                    SINGLELN_COMMENT,     // COLON: not ending sequence of single line comment, go back to SINGLELN_COMMENT state
                    SINGLELN_COMMENT      // OTHER: not ending sequence of single line comment, go back to SINGLELN_COMMENT state
                },
                // Transitions from SINGLELN_COMMENT state
                {
                    SINGLELN_COMMENT,     // LETTER: regular single line comment, keep processing it
                    SINGLELN_COMMENT,     // NUMBER: regular single line comment, keep processing it
                    STATEMENT,            // LF: end of single line comment, switch to STATEMENT state
                    SINGLELN_COMMENT_END, // CR: possible beginning of ending sequence of multi line comment,
                                          //     switch to SINGLELN_COMMENT_END state
                    SINGLELN_COMMENT,     // APOSTROPHE: regular single line comment, keep processing it
                    SINGLELN_COMMENT,     // STAR: regular single line comment, keep processing it
                    SINGLELN_COMMENT,     // DASH: regular single line comment, keep processing it
                    SINGLELN_COMMENT,     // SLASH: regular single line comment, keep processing it
                    SINGLELN_COMMENT,     // COLON: regular single line comment, keep processing it
                    SINGLELN_COMMENT      // OTHER: regular single line comment, keep processing it
                }
            };
        }

        /**
         * State automaton action table.
         */
        private static final Action[][] ACTION = {
            // Actions performed on transitions from STATEMENT state
            {
                Parser::copyChar,  // LETTER: copy regular statement character to output
                Parser::copyChar,  // NUMBER: copy regular statement character to output
                Parser::copyChar,  // LF: copy regular statement character to output
                Parser::copyChar,  // CR: copy regular statement character to output
                Parser::copyChar,  // APOSTROPHE: copy SQL string character to output
                Parser::copyChar,  // STAR: copy regular statement character to output
                Parser::copyChar,  // DASH: copy character to output, no matter wheter it's comment or not
                Parser::copyChar,  // SLASH: copy character to output, no matter wheter it's comment or not
                Parser::doNothing, // COLON: delay character copying until it's obvious whether this is parameter or not
                Parser::copyChar   // OTHER: copy regular statement character to output
            },
            // Actions performed on transitions from STRING state
            {
               Parser::copyChar, // LETTER: copy SQL string character to output
               Parser::copyChar, // NUMBER: copy SQL string character to output
               Parser::copyChar, // LF: copy SQL string character to output
               Parser::copyChar, // CR: copy SQL string character to output
               Parser::copyChar, // APOSTROPHE: copy SQL string character to output
               Parser::copyChar, // STAR: copy SQL string character to output
               Parser::copyChar, // DASH: copy SQL string character to output
               Parser::copyChar, // SLASH: copy SQL string character to output
               Parser::copyChar, // COLON: copy SQL string character to output
               Parser::copyChar  // OTHER: copy SQL string character to output
           },
           // Actions performed on transitions from COLON state
           {
               Parser::setFirstParamChar,   // LETTER: set first parameter character
               Parser::addColonAndCopyChar, // NUMBER: not a parameter, add delayed colon and copy current statement character
                                            //         to output
               Parser::addColonAndCopyChar, // LF: not a parameter, add delayed colon and copy current statement character
                                            //     to output
               Parser::addColonAndCopyChar, // CR: not a parameter, add delayed colon and copy current statement character
                                            //     to output
               Parser::addColonAndCopyChar, // APOSTROPHE: not a parameter, add delayed colon and copy current SQL string
                                            //             character to output
               Parser::addColonAndCopyChar, // STAR: not a parameter, add delayed colon and copy current statement character
                                            //       to output
               Parser::addColonAndCopyChar, // DASH: not a parameter, add delayed colon and copy current statement character
                                            //       to output, no matter wheter it's comment or not
               Parser::addColonAndCopyChar, // SLASH: not a parameter, add delayed colon and copy current statement character
                                            //        to output, no matter wheter it's comment or not
               Parser::addColon,            // COLON: not a parameter, add delayed colon and delay current colon copying
                                            //        until it's obvious whether this is parameter or not
               Parser::addColonAndCopyChar  // OTHER: not a parameter, add delayed colon and copy current statement character
                                            //        to output
           },
           // Actions performed on transitions from PARAMETER state
           {
               Parser::setNextParamChar,       // LETTER: set next parameter character
               Parser::setNextParamChar,       // NUMBER: set next parameter character
               Parser::finishParamAndCopyChar, // LF: finish parameter processing and copy current character as part
                                               //     of regular statement
               Parser::finishParamAndCopyChar, // CR: finish parameter processing and copy current character as part
                                               //     of regular statement
               Parser::finishParamAndCopyChar, // APOSTROPHE: finish parameter processing and copy current character as part
                                               //             of regular statement
               Parser::finishParamAndCopyChar, // STAR: finish parameter processing and copy current character as part
                                               //       of regular statement
               Parser::finishParamAndCopyChar, // DASH: finish parameter processing and copy current character as part
                                               //       of regular statement
               Parser::finishParamAndCopyChar, // SLASH: finish parameter processing and copy current character as part
                                               //        of regular statement
               Parser::finishParam,            // COLON: finish parameter processing and delay character copying until
                                               //        it's obvious whether this is next parameter or not
               Parser::finishParamAndCopyChar  // OTHER: finish parameter processing and copy current character as part
                                               //        of regular statement
           },
           // Actions performed on transitions from MULTILN_COMMENT_BG state
           {
               Parser::copyChar,  // LETTER: copy regular statement character to output
               Parser::copyChar,  // NUMBER: copy regular statement character to output
               Parser::copyChar,  // LF: copy regular statement character to output
               Parser::copyChar,  // CR: copy regular statement character to output
               Parser::copyChar,  // APOSTROPHE: copy SQL string character to output
               Parser::copyChar,  // STAR: copy multi line comment character to output
               Parser::copyChar,  // DASH: copy character to output, no matter wheter it's comment or not
               Parser::copyChar,  // SLASH: copy character to output, no matter wheter it's comment or not
               Parser::doNothing, // COLON: delay character copying until it's obvious whether this is parameter or not
               Parser::copyChar   // OTHER: copy regular statement character to output
           },
           // Actions performed on transitions from MULTILN_COMMENT_END state
           {
               Parser::copyChar, // LETTER: copy multi line comment character to output
               Parser::copyChar, // NUMBER: copy multi line comment character to output
               Parser::copyChar, // LF: copy multi line comment character to output
               Parser::copyChar, // CR: copy multi line comment character to output
               Parser::copyChar, // APOSTROPHE: copy multi line comment character to output
               Parser::copyChar, // STAR: copy multi line comment character to output
               Parser::copyChar, // DASH: copy multi line comment character to output
               Parser::copyChar, // SLASH: copy multi line comment character to output
               Parser::copyChar, // COLON: copy multi line comment character to output
               Parser::copyChar  // OTHER: copy multi line comment character to output
           },
           // Actions performed on transitions from MULTILN_COMMENT state
           {
               Parser::copyChar, // LETTER: copy multi line comment character to output
               Parser::copyChar, // NUMBER: copy multi line comment character to output
               Parser::copyChar, // LF: copy multi line comment character to output
               Parser::copyChar, // CR: copy multi line comment character to output
               Parser::copyChar, // APOSTROPHE: copy multi line comment character to output
               Parser::copyChar, // STAR: copy multi line comment character to output
               Parser::copyChar, // DASH: copy multi line comment character to output
               Parser::copyChar, // SLASH: copy multi line comment character to output
               Parser::copyChar, // COLON: copy multi line comment character to output
               Parser::copyChar  // OTHER: copy multi line comment character to output
           },
           // Actions performed on transitions from SINGLELN_COMMENT_BG state
           {
               Parser::copyChar,  // LETTER: copy regular statement character to output
               Parser::copyChar,  // NUMBER: copy regular statement character to output
               Parser::copyChar,  // LF: copy regular statement character to output
               Parser::copyChar,  // CR: copy regular statement character to output
               Parser::copyChar,  // APOSTROPHE: copy SQL string character to output
               Parser::copyChar,  // STAR: copy regular statement character to output
               Parser::copyChar,  // DASH: copy single line comment character to output
               Parser::copyChar,  // SLASH: copy character to output, no matter wheter it's comment or not
               Parser::doNothing, // COLON: delay character copying until it's obvious whether this is parameter or not
               Parser::copyChar   // OTHER: copy regular statement character to output
           },
           // Actions performed on transitions from SINGLELN_COMMENT_END state
           {
               Parser::copyChar,  // LETTER: copy single line comment character to output
               Parser::copyChar,  // NUMBER: copy single line comment character to output
               Parser::copyChar,  // LF: copy single line comment character to output
               Parser::copyChar,  // CR: copy single line comment character to output
               Parser::copyChar,  // APOSTROPHE: copy single line comment character to output
               Parser::copyChar,  // STAR: copy single line comment character to output
               Parser::copyChar,  // DASH: copy single line comment character to output
               Parser::copyChar,  // SLASH: copy single line comment character to output
               Parser::copyChar,  // COLON: copy single line comment character to output
               Parser::copyChar   // OTHER: copy single line comment character to output
           },
           // Actions performed on transitions from SINGLELN_COMMENT state
           {
               Parser::copyChar,  // LETTER: copy single line comment character to output
               Parser::copyChar,  // NUMBER: copy single line comment character to output
               Parser::copyChar,  // LF: copy single line comment character to output
               Parser::copyChar,  // CR: copy single line comment character to output
               Parser::copyChar,  // APOSTROPHE: copy single line comment character to output
               Parser::copyChar,  // STAR: copy single line comment character to output
               Parser::copyChar,  // DASH: copy single line comment character to output
               Parser::copyChar,  // SLASH: copy single line comment character to output
               Parser::copyChar,  // COLON: copy single line comment character to output
               Parser::copyChar   // OTHER: copy single line comment character to output
           }
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
        private static void addColonAndCopyChar(Parser parser) {
            parser.sb.append(':');
            parser.sb.append(parser.c);
        }

        /**
         * Store 1st named parameter letter.
         *
         * @param parser parser instance
         */
        private static void setFirstParamChar(Parser parser) {
            parser.nap.setLength(0);
            parser.nap.append(parser.c);
        }

        /**
         * Store next named parameter letter or number.
         *
         * @param parser parser instance
         */
        private static void setNextParamChar(Parser parser) {
            parser.nap.append(parser.c);
        }

        /**
         * Finish stored named parameter and copy current character from input string to output as is.
         *
         * @param parser parser instance
         */
        private static void finishParamAndCopyChar(Parser parser) {
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
        private static void finishParam(Parser parser) {
            String parName = parser.nap.toString();
            parser.names.add(parName);
            parser.sb.append('?');
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

        Parser(String statement) {
            this.sb = new StringBuilder(statement.length());
            this.nap = new StringBuilder(32);
            this.names = new LinkedList<>();
            this.statement = statement;
            this.c = '\0';
            this.cl = null;

        }

        String convert() {
            State state = State.STATEMENT;  // Initial state: common statement processing
            int len = statement.length();
            for (int i = 0; i < len; i++) {
                c = statement.charAt(i);
                cl = CharClass.charClass(c);
                ACTION[state.ordinal()][cl.ordinal()].accept(this);
                state = State.TRANSITION[state.ordinal()][cl.ordinal()];
            }
            // Process end of statement
            if (state == State.PARAMETER) {
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
