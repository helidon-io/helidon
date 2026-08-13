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

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import io.helidon.common.configurable.Resource;
import io.helidon.data.DataException;
import io.helidon.service.registry.Service;

/**
 * Executes persistence-unit bootstrap scripts while retaining ownership of all
 * JDBC resources.
 * <p>
 * Bootstrap statement boundaries use one internal portable profile. The
 * profile removes only active semicolon delimiters and otherwise preserves
 * source text exactly, including comments, hints, whitespace, and line
 * endings. Database-client boundary commands are rejected because they are
 * not JDBC SQL.
 * <p>
 * Manual-commit completion is tracked independently from cleanup. A commit
 * failure always produces an {@link BootstrapOutcome#UNKNOWN unknown outcome}:
 * rollback is then best-effort server cleanup and the connection is invalidated
 * rather than returned through an ordinary pooled close.
 */
final class JdbcScriptRunner {

    // UTF-8 scripts may begin with this marker.
    private static final char BYTE_ORDER_MARK = '\ufeff';

    // One immutable policy keeps all bootstrap allocation and plan limits consistent.
    private static final BootstrapPolicy POLICY = new BootstrapPolicy(8 * 1024 * 1024,
                                                                       16 * 1024 * 1024,
                                                                       10_000);

    // The release exposes no script-dialect configuration; every bootstrap path uses this fixed profile.
    private static final ScriptBoundaryProfile SCRIPT_BOUNDARY_PROFILE = ScriptBoundaryProfile.PORTABLE;

    private JdbcScriptRunner() {
    }

    /**
     * Loads every resource before acquiring a connection and then executes the
     * scripts in declaration order on one connection.
     *
     * @param unitName persistence-unit name
     * @param dataSource persistence-unit datasource
     * @param resources ordered script resources
     */
    static void execute(String unitName, DataSource dataSource, List<Resource> resources) {
        List<JdbcBootstrapResource> described = new ArrayList<>(resources.size());
        for (Resource resource : resources) {
            described.add(JdbcBootstrapResource.create(JdbcBootstrapResource.Role.INIT,
                                                       described.size() + 1,
                                                       resource));
        }
        execute(unitName, dataSource, load(unitName, described));
    }

    /**
     * Executes scripts which have already been detached from their configured
     * resources.
     *
     * @param unitName persistence-unit name
     * @param dataSource persistence-unit datasource
     * @param preparedScripts preloaded scripts
     */
    static void execute(String unitName, DataSource dataSource, PreparedScripts preparedScripts) {
        List<Script> scripts = preparedScripts.scripts;
        if (scripts.isEmpty()) {
            return;
        }

        // Explicit cleanup keeps rollback and close failures in their original order.
        Connection connection = null;
        Statement statement = null;
        boolean manualCommit = false;

        // Completion state decides whether ordinary close is safe after the execution block exits.
        BootstrapOutcome outcome = BootstrapOutcome.NOT_STARTED;
        Throwable failure = null;
        try {
            connection = dataSource.getConnection();
            // Keep the datasource's transaction mode instead of changing it for bootstrap.
            manualCommit = !connection.getAutoCommit();
            statement = connection.createStatement();
            for (Script script : scripts) {
                execute(unitName, statement, script);
            }
            // Close before commit so a failure can still roll back a manual commit connection.
            Statement completedStatement = statement;
            // Prevent cleanup from closing the same statement again if close reports a failure.
            statement = null;
            try {
                completedStatement.close();
            } catch (SQLException sqlException) {
                throw sqlException;
            } catch (RuntimeException closeFailure) {
                // This close occurs before the shared cleanup path, so sanitize its runtime failure here.
                throw (RuntimeException) JdbcExceptionTranslator.sanitize("closing a bootstrap statement", closeFailure);
            }
            if (manualCommit) {
                try {
                    connection.commit();
                } catch (SQLException | RuntimeException commitFailure) {
                    // A rollback cannot establish whether a failed commit reached the database.
                    outcome = BootstrapOutcome.UNKNOWN;
                    throw bootstrapCommitFailure(unitName, commitFailure);
                } catch (Error commitFailure) {
                    outcome = BootstrapOutcome.UNKNOWN;
                    throw commitFailure;
                }
            }
            outcome = BootstrapOutcome.COMMITTED;
        } catch (Throwable caught) {
            failure = caught;
        }

        if (failure != null && manualCommit && connection != null
                && (outcome == BootstrapOutcome.NOT_STARTED || outcome == BootstrapOutcome.UNKNOWN)) {
            // Rollback after UNKNOWN is cleanup only; even success cannot prove that commit did not take effect.
            try {
                connection.rollback();
                if (outcome == BootstrapOutcome.NOT_STARTED) {
                    outcome = BootstrapOutcome.ROLLED_BACK;
                }
            } catch (Throwable rollbackFailure) {
                failure = JdbcExceptionTranslator.suppress(failure,
                                                           "rolling back a bootstrap transaction",
                                                           rollbackFailure);
                outcome = BootstrapOutcome.UNKNOWN;
            }
        }
        failure = close(statement, "closing a bootstrap statement", failure);
        if (outcome == BootstrapOutcome.UNKNOWN) {
            // Never restore or ordinarily close a connection whose transaction outcome is unknown.
            failure = JdbcConnectionInvalidator.invalidate(connection, failure);
        } else {
            failure = close(connection, "closing a bootstrap connection", failure);
        }
        if (failure != null) {
            rethrow(unitName, failure);
        }
    }

