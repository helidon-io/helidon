/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.dbclient.common;

/**
 * DB CLient Configuration Properties.
 */
public class DbConfig {

    /**
     * DB CLient Configuration Properties Names.
     */
    public static final class Properties {

        /** Database connection URL. */
        public static final String URL = "url";
        /** Database connection user name. */
        public static final String USERNAME = "username";
        /** Database connection user password. */
        public static final String PASSWORD = "password";
        /** Turn on metrics for database connection pool (JDBC only). */
        public static final String METRICS = "metrics";

        /**
         * Disable constructor access for utility class.
         */
        private Properties() {
            throw new IllegalStateException("Instances of DbConfig.Properties are not allowed.");
        }

    }

    /**
     * DB CLient Configuration Properties Values.
     */
    public static final class Values {

        /** Expected property value to turn on metrics for database connection pool (JDBC only). */
        public static final String METRICS = "true";

        /**
         * Disable constructor access for utility class.
         */
        private Values() {
            throw new IllegalStateException("Instances of DbConfig.Values are not allowed.");
        }

    }

    /**
     * Disable constructor access for utility class.
     */
    private DbConfig() {
        throw new IllegalStateException("Instances of DbConfigProperties are not allowed.");
    }

}
