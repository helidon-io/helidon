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

@SuppressWarnings("ALL")
class UpgradeSnippets {

    /*
    void snippet_1(ServerRequest serverRequest) {
        // tag::snippet_1[]
        Span myNewSpan = GlobalTracer.get()
                .buildSpan("my-operation")
                .asChildOf(serverRequest.spanContext())
                .start();
        // end::snippet_1[]
    }
    */

    /*
    void snippet_2(ServerRequest serverRequest) {
        // tag::snippet_2[]
        Tracer.SpanBuilder spanBuilder = serverRequest.tracer()
                .buildSpan("my-operation");
        serverRequest.spanContext().ifPresent(spanBuilder::asChildOf);
        Span myNewSpan = spanBuilder.start();
        // end::snippet_2[]
    }
    */

    /*
    void snippet_3() {
        // tag::snippet_3[]
        Config.builder()
                // system properties with a polling strategy of 10 seconds
                .addSource(ConfigSources.systemProperties()
                                   .pollingStrategy(PollingStrategies.regular(Duration.ofSeconds(10))))
                // environment variables
                .addSource(ConfigSources.environmentVariables())
                // optional file config source with change watcher
                .addSource(ConfigSources.file(Paths.get("/conf/app.yaml"))
                                   .optional()
                                   .changeWatcher(FileSystemWatcher.create()))
                // classpath config source
                .addSource(ConfigSources.classpath("application.yaml"))
                // map config source (also supports polling strategy)
                .addSource(ConfigSources.create(Map.of("key", "value")))
                .build();
        // end::snippet_3[]
    }
    */

    /*
    void snippet_4() {
        // tag::snippet_4[]
        WebServer.builder()
                .addMediaSupport(JsonpSupport.create()) //registers reader and writer for Json-P
                .build();
        // end::snippet_4[]
    }
    */

    /*
    void snippet_5() {
        // tag::snippet_5[]
        WebServerTls.builder()
                .privateKey(KeyConfig.keystoreBuilder()
                                    .keystore(Resource.create("certificate.p12"))
                                    .keystorePassphrase("helidon"));
        // end::snippet_5[]
    }
    */

    /*
    void snippet_6() {
        // tag::snippet_6[]
        WebServer.builder(routing())
                .tls(webServerTls)
                .build();
        // end::snippet_6[]
    }
    */

    /*
    void snippet_7() {
        // tag::snippet_7[]
        WebServer.builder()
                .addSocket(SocketConfigurationBuilder.builder()
                                   .port(8001)
                                   .name("admin"));
        // end::snippet_7[]
    }
    */

    /*
    void snippet_8() {
        // tag::snippet_8[]
        WebServer.builder()
                .port(8001)
                .host("localhost")
                .routing(createRouting())
                .build();
        // end::snippet_8[]
    }
    */

}
