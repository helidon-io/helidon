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

import io.helidon.lra.coordinator.Lra;
import io.helidon.microprofile.testing.junit5.AddBean;
import io.helidon.microprofile.testing.junit5.HelidonTest;

import jakarta.inject.Inject;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.lra.annotation.LRAStatus;
import org.eclipse.microprofile.lra.annotation.ws.rs.LRA;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;

@HelidonTest
@AddBean(WithdrawTestResource.class)
@AddBean(TestLraCoordinator.class)
public class LraExternalResourceTest {

    private final WithdrawTestResource withdrawTestResource;
    private final TestLraCoordinator coordinator;
    private final WebTarget target;

    @Inject
    public LraExternalResourceTest(WithdrawTestResource withdrawTestResource, TestLraCoordinator coordinator, WebTarget target) {
        this.withdrawTestResource = withdrawTestResource;
        this.coordinator = coordinator;
        this.target = target;
    }

    @Test
    public void testLraComplete() {
        try (Response res = target
                .path("/test/external/withdraw")
                .request()
                .put(Entity.entity("test", MediaType.TEXT_PLAIN_TYPE))) {
            assertThat(res.getStatus(), is(200));
            String lraId = res.getHeaderString(LRA.LRA_HTTP_CONTEXT_HEADER);
            Lra lra = coordinator.lra(lraId);
            assertThat(lra.status(), is(LRAStatus.Closed));
            assertThat(withdrawTestResource.getCompletedLras(), contains(lraId));
        }
    }

    @Test
    public void testLraCompensate() {
        try (Response res = target
                .path("/test/external/withdraw")
                .request()
                .put(Entity.entity("BOOM", MediaType.TEXT_PLAIN_TYPE))) {
            assertThat(res.getStatus(), is(500));
            String lraId = res.getHeaderString(LRA.LRA_HTTP_CONTEXT_HEADER);
            Lra lra = coordinator.lra(lraId);
            assertThat(lra.status(), is(LRAStatus.Cancelled));
            assertThat(withdrawTestResource.getCancelledLras(), contains(lraId));
        }
    }
}
