/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.dbclient.common;

/**
 * Statement tests.
 */
public interface StatementTests {

    /**
     * Test exceptional states.
     */
    interface StmtExceptionalTest {

        /**
         * Verify that execution of query with non-existing named statement throws an exception.
         */
        void testCreateNamedQueryNonExistentStmt();

        /**
         * Verify that execution of query with both named and ordered arguments throws an exception.
         */
        void testCreateNamedQueryNamedAndOrderArgsWithoutArgs();

        /**
         * Verify that execution of query with both named and ordered arguments throws an exception.
         */
        void testCreateNamedQueryNamedAndOrderArgsWithArgs();

        /**
         * Verify that execution of query with named arguments throws an exception while trying to set ordered argument.
         */
        void testCreateNamedQueryNamedArgsSetOrderArg();

        /**
         * Verify that execution of query with ordered arguments throws an exception while trying to set named argument.
         */
        void testCreateNamedQueryOrderArgsSetNamedArg();
    }

    /**
     * Test DbStatementGet methods.
     */
    interface StmtGetTest {

        /**
         * Verify {@code params(Object... parameters)} parameters setting method.
         */
        void testGetArrayParams();

        /**
         * Verify {@code params(List<?>)} parameters setting method.
         */
        void testGetListParams();

        /**
         * Verify {@code params(Map<?>)} parameters setting method.
         */
        void testGetMapParams();

        /**
         * Verify {@code addParam(Object parameter)} parameters setting method.
         */
        void testGetOrderParam();

        /**
         * Verify {@code addParam(String name, Object parameter)} parameters setting method.
         */
        void testGetNamedParam();

        /**
         * Verify {@code namedParam(Object parameters)} mapped parameters setting method.
         */
        void testGetMappedNamedParam();

        /**
         * Verify {@code indexedParam(Object parameters)} mapped parameters setting method.
         */
        void testGetMappedOrderParam();
    }

    /**
     * Test DbStatementQuery methods.
     */
    interface StmtQueryTest {

        /**
         * Verify {@code params(Object... parameters)} parameters setting method.
         */
        void testQueryArrayParams();

        /**
         * Verify {@code params(List<?>)} parameters setting method.
         */
        void testQueryListParams();

        /**
         * Verify {@code params(Map<?>)} parameters setting method.
         */
        void testQueryMapParams();

        /**
         * Verify {@code params(Map<?>)} with missing parameters.
         */
        void testQueryMapMissingParams();

        /**
         * Verify {@code addParam(Object parameter)} parameters setting method.
         */
        void testQueryOrderParam();

        /**
         * Verify {@code addParam(String name, Object parameter)} parameters setting method.
         */
        void testQueryNamedParam();

        /**
         * Verify {@code namedParam(Object parameters)} mapped parameters setting method.
         */
        void testQueryMappedNamedParam();

        /**
         * Verify {@code indexedParam(Object parameters)} mapped parameters setting method.
         */
        void testQueryMappedOrderParam();
    }

    /**
     * Test DbStatementDml methods.
     */
    interface StmtDmlTest {

        /**
         * Verify {@code params(Object... parameters)} parameters setting method.
         */
        void testDmlArrayParams();

        /**
         * Verify {@code params(List<?>)} parameters setting method.
         */
        void testDmlListParams();

        /**
         * Verify {@code params(Map<?>)} parameters setting method.
         */
        void testDmlMapParams();

        /**
         * Verify {@code addParam(Object parameter)} parameters setting method.
         */
        void testDmlOrderParam();

        /**
         * Verify {@code addParam(String name, Object parameter)} parameters setting method.
         */
        void testDmlNamedParam();

        /**
         * Verify {@code namedParam(Object parameters)} mapped parameters setting method.
         */
        void testDmlMappedNamedParam();

        /**
         * Verify {@code indexedParam(Object parameters)} mapped parameters setting method.
         */
        void testDmlMappedOrderParam();
    }
}
