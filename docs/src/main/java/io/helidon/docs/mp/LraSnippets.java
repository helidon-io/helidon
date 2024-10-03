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
package io.helidon.docs.mp;

import java.net.URI;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.lra.coordinator.Lra;
import io.helidon.microprofile.config.ConfigCdiExtension;
import io.helidon.microprofile.lra.LraCdiExtension;
import io.helidon.microprofile.testing.junit5.AddBean;
import io.helidon.microprofile.testing.junit5.AddConfig;
import io.helidon.microprofile.testing.junit5.AddExtension;
import io.helidon.microprofile.testing.junit5.AddJaxRs;
import io.helidon.microprofile.testing.junit5.DisableDiscovery;
import io.helidon.microprofile.testing.junit5.HelidonTest;
import io.helidon.microprofile.testing.lra.TestLraCoordinator;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.lra.LRAResponse;
import org.eclipse.microprofile.lra.annotation.AfterLRA;
import org.eclipse.microprofile.lra.annotation.Compensate;
import org.eclipse.microprofile.lra.annotation.Complete;
import org.eclipse.microprofile.lra.annotation.Forget;
import org.eclipse.microprofile.lra.annotation.LRAStatus;
import org.eclipse.microprofile.lra.annotation.ParticipantStatus;
import org.eclipse.microprofile.lra.annotation.Status;
import org.eclipse.microprofile.lra.annotation.ws.rs.LRA;
import org.eclipse.microprofile.lra.annotation.ws.rs.Leave;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_CONTEXT_HEADER;
import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_ENDED_CONTEXT_HEADER;
import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_PARENT_CONTEXT_HEADER;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;

@SuppressWarnings("ALL")
class LraSnippets {

    class Snippet1 {

