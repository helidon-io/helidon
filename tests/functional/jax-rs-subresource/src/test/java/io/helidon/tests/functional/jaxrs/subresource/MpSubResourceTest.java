/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.tests.functional.jaxrs.subresource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.logging.LogManager;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;

import io.helidon.microprofile.server.Server;
import io.helidon.jersey.connector.HelidonConnectorProvider;

import org.glassfish.jersey.client.JerseyClient;
import org.glassfish.jersey.client.spi.ConnectorProvider;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Unit test for {@link MpSubResource}.
 */
class MpSubResourceTest {
    private static Client client;
    private static Server server;
    private static WebTarget baseTarget;

    @BeforeAll
    static void initClass() throws IOException {
        LogManager.getLogManager().readConfiguration(MpSubResourceTest.class.getResourceAsStream("/logging.properties"));
        Main.main(new String[0]);
        server = Main.server();
        client = ClientBuilder.newClient();
        baseTarget = client.target("http://localhost:" + server.port());
    }

    @AfterAll
    static void destroyClass() {
        server.stop();
    }

    @Test
    void testMainPublic() {
        // first try public
        WebTarget target = baseTarget.path("/main/public");

        // annotated as @PermitAll - must be allowed for anybody
        assertOk(target.request().get(), "Hello World");
        assertOk(secureRequest(target, "john"), "Hello World");
        assertOk(secureRequest(target, "tom"), "Hello World");
        assertOk(secureRequest(target, "jack"), "Hello World");
        assertOk(secureRequest(target, "jill"), "Hello World");
    }

    @Test
    void testMainGetIt() {
        WebTarget target = baseTarget.path("/main");

        // roles allowed "user"
        assertNotAuthenticated(target.request().get());
        // john does not have roles
        assertNotAuthorized(secureRequest(target, "john"));
        // all bellow have correct role "user"
        assertOk(secureRequest(target, "tom"), "Hello World");
        assertOk(secureRequest(target, "jack"), "Hello World");
        assertOk(secureRequest(target, "jill"), "Hello World");
    }

    @Test
    void testSubNoSecurity() {
        WebTarget target = baseTarget.path("/main/sub/thename");

        // annotated as @PermitAll - must be allowed for anybody
        assertOk(target.request().get(), "Unsecured thename");
        assertOk(secureRequest(target, "john"), "Unsecured thename");
        assertOk(secureRequest(target, "tom"), "Unsecured thename");
        assertOk(secureRequest(target, "jack"), "Unsecured thename");
        assertOk(secureRequest(target, "jill"), "Unsecured thename");
    }

    @Test
    void testCombinationSecurity() {
        WebTarget target = baseTarget.path("/main/sub/thename/secure");

        // roles allowed "user"
        assertNotAuthenticated(target.request().get());
        // john does not have roles
        assertNotAuthorized(secureRequest(target, "john"));
        // tom does not have "sub" role
        assertNotAuthorized(secureRequest(target, "tom"));
        // jill does not have "admion" role
        assertNotAuthorized(secureRequest(target, "jill"));

        // bellow have correct roles "user,sub,admin"
        String okMessage = "Secured by parent thename";
        target = baseTarget.path("/main/sub/thename/parent");
        assertOk(secureRequest(target, "jack"), okMessage);

    }

    @Test
    void testSubSub() {
        WebTarget target = baseTarget.path("/main/sub/thename/sub/subsub");

        // not in subsub role
        assertNotAuthorized(secureRequest(target, "jill"));
        // he has it all...
        assertOk(secureRequest(target, "jack"), "Hello subsub");
    }

    @Test
    void testSubSubPermitAll() {
        WebTarget target = baseTarget.path("/main/sub/thename/sub");

        // @PermitAll
        String testString = "Hello World";
        assertOk(target.request().get(), testString);
        assertOk(secureRequest(target, "john"), testString);
        assertOk(secureRequest(target, "tom"), testString);
        assertOk(secureRequest(target, "jill"), testString);
        assertOk(secureRequest(target, "jack"), testString);
    }

    @Test
    void testSubParentSecurity() {
        WebTarget target = baseTarget.path("/main/sub/thename/parent");

        // roles allowed "user"
        assertNotAuthenticated(target.request().get());
        // john does not have roles
        assertNotAuthorized(secureRequest(target, "john"));
        // tom does not have "sub" role
        assertNotAuthorized(secureRequest(target, "tom"));
        // all bellow have correct role "user"
        String okMessage = "Secured by parent thename";

        assertOk(secureRequest(target, "jack"), okMessage);
        assertOk(secureRequest(target, "jill"), okMessage);
    }

    /**
     * Verifies that the {@code HelidonConnectorProvider} is being used
     * by Jersey.
     */
    @Test
    public void testConnectorLoaded() {
        JerseyClient jerseyClient = (JerseyClient) client;
        ConnectorProvider provider = jerseyClient.getConfiguration().getConnectorProvider();
        assertThat(provider, instanceOf(HelidonConnectorProvider.class));
    }

    private void assertOk(Response response, String expectedMessage) {
        assertThat(response.getStatus(), is(200));
        assertThat(response.readEntity(String.class), is(expectedMessage));
    }

    private void assertNotAuthorized(Response response) {
        assertThat("Should not be authorized", response.getStatus(), is(403));
    }

    private Response secureRequest(WebTarget target, String username) {
        String basicAuth = basicAuth(username);
        return target.request()
                .header("Authorization", "basic " + basicAuth)
                .get();
    }

    private String basicAuth(String username) {
        String clear = username + ":password";
        return Base64.getEncoder().encodeToString(clear.getBytes(StandardCharsets.UTF_8));
    }

    private void assertNotAuthenticated(Response response) {
        assertThat("Status must be 401", response.getStatus(), is(401));
    }
}