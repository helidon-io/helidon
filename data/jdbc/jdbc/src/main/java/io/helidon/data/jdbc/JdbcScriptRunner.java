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
package io.helidon.data.jdbc;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.sql.DataSource;

import io.helidon.data.DataException;

/**
 * Executes persistence-unit bootstrap scripts while retaining ownership of all
 * JDBC resources.
 */
final class JdbcScriptRunner {
    /** Unicode byte-order mark accepted at the start of a UTF-8 script. */
    private static final char BYTE_ORDER_MARK = '\ufeff';

    /** Prevents construction of this utility. */
    private JdbcScriptRunner() {
    }

    /**
     * Loads every script before acquiring a connection and then executes them
     * in declaration order on one connection.
     *
     * @param unitName persistence-unit name
     * @param dataSource persistence-unit datasource
     * @param paths ordered classpath script paths
     */
    static void execute(String unitName, DataSource dataSource, List<Path> paths) {
        Objects.requireNonNull(unitName, "Persistence-unit name must not be null");
        Objects.requireNonNull(dataSource, "Script datasource must not be null");
        Objects.requireNonNull(paths, "Script paths must not be null");
        if (paths.isEmpty()) {
            return;
        }

        List<Script> scripts = paths.stream()
                .map(path -> load(unitName, path))
                .toList();

        Connection connection = null;
        Statement statement = null;
        boolean manualCommit = false;
        boolean completed = false;
        Throwable failure = null;
        try {
            connection = dataSource.getConnection();
            manualCommit = !connection.getAutoCommit();
            statement = connection.createStatement();
            for (Script script : scripts) {
                execute(unitName, statement, script);
            }
            Statement completedStatement = statement;
            statement = null;
            completedStatement.close();
            if (manualCommit) {
                connection.commit();
            }
            completed = true;
        } catch (Throwable caught) {
            failure = caught;
        }

        if (failure != null && manualCommit && !completed && connection != null) {
            try {
                connection.rollback();
            } catch (Throwable rollbackFailure) {
                suppress(failure, rollbackFailure);
            }
        }
        failure = close(statement, failure);
        failure = close(connection, failure);
        if (failure != null) {
            rethrow(unitName, failure);
        }
    }

    /**
     * Executes one preloaded script.
     *
     * @param unitName persistence-unit name
     * @param statement provider-owned statement
     * @param script script content
     */
    private static void execute(String unitName, Statement statement, Script script) {
        for (int index = 0; index < script.statements().size(); index++) {
            String sql = script.statements().get(index);
            try {
                boolean resultSet = statement.execute(sql);
                while (resultSet || statement.getUpdateCount() != -1) {
                    resultSet = statement.getMoreResults(Statement.CLOSE_CURRENT_RESULT);
                }
            } catch (SQLException e) {
                throw statementFailure(unitName, script.path(), index + 1, e);
            }
        }
    }

    /**
     * Loads and parses one UTF-8 classpath script.
     *
     * @param unitName persistence-unit name
     * @param path configured classpath path
     * @return parsed script
     */
    private static Script load(String unitName, Path path) {
        Objects.requireNonNull(path, "Script path must not be null");
        String resourceName = resourceName(path);
        try (InputStream input = resource(resourceName)) {
            if (input == null) {
                throw new DataException("JDBC persistence unit '" + unitName
                                                + "' script resource was not found: " + path);
            }
            String content = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            if (!content.isEmpty() && content.charAt(0) == BYTE_ORDER_MARK) {
                content = content.substring(1);
            }
            return new Script(path, statements(unitName, path, content));
        } catch (IOException e) {
            throw new DataException("JDBC persistence unit '" + unitName
                                            + "' could not read script resource '" + path + "'", e);
        }
    }

    /**
     * Resolves one classpath resource using the application context loader
     * with the provider loader as a fallback.
     *
     * @param resourceName normalized resource name
     * @return resource stream, or {@code null}
     */
    private static InputStream resource(String resourceName) {
        ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
        if (contextLoader != null) {
            InputStream input = contextLoader.getResourceAsStream(resourceName);
            if (input != null) {
                return input;
            }
        }
        ClassLoader providerLoader = JdbcScriptRunner.class.getClassLoader();
        return providerLoader == null ? ClassLoader.getSystemResourceAsStream(resourceName)
                : providerLoader.getResourceAsStream(resourceName);
    }