    /**
     * Loads and parses all configured UTF-8 script resources. Every resource
     * is released before this method returns, including resources which have
     * not yet been read when an earlier resource fails.
     *
     * @param unitName persistence-unit name
     * @param resources ordered, safely described script resources
     * @return detached parsed scripts
     */
    static PreparedScripts load(String unitName, List<JdbcBootstrapResource> resources) {
        List<Script> scripts = new ArrayList<>(resources.size());
        BootstrapBudget budget = POLICY.newBudget();
        for (int index = 0; index < resources.size(); index++) {
            try {
                scripts.add(load(unitName, resources.get(index), budget));
            } catch (Throwable failure) {
                for (int remaining = index + 1; remaining < resources.size(); remaining++) {
                    release(unitName, resources.get(remaining), failure);
                }
                rethrow(unitName, failure);
                throw new AssertionError("The bootstrap resource failure should already have been rethrown.");
            }
        }
        return new PreparedScripts(List.copyOf(scripts));
    }

    /**
     * Splits a script using the internal portable statement-boundary profile.
     *
     * @param unitName persistence-unit name
     * @param content script content
     * @return executable statements
     */
    static List<String> statements(String unitName, String content) {
        JdbcBootstrapResource.Descriptor descriptor = new JdbcBootstrapResource.Descriptor(
                JdbcBootstrapResource.Role.INIT,
                JdbcBootstrapResource.SourceType.CONFIGURED_TEXT,
                1);
        return statements(unitName, descriptor, content, POLICY.newBudget(), SCRIPT_BOUNDARY_PROFILE);
    }

