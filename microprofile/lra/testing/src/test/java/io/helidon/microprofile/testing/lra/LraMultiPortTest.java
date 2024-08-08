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

package io.helidon.microprofile.testing.lra;

import java.net.URI;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import io.helidon.lra.coordinator.Lra;
import io.helidon.microprofile.server.RoutingName;
import io.helidon.microprofile.server.RoutingPath;
import io.helidon.microprofile.testing.junit5.AddBean;
import io.helidon.microprofile.testing.junit5.AddConfig;
import io.helidon.microprofile.testing.junit5.HelidonTest;
import io.helidon.microprofile.testing.junit5.Socket;
import io.helidon.webserver.http.HttpService;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.lra.annotation.Compensate;
import org.eclipse.microprofile.lra.annotation.Complete;
import org.eclipse.microprofile.lra.annotation.LRAStatus;
import org.eclipse.microprofile.lra.annotation.ws.rs.LRA;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;

@HelidonTest
@AddConfig(key = "server.sockets.0.name", value = "test-route")
@AddConfig(key = "server.sockets.0.port", value = "0")
@AddConfig(key = "server.sockets.0.bind-address", value = "localhost")
@AddBean(TestLraCoordinator.class)
@Path("/test/multi-port")
public class LraMultiPortTest {

    private final WebTarget target;
    private final WebTarget otherTarget;
    private final Set<String> completedLras;
    private final Set<String> cancelledLras;
    private final TestLraCoordinator coordinator;

    @Inject
    public LraMultiPortTest(WebTarget target,
                            TestLraCoordinator coordinator,
                            @Socket("test-route") WebTarget otherTarget) {
        this.target = target;
        this.coordinator = coordinator;
        this.otherTarget = otherTarget;
        this.completedLras = new CopyOnWriteArraySet<>();
        this.cancelledLras = new CopyOnWriteArraySet<>();
    }

    @Produces
    @ApplicationScoped
    @RoutingName("test-route")
    @RoutingPath("/test/route")
    public HttpService anotherRoute() {
        return r -> r.any((req, res) -> res.send("Hello from test route!"));
    }

    @PUT
    @Path("/outer")
    @LRA(LRA.Type.REQUIRES_NEW)
    public Response withdraw(@HeaderParam(LRA.LRA_HTTP_CONTEXT_HEADER) Optional<URI> lraId, String content) {
        try (Response res = target.path("/test/multi-port/inner")
                .request()
                .put(Entity.entity(content, MediaType.TEXT_PLAIN_TYPE))) {
            assertThat(res.getStatus(), is(Response.Status.OK.getStatusCode()));
        }
        return Response.ok().build();
    }

    @PUT
    @Path("/inner")
    @LRA(LRA.Type.REQUIRED)
    public Response deposit(@HeaderParam(LRA.LRA_HTTP_CONTEXT_HEADER) Optional<URI> lraId, String content) {
        if ("BOOM".equals(content)) {
            throw new IllegalArgumentException("BOOM");
        }
        return Response.ok().build();
    }

    @Complete
    public void complete(URI lraId) {
        completedLras.add(lraId.toString());
    }

    @Compensate
    public void rollback(URI lraId) {
        cancelledLras.add(lraId.toString());
    }

    @Test
    public void testLra() {
        try (Response res = target
                .path("/test/multi-port/outer")
                .request()
                .put(Entity.entity("test", MediaType.TEXT_PLAIN_TYPE))) {
            assertThat(res.getStatus(), is(200));
            String lraId = res.getHeaderString(LRA.LRA_HTTP_CONTEXT_HEADER);
            Lra lra = coordinator.lra(lraId);
            assertThat(lra.status(), is(LRAStatus.Closed));
            assertThat(completedLras, contains(lraId));
        }
    }

    @Test
    public void testCompensatedLra() {
        try (Response res = target
                .path("/test/multi-port/outer")
                .request()
                .put(Entity.entity("BOOM", MediaType.TEXT_PLAIN_TYPE))) {
            assertThat(res.getStatus(), is(500));
            String lraId = res.getHeaderString(LRA.LRA_HTTP_CONTEXT_HEADER);
            Lra lra = coordinator.lra(lraId);
            assertThat(lra.status(), is(LRAStatus.Cancelled));
            assertThat(cancelledLras, contains(lraId));
        }
    }

    @Test
    public void testOtherRoute() {
        try (Response res = otherTarget
                .path("/test/route")
                .request()
                .get()) {
            assertThat(res.getStatus(), is(200));
            assertThat(res.readEntity(String.class), is("Hello from test route!"));
        }
    }

}