    /**
     * Normalizes a configured path for {@link ClassLoader} lookup.
     *
     * @param path configured path
     * @return normalized classpath resource name
     */
    private static String resourceName(Path path) {
        String name = path.toString().replace('\\', '/');
        while (name.startsWith("/")) {
            name = name.substring(1);
        }
        while (name.startsWith("./")) {
            name = name.substring(2);
        }
        if (name.isBlank()) {
            throw new IllegalArgumentException("Script path must not be blank");
        }
        return name;
    }

    /**
     * Splits a script at semicolons outside quoted text, comments, and
     * PostgreSQL-style dollar-quoted regions.
     *
     * @param unitName persistence-unit name
     * @param path configured script path
     * @param content script content
     * @return executable statements
     */
    private static List<String> statements(String unitName, Path path, String content) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        State state = State.NORMAL;
        String dollarDelimiter = null;

        for (int index = 0; index < content.length(); index++) {
            char character = content.charAt(index);
            switch (state) {
            case NORMAL -> {
                if (character == '\'') {
                    current.append(character);
                    state = State.SINGLE_QUOTE;
                } else if (character == '"') {
                    current.append(character);
                    state = State.DOUBLE_QUOTE;
                } else if (character == '`') {
                    current.append(character);
                    state = State.BACKTICK_QUOTE;
                } else if (character == '-' && next(content, index) == '-') {
                    current.append(' ');
                    index++;
                    state = State.LINE_COMMENT;
                } else if (character == '/' && next(content, index) == '*') {
                    current.append(' ');
                    index++;
                    state = State.BLOCK_COMMENT;
                } else if (character == '$') {
                    String delimiter = dollarDelimiter(content, index);
                    if (delimiter == null) {
                        current.append(character);
                    } else {
                        current.append(delimiter);
                        index += delimiter.length() - 1;
                        dollarDelimiter = delimiter;
                        state = State.DOLLAR_QUOTE;
                    }
                } else if (character == ';') {
                    addStatement(statements, current);
                } else {
                    current.append(character);
                }
            }
            case SINGLE_QUOTE -> {
                current.append(character);
                if (character == '\'' && next(content, index) == '\'') {
                    current.append('\'');
                    index++;
                } else if (character == '\'') {
                    state = State.NORMAL;
                }
            }
            case DOUBLE_QUOTE -> {
                current.append(character);
                if (character == '"' && next(content, index) == '"') {
                    current.append('"');
                    index++;
                } else if (character == '"') {
                    state = State.NORMAL;
                }
            }
            case BACKTICK_QUOTE -> {
                current.append(character);
                if (character == '`' && next(content, index) == '`') {
                    current.append('`');
                    index++;
                } else if (character == '`') {
                    state = State.NORMAL;
                }
            }
            case LINE_COMMENT -> {
                if (character == '\n' || character == '\r') {
                    current.append(character);
                    state = State.NORMAL;
                }
            }
            case BLOCK_COMMENT -> {
                if (character == '*' && next(content, index) == '/') {
                    index++;
                    state = State.NORMAL;
                } else if (character == '\n' || character == '\r') {
                    current.append(character);
                }
            }
            case DOLLAR_QUOTE -> {
                if (content.startsWith(dollarDelimiter, index)) {
                    current.append(dollarDelimiter);
                    index += dollarDelimiter.length() - 1;
                    dollarDelimiter = null;
                    state = State.NORMAL;
                } else {
                    current.append(character);
                }
            }
            default -> throw new IllegalStateException("Unexpected script parser state: " + state);
            }
        }