    /**
     * Splits one bounded script while enforcing the plan-wide statement limit.
     *
     * @param unitName persistence-unit name
     * @param descriptor safe script descriptor
     * @param content script content
     * @param budget bootstrap plan budget
     * @param profile statement-boundary profile
     * @return executable statements
     */
    private static List<String> statements(String unitName,
                                           JdbcBootstrapResource.Descriptor descriptor,
                                           String content,
                                           BootstrapBudget budget,
                                           ScriptBoundaryProfile profile) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder(content.length());
        State state = State.NORMAL;
        String dollarDelimiter = null;
        char qQuoteClosingDelimiter = '\0';
        boolean executableContent = false;
        boolean onlyWhitespaceOnLine = true;
        for (int index = 0; index < content.length(); index++) {
            int firstConsumed = index;
            char character = content.charAt(index);
            switch (state) {
            case NORMAL -> {
                if (onlyWhitespaceOnLine && unsupportedClientBoundary(content, index, character)) {
                    throw scriptFailure(unitName,
                                        descriptor,
                                        profile,
                                        "contains an unsupported database-client statement boundary",
                                        index);
                } else if (character == '\'') {
                    current.append(character);
                    executableContent = true;
                    state = State.SINGLE_QUOTE;
                } else if (character == '"') {
                    current.append(character);
                    executableContent = true;
                    state = State.DOUBLE_QUOTE;
                } else if (character == '`') {
                    current.append(character);
                    executableContent = true;
                    state = State.BACKTICK_QUOTE;
                } else if (character == '-'
                        && next(content, index) == '-'
                        && JdbcSqlLexicalRules.lineComment(content, index)) {
                    // Use the same conservative delimiter as runtime marker recognition. In particular,
                    // balance--1 remains SQL, so a following semicolon remains an active statement boundary.
                    current.append("--");
                    index++;
                    state = State.LINE_COMMENT;
                } else if (character == '/' && next(content, index) == '*') {
                    current.append("/*");
                    // MySQL executable comments are SQL input rather than ignorable commentary.
                    executableContent |= character(content, index + 2) == '!';
                    index++;
                    state = State.BLOCK_COMMENT;
                } else if ((character == 'q' || character == 'Q')
                        && JdbcSqlLexicalRules.qQuoteClosingDelimiter(content, index) != '\0') {
                    qQuoteClosingDelimiter = JdbcSqlLexicalRules.qQuoteClosingDelimiter(content, index);
                    current.append(content, index, index + 3);
                    index += 2;
                    executableContent = true;
                    state = State.Q_QUOTE;
                } else if (character == '$') {
                    String delimiter = JdbcSqlLexicalRules.dollarDelimiter(content, index);
                    if (delimiter == null) {
                        current.append(character);
                        executableContent = true;
                    } else {
                        current.append(delimiter);
                        index += delimiter.length() - 1;
                        dollarDelimiter = delimiter;
                        executableContent = true;
                        state = State.DOLLAR_QUOTE;
                    }
                } else if (character == ';') {
                    addStatement(unitName, descriptor, statements, current, executableContent, budget);
                    executableContent = false;
                } else {
                    current.append(character);
                    executableContent |= !Character.isWhitespace(character);
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
                current.append(character);
                if (character == '\n' || character == '\r') {
                    state = State.NORMAL;
                }
            }
            case BLOCK_COMMENT -> {
                if (character == '/' && next(content, index) == '*') {
                    throw scriptFailure(unitName,
                                        descriptor,
                                        profile,
                                        "contains a nested block comment",
                                        index);
                } else if (character == '*' && next(content, index) == '/') {
                    current.append("*/");
                    index++;
                    state = State.NORMAL;
                } else {
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
            case Q_QUOTE -> {
                current.append(character);
                if (character == qQuoteClosingDelimiter && next(content, index) == '\'') {
                    current.append('\'');
                    index++;
                    qQuoteClosingDelimiter = '\0';
                    state = State.NORMAL;
                }
            }
            default -> throw new IllegalStateException("The script parser entered the unexpected state '" + state + "'.");
            }
            onlyWhitespaceOnLine = updateLineState(content, firstConsumed, index, onlyWhitespaceOnLine);
        }
        // A line comment may end at the end of the file without a newline.
        validateTermination(unitName, descriptor, content, state, profile);
        addStatement(unitName, descriptor, statements, current, executableContent, budget);
        return List.copyOf(statements);
    }

    /**
     * Validates the parser state after consuming a complete script.
     *
     * @param unitName persistence-unit name
     * @param descriptor safe script descriptor
     * @param content script content
     * @param state final parser state
     * @param profile statement-boundary profile
     */
    private static void validateTermination(String unitName,
                                            JdbcBootstrapResource.Descriptor descriptor,
                                            String content,
                                            State state,
                                            ScriptBoundaryProfile profile) {
        if (state != State.NORMAL && state != State.LINE_COMMENT) {
            throw scriptFailure(unitName,
                                descriptor,
                                profile,
                                "has an unterminated " + state.description(),
                                content.length());
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
                // One script statement may expose several result channels.
                while (resultSet || statement.getUpdateCount() != -1) {
                    resultSet = statement.getMoreResults(Statement.CLOSE_CURRENT_RESULT);
                }
            } catch (SQLException e) {
                throw statementFailure(unitName, script.descriptor(), index + 1, e);
            }
        }
    }

    /**
     * Loads and parses one UTF-8 script resource.
     *
     * @param unitName persistence-unit name
     * @param resource configured resource
     * @return parsed script
     */
    private static Script load(String unitName,
                               JdbcBootstrapResource bootstrapResource,
                               BootstrapBudget budget) {
        JdbcBootstrapResource.Descriptor descriptor = bootstrapResource.descriptor();
        if (descriptor.sourceType() == JdbcBootstrapResource.SourceType.URI) {
            DataException failure = new DataException(persistenceUnitDescription(unitName)
                                                              + " does not support a URI value for the '"
                                                              + scriptConfigKey(descriptor.role())
                                                              + "' configuration key.");
            // A programmatic caller may have already caused Resource to open the URI stream.
            release(unitName, bootstrapResource, failure);
            throw failure;
        }

        InputStream input;
        try {
            input = bootstrapResource.resource().stream();
        } catch (Error error) {
            throw error;
        } catch (Throwable failure) {
            throw resourceFailure(unitName, descriptor, "open", failure);
        }

        Script script = null;
        Throwable failure = null;
        try {
            byte[] bytes = read(unitName, descriptor, input, budget);
            String content = decode(unitName, descriptor, bytes);
            if (!content.isEmpty() && content.charAt(0) == BYTE_ORDER_MARK) {
                content = content.substring(1);
            }
            script = new Script(descriptor,
                                statements(unitName,
                                           descriptor,
                                           content,
                                           budget,
                                           SCRIPT_BOUNDARY_PROFILE));
        } catch (Error error) {
            failure = error;
        } catch (DataException dataException) {
            failure = dataException;
        } catch (Throwable caught) {
            failure = resourceFailure(unitName, descriptor, "process", caught);
        }
        failure = closeInput(unitName, descriptor, input, failure);
        if (failure != null) {
            rethrow(unitName, failure);
        }
        return script;
    }

    /**
     * Closes an unconsumed resource after another resource fails.
     *
     * @param bootstrapResource resource to release
     * @param failure primary failure
     */
    private static void release(String unitName,
                                JdbcBootstrapResource bootstrapResource,
                                Throwable failure) {
        JdbcBootstrapResource.Descriptor descriptor = bootstrapResource.descriptor();
        InputStream input;
        try {
            input = bootstrapResource.resource().stream();
        } catch (Error error) {
            throw error;
        } catch (Throwable closeFailure) {
            failure.addSuppressed(resourceFailure(unitName, descriptor, "open", closeFailure));
            return;
        }
        try {
            // Acquiring and closing transfers and releases the one-shot stream.
            input.close();
        } catch (Error error) {
            throw error;
        } catch (Throwable closeFailure) {
            failure.addSuppressed(resourceFailure(unitName, descriptor, "close", closeFailure));
        }
    }

    /**
     * Closes the current resource stream and converts non-fatal cleanup
     * failures before they can enter the application-visible failure tree.
     *
     * @param unitName persistence-unit name
     * @param descriptor safe resource descriptor
     * @param input current input stream
     * @param failure earlier failure, or {@code null}
     * @return primary failure after cleanup, or {@code null}
     */
    private static Throwable closeInput(String unitName,
                                        JdbcBootstrapResource.Descriptor descriptor,
                                        InputStream input,
                                        Throwable failure) {
        try {
            input.close();
        } catch (Error closeError) {
            if (failure == null) {
                return closeError;
            }
            if (failure != closeError) {
                closeError.addSuppressed(failure);
            }
            return closeError;
        } catch (Throwable closeFailure) {
            DataException sanitized = resourceFailure(unitName, descriptor, "close", closeFailure);
            if (failure == null) {
                return sanitized;
            }
            failure.addSuppressed(sanitized);
        }
        return failure;
    }

    /**
     * Reads one resource without exceeding either its own byte limit or the
     * remaining aggregate plan limit. Reading one extra byte is the only
     * reliable proof that a stream exceeds the applicable limit.
     *
     * @param unitName persistence-unit name
     * @param descriptor safe resource descriptor
     * @param input resource input
     * @param budget bootstrap plan budget
     * @return bounded resource bytes
     */
    private static byte[] read(String unitName,
                               JdbcBootstrapResource.Descriptor descriptor,
                               InputStream input,
                               BootstrapBudget budget) {
        int remainingTotal = budget.remainingBytes();
        int limit = Math.min(POLICY.maxResourceBytes(), remainingTotal);
        byte[] bytes;
        try {
            bytes = input.readNBytes(limit + 1);
        } catch (Error error) {
            throw error;
        } catch (Throwable failure) {
            throw resourceFailure(unitName, descriptor, "read", failure);
        }
        if (bytes.length > limit) {
            String limitDescription = remainingTotal < POLICY.maxResourceBytes()
                    ? "aggregate bootstrap byte limit of " + POLICY.maxTotalBytes()
                    : "per-resource bootstrap byte limit of " + POLICY.maxResourceBytes();
            throw new DataException(persistenceUnitDescription(unitName) + " cannot load the " + descriptor
                                            + " because it exceeds the " + limitDescription + ".");
        }
        budget.addBytes(bytes.length);
        return bytes;
    }

    /**
     * Decodes one bounded resource as strict UTF-8. Reporting malformed input
     * rather than replacing it prevents byte errors from changing SQL tokens
     * or statement delimiters.
     *
     * @param unitName persistence-unit name
     * @param descriptor safe resource descriptor
     * @param bytes bounded bytes
     * @return decoded content
     */
    private static String decode(String unitName,
                                 JdbcBootstrapResource.Descriptor descriptor,
                                 byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException failure) {
            throw resourceFailure(unitName, descriptor, "decode", failure);
        }
    }

