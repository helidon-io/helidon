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

package io.helidon.microprofile.lra;

import java.net.URI;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.lra.annotation.AfterLRA;
import org.eclipse.microprofile.lra.annotation.Compensate;
import org.eclipse.microprofile.lra.annotation.Complete;
import org.eclipse.microprofile.lra.annotation.Forget;
import org.eclipse.microprofile.lra.annotation.LRAStatus;
import org.eclipse.microprofile.lra.annotation.ParticipantStatus;
import org.eclipse.microprofile.lra.annotation.Status;

@ApplicationScoped
public class ParticipantDispatchResource {

    static final AtomicBoolean COMPLETE_CALLED = new AtomicBoolean();
    static final AtomicBoolean COMPENSATE_CALLED = new AtomicBoolean();
    static final AtomicBoolean FORGET_CALLED = new AtomicBoolean();
    static final AtomicBoolean AFTER_LRA_CALLED = new AtomicBoolean();
    static final AtomicBoolean STATUS_CALLED = new AtomicBoolean();
    static final AtomicBoolean UNANNOTATED_METHOD_CALLED = new AtomicBoolean();

    @Complete
    public Response complete(URI lraId) {
        COMPLETE_CALLED.set(true);
        return Response.ok(ParticipantStatus.Completed.name()).build();
    }

    @Compensate
    public Response compensate(URI lraId) {
        COMPENSATE_CALLED.set(true);
        return Response.ok(ParticipantStatus.Compensated.name()).build();
    }

    @Forget
    public Response forget(URI lraId) {
        FORGET_CALLED.set(true);
        return Response.ok().build();
    }

    @AfterLRA
    public Response afterLra(URI lraId, LRAStatus status) {
        AFTER_LRA_CALLED.set(true);
        return Response.ok(status.name()).build();
    }

    @Status
    public ParticipantStatus status(URI lraId) {
        STATUS_CALLED.set(true);
        return ParticipantStatus.Active;
    }

    public Response unannotatedParticipantMethod(URI lraId) {
        UNANNOTATED_METHOD_CALLED.set(true);
        return Response.ok().build();
    }
}
