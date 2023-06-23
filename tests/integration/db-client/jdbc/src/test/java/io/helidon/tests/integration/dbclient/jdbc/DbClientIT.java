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
package io.helidon.tests.integration.dbclient.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.config.ConfigValue;
import io.helidon.dbclient.DbClient;
import io.helidon.tests.integration.dbclient.common.ConfigIT;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

// Initial tests configuration and DbClient validation
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class DbClientIT {

    public static final Config CONFIG = Config.create(ConfigSources.classpath(ConfigIT.configFile()));
    public static final String SYS_PROPERTY_URL = "db.client.id.url";
    public static final String SYS_PROPERTY_USER = "db.client.id.user";
    public static final String SYS_PROPERTY_PASSWORD = "db.client.id.password";

    // Validate integration tests configuration
    @Test
    @Order(1)
    void testConfiguration() {
        // DB credentials properties must exist
        String url = System.getProperty(SYS_PROPERTY_URL);
        String user = System.getProperty(SYS_PROPERTY_USER);
        String password = System.getProperty(SYS_PROPERTY_PASSWORD);
        assertThat(url, is(notNullValue()));
        assertThat(user, is(notNullValue()));
        assertThat(password, is(notNullValue()));
        // Database configuration node must exist
        Config dbConfig = CONFIG.get("db");
        assertThat(dbConfig.exists(), is(true));
        // Connection configuration node must exist
        Config connection = dbConfig.get("connection");
        assertThat(connection.exists(), is(true));
        ConfigValue<String> urlConfig = connection.get("url").as(String.class);
        ConfigValue<String> userConfig = connection.get("username").as(String.class);
        ConfigValue<String> passwordConfig = connection.get("password").as(String.class);
        assertThat(urlConfig.isPresent(), is(true));
        assertThat(userConfig.isPresent(), is(true));
        assertThat(passwordConfig.isPresent(), is(true));
        assertThat(urlConfig.get(), is(url));
        assertThat(userConfig.get(), is(user));
        assertThat(passwordConfig.get(), is(password));
    }

    @Test
    @Order(2)
    void testDbClientConfigBuilder() {
        // Database configuration node
        Config dbConfig = CONFIG.get("db");
        assertThat(dbConfig.exists(), is(true));
        // Build DbClient from configuration node
        DbClient client = DbClient.builder()
                .config(dbConfig)
                .build();
        // Read configuration options to detect database type
        ConfigValue<String> sourceValue = dbConfig.get("source").as(String.class);
        ConfigValue<String> urlValue = dbConfig.get("connection.url").as(String.class);
        assertThat(sourceValue.isPresent(), is(true));
        assertThat(urlValue.isPresent(), is(true));
        String source = sourceValue.get();
        String url = urlValue.get();
        // Extract first 2 fields separated by ':' from DB URL, e.g. "jdbc:h2"
        Matcher matcher = Pattern.compile("(.*?:.*?):").matcher(url);
        assertThat(matcher.find(), is(true));
        String dbType = matcher.group(1);
        assertThat(dbType, is(notNullValue()));
        // Verify that DbClient type matches fields from URL
        assertThat(client.dbType(), is(dbType));
    }

    // Unwrap DbClient to JDBC pool Connection, verify that instance exists and close this connection
    @Test
    @Order(3)
    void testDbClientUnwrapConnection() throws SQLException {
        DbClient client = DbClient.builder()
                .config(CONFIG.get("db"))
                .build();
        Connection connection = client.unwrap(Connection.class);
        assertThat(connection, is(notNullValue()));
        connection.close();
    }

}
