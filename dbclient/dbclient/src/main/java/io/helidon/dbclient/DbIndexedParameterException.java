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
 * A {@link RuntimeException} used by Helidon database client for indexed parameter of database statement.
 */
public class DbIndexedParameterException extends DbStatementException {

    private final int index;

    /**
     * Create a new exception for message, parameter index and statement.
     *
     * @param message descriptive message
     * @param index parameter index
     * @param statement statement being processed
     */
    public DbIndexedParameterException(String message, int index, String statement) {
        super(message, statement);
        this.index = index;
    }

    /**
     * Create a new exception for message, parameter index, statement and cause.
     *
     * @param message descriptive message
     * @param index parameter index
     * @param statement statement being processed
     * @param cause original throwable causing this exception
     */
    public DbIndexedParameterException(String message, int index, String statement, Throwable cause) {
        super(message, statement, cause);
        this.index = index;
    }

    /**
     * Index of parameter being processed.
     *
     * @return statement parameter index
     */
    public int index() {
        return index;
    }

}
