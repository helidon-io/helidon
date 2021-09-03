/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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
 * Usual supported statement types.
 */
public enum DbStatementType {
    /**
     * Query is statement that returns zero or more results.
     */
    QUERY("q"),
    /**
     * Get is a statement that returns zero or one results.
     */
    GET("g"),
    /**
     * Insert is a statements that creates new records.
     */
    INSERT("i"),
    /**
     * Update is a statement that updates existing records.
     */
    UPDATE("u"),
    /**
     * Delete is a statement that deletes existing records.
     */
    DELETE("d"),
    /**
     * Generic DML statement.
     */
    DML("dml"),
    /**
     * Database command not related to a specific collection.
     */
    COMMAND("c");

    private final String prefix;

    DbStatementType(String prefix) {
        this.prefix = prefix;
    }

    /**
     * Short prefix of this type.
     * This is used when generating a name for an unnamed statement.
     *
     * @return short prefix defining this type (should be very short)
     */
    public String prefix() {
        return prefix;
    }
}
