/*
 * Copyright (c) 2024, 2025 Oracle and/or its affiliates.
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
 * Test set of basic JDBC delete calls in transaction.
 */
public interface TransactionTests {

    /**
     * Test set of basic JDBC delete calls in transaction.
     */
    interface TxDeleteTest {

        /**
         * Verify {@code createNamedDelete(String, String)} API method with ordered parameters.
         */
        void testCreateNamedDeleteStrStrOrderArgs();

        /**
         * Verify {@code createNamedDelete(String)} API method with named parameters.
         */
        void testCreateNamedDeleteStrNamedArgs();

        /**
         * Verify {@code createNamedDelete(String)} API method with ordered parameters.
         */
        void testCreateNamedDeleteStrOrderArgs();

        /**
         * Verify {@code createDelete(String)} API method with named parameters.
         */
        void testCreateDeleteNamedArgs();

        /**
         * Verify {@code createDelete(String)} API method with ordered parameters.
         */
        void testCreateDeleteOrderArgs();

        /**
         * Verify {@code namedDelete(String)} API method with ordered parameters.
         */
        void testNamedDeleteOrderArgs();

        /**
         * Verify {@code delete(String)} API method with ordered parameters.
         */
        void testDeleteOrderArgs();
    }

    /**
     * Test exceptional statements.
     */
    interface TxExceptionalStmtTest {

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
     * Test set of basic JDBC get calls in transaction.
     */
    interface TxGetTest {

        /**
         * Verify {@code createNamedGet(String, String)} API method with named parameters.
         */
        void testCreateNamedGetStrStrNamedArgs();

        /**
         * Verify {@code createNamedGet(String)} API method with named parameters.
         */
        void testCreateNamedGetStrNamedArgs();

        /**
         * Verify {@code createNamedGet(String)} API method with ordered parameters.
         */
        void testCreateNamedGetStrOrderArgs();

        /**
         * Verify {@code createGet(String)} API method with named parameters.
         */
        void testCreateGetNamedArgs();

        /**
         * Verify {@code createGet(String)} API method with ordered parameters.
         */
        void testCreateGetOrderArgs();

        /**
         * Verify {@code namedGet(String)} API method with ordered parameters passed directly to the {@code query} method.
         */
        void testNamedGetStrOrderArgs();

        /**
         * Verify {@code get(String)} API method with ordered parameters passed directly to the {@code query} method.
         */
        void testGetStrOrderArgs();
    }

    /**
     * Test set of basic JDBC inserts in transaction.
     */
    interface TxInsertTest {

        /**
         * Verify {@code createNamedInsert(String, String)} API method with named parameters.
         */
        void testCreateNamedInsertStrStrNamedArgs();

        /**
         * Verify {@code createNamedInsert(String)} API method with named parameters.
         */
        void testCreateNamedInsertStrNamedArgs();

        /**
         * Verify {@code createNamedInsert(String)} API method with ordered parameters.
         */
        void testCreateNamedInsertStrOrderArgs();

        /**
         * Verify {@code createInsert(String)} API method with named parameters.
         */
        void testCreateInsertNamedArgs();

        /**
         * Verify {@code createInsert(String)} API method with ordered parameters.
         */
        void testCreateInsertOrderArgs();

        /**
         * Verify {@code namedInsert(String)} API method with ordered parameters passed directly to the {@code insert} method.
         */
        void testNamedInsertOrderArgs();

        /**
         * Verify {@code insert(String)} API method with ordered parameters passed directly to the {@code insert} method.
         */
        void testInsertOrderArgs();

        /**
         * Verify {@code namedInsert(String)} API method with named parameters and returned generated keys.
         */
        void testInsertNamedArgsReturnedKeys() throws Exception;

        /**
         * Verify {@code namedInsert(String)} API method with named parameters and returned insert columns.
         */
        void testInsertNamedArgsReturnedColumns() throws Exception;

    }

    /**
     * Test set of basic JDBC queries in transaction.
     */
    interface TxQueriesTest {

        /**
         * Verify {@code createNamedQuery(String, String)} API method with ordered parameters.
         */
        void testCreateNamedQueryStrStrOrderArgs();

        /**
         * Verify {@code createNamedQuery(String)} API method with named parameters.
         */
        void testCreateNamedQueryStrNamedArgs();

        /**
         * Verify {@code createNamedQuery(String)} API method with ordered parameters.
         */
        void testCreateNamedQueryStrOrderArgs();

        /**
         * Verify {@code createQuery(String)} API method with named parameters.
         */
        void testCreateQueryNamedArgs();

        /**
         * Verify {@code createQuery(String)} API method with ordered parameters.
         */
        void testCreateQueryOrderArgs();

        /**
         * Verify {@code namedQuery(String)} API method with ordered parameters passed directly to the {@code namedQuery} method.
         */
        void testNamedQueryOrderArgs();

        /**
         * Verify {@code query(String)} API method with ordered parameters passed directly to the {@code query} method.
         */
        void testQueryOrderArgs();
    }

    /**
     * Test set of basic JDBC updates in transaction.
     */
    interface TxUpdateTest {

        /**
         * Verify {@code createNamedUpdate(String, String)} API method with named parameters.
         */
        void testCreateNamedUpdateStrStrNamedArgs();

        /**
         * Verify {@code createNamedUpdate(String)} API method with named parameters.
         */
        void testCreateNamedUpdateStrNamedArgs();

        /**
         * Verify {@code createNamedUpdate(String)} API method with ordered parameters.
         */
        void testCreateNamedUpdateStrOrderArgs();

        /**
         * Verify {@code createUpdate(String)} API method with named parameters.
         */
        void testCreateUpdateNamedArgs();

        /**
         * Verify {@code createUpdate(String)} API method with ordered parameters.
         */
        void testCreateUpdateOrderArgs();

        /**
         * Verify {@code namedUpdate(String)} API method with named parameters.
         */
        void testNamedUpdateNamedArgs();

        /**
         * Verify {@code update(String)} API method with ordered parameters.
         */
        void testUpdateOrderArgs();
    }
}
