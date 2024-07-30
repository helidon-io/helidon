/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
package io.helidon.docs.se.guides;

import io.helidon.common.context.Contexts;
import io.helidon.config.Config;
import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.health.DbClientHealthCheck;
import io.helidon.http.NotFoundException;
import io.helidon.http.Status;
import io.helidon.logging.common.LogConfig;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import io.helidon.webserver.observe.ObserveFeature;
import io.helidon.webserver.observe.health.HealthObserver;

import jakarta.json.JsonObject;

@SuppressWarnings("ALL")
class DbclientSnippets {

    // stub
    static class GreetService implements HttpService {
        @Override
        public void routing(HttpRules rules) {
        }
    }

    // stub
    static class LibraryService implements HttpService {
        @Override
        public void routing(HttpRules rules) {
        }
    }

    class Main {

        // tag::snippet_1[]
        public static void main(String[] args) {

            // load logging configuration
            LogConfig.configureRuntime();

            // initialize global config from default configuration
            Config config = Config.create();
            Config.global(config);

            DbClient dbClient = DbClient.create(config.get("db")); // <1>
            Contexts.globalContext().register(dbClient); // <2>

            HealthObserver healthObserver = HealthObserver.builder()
                    .useSystemServices(false)
                    .details(true)
                    .addCheck(DbClientHealthCheck.create(dbClient, config.get("db.health-check"))) // <3>
                    .build();

            ObserveFeature observe = ObserveFeature.builder()
                    .config(config.get("server.features.observe"))
                    .addObserver(healthObserver) // <4>
                    .build();

            WebServer server = WebServer.builder()
                    .config(config.get("server"))
                    .addFeature(observe) // <5>
                    .routing(Main::routing)
                    .build()
                    .start();

            System.out.println("WEB server is up! http://localhost:" + server.port() + "/simple-greet");
        }
        // end::snippet_1[]

        // stub
        static void routing(HttpRouting.Builder routing) {
        }
    }

    class Snippet2 {
        // tag::snippet_2[]
        public class LibraryService implements HttpService {

            private final DbClient dbClient;    // <1>

            LibraryService() {
                dbClient = Contexts.globalContext()
                        .get(DbClient.class)
                        .orElseGet(this::newDbClient); // <2>
                dbClient.execute()
                        .namedDml("create-table"); // <3>
            }

            private DbClient newDbClient() {
                return DbClient.create(Config.global().get("db"));
            }

            @Override
            public void routing(HttpRules rules) {
                // TODO
            }
        }
        // end::snippet_2[]
    }

    class Snippet3 implements HttpService {

        DbClient dbClient;

        // tag::snippet_3[]
        @Override
        public void routing(HttpRules rules) {
            rules
                    .get("/{name}", this::getBook)      // <1>
                    .put("/{name}", this::addBook)      // <2>
                    .delete("/{name}", this::deleteBook)   // <3>
                    .get("/json/{name}", this::getJsonBook); // <4>
        }
        // end::snippet_3[]

        // tag::snippet_4[]
        private void getBook(ServerRequest request,
                             ServerResponse response) {

            String bookName = request.path()
                    .pathParameters()
                    .get("name"); // <1>

            String bookInfo = dbClient.execute()
                    .namedGet("select-book", bookName)   // <2>
                    .map(row -> row.column("INFO").asString().get())
                    .orElseThrow(() -> new NotFoundException(
                            "Book not found: " + bookName)); // <3>
            response.send(bookInfo); // <4>
        }
        // end::snippet_4[]

        // tag::snippet_5[]
        private void getJsonBook(ServerRequest request,
                                 ServerResponse response) {

            String bookName = request.path()
                    .pathParameters()
                    .get("name");

            JsonObject bookJson = dbClient.execute()
                    .namedGet("select-book", bookName)
                    .map(row -> row.as(JsonObject.class))
                    .orElseThrow(() -> new NotFoundException(
                            "Book not found: " + bookName));
            response.send(bookJson);
        }
        // end::snippet_5[]

        // tag::snippet_6[]
        private void addBook(ServerRequest request,
                             ServerResponse response) {

            String bookName = request.path()
                    .pathParameters()
                    .get("name");

            String newValue = request.content().as(String.class);
            dbClient.execute()
                    .createNamedInsert("insert-book")
                    .addParam("name", bookName) // <1>
                    .addParam("info", newValue)
                    .execute();
            response.status(Status.CREATED_201).send(); // <2>
        }
        // end::snippet_6[]

        // tag::snippet_7[]
        private void deleteBook(ServerRequest request,
                                ServerResponse response) {

            String bookName = request.path()
                    .pathParameters()
                    .get("name");

            dbClient.execute().namedDelete("delete-book", bookName); // <1>
            response.status(Status.NO_CONTENT_204).send(); // <2>
        }
        // end::snippet_7[]
    }

    // tag::snippet_8[]
    static void routing(HttpRouting.Builder routing) {
        routing
                .register("/greet", new GreetService())
                .register("/library", new LibraryService()) // <1>
                .get("/simple-greet", (req, res) -> res.send("Hello World!"));
    }
    // end::snippet_8[]

}
