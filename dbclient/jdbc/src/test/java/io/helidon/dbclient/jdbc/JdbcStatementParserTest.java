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

import java.util.ArrayList;
import java.util.List;

import io.helidon.dbclient.jdbc.JdbcStatement.Parser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
                "SELECT *, 2 FROM table\r\n" +
                "  WHERE name LIKE 'a?e%'\n";
        Parser parser = new Parser(stmtIn);
        String stmtOut = parser.convert();
        List<String> names= parser.namesOrder();
        assertEquals(stmtIn, stmtOut);
        assertTrue(names.isEmpty());
    }
    
    /**
     * Test simple SQL statement with parameters.
     * Parameters contain both letters and numbers in proper order.
     */
    @Test
    void testStatementWithParameters() {
        String stmtIn =
                "SELECT t.*, 'first' FROM table t\r\n" +
                "  WHERE name = :n4m3\n" +
                "   AND age > :ag3";
        String stmtExp =
                "SELECT t.*, 'first' FROM table t\r\n" +
                "  WHERE name = ?\n" +
                "   AND age > ?";
        List<String> namesExp = new ArrayList<>(2);
        namesExp.add("n4m3");
        namesExp.add("ag3");
        Parser parser = new Parser(stmtIn);
        String stmtOut = parser.convert();
        List<String> names= parser.namesOrder();
        assertEquals(stmtExp, stmtOut);
        assertEquals(namesExp, names);
    }

    /**
     * Test simple SQL statement with parameters inside multi-line comment.
     * Only parameters outside comments shall be returned.
     */
    @Test
    void testStatementWithParametersInMultiLineCommnet() {
        String stmtIn =
                "SELECT t.*, 'first' FROM table t /* Parameter for name is :n4me\r\n" +
                " and for age is :ag3 */\n" +
                "  WHERE address IS NULL\r\n" +
                " AND name = :n4m3\n" +
                "   AND age > :ag3";
        String stmtExp =
                "SELECT t.*, 'first' FROM table t /* Parameter for name is :n4me\r\n" +
                " and for age is :ag3 */\n" +
                "  WHERE address IS NULL\r\n" +
                " AND name = ?\n" +
                "   AND age > ?";
        List<String> namesExp = new ArrayList<>(2);
        namesExp.add("n4m3");
        namesExp.add("ag3");
        Parser parser = new Parser(stmtIn);
        String stmtOut = parser.convert();
        List<String> names= parser.namesOrder();
        assertEquals(stmtExp, stmtOut);
        assertEquals(namesExp, names);
    }

    /**
     * Test simple SQL statement with parameters inside multi-line comment.
     * Only parameters outside comments shall be returned.
     */
    @Test
    void testStatementWithParametersInSingleLineCommnet() {
        String stmtIn =
                "SELECT t.*, 'first' FROM table t -- Parameter for name is :n4me\r\r\n" +
                "  WHERE address IS NULL\r\n" +
                " AND name = :myN4m3\n" +
                "   AND age > :ag3";
        String stmtExp =
                "SELECT t.*, 'first' FROM table t -- Parameter for name is :n4me\r\r\n" +
                "  WHERE address IS NULL\r\n" +
                " AND name = ?\n" +
                "   AND age > ?";
        List<String> namesExp = new ArrayList<>(2);
        namesExp.add("myN4m3");
        namesExp.add("ag3");
        Parser parser = new Parser(stmtIn);
        String stmtOut = parser.convert();
        List<String> names= parser.namesOrder();
        assertEquals(stmtExp, stmtOut);
        assertEquals(namesExp, names);
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
       List<String> namesExp = new ArrayList<>(2);
        namesExp.add("zip");
        Parser parser = new Parser(stmtIn);
        String stmtOut = parser.convert();
        List<String> names= parser.namesOrder();
        assertEquals(stmtExp, stmtOut);
        assertEquals(namesExp, names);
    }

}
