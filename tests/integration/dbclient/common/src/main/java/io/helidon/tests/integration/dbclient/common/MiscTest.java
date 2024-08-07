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
 * Miscellaneous test.
 */
public interface MiscTest {

    /**
     * Source data verification.
     */
    void testFlowControl();

    /**
     * Check that statement interceptor was called before statement execution.
     */
    void testStatementInterceptor();

    /**
     * Verify insertion of using indexed mapping.
     */
    void testInsertWithOrderMapping();

    /**
     * Verify insertion of using named mapping.
     */
    void testInsertWithNamedMapping();

    /**
     * Verify update of using indexed mapping.
     */
    void testUpdateWithOrderMapping();

    /**
     * Verify update of using named mapping.
     */
    void testUpdateWithNamedMapping();

    /**
     * Verify delete of using indexed mapping.
     */
    void testDeleteWithOrderMapping();

    /**
     * Verify delete of using named mapping.
     */
    void testDeleteWithNamedMapping();

    /**
     * Verify query of as a result using mapping.
     */
    void testQueryWithMapping();

    /**
     * Verify get of as a result using mapping.
     */
    void testGetWithMapping();
}
