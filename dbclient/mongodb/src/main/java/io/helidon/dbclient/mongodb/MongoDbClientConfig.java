/*
 * Copyright (c) 2019, 2023 Oracle and/or its affiliates.
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
package io.helidon.dbclient.mongodb;

/**
 * Configuration parameters for MongoDB.
 */
public class MongoDbClientConfig {

    private final String url;
    private final String username;
    private final String password;
    private final String credDb;

    /**
     * Create a new instance.
     *
     * @param url      url
     * @param username username
     * @param password password
     * @param credDb   the database where the user is defined
     */
    MongoDbClientConfig(String url, String username, String password, String credDb) {
        this.url = url;
        this.username = username;
        this.password = password;
        this.credDb = credDb;
    }

    /**
     * Get the URL.
     *
     * @return url
     */
    String url() {
        return url;
    }

    /**
     * Get the username.
     *
     * @return username
     */
    String username() {
        return username;
    }

    /**
     * Get the password.
     *
     * @return password
     */
    String password() {
        return password;
    }

    /**
     * Get the database where the user is defined.
     *
     * @return credentials database
     */
    String credDb() {
        return credDb;
    }

}