    /**
     * Adds one statement containing executable input and resets the source
     * buffer. The source is deliberately not trimmed: comments, hints,
     * whitespace, and line endings are part of the SQL supplied by the
     * application and may be significant to a database or JDBC driver.
     *
     * @param unitName persistence-unit name
     * @param descriptor safe resource descriptor
     * @param statements target statements
     * @param current current statement buffer
     * @param executableContent whether the buffer contains executable input
     * @param budget bootstrap plan budget
     */
    private static void addStatement(String unitName,
                                     JdbcBootstrapResource.Descriptor descriptor,
                                     List<String> statements,
                                     StringBuilder current,
                                     boolean executableContent,
                                     BootstrapBudget budget) {
        if (executableContent) {
            if (!budget.addStatement()) {
                throw new DataException(persistenceUnitDescription(unitName) + " cannot load the " + descriptor
                                                + " because it exceeds the bootstrap statement limit of "
                                                + POLICY.maxStatements() + ".");
            }
            statements.add(current.toString());
        }
        current.setLength(0);
    }

    /**
     * Detects database-client statement-boundary commands which have no JDBC
     * meaning. The caller invokes this method only where the physical line has
     * contained only whitespace before the current offset. This keeps
     * recognition limited to line commands without rescanning the line prefix.
     *
     * @param content script content
     * @param index current source offset
     * @param character character at the current offset
     * @return whether a client boundary begins at the offset
     */
    private static boolean unsupportedClientBoundary(String content, int index, char character) {
        if (character == '/') {
            return lineSuffixIsWhitespace(content, index + 1);
        }
        if ((character == 'g' || character == 'G')
                && content.regionMatches(true, index, "GO", 0, 2)) {
            return lineSuffixIsWhitespace(content, index + 2);
        }
        if ((character == 'd' || character == 'D')
                && content.regionMatches(true, index, "DELIMITER", 0, 9)) {
            int end = index + 9;
            return end == content.length() || Character.isWhitespace(content.charAt(end));
        }
        return false;
    }

