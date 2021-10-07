/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.demo.todos.backend;

import java.io.IOException;
import java.util.Base64;
import java.util.Properties;

import javax.inject.Inject;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;

import io.helidon.common.http.Http;
import io.helidon.config.mp.MpConfigSources;
import io.helidon.config.yaml.YamlMpConfigSource;
import io.helidon.microprofile.tests.junit5.Configuration;
import io.helidon.microprofile.tests.junit5.HelidonTest;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Session;
import org.cassandraunit.utils.EmbeddedCassandraServerHelper;
import org.eclipse.microprofile.config.spi.ConfigProviderResolver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@HelidonTest
@Configuration(useExisting = true)
class BackendTests {

    private final static String CASSANDRA_HOST = "127.0.0.1";

    @Inject
    private WebTarget webTarget;

    @BeforeAll
    static void init() throws IOException {
        Properties cassandraProperties = initCassandra();

        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        ConfigProviderResolver configResolver = ConfigProviderResolver.instance();

        org.eclipse.microprofile.config.Config mpConfig = configResolver.getBuilder()
                .withSources(YamlMpConfigSource.create(cl.getResource("test-application.yaml")),
                             MpConfigSources.create(cassandraProperties))
                .build();

        configResolver.registerConfig(mpConfig, null);
    }

    @AfterAll
    static void stopServer() {
        EmbeddedCassandraServerHelper.cleanEmbeddedCassandra();
    }

    private static Properties initCassandra() throws IOException {
        EmbeddedCassandraServerHelper.startEmbeddedCassandra(EmbeddedCassandraServerHelper.CASSANDRA_RNDPORT_YML_FILE, 20000L);
        Properties prop = new Properties();
        prop.put("cassandra.port", String.valueOf(EmbeddedCassandraServerHelper.getNativeTransportPort()));
        prop.put("cassandra.servers.host.host", CASSANDRA_HOST);

        Cluster cluster = Cluster.builder()
                .withoutMetrics()
                .addContactPoint(CASSANDRA_HOST)
                .withPort(EmbeddedCassandraServerHelper.getNativeTransportPort())
                .build();

        Session session = cluster.connect();
        session.execute("CREATE KEYSPACE backend WITH REPLICATION = {'class' : 'SimpleStrategy', 'replication_factor' : 1};");
        session.execute(
                "CREATE TABLE backend.backend (id ascii, user ascii, message ascii, completed Boolean, created timestamp, "
                        + "PRIMARY KEY (id));");
        session.execute("select * from backend.backend;");

        session.close();
        cluster.close();

        return prop;
    }

    @Test
    void testTodoScenario() {
        String basicAuth = "Basic " + Base64.getEncoder().encodeToString("john:password".getBytes());
        JsonObject todo = Json.createObjectBuilder()
                .add("title", "todo title")
                .build();

        // Add a new todo
        JsonObject returnedTodo = webTarget
                .path("/api/backend")
                .request(MediaType.APPLICATION_JSON_TYPE)
                .header(Http.Header.AUTHORIZATION, basicAuth)
                .post(Entity.json(todo), JsonObject.class);

        assertEquals("john", returnedTodo.getString("user"));
        assertEquals(todo.getString("title"), returnedTodo.getString("title"));

        // Get the todo created earlier
        JsonObject fromServer = webTarget.path("/api/backend/" + returnedTodo.getString("id"))
                .request(MediaType.APPLICATION_JSON_TYPE)
                .header(Http.Header.AUTHORIZATION, basicAuth)
                .get(JsonObject.class);

        assertEquals(returnedTodo, fromServer);

        // Update the todo created earlier
        JsonObject updatedTodo = Json.createObjectBuilder()
                .add("title", "updated title")
                .add("completed", false)
                .build();

        fromServer = webTarget.path("/api/backend/" + returnedTodo.getString("id"))
                .request(MediaType.APPLICATION_JSON_TYPE)
                .header(Http.Header.AUTHORIZATION, basicAuth)
                .put(Entity.json(updatedTodo), JsonObject.class);

        assertEquals(updatedTodo.getString("title"), fromServer.getString("title"));

        // Delete the todo created earlier
        fromServer = webTarget.path("/api/backend/" + returnedTodo.getString("id"))
                .request(MediaType.APPLICATION_JSON_TYPE)
                .header(Http.Header.AUTHORIZATION, basicAuth)
                .delete(JsonObject.class);

        assertEquals(returnedTodo.getString("id"), fromServer.getString("id"));

        // Get list of todos
        JsonArray jsonValues = webTarget.path("/api/backend")
                .request(MediaType.APPLICATION_JSON_TYPE)
                .header(Http.Header.AUTHORIZATION, basicAuth)
                .get(JsonArray.class);

        assertEquals(0, jsonValues.size(), "There should be no todos on server");
    }

}
