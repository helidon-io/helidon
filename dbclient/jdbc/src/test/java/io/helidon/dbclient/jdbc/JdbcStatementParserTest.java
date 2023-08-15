/*
 * Copyright (c) 2019, 2023 Oracle and/or its affiliates.
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

import java.util.List;

import io.helidon.dbclient.jdbc.JdbcStatement.Parser;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.collection.IsEmptyCollection.empty;

/**
 * Unit test for {@link JdbcStatement.Parser}.
 * Tests must cover as large automaton states space as possible.
 */
public class JdbcStatementParserTest {

    /**
     * Test simple SQL statement without parameters.
     * String parsing shall go trough all local transitions without leaving STMT and STR states.
     */
    @Test
    void testStatementWithNoParameter() {
        String stmtIn =
                """
                        SELECT *, 2 FROM table\r
                          WHERE name LIKE 'a?e%'
                        """;
        Parser parser = new Parser(stmtIn);
        String stmtOut = parser.convert();
        List<String> names = parser.namesOrder();
        assertThat(stmtOut, is(stmtIn));
        assertThat(names, empty());
    }
    
    /**
     * Test simple SQL statement with parameters.
     * Parameters names follow the same rules for identifiers defined in Section 4.4.1 of the JPA 2.0 specification
     */
    @Test
    void testStatementWithParameters() {
        String stmtIn =
                """
                        SELECT t.*, 'first' FROM table t\r
                          WHERE name = :my_n4m3
                           AND age > :ag3""";
        String stmtExp =
                """
                        SELECT t.*, 'first' FROM table t\r
                          WHERE name = ?
                           AND age > ?""";
        Parser parser = new Parser(stmtIn);
        String stmtOut = parser.convert();
        List<String> names = parser.namesOrder();
        assertThat(stmtOut, is(stmtExp));
        assertThat(names, contains("my_n4m3", "ag3"));
    }

    /**
     * Test simple SQL statement with parameters inside multi-line comment.
     * Only parameters outside comments shall be returned.
     */
    @Test
    void testStatementWithParametersInMultiLineCommnet() {
        String stmtIn =
                """
                        SELECT t.*, 'first' FROM table t /* Parameter for name is :n4me\r
                         and for age is :ag3 */
                          WHERE address IS NULL\r
                         AND name = :n4m3
                           AND age > :ag3""";
        String stmtExp =
                """
                        SELECT t.*, 'first' FROM table t /* Parameter for name is :n4me\r
                         and for age is :ag3 */
                          WHERE address IS NULL\r
                         AND name = ?
                           AND age > ?""";
        Parser parser = new Parser(stmtIn);
        String stmtOut = parser.convert();
        List<String> names = parser.namesOrder();
        assertThat(stmtOut, is(stmtExp));
        assertThat(names, contains("n4m3", "ag3"));
    }

    /**
     * Test simple SQL statement with parameters inside multi-line comment.
     * Only parameters outside comments shall be returned.
     */
    @Test
    void testStatementWithParametersInSingleLineCommnet() {
        String stmtIn =
                """
                        SELECT t.*, 'first' FROM table t -- Parameter for name is :n4me\r\r
                          WHERE address IS NULL\r
                         AND name = :myN4m3
                           AND age > :ag3""";
        String stmtExp =
                """
                        SELECT t.*, 'first' FROM table t -- Parameter for name is :n4me\r\r
                          WHERE address IS NULL\r
                         AND name = ?
                           AND age > ?""";
        Parser parser = new Parser(stmtIn);
        String stmtOut = parser.convert();
        List<String> names = parser.namesOrder();
        assertThat(stmtOut, is(stmtExp));
        assertThat(names, contains("myN4m3", "ag3"));
    }

    /**
     * Test simple SQL statement with valid and invalid name parameters.
     * Only parameters outside comments shall be returned.
     */
    @Test
    void testStatementWithValidandinvalidParameters() {
        String stmtIn =
                "SELECT p.firstName, p.secondName, a.street, a.towm" +
                "  FROM person p" +
                " INNER JOIN address a ON a.id = p.aid" +
                " WHERE p.age > :12age" +
                "   AND a.zip = :zip";
        String stmtExp =
                "SELECT p.firstName, p.secondName, a.street, a.towm" +
                "  FROM person p" +
                " INNER JOIN address a ON a.id = p.aid" +
                " WHERE p.age > :12age" +
                "   AND a.zip = ?";
        Parser parser = new Parser(stmtIn);
        String stmtOut = parser.convert();
        List<String> names = parser.namesOrder();
        assertThat(stmtOut, is(stmtExp));
        assertThat(names, contains("zip"));
    }

    @Test
    void testStatementWithUnderscores() {
        String stmtIn = """
                INSERT INTO example (created_at)
                      VALUES (:created_at)
                      RETURNING example_id, created_at;""";
        String stmtExp = """
                INSERT INTO example (created_at)
                      VALUES (?)
                      RETURNING example_id, created_at;""";
        Parser parser = new Parser(stmtIn);
        String stmtOut = parser.convert();
        List<String> names = parser.namesOrder();
        assertThat(stmtOut, is(stmtExp));
        assertThat(names, contains("created_at"));
    }

}
