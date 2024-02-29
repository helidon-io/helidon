/*
 * Copyright (c) 2021, 2024 Oracle and/or its affiliates.
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

package io.helidon.examples.todos.backend;

import java.util.Base64;
import java.util.Properties;

import io.helidon.config.mp.MpConfigSources;
import io.helidon.config.yaml.mp.YamlMpConfigSource;
import io.helidon.http.HeaderNames;
import io.helidon.microprofile.testing.junit5.Configuration;
import io.helidon.microprofile.testing.junit5.HelidonTest;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Session;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.spi.ConfigProviderResolver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.testcontainers.containers.CassandraContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@Testcontainers
@HelidonTest
@Configuration(useExisting = true)
@EnabledOnOs(OS.LINUX) // due to usage of docker with testcontainers, only Linux is enabled by default
class BackendTests {
    @Container
    static final CassandraContainer<?> CASSANDRA_CONTAINER = new CassandraContainer<>("cassandra:3.11.2")
            .withReuse(true);

    @Inject
    private WebTarget webTarget;

    @BeforeAll
    static void init() {
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
    }

    private static Properties initCassandra() {
        String host = CASSANDRA_CONTAINER.getHost();
        Integer port = CASSANDRA_CONTAINER.getMappedPort(CassandraContainer.CQL_PORT);

        Properties prop = new Properties();
        prop.put("cassandra.port", String.valueOf(port));
        prop.put("cassandra.servers.host.host", host);

        Cluster cluster = Cluster.builder()
                .withoutMetrics()
                .addContactPoint(host)
                .withPort(port)
                .build();

        Session session = cluster.newSession();
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
        String basicAuth = "Basic " + Base64.getEncoder().encodeToString("john:changeit".getBytes());
        JsonObject todo = Json.createObjectBuilder()
                .add("title", "todo title")
                .build();

        // Add a new todo
        JsonObject returnedTodo = webTarget
                .path("/api/backend")
                .request(MediaType.APPLICATION_JSON_TYPE)
                .header(HeaderNames.AUTHORIZATION.defaultCase(), basicAuth)
                .post(Entity.json(todo), JsonObject.class);

        assertThat(returnedTodo.getString("user"), is("john"));
        assertThat(returnedTodo.getString("title"), is(todo.getString("title")));

        // Get the todo created earlier
        JsonObject fromServer = webTarget.path("/api/backend/" + returnedTodo.getString("id"))
                .request(MediaType.APPLICATION_JSON_TYPE)
                .header(HeaderNames.AUTHORIZATION.defaultCase(), basicAuth)
                .get(JsonObject.class);

        assertThat(fromServer, is(returnedTodo));

        // Update the todo created earlier
        JsonObject updatedTodo = Json.createObjectBuilder()
                .add("title", "updated title")
                .add("completed", false)
                .build();

        fromServer = webTarget.path("/api/backend/" + returnedTodo.getString("id"))
                .request(MediaType.APPLICATION_JSON_TYPE)
                .header(HeaderNames.AUTHORIZATION.defaultCase(), basicAuth)
                .put(Entity.json(updatedTodo), JsonObject.class);

        assertThat(fromServer.getString("title"), is(updatedTodo.getString("title")));

        // Delete the todo created earlier
        fromServer = webTarget.path("/api/backend/" + returnedTodo.getString("id"))
                .request(MediaType.APPLICATION_JSON_TYPE)
                .header(HeaderNames.AUTHORIZATION.defaultCase(), basicAuth)
                .delete(JsonObject.class);

        assertThat(fromServer.getString("id"), is(returnedTodo.getString("id")));

        // Get list of todos
        JsonArray jsonValues = webTarget.path("/api/backend")
                .request(MediaType.APPLICATION_JSON_TYPE)
                .header(HeaderNames.AUTHORIZATION.defaultCase(), basicAuth)
                .get(JsonArray.class);

        assertThat("There should be no todos on server", jsonValues.size(), is(0));
    }

}
