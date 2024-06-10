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
package io.helidon.docs.mp.guides;

import java.util.concurrent.atomic.AtomicReference;

import io.helidon.config.Config;
import io.helidon.microprofile.server.Server;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import static io.helidon.config.ConfigSources.classpath;
import static io.helidon.config.ConfigSources.file;

@SuppressWarnings("ALL")
class ConfigSnippets {

    class Snippet1 {
        // tag::snippet_1[]
        public final class Main {

            private Main() {
            } // <1>

            public static void main(final String[] args) {
                Server server = startServer();
                System.out.println("http://localhost:" + server.port() + "/greet");
            }

            static Server startServer() {
                return Server.create().start(); // <2>
            }

        }
        // end::snippet_1[]
    }

    class Snippet2 {
        // tag::snippet_2[]
        static Server startServer() {
            return Server.create().start(); // <1>
        }
        // end::snippet_2[]
    }

    class Snippet3 {

        // tag::snippet_3[]
        @ApplicationScoped // <1>
        public class GreetingProvider {
            private final AtomicReference<String> message = new AtomicReference<>(); // <2>

            @Inject
            public GreetingProvider(@ConfigProperty(name = "app.greeting") String message) {   // <3>
                this.message.set(message);
            }

            String getMessage() {
                return message.get();
            }

            void setMessage(String message) {
                this.message.set(message);
            }
        }
        // end::snippet_3[]
    }

    class Snippet4 {

        // tag::snippet_4[]
        @ApplicationScoped
        public class GreetingProvider {

            @Inject
            @ConfigProperty(name = "app.greeting") // <1>
            private volatile String message; // <2>

            String getMessage() {
                return message;
            }

            void setMessage(String message) {
                this.message = message;
            }
        }
        // end::snippet_4[]
    }

    class Snippet5 {

        // tag::snippet_5[]
        @ApplicationScoped
        public class GreetingProvider {
            private final AtomicReference<String> message = new AtomicReference<>();

            @Inject // <1>
            public GreetingProvider(Config config) {
                String message = config.get("app.greeting").asString().get(); // <2>
                this.message.set(message);
            }

            String getMessage() {
                return message.get();
            }

            void setMessage(String message) {
                this.message.set(message);
            }
        }
        // end::snippet_5[]
    }

    class Snippet6 {

        // tag::snippet_6[]
        @ApplicationScoped
        public class GreetingProvider {
            private final AtomicReference<String> message = new AtomicReference<>();
            private final AtomicReference<String> sender = new AtomicReference<>();

            @Inject
            Config config;

            public void onStartUp(@Observes @Initialized(ApplicationScoped.class) Object init) {
                Config appNode = config.get("app.greeting"); // <1>
                message.set(appNode.get("message").asString().get());  // <2>
                sender.set(appNode.get("sender").asString().get());   // <3>
            }

            String getMessage() {
                return sender.get() + " says " + message.get();
            }

            void setMessage(String message) {
                this.message.set(message);
            }
        }
        // end::snippet_6[]
    }

    class Snippet7 {

        // tag::snippet_7[]
        private static Config buildConfig() {
            return Config.builder()
                    .sources(
                            file("/etc/config/config-file.properties").optional(), // <1>
                            classpath("META-INF/microprofile-config.properties")) // <2>
                    .build();
        }
        // end::snippet_7[]
    }

    class Snippet8 {

        // tag::snippet_8[]
        @ApplicationScoped
        public class GreetingProvider {

            @Inject
            @ConfigProperty(name = "app.greeting") // <1>
            private volatile String message; // <2>

            String getMessage() {
                return message;
            }

            void setMessage(String message) {
                this.message = message;
            }
        }
        // end::snippet_8[]
    }

}
