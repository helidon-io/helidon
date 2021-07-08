/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
 *
 */

package io.helidon.microprofile.lra.resources;

import java.net.URI;

import javax.enterprise.context.ApplicationScoped;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;

import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_CONTEXT_HEADER;
import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_PARENT_CONTEXT_HEADER;

import org.eclipse.microprofile.lra.annotation.Compensate;
import org.eclipse.microprofile.lra.annotation.Complete;
import org.eclipse.microprofile.lra.annotation.ParticipantStatus;

@ApplicationScoped
@Path(JaxRsNestedCompleteOrCompensate.PATH_BASE)
public class JaxRsNestedCompleteOrCompensate extends CdiNestedCompleteOrCompensate {

    public static final String PATH_BASE = "jaxrs-nested";

    @Complete
    @Path("complete")
    @PUT
    public Response complete(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) URI lraId,
                             @HeaderParam(LRA_HTTP_PARENT_CONTEXT_HEADER) URI parentLraId) {
        System.out.println("COMPLETING    lra " + lraId);
        System.out.println("COMPLETING parent " + parentLraId);
        basicTest.getCompletable(CS_COMPLETED, lraId).complete(parentLraId);
        return Response.ok(ParticipantStatus.Completed.name()).build();
    }

    @Compensate
    @Path("compensate")
    @PUT
    public Response compensate(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) URI lraId,
                               @HeaderParam(LRA_HTTP_PARENT_CONTEXT_HEADER) URI parentLraId) {
        System.out.println("COMPENSATING    lra " + lraId);
        System.out.println("COMPENSATING parent " + parentLraId);
        basicTest.getCompletable(CS_COMPENSATED, lraId).complete(parentLraId);
        return Response.ok(ParticipantStatus.Compensated.name()).build();
    }
}