        // tag::snippet_1[]
        @PUT
        @LRA(value = LRA.Type.REQUIRES_NEW,
             timeLimit = 500,
             timeUnit = ChronoUnit.MILLIS)
        @Path("start-example")
        public Response startLra(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) URI lraId,
                                 String data) {
            return Response.ok().build();
        }
        // end::snippet_1[]
    }

    class Snippet2 {

        // tag::snippet_2[]
        @PUT
        @Path("/compensate")
        @Compensate
        public Response compensateWork(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) URI lraId,
                                       @HeaderParam(LRA_HTTP_PARENT_CONTEXT_HEADER) URI parent) {
            return LRAResponse.compensated();
        }
        // end::snippet_2[]
    }

    class Snippet3 {

        // tag::snippet_3[]
        @Compensate
        public void compensate(URI lraId) {
        }
        // end::snippet_3[]
    }

    class Snippet4 {

        // tag::snippet_4[]
        @PUT
        @Path("/complete")
        @Complete
        public Response complete(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) URI lraId,
                                 @HeaderParam(LRA_HTTP_PARENT_CONTEXT_HEADER) URI parentLraId) {
            return LRAResponse.completed();
        }
        // end::snippet_4[]
    }

    class Snippet5 {

        // tag::snippet_5[]
        @Complete
        public void complete(URI lraId) {
        }
        // end::snippet_5[]
    }

    class Snippet6 {

        // tag::snippet_6[]
        @DELETE
        @Path("/forget")
        @Forget
        public Response forget(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) URI lraId,
                               @HeaderParam(LRA_HTTP_PARENT_CONTEXT_HEADER) URI parent) {
            return Response.noContent().build();
        }
        // end::snippet_6[]
    }

    class Snippet7 {

        // tag::snippet_7[]
        @Forget
        public void forget(URI lraId) {
        }
        // end::snippet_7[]
    }

    class Snippet8 {

        // tag::snippet_8[]
        @PUT
        @Path("/leave")
        @Leave
        public Response leaveLRA(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) URI lraIdtoLeave) {
            return Response.ok().build();
        }
        // end::snippet_8[]
    }

    class Snippet9 {

        // tag::snippet_9[]
        @GET
        @Path("/status")
        @Status
        public Response reportStatus(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) URI lraId) {
            return Response.ok(ParticipantStatus.FailedToCompensate).build();
        }
        // end::snippet_9[]
    }

    class Snippet10 {

        // tag::snippet_10[]
        @Status
        public Response reportStatus(URI lraId) {
            return Response.ok(ParticipantStatus.FailedToCompensate)
                    .build();
        }
        // end::snippet_10[]
    }

    class Snippet11 {

        // tag::snippet_11[]
        @PUT
        @Path("/finished")
        @AfterLRA
        public Response whenLRAFinishes(@HeaderParam(LRA_HTTP_ENDED_CONTEXT_HEADER) URI lraId,
                                        @HeaderParam(LRA_HTTP_PARENT_CONTEXT_HEADER) URI parentLraId,
                                        LRAStatus status) {
            return Response.ok().build();
        }
        // end::snippet_11[]
    }

    class Snippet12 {

        // tag::snippet_12[]
        public void whenLRAFinishes(URI lraId, LRAStatus status) {
        }
        // end::snippet_12[]
    }

    static final Logger LOGGER = Logger.getLogger("lra");

    class Snippet13 {

        // tag::snippet_13[]
        @PUT
        @LRA(LRA.Type.REQUIRES_NEW) // <1>
        @Path("start-example")
        public Response startExample(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) URI lraId, //<2>
                                     String data) {
            if (data.contains("BOOM")) {
                throw new RuntimeException("BOOM 💥"); // <3>
            }

            LOGGER.info("Data " + data + " processed 🏭");
            return Response.ok().build(); // <4>
        }

        @PUT
        @Complete // <5>
        @Path("complete-example")
        public Response completeExample(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) URI lraId) {
            LOGGER.log(Level.INFO, "LRA ID: {0} completed 🎉", lraId);
            return LRAResponse.completed();
        }

        @PUT
        @Compensate // <6>
        @Path("compensate-example")
        public Response compensateExample(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) URI lraId) {
            LOGGER.log(Level.SEVERE, "LRA ID: {0} compensated 🦺", lraId);
            return LRAResponse.compensated();
        }
        // end::snippet_13[]
    }

    // tag::snippet_14[]
    @ApplicationScoped
    @Path("/test")
    public class WithdrawResource {

        private final List<String> completedLras = new CopyOnWriteArrayList<>();
        private final List<String> cancelledLras = new CopyOnWriteArrayList<>();

        @PUT
        @Path("/withdraw")
        @LRA(LRA.Type.REQUIRES_NEW)
        public Response withdraw(@HeaderParam(LRA.LRA_HTTP_CONTEXT_HEADER) Optional<URI> lraId, String content) {
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

        public List<String> getCompletedLras() {
            return completedLras;
        }
    }
    // end::snippet_14[]

    // tag::snippet_15[]
    @HelidonTest
    //@AddBean(WithdrawResource.class) //<1>
    @AddBean(TestLraCoordinator.class) //<2>
    public class LraTest {

        @Inject
        private WithdrawResource withdrawTestResource;

        @Inject
        private TestLraCoordinator coordinator; //<3>

        @Inject
        private WebTarget target;

        @Test
        public void testLraComplete() {
            try (Response res = target
                    .path("/test/withdraw")
                    .request()
                    .put(Entity.entity("test", MediaType.TEXT_PLAIN_TYPE))) {
                assertThat(res.getStatus(), is(200));
                String lraId = res.getHeaderString(LRA.LRA_HTTP_CONTEXT_HEADER);
                Lra lra = coordinator.lra(lraId); //<4>
                assertThat(lra.status(), is(LRAStatus.Closed)); //<5>
                assertThat(withdrawTestResource.getCompletedLras(), contains(lraId));
            }
        }
    }
    // end::snippet_15[]

    // tag::snippet_16[]
    @HelidonTest
    @AddBean(TestLraCoordinator.class)
    @AddConfig(key = "server.sockets.500.port", value = "8070") //<1>
    @AddConfig(key = "server.sockets.500.bind-address", value = "custom.bind.name") //<2>
    @AddConfig(key = "helidon.lra.coordinator.persistence", value = "true") //<3>
    @AddConfig(key = "helidon.lra.participant.use-build-time-index", value = "true") //<4>
    public class LraCustomConfigTest {
    }
    // end::snippet_16[]

    // tag::snippet_17[]
    @HelidonTest
    @DisableDiscovery
    @AddJaxRs
    @AddBean(TestLraCoordinator.class)
    @AddExtension(LraCdiExtension.class)
    @AddExtension(ConfigCdiExtension.class)
    @AddBean(WithdrawResource.class)
    public class LraNoDiscoveryTest {
    }
    // end::snippet_17[]
}
