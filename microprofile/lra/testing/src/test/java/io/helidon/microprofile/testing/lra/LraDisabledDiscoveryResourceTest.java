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
import io.helidon.microprofile.config.ConfigCdiExtension;
import io.helidon.microprofile.lra.LraCdiExtension;
import io.helidon.microprofile.testing.junit5.AddBean;
import io.helidon.microprofile.testing.junit5.AddExtension;
import io.helidon.microprofile.testing.junit5.AddJaxRs;
import io.helidon.microprofile.testing.junit5.DisableDiscovery;
import io.helidon.microprofile.testing.junit5.HelidonTest;

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
@DisableDiscovery
@AddJaxRs
@AddBean(TestLraCoordinator.class)
@AddExtension(LraCdiExtension.class)
@AddExtension(ConfigCdiExtension.class)
@Path("/test/internal")
public class LraDisabledDiscoveryResourceTest {

    private final WebTarget target;
    private final Set<String> completedLras;
    private final Set<String> cancelledLras;
    private final TestLraCoordinator coordinator;

    @Inject
    public LraDisabledDiscoveryResourceTest(WebTarget target,
                                            TestLraCoordinator coordinator) {
        this.target = target;
        this.coordinator = coordinator;
        this.completedLras = new CopyOnWriteArraySet<>();
        this.cancelledLras = new CopyOnWriteArraySet<>();
    }

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

    @Test
    public void testLraComplete() {
        try (Response res = target
                .path("/test/internal/withdraw")
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
    public void testLraCompensate() {
        try (Response res = target
                .path("/test/internal/withdraw")
                .request()
                .put(Entity.entity("BOOM", MediaType.TEXT_PLAIN_TYPE))) {
            assertThat(res.getStatus(), is(500));
            String lraId = res.getHeaderString(LRA.LRA_HTTP_CONTEXT_HEADER);
            Lra lra = coordinator.lra(lraId);
            assertThat(lra.status(), is(LRAStatus.Cancelled));
            assertThat(cancelledLras, contains(lraId));
        }
    }
}
