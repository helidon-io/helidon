/*
 * Copyright (c) 2021, 2023 Oracle and/or its affiliates.
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

package io.helidon.microprofile.example.lra;

import java.net.URI;
import java.time.temporal.ChronoUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.lra.LRAResponse;
import org.eclipse.microprofile.lra.annotation.Compensate;
import org.eclipse.microprofile.lra.annotation.Complete;
import org.eclipse.microprofile.lra.annotation.ws.rs.LRA;

import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_CONTEXT_HEADER;

/**
 * Example resource with LRA.
 */
@Path("/example")
@ApplicationScoped
public class LRAExampleResource {

    private static final Logger LOGGER = Logger.getLogger(LRAExampleResource.class.getName());

    /**
     * Starts a new long-running action.
     *
     * @param lraId id of this action
     * @param data entity
     * @return empty response
     *
     * @throws InterruptedException this method is sleeping on thread, so it can throw interrupted exception
     */
    @PUT
    @LRA(value = LRA.Type.REQUIRES_NEW, timeLimit = 500, timeUnit = ChronoUnit.MILLIS)
    @Path("start-example")
    public Response startExample(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) URI lraId,
                                 String data) throws InterruptedException {
        if (data.contains("BOOM")) {
            throw new RuntimeException("BOOM 💥");
        }

        if (data.contains("TIMEOUT")) {
            Thread.sleep(2000);
        }

        LOGGER.info("Data " + data + " processed 🏭");
        return Response.ok().build();
    }

    /**
     * Completes the long-running action.
     *
     * @param lraId id of this action
     * @return completed response
     */
    @PUT
    @Complete
    @Path("complete-example")
    public Response completeExample(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) URI lraId) {
        LOGGER.log(Level.INFO, "LRA id: {0} completed 🎉", lraId);
        return LRAResponse.completed();
    }

    /**
     * Compensation for long-running action.
     *
     * @param lraId id of action to compensate
     * @return compensated response
     */
    @PUT
    @Compensate
    @Path("compensate-example")
    public Response compensateExample(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) URI lraId) {
        LOGGER.log(Level.SEVERE, "LRA id: {0} compensated 🚒", lraId);
        return LRAResponse.compensated();
    }

}
