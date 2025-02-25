/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.eureka;

import io.helidon.common.config.Config;
import io.helidon.integrations.eureka.EurekaRegistrationServerFeature;
import io.helidon.service.registry.Services;
import io.helidon.webclient.api.WebClient;
import io.helidon.webserver.WebServer;

import jakarta.json.JsonObject;
import jakarta.json.JsonString;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.helidon.common.media.type.MediaTypes.APPLICATION_JSON;
import static io.helidon.http.HeaderNames.ACCEPT_ENCODING;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

class EurekaRegistrationServerFeatureIT {

    /**
     * A {@link WebServer} emulating the usual Helidon Quickstart SE behavior that is used only as a vehicle for causing
     * its affiliated {@link EurekaRegistrationServerFeature} to be installed and started.
     */
    private WebServer ws;

    /**
     * A {@link WebClient} to use to verify that Eureka service instance registration took place successfully.
     */
    private WebClient wc;

    /**
     * Creates a new {@link EurekaRegistrationServerFeatureIT}.
     *
     * <p>Note that a Spring Eureka Server instance will have already been started by Maven Failsafe.</p>
     */
    EurekaRegistrationServerFeatureIT() {
        super();
    }

    /**
     * Sets up the verification {@link WebClient} and starts the {@link WebServer}.
     */
    @BeforeEach
    void beforeEach() {
        Config c = Services.get(Config.class);

        // Build the WebClient that will be used only by this test to verify that service instance registration
        // successfully occurred. For fidelity, use the same configuration as will be used by the ClientInfoConfig
        // class.
        this.wc = WebClient.builder()
            .config(c.get("helidon.server.features.eureka.client"))
            .sendExpectContinue(false) // Spring/Eureka can't handle it
            .build();

        // Configuration: ../../../../../resources/application.properties
        //
        // This is deliberately just the quickstart SE recipe; installing our server feature should be automagic if the
        // configuration is set up properly.
        //
        // The only difference is we use "helidon.server" as a root key instead of just "server" because the Spring
        // Eureka server uses a "server.port" property, and for convenience and ease of maintenance we want to share the
        // single application.properties classpath resource.
        this.ws = WebServer.builder()
            .config(c.get("helidon.server"))
            .routing(rb -> rb.get("/hello", (req, res) -> res.send("Hello World!")))
            .build();
        assertThat(this.ws.prototype().features().get(0), instanceOf(EurekaRegistrationServerFeature.class));
        this.ws.start();
    }

    @AfterEach
    void afterEach() {
        if (this.wc != null) {
            this.wc.closeResource();
        }
        if (this.ws != null) {
            this.ws.stop();
        }
    }

    @Test
    void test() throws InterruptedException {
        assertThat(this.ws.isRunning(), is(true));
        Thread.sleep(500L); // wait for the registration/renewal attempt to happen in the background
        try (var response = this.wc
             .get("/v2/apps/" + ((EurekaRegistrationServerFeature)this.ws.prototype()
                                 .features()
                                 .get(0))
                  .prototype()
                  .instanceInfo()
                  .appName())
             .accept(APPLICATION_JSON)
             .header(ACCEPT_ENCODING, "gzip")
             .request()) {
            assertThat(response.status().code(), is(200));
            assertThat(response.entity().hasEntity(), is(true));
            assertThat(((JsonString)response.entity()
                        .as(JsonObject.class)
                        .getValue("/application/instance/0/status"))
                       .getString(), is("UP"));
        }
    }

}
