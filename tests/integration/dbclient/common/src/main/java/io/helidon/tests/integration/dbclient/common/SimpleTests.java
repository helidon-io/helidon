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
 * Simple test.
 */
public interface SimpleTests {

    /**
     * Test set of basic JDBC delete calls.
     */
    interface DeleteTest {

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
     * Test set of basic JDBC DML statement calls.
     */
    interface DmlTest {

        /**
         * Verify {@code createNamedDmlStatement(String, String)} API method with insert with named parameters.
         */
        void testCreateNamedDmlWithInsertStrStrNamedArgs();

        /**
         * Verify {@code createNamedDmlStatement(String)} API method with insert with named parameters.
         */
        void testCreateNamedDmlWithInsertStrNamedArgs();

        /**
         * Verify {@code createNamedDmlStatement(String)} API method with insert with ordered parameters.
         */
        void testCreateNamedDmlWithInsertStrOrderArgs();

        /**
         * Verify {@code createDmlStatement(String)} API method with insert with named parameters.
         */
        void testCreateDmlWithInsertNamedArgs();

        /**
         * Verify {@code createDmlStatement(String)} API method with insert with ordered parameters.
         */
        void testCreateDmlWithInsertOrderArgs();

        /**
         * Verify {@code namedDml(String)} API method with insert with ordered parameters passed directly
         * to the {@code insert} method.
         */
        void testNamedDmlWithInsertOrderArgs();

        /**
         * Verify {@code dml(String)} API method with insert with ordered parameters passed directly
         * to the {@code insert} method.
         */
        void testDmlWithInsertOrderArgs();

        /**
         * Verify {@code createNamedDmlStatement(String, String)} API method with update with named parameters.
         */
        void testCreateNamedDmlWithUpdateStrStrNamedArgs();

        /**
         * Verify {@code createNamedDmlStatement(String)} API method with update with named parameters.
         */
        void testCreateNamedDmlWithUpdateStrNamedArgs();

        /**
         * Verify {@code createNamedDmlStatement(String)} API method with update with ordered parameters.
         */
        void testCreateNamedDmlWithUpdateStrOrderArgs();

        /**
         * Verify {@code createDmlStatement(String)} API method with update with named parameters.
         */
        void testCreateDmlWithUpdateNamedArgs();

        /**
         * Verify {@code createDmlStatement(String)} API method with update with ordered parameters.
         */
        void testCreateDmlWithUpdateOrderArgs();

        /**
         * Verify {@code namedDml(String)} API method with update with ordered parameters passed directly
         * to the {@code insert} method.
         */
        void testNamedDmlWithUpdateOrderArgs();

        /**
         * Verify {@code dml(String)} API method with update with ordered parameters passed directly
         * to the {@code insert} method.
         */
        void testDmlWithUpdateOrderArgs();

        /**
         * Verify {@code createNamedDmlStatement(String, String)} API method with delete with ordered parameters.
         */
        void testCreateNamedDmlWithDeleteStrStrOrderArgs();

        /**
         * Verify {@code createNamedDmlStatement(String)} API method with delete with named parameters.
         */
        void testCreateNamedDmlWithDeleteStrNamedArgs();

        /**
         * Verify {@code createNamedDmlStatement(String)} API method with delete with ordered parameters.
         */
        void testCreateNamedDmlWithDeleteStrOrderArgs();

        /**
         * Verify {@code createDmlStatement(String)} API method with delete with named parameters.
         */
        void testCreateDmlWithDeleteNamedArgs();

        /**
         * Verify {@code createDmlStatement(String)} API method with delete with ordered parameters.
         */
        void testCreateDmlWithDeleteOrderArgs();

        /**
         * Verify {@code namedDml(String)} API method with delete with ordered parameters.
         */
        void testNamedDmlWithDeleteOrderArgs();

        /**
         * Verify {@code dml(String)} API method with delete with ordered parameters.
         */
        void testDmlWithDeleteOrderArgs();
    }

    /**
     * Test set of basic JDBC get calls.
     */
    interface GetTest {

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
     * Test set of basic JDBC inserts.
     */
    interface InsertTest {

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
     * Test set of basic JDBC queries.
     */
    interface QueriesTest {

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
     * Test set of basic JDBC updates.
     */
    interface UpdateTest {

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