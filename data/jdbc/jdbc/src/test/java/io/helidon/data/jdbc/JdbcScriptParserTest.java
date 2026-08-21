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

import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

import javax.sql.DataSource;

import io.helidon.common.configurable.Resource;
import io.helidon.data.DataException;
import io.helidon.service.registry.Service;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcScriptParserTest {

    @Test
    void appliesDollarQuoteGrammarOnlyAtTokenBoundaries() {
        String content = "select $$empty;tag$$;"
                + "select ($tag$named;tag$tag$);"
                + "select identifier$tag$;"
                + "select $1$;"
                + "select $bad-tag$;";

        assertThat(parse(content),
                   is(List.of("select $$empty;tag$$",
                              "select ($tag$named;tag$tag$)",
                              "select identifier$tag$",
                              "select $1$",
                              "select $bad-tag$")));
    }

    @Test
    void preservesCommentsHintsWhitespaceAndLineEndings() {
        String first = "  /*+ INDEX(T IDX_T) */\r\nSELECT 1 -- keep ; here\r\n";
        String second = "\r\n-- leading comment\r\nSELECT 2 /*!40101 + 0 */";

        assertThat(parse(first + ";" + second + ";\r\n"), is(List.of(first, second)));
    }

    /**
     * Verifies that portable comments protect semicolons and consecutive
     * subtraction operators remain executable when separated by whitespace.
     */
    @Test
    void appliesThePortableLineCommentDelimiter() {
        assertThat(parse("UPDATE account SET balance = balance - -1;SELECT 2;"),
                   is(List.of("UPDATE account SET balance = balance - -1", "SELECT 2")));

        String commented = "SELECT 1 -- comment; retained\n";
        assertThat(parse(commented + ";SELECT 2;"), is(List.of(commented, "SELECT 2")));
    }

    /**
     * Verifies that an ambiguous database comment cannot activate text after
     * its semicolon as a separate bootstrap statement or acquire a connection.
     */
    @Test
    void rejectsAmbiguousDoubleDashBeforeAcquiringAConnection() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        String privateSql = "SELECT 1--PRIVATE_VALUE; DELETE FROM CUSTOMER;";

        DataException failure = assertThrows(DataException.class,
                                             () -> JdbcScriptRunner.execute(
                                                     "test",
                                                     dataSource,
                                                     List.of(Resource.create("private description", privateSql))));

        assertThat(failure.getMessage(), containsString("contains an ambiguous double dash"));
        assertThat(failure.getMessage(), containsString("profile is PORTABLE"));
        assertThat(failure.getMessage(), containsString("source offset is 8"));
        assertThat(failure.getMessage(), not(containsString("PRIVATE_VALUE")));
        assertThat(failure.getMessage(), not(containsString("private description")));
        verify(dataSource, never()).getConnection();
    }

    @Test
    void retainsExecutableCommentsAsStatements() {
        String executableComment = "/*!40101 SET @saved = 1 */";

        assertThat(parse(executableComment + ";"), is(List.of(executableComment)));
        assertThat(parse("/* ordinary comment */;"), is(List.of()));
    }

    @Test
    void protectsSemicolonsAndClientCommandsInsideCompleteQuotedRegions() {
        String oracle = "select q'[value; GO]' from dual";
        String postgres = "select $body$\nGO\nDELIMITER $$\n/\n$body$";
        String mysql = "select `semi;colon`";

        assertThat(parse(oracle + ";" + postgres + ";" + mysql + ";"),
                   is(List.of(oracle, postgres, mysql)));
    }

    @Test
    void rejectsNestedBlockComments() {
        String privateSql = "select PRIVATE_VALUE /* outer /* inner */";

        DataException failure = assertThrows(DataException.class, () -> parse(privateSql));

        assertThat(failure.getMessage(), containsString("contains a nested block comment"));
        assertThat(failure.getMessage(), containsString("profile is PORTABLE"));
        assertThat(failure.getMessage(), containsString("source offset"));
        assertThat(failure.getMessage(), not(containsString("PRIVATE_VALUE")));
    }

    @Test
    void reportsUnterminatedProtectedRegionsWithoutRenderingSql() {
        for (String content : List.of("select PRIVATE_VALUE /* unterminated",
                                      "select $$PRIVATE_VALUE",
                                      "select q'[PRIVATE_VALUE'")) {
            DataException failure = assertThrows(DataException.class, () -> parse(content));

            assertThat(failure.getMessage(), containsString("unterminated"));
            assertThat(failure.getMessage(), containsString("profile is PORTABLE"));
            assertThat(failure.getMessage(), containsString("source offset"));
            assertThat(failure.getMessage(), not(containsString("PRIVATE_VALUE")));
        }

        assertThat(parse("select identifier$tag$;"), is(List.of("select identifier$tag$")));
    }

    @Test
    void rejectsDatabaseClientStatementBoundaries() {
        for (String content : List.of("SELECT 1;\nGO\nSELECT 2;",
                                      "SELECT 1;\n/\n",
                                      "DELIMITER $$\nSELECT 1$$")) {
            DataException failure = assertThrows(DataException.class, () -> parse(content));

            assertThat(failure.getMessage(),
                       containsString("unsupported database-client statement boundary"));
            assertThat(failure.getMessage(), containsString("profile is PORTABLE"));
            assertThat(failure.getMessage(), containsString("source offset"));
            assertThat(failure.getMessage(), not(containsString(content)));
        }
    }

    @Test
    void rejectsAmbiguousBoundariesBeforeAcquiringAConnection() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        String privateSql = "SELECT PRIVATE_VALUE;\nGO\nSELECT 2;";

        DataException failure = assertThrows(DataException.class,
                                             () -> JdbcScriptRunner.execute(
                                                     "test",
                                                     dataSource,
                                                     List.of(Resource.create("private description", privateSql))));

        assertThat(failure.getMessage(), not(containsString("PRIVATE_VALUE")));
        assertThat(failure.getMessage(), not(containsString("private description")));
        verify(dataSource, never()).getConnection();
    }

    @Test
    void describesAnUnnamedPersistenceUnitWithoutInternalIdentifiers() {
        String privateSql = "SELECT PRIVATE_VALUE /* unterminated";

        DataException failure = assertThrows(
                DataException.class,
                () -> JdbcScriptRunner.statements(Service.Named.DEFAULT_NAME, privateSql));

        assertThat(failure.getMessage(),
                   is("The JDBC persistence unit configuration cannot load the configured text init script because it "
                              + "has an unterminated block comment. The statement boundary profile is PORTABLE, and "
                              + "the source offset is 36."));
        assertThat(failure.getMessage(), not(containsString("@default")));
        assertThat(failure.getMessage(), not(containsString("#1")));
        assertThat(failure.getMessage(), not(containsString("unspecified")));
        assertThat(failure.getMessage(), not(containsString("PRIVATE_VALUE")));
    }

    @Test
    void suppliesPreservedSqlDirectlyToJdbc() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        String sql = "  SELECT /*+ INDEX(T IDX_T) */ 1 -- retained\r\n";
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.execute(anyString())).thenReturn(false);
        when(statement.getLargeUpdateCount()).thenReturn(-1L);

        JdbcScriptRunner.execute("test",
                                 dataSource,
                                 List.of(Resource.create("preserved SQL", sql + ";")));

        verify(statement).execute(sql);
        verify(statement).close();
        verify(connection).close();
    }

    private static List<String> parse(String content) {
        return JdbcScriptRunner.statements("test", content);
    }
}