    /**
     * Updates the whitespace state of the current physical line after the
     * parser consumes a range of source characters.
     *
     * @param content script content
     * @param start first consumed offset
     * @param end last consumed offset
     * @param onlyWhitespace current line state before the range
     * @return line state after the range
     */
    private static boolean updateLineState(String content,
                                           int start,
                                           int end,
                                           boolean onlyWhitespace) {
        for (int current = start; current <= end; current++) {
            char character = content.charAt(current);
            if (character == '\n' || character == '\r') {
                onlyWhitespace = true;
            } else if (!Character.isWhitespace(character)) {
                onlyWhitespace = false;
            }
        }
        return onlyWhitespace;
    }

    /**
     * Tests whether only horizontal whitespace follows an offset on its
     * physical line.
     *
     * @param content script content
     * @param index source offset
     * @return whether the line suffix is whitespace
     */
    private static boolean lineSuffixIsWhitespace(String content, int index) {
        for (int current = index; current < content.length(); current++) {
            char character = content.charAt(current);
            if (character == '\n' || character == '\r') {
                return true;
            }
            if (!Character.isWhitespace(character)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns a character at the requested offset, or NUL outside the input.
     *
     * @param content script content
     * @param index requested offset
     * @return character at the offset, or NUL
     */
    private static char character(String content, int index) {
        return index >= 0 && index < content.length() ? content.charAt(index) : '\0';
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
     * Creates a safe lexical-policy failure without rendering script content.
     *
     * @param unitName persistence-unit name
     * @param descriptor safe resource descriptor
     * @param profile statement-boundary profile
     * @param problem stable problem description
     * @param offset zero-based source offset
     * @return data-layer failure
     */
    private static DataException scriptFailure(String unitName,
                                               JdbcBootstrapResource.Descriptor descriptor,
                                               ScriptBoundaryProfile profile,
                                               String problem,
                                               int offset) {
        return new DataException(persistenceUnitDescription(unitName) + " cannot load the " + descriptor
                                         + " because it " + problem + ". The statement boundary profile is " + profile
                                         + ", and the source offset is " + offset + ".");
    }

    /**
     * Creates a statement-specific failure.
     *
     * @param unitName persistence-unit name
     * @param descriptor safe script descriptor
     * @param position one-based statement position
     * @param cause JDBC failure
     * @return translated failure
     */
    private static DataException statementFailure(String unitName,
                                                  JdbcBootstrapResource.Descriptor descriptor,
                                                  int position,
                                                  SQLException cause) {
        return new DataException(persistenceUnitDescription(unitName) + " could not execute statement " + position
                                         + " from the " + descriptor + "." + sqlDiagnostic(cause),
                                 JdbcExceptionTranslator.sanitize("bootstrap statement", cause));
    }

    /**
     * Creates a connection, statement-creation, or cleanup failure.
     *
     * @param unitName persistence-unit name
     * @param cause JDBC failure
     * @return translated failure
     */
    private static DataException resourceFailure(String unitName, SQLException cause) {
        return new DataException(persistenceUnitDescription(unitName)
                                         + " could not complete bootstrap resource handling."
                                         + sqlDiagnostic(cause),
                                 JdbcExceptionTranslator.sanitize("bootstrap resource", cause));
    }

    /**
     * Rebuilds a configurable-resource failure from safe provider metadata.
     * The original message, cause tree, suppressed failures, and arbitrary
     * resource description are deliberately not retained.
     *
     * @param unitName persistence-unit name
     * @param descriptor safe resource descriptor
     * @param action stable resource action
     * @param cause original resource failure
     * @return safe data-layer failure
     */
    private static DataException resourceFailure(String unitName,
                                                 JdbcBootstrapResource.Descriptor descriptor,
                                                 String action,
                                                 Throwable cause) {
        return new DataException(persistenceUnitDescription(unitName) + " could not " + action
                                         + " the " + descriptor + ".",
                                 JdbcExceptionTranslator.sanitize(resourceDiagnosticOperation(action), cause));
    }

    /**
     * Creates a failure which makes an indeterminate bootstrap commit explicit.
     * The driver failure is sanitized before becoming an application-visible
     * cause.
     *
     * @param unitName persistence-unit name
     * @param cause commit failure
     * @return data-layer failure
     */
    private static DataException bootstrapCommitFailure(String unitName, Throwable cause) {
        String message = persistenceUnitDescription(unitName)
                + " could not commit the bootstrap transaction, and the outcome is unknown.";
        if (cause instanceof SQLException sqlException) {
            return new DataException(message + sqlDiagnostic(sqlException),
                                     JdbcExceptionTranslator.sanitize("committing a bootstrap transaction", sqlException));
        }
        return new DataException(message, JdbcExceptionTranslator.sanitize("committing a bootstrap transaction", cause));
    }

    /**
     * Closes one bootstrap resource while preserving the first failure. A JDBC
     * cleanup failure is sanitized before storage or suppression. A primary
     * fatal error remains primary and is rethrown by {@link #rethrow(String, Throwable)}.
     *
     * @param resource resource to close, or {@code null}
     * @param operation stable cleanup operation label
     * @param failure earlier failure, or {@code null}
     * @return first failure
     */
    private static Throwable close(AutoCloseable resource, String operation, Throwable failure) {
        if (resource == null) {
            return failure;
        }
        try {
            resource.close();
        } catch (Throwable closeFailure) {
            // Fatal errors remain fatal only when no earlier operation failure owns the failure tree.
            if (failure == null && closeFailure instanceof Error) {
                return closeFailure;
            }
            if (failure == null) {
                return JdbcExceptionTranslator.prepare(operation, closeFailure);
            }
            failure = JdbcExceptionTranslator.suppress(failure, operation, closeFailure);
        }
        return failure;
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
        throw new DataException(persistenceUnitDescription(unitName) + " could not execute its bootstrap scripts.",
                                JdbcExceptionTranslator.sanitize("executing bootstrap scripts", failure));
    }

    /**
     * Formats safe JDBC diagnostics.
     *
     * @param cause JDBC failure
     * @return diagnostic suffix
     */
    private static String sqlDiagnostic(SQLException cause) {
        String state = cause.getSQLState() == null ? "not provided" : "'" + cause.getSQLState() + "'";
        return " The SQL state is " + state + ", and the vendor code is " + cause.getErrorCode() + ".";
    }

    private static String persistenceUnitDescription(String unitName) {
        return Service.Named.DEFAULT_NAME.equals(unitName)
                ? "The JDBC persistence unit configuration"
                : "JDBC persistence unit '" + unitName + "'";
    }

    private static String scriptConfigKey(JdbcBootstrapResource.Role role) {
        return switch (role) {
        case INIT -> "init-script";
        case DROP -> "drop-script";
        };
    }

    /**
     * Returns a natural description of a safe bootstrap resource action.
     *
     * @param action resource action
     * @return diagnostic operation
     */
    private static String resourceDiagnosticOperation(String action) {
        return switch (action) {
        case "open" -> "opening a bootstrap resource";
        case "process" -> "processing a bootstrap resource";
        case "close" -> "closing a bootstrap resource";
        case "read" -> "reading a bootstrap resource";
        case "decode" -> "decoding a bootstrap resource";
        default -> throw new IllegalArgumentException("The bootstrap resource action is not recognized.");
        };
    }

    /**
     * Detached scripts which no longer retain configured resources.
     */
    static final class PreparedScripts {
        private final List<Script> scripts;

        private PreparedScripts(List<Script> scripts) {
            this.scripts = scripts;
        }
    }

    /**
     * Parsed script.
     *
     * @param descriptor safe resource descriptor
     * @param statements parsed statements
     */
    private record Script(JdbcBootstrapResource.Descriptor descriptor, List<String> statements) {
    }

    /**
     * Fixed bootstrap safety policy. Limits are implementation safeguards, not
     * configurable JDBC behavior, and are intentionally kept together so
     * configured and programmatic resource paths cannot diverge.
     *
     * @param maxResourceBytes maximum bytes in one resource
     * @param maxTotalBytes maximum bytes in one bootstrap plan
     * @param maxStatements maximum statements in one bootstrap plan
     */
    private record BootstrapPolicy(int maxResourceBytes, int maxTotalBytes, int maxStatements) {

        BootstrapPolicy {
            if (maxResourceBytes < 1 || maxTotalBytes < maxResourceBytes || maxStatements < 1) {
                throw new IllegalArgumentException("The JDBC bootstrap safety policy is invalid.");
            }
        }

        /**
         * Creates independent mutable accounting for one persistence unit.
         *
         * @return new bootstrap budget
         */
        BootstrapBudget newBudget() {
            return new BootstrapBudget(this);
        }
    }

    /**
     * Mutable byte and statement accounting for one detached bootstrap plan.
     * Loading is synchronous, so this state never crosses a thread boundary.
     */
    private static final class BootstrapBudget {

        private final BootstrapPolicy policy;
        private int bytes;
        private int statements;

        private BootstrapBudget(BootstrapPolicy policy) {
            this.policy = policy;
        }

        /**
         * Returns the aggregate byte capacity not yet consumed.
         *
         * @return remaining byte capacity
         */
        int remainingBytes() {
            return policy.maxTotalBytes() - bytes;
        }

        /**
         * Accounts for a resource after its bounded read succeeds.
         *
         * @param count resource byte count
         */
        void addBytes(int count) {
            bytes += count;
        }

        /**
         * Attempts to reserve one parsed statement.
         *
         * @return whether the statement remains within the plan limit
         */
        boolean addStatement() {
            if (statements == policy.maxStatements()) {
                return false;
            }
            statements++;
            return true;
        }
    }

    /**
     * Outcome of provider-owned bootstrap transaction completion. This state
     * describes database completion, not whether JDBC resource cleanup later
     * succeeded.
     */
    private enum BootstrapOutcome {

        /** No commit or successful rollback has completed. */
        NOT_STARTED,

        /** Commit completed successfully. */
        COMMITTED,

        /** Rollback completed before any commit attempt had an unknown result. */
        ROLLED_BACK,

        /** Completion cannot be proven; the connection must be invalidated. */
        UNKNOWN
    }

    /**
     * Internal bootstrap statement-boundary policy. Keeping the release to one
     * immutable profile avoids inferring database behavior from a datasource,
     * driver, JDBC URL, or script contents.
     */
    private enum ScriptBoundaryProfile {

        /**
         * Semicolon boundaries outside complete quoted and comment regions;
         * database-client boundary commands are not accepted.
         */
        PORTABLE
    }

    /**
     * Script lexical state.
     */
    private enum State {

        NORMAL("SQL text"),
        SINGLE_QUOTE("single-quoted string"),
        DOUBLE_QUOTE("double-quoted text"),
        BACKTICK_QUOTE("backtick-quoted text"),
        LINE_COMMENT("line comment"),
        BLOCK_COMMENT("block comment"),
        DOLLAR_QUOTE("dollar-quoted text"),
        Q_QUOTE("q-quoted text");

        private final String description;

        State(String description) {
            this.description = description;
        }

        String description() {
            return description;
        }
    }
}
