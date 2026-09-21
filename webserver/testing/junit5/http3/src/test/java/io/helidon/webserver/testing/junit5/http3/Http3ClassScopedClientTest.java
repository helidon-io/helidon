/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.webserver.testing.junit5.http3;

import io.helidon.webclient.http3.Http3Client;
import io.helidon.webserver.ListenerConfig;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http3.Http3Config;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.SetUpServer;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ServerTest
@Http3ClientTls(resource = "client-truststore.p12")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Execution(ExecutionMode.SAME_THREAD)
class Http3ClassScopedClientTest {
    private static Http3Client highLevelClient;
    private static Http3LowLevelClient lowLevelClient;
    private static Http3Client previousMethodHighLevelClient;
    private static Http3LowLevelClient previousMethodLowLevelClient;
    private static Http3Client previousConstructorHighLevelClient;
    private static Http3LowLevelClient previousConstructorLowLevelClient;

    private final Http3Client constructorHighLevelClient;
    private final Http3LowLevelClient constructorLowLevelClient;

    Http3ClassScopedClientTest(Http3Client constructorHighLevelClient,
                               Http3LowLevelClient constructorLowLevelClient) {
        this.constructorHighLevelClient = constructorHighLevelClient;
        this.constructorLowLevelClient = constructorLowLevelClient;
    }

    @SetUpServer
    static void setUpServer(WebServerConfig.Builder serverBuilder) {
        serverBuilder.protocolsDiscoverServices(false);
    }

    @SetUpRoute
    static void routing(HttpRules rules, ListenerConfig.Builder listenerBuilder) {
        Http3AbstractTestingTest.configureListener(listenerBuilder, Http3Config.create());
        Http3AbstractTestingTest.addGreetingRoutes(rules, "class-scoped");
    }

    @BeforeAll
    static void clients(Http3Client highLevel, Http3LowLevelClient lowLevel) {
        highLevelClient = highLevel;
        lowLevelClient = lowLevel;
    }

    @Test
    @Order(1)
    void firstTestUsesClassAndMethodScopedClients(Http3Client methodHighLevelClient,
                                                  Http3LowLevelClient methodLowLevelClient) {
        assertClientsUsable();
        Http3AbstractTestingTest.assertGreeting(methodHighLevelClient, "class-scoped");
        assertThat(methodLowLevelClient.get("/greet").status(), is(200));
        previousMethodHighLevelClient = methodHighLevelClient;
        previousMethodLowLevelClient = methodLowLevelClient;
        Http3AbstractTestingTest.assertGreeting(constructorHighLevelClient, "class-scoped");
        assertThat(constructorLowLevelClient.get("/greet").status(), is(200));
        previousConstructorHighLevelClient = constructorHighLevelClient;
        previousConstructorLowLevelClient = constructorLowLevelClient;
    }

    @Test
    @Order(2)
    void secondTestKeepsClassClientsAndClosesPreviousMethodClients(Http3Client methodHighLevelClient,
                                                                  Http3LowLevelClient methodLowLevelClient) {
        assertClientsUsable();
        assertAll(
                () -> assertThrows(IllegalStateException.class,
                                   () -> previousMethodHighLevelClient.get("/greet").request()),
                () -> assertThrows(IllegalStateException.class,
                                   () -> previousMethodLowLevelClient.get("/greet")),
                () -> assertThat(constructorHighLevelClient, not(sameInstance(previousConstructorHighLevelClient))),
                () -> assertThat(constructorLowLevelClient, not(sameInstance(previousConstructorLowLevelClient))),
                () -> assertThrows(IllegalStateException.class,
                                   () -> previousConstructorHighLevelClient.get("/greet").request()),
                () -> assertThrows(IllegalStateException.class,
                                   () -> previousConstructorLowLevelClient.get("/greet")));
        Http3AbstractTestingTest.assertGreeting(methodHighLevelClient, "class-scoped");
        assertThat(methodLowLevelClient.get("/greet").status(), is(200));
        Http3AbstractTestingTest.assertGreeting(constructorHighLevelClient, "class-scoped");
        assertThat(constructorLowLevelClient.get("/greet").status(), is(200));
    }

    private static void assertClientsUsable() {
        assertAll(
                () -> Http3AbstractTestingTest.assertGreeting(highLevelClient, "class-scoped"),
                () -> assertThat(lowLevelClient.get("/greet").status(), is(200)));
    }
}