        if (state != State.NORMAL && state != State.LINE_COMMENT) {
            throw new DataException("JDBC persistence unit '" + unitName
                                            + "' script '" + path
                                            + "' has an unterminated " + state.description());
        }
        addStatement(statements, current);
        return List.copyOf(statements);
    }

    /**
     * Adds one non-blank statement and resets the source buffer.
     *
     * @param statements target statements
     * @param current current statement buffer
     */
    private static void addStatement(List<String> statements, StringBuilder current) {
        String sql = current.toString().trim();
        if (!sql.isEmpty()) {
            statements.add(sql);
        }
        current.setLength(0);
    }

    /**
     * Returns the next character, or NUL at end of input.
     *
     * @param content script content
     * @param index current index
     * @return next character
     */
    private static char next(String content, int index) {
        int next = index + 1;
        return next < content.length() ? content.charAt(next) : '\0';
    }

    /**
     * Recognizes a PostgreSQL-style dollar delimiter.
     *
     * @param content script content
     * @param start opening dollar index
     * @return complete delimiter, or {@code null}
     */
    private static String dollarDelimiter(String content, int start) {
        int end = content.indexOf('$', start + 1);
        if (end < 0) {
            return null;
        }
        for (int index = start + 1; index < end; index++) {
            char character = content.charAt(index);
            if (!Character.isLetterOrDigit(character) && character != '_') {
                return null;
            }
        }
        return content.substring(start, end + 1);
    }

    /**
     * Creates a statement-specific failure.
     *
     * @param unitName persistence-unit name
     * @param path script path
     * @param position one-based statement position
     * @param cause JDBC failure
     * @return translated failure
     */
    private static DataException statementFailure(String unitName, Path path, int position, SQLException cause) {
        return new DataException("JDBC persistence unit '" + unitName
                                         + "' script '" + path
                                         + "' failed at statement " + position
                                         + sqlDiagnostic(cause),
                                 cause);
    }

    /**
     * Creates a connection, statement-creation, or cleanup failure.
     *
     * @param unitName persistence-unit name
     * @param cause JDBC failure
     * @return translated failure
     */
    private static DataException resourceFailure(String unitName, SQLException cause) {
        return new DataException("JDBC persistence unit '" + unitName
                                         + "' bootstrap script resource handling failed"
                                         + sqlDiagnostic(cause),
                                 cause);
    }

    /**
     * Closes one bootstrap resource while preserving the first failure.
     *
     * @param resource resource to close, or {@code null}
     * @param failure earlier failure, or {@code null}
     * @return first failure
     */
    private static Throwable close(AutoCloseable resource, Throwable failure) {
        if (resource == null) {
            return failure;
        }
        try {
            resource.close();
        } catch (Throwable closeFailure) {
            if (failure == null) {
                return closeFailure;
            }
            suppress(failure, closeFailure);
        }
        return failure;
    }

    /**
     * Attaches a later failure without risking self-suppression.
     *
     * @param failure primary failure
     * @param suppressed later failure
     */
    private static void suppress(Throwable failure, Throwable suppressed) {
        if (failure != suppressed) {
            failure.addSuppressed(suppressed);
        }
    }

    /**
     * Rethrows a captured bootstrap failure without losing its category.
     *
     * @param unitName persistence-unit name
     * @param failure captured failure
     */
    private static void rethrow(String unitName, Throwable failure) {
        if (failure instanceof DataException dataException) {
            throw dataException;
        }
        if (failure instanceof SQLException sqlException) {
            throw resourceFailure(unitName, sqlException);
        }
        if (failure instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        throw new DataException("JDBC persistence unit '" + unitName + "' bootstrap script failed", failure);
    }

    /**
     * Formats safe JDBC diagnostics.
     *
     * @param cause JDBC failure
     * @return diagnostic suffix
     */
    private static String sqlDiagnostic(SQLException cause) {
        String state = cause.getSQLState() == null ? "unknown" : cause.getSQLState();
        return " [SQLState=" + state + ", vendorCode=" + cause.getErrorCode() + "]";
    }

    /**
     * Parsed classpath script.
     *
     * @param path configured path
     * @param statements parsed statements
     */
    private record Script(Path path, List<String> statements) {
    }

    /**
     * Script lexical state.
     */
    private enum State {
        /** Ordinary SQL text. */
        NORMAL("SQL text"),
        /** Single-quoted string. */
        SINGLE_QUOTE("single-quoted string"),
        /** Double-quoted identifier or string. */
        DOUBLE_QUOTE("double-quoted text"),
        /** Backtick-quoted identifier. */
        BACKTICK_QUOTE("backtick-quoted text"),
        /** Line comment. */
        LINE_COMMENT("line comment"),
        /** Block comment. */
        BLOCK_COMMENT("block comment"),
        /** Dollar-quoted body. */
        DOLLAR_QUOTE("dollar-quoted text");

        /** Diagnostic description. */
        private final String description;

        State(String description) {
            this.description = description;
        }

        String description() {
            return description;
        }
    }
}
