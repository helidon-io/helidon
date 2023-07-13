/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
package io.helidon.dbclient;

/**
 * Statement parameters.
 */
public class DbStatementParameters {

    /**
     * Undefined parameters (neither named or indexed).
     */
    public static final DbStatementParameters UNDEFINED = new DbStatementParameters();

    /**
     * Create a new instance.
     */
    DbStatementParameters() {
        // package-private to restrict implementations
    }

    /**
     * Add next parameter to the list of ordered parameters (e.g. the ones that use {@code ?} in SQL).
     *
     * @param parameter next parameter to set on this statement
     * @return updated db statement
     */
    public DbStatementParameters addParam(Object parameter) {
        throw new DbClientException("Cannot use indexed parameters.");
    }

    /**
     * Add next parameter to the map of named parameters (e.g. the ones that use {@code :name} in Helidon
     * JDBC SQL integration).
     *
     * @param name      name of parameter
     * @param parameter value of parameter
     * @return updated db statement
     */
    public DbStatementParameters addParam(String name, Object parameter) {
        throw new DbClientException("Cannot use named parameters.");
    }
}
