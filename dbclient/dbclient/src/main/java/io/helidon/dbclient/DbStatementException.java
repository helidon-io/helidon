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
 * A {@link DbClientException} used for database statement.
 */
public class DbStatementException extends DbClientException {

    private final String statement;

    /**
     * Create a new exception for message and statement.
     *
     * @param message descriptive message
     * @param statement statement being processed
     */
    public DbStatementException(String message, String statement) {
        super(message);
        this.statement = statement;
    }

    /**
     * Create a new exception for message, statement and cause.
     *
     * @param message descriptive message
     * @param statement statement being processed
     * @param cause original throwable causing this exception
     */
    public DbStatementException(String message, String statement, Throwable cause) {
        super(message, cause);
        this.statement = statement;
    }

    /**
     * Database statement that caused an exception.
     *
     * @return database statement
     */
    public String statement() {
        return statement;
    }

}
