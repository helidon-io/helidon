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

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Session;
import io.helidon.common.http.Http;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.media.jsonp.JsonpSupport;
import io.helidon.microprofile.server.Server;
import io.helidon.webclient.WebClient;
import org.cassandraunit.utils.EmbeddedCassandraServerHelper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Properties;

import static io.helidon.config.ConfigSources.classpath;

class BackendTests {

    private static WebClient client;
    private static Server server;
    private final static String CASSANDRA_HOST = "127.0.0.1";

    @BeforeAll
    static void init() throws IOException {
        Properties cassandraProperties = initCassandra();

        Config config = Config.builder()
                .sources(List.of(
                        classpath("test-application.yaml"),
                        ConfigSources.create(cassandraProperties)
                ))
                .build();

        server = Server.builder()
                .config(config)
                .build();

        server.start();

        client = WebClient.builder()
                .baseUri("http://0.0.0.0:" + server.port())
                .addMediaSupport(JsonpSupport.create())
                .build();
    }

    @AfterAll
    static void stopServer() {
        EmbeddedCassandraServerHelper.cleanEmbeddedCassandra();
        server.stop();
    }

    private static Properties initCassandra() throws IOException {
        EmbeddedCassandraServerHelper.startEmbeddedCassandra(EmbeddedCassandraServerHelper.CASSANDRA_RNDPORT_YML_FILE, 20000L);
        Properties prop = new Properties();
        prop.put("cassandra.port", EmbeddedCassandraServerHelper.getNativeTransportPort());
        prop.put("cassandra.servers.host.host", CASSANDRA_HOST);

        Cluster cluster  = Cluster.builder()
                .withoutMetrics()
                .addContactPoint(CASSANDRA_HOST)
                .withPort(EmbeddedCassandraServerHelper.getNativeTransportPort())
                .build();

        Session session = cluster.connect();
        session.execute("CREATE KEYSPACE backend WITH REPLICATION = {'class' : 'SimpleStrategy', 'replication_factor' : 1};");
        session.execute("CREATE TABLE backend.backend (id ascii, user ascii, message ascii, completed Boolean, created timestamp, PRIMARY KEY (id));");
        session.execute("select * from backend.backend;");

        session.close();
        cluster.close();

        return prop;
    }

    @Test
    void testTODOScenario() {
        final String encodingID = Base64.getEncoder().encodeToString("john:password".getBytes());
        JsonObject todo = Json.createObjectBuilder()
                .add("title",  "todo title")
                .build();

        // Add a new todo
        final JsonObject returnedTodo = client.post()
                .path("/api/backend")
                .headers(headers ->  {
                    headers.add(Http.Header.AUTHORIZATION, "Basic " + encodingID);
                    return headers;
                })
                .submit(todo, JsonObject.class)
                .await();

        Assertions.assertEquals("john", returnedTodo.getString("user"));
        Assertions.assertEquals(todo.getString("title"), returnedTodo.getString("title"));

        // Get the todo created earlier
        client.get()
                .path("/api/backend/" + returnedTodo.getString("id"))
                .headers(headers ->  {
                    headers.add(Http.Header.AUTHORIZATION, "Basic " + encodingID);
                    return headers;
                })
                .request(JsonObject.class)
                .thenAccept(jsonObject -> {
                    Assertions.assertEquals(returnedTodo, jsonObject);
                })
                .await();

        // Update the todo created earlier
        JsonObject updatedTODO = Json.createObjectBuilder()
                .add("title",  "updated title")
                .add("completed", false)
                .build();

        client.put()
                .path("/api/backend/" + returnedTodo.getString("id"))
                .headers(headers ->  {
                    headers.add(Http.Header.AUTHORIZATION, "Basic " + encodingID);
                    return headers;
                })
                .submit(updatedTODO, JsonObject.class)
                .thenAccept(jsonObject -> {
                    Assertions.assertEquals(updatedTODO.getString("title"), jsonObject.getString("title"));
                })
                .await();

        // Delete the todo created earlier
        client.delete()
                .path("/api/backend/" + returnedTodo.getString("id"))
                .headers(headers ->  {
                    headers.add(Http.Header.AUTHORIZATION, "Basic " + encodingID);
                    return headers;
                })
                .request(JsonObject.class)
                .thenAccept(jsonObject -> {
                    Assertions.assertEquals(returnedTodo.getString("id"), jsonObject.getString("id"));
                })
                .await();

        // Get list of todos
        client.get()
                .path("/api/backend")
                .headers(headers ->  {
                    headers.add(Http.Header.AUTHORIZATION, "Basic " + encodingID);
                    return headers;
                })
                .request(JsonArray.class)
                .thenAccept(jsonValues -> {
                    Assertions.assertEquals(0, jsonValues.size());
                })
                .await();
    }

}
