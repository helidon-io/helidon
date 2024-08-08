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

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.lra.annotation.Compensate;
import org.eclipse.microprofile.lra.annotation.Complete;
import org.eclipse.microprofile.lra.annotation.ws.rs.LRA;

@ApplicationScoped
@Path("/test/external")
public class WithdrawTestResource {

    private final Set<String> completedLras;
    private final Set<String> cancelledLras;

    public WithdrawTestResource() {
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

    Set<String> getCompletedLras() {
        return completedLras;
    }

    Set<String> getCancelledLras() {
        return cancelledLras;
    }
}
