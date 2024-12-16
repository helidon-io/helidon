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
 * Observability test.
 */
public interface ObservabilityTest {

    /**
     * Read and check Database Client health status from Helidon Web Server.
     */
    void testHttpHealthNoDetails();

    /**
     * Read and check Database Client health status from Helidon Web Server.
     */
    void testHttpHealthDetails();

    /**
     * Read and check Database Client metrics from Helidon Web Server.
     */
    void testHttpMetrics();

    /**
     * Verify health check implementation with default settings.
     */
    void testHealthCheck();

    /**
     * Verify health check implementation with builder and custom name.
     */
    void testHealthCheckWithName();

    /**
     * Verify health check implementation using custom DML named statement.
     */
    void testHealthCheckWithCustomNamedDML();

    /**
     * Verify health check implementation using custom DML statement.
     */
    void testHealthCheckWithCustomDML();

    /**
     * Verify health check implementation using custom query named statement.
     */
    void testHealthCheckWithCustomNamedQuery();

    /**
     * Verify health check implementation using custom query statement.
     */
    void testHealthCheckWithCustomQuery();

}
