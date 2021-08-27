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
 */
package io.helidon.microprofile.lra;

import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.inject.Inject;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import io.helidon.common.reactive.Single;

import org.eclipse.microprofile.lra.LRAResponse;
import org.eclipse.microprofile.lra.annotation.LRAStatus;
import org.eclipse.microprofile.lra.annotation.ParticipantStatus;

import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_CONTEXT_HEADER;
import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_ENDED_CONTEXT_HEADER;
import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_PARENT_CONTEXT_HEADER;

/**
 * JaxRs resource for LRA CDI methods.
 */
@Path(ParticipantCdiResource.CDI_PARTICIPANT_PATH)
public class ParticipantCdiResource {

    static final String CDI_PARTICIPANT_PATH = "lra-client-cdi-resource";

    private static final Logger LOGGER = Logger.getLogger(ParticipantCdiResource.class.getName());

    private static final Map<ParticipantStatus, Supplier<Response>> PARTICIPANT_RESPONSE_BUILDERS =
            Map.of(
                    ParticipantStatus.Compensating, () -> LRAResponse.compensating(ParticipantStatus.Compensating),
                    ParticipantStatus.Compensated, () -> LRAResponse.compensated(ParticipantStatus.Compensated),
                    ParticipantStatus.FailedToCompensate,
                    () -> LRAResponse.failedToCompensate(ParticipantStatus.FailedToCompensate),
                    ParticipantStatus.Completing, () -> LRAResponse.compensating(ParticipantStatus.Completing),
                    ParticipantStatus.Completed, () -> LRAResponse.compensating(ParticipantStatus.Completed),
                    ParticipantStatus.FailedToComplete, () -> LRAResponse.failedToComplete(ParticipantStatus.FailedToComplete)
            );

    @Inject
    private ParticipantService participantService;

    /**
     * Endpoint for non-JaxRs methods annotated with {@link org.eclipse.microprofile.lra.annotation.Compensate @Compensate}.
     *
     * @param lraId      id of LRA
     * @param parentId   id of parent LRA
     * @param fqdn       fully qualified name of the LRA method's class
     * @param methodName name of the LRA method
     * @return jax-rs {@link javax.ws.rs.core.Response response}
     */
    @PUT
    @Path("/compensate/{fqdn}/{methodName}")
    public Response compensate(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) URI lraId,
                               @HeaderParam(LRA_HTTP_PARENT_CONTEXT_HEADER) URI parentId,
                               @PathParam("fqdn") String fqdn,
                               @PathParam("methodName") String methodName) {
        try {
            Object result = participantService.invoke(fqdn, methodName, lraId, parentId);
            if (result instanceof Response) {
                return (Response) result;
            } else if (result instanceof ParticipantStatus) {
                return PARTICIPANT_RESPONSE_BUILDERS.get(((ParticipantStatus) result)).get();
            } else {
                return Response.ok(result).build();
            }
        } catch (InvocationTargetException e) {
            return LRAResponse.completed();
        }
    }

    /**
     * Endpoint for non-JaxRs methods annotated with {@link org.eclipse.microprofile.lra.annotation.Complete @Complete}.
     *
     * @param lraId      id of LRA
     * @param parentId   id of parent LRA
     * @param fqdn       fully qualified name of the LRA method's class
     * @param methodName name of the LRA method
     * @return jax-rs {@link javax.ws.rs.core.Response response}
     */
    @PUT
    @Path("/complete/{fqdn}/{methodName}")
    public CompletionStage<Response> complete(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) URI lraId,
                                              @HeaderParam(LRA_HTTP_PARENT_CONTEXT_HEADER) URI parentId,
                                              @PathParam("fqdn") String fqdn,
                                              @PathParam("methodName") String methodName) {
        try {
            Object result = participantService.invoke(fqdn, methodName, lraId, parentId);
            if (CompletionStage.class.isAssignableFrom(result.getClass())) {
                return ((CompletionStage<?>) result).thenApply(o -> (Response) o);
            } else {
                return CompletableFuture.completedFuture((Response) result);
            }
        } catch (InvocationTargetException e) {
            return CompletableFuture.completedFuture(LRAResponse.completed());
        }
    }

    /**
     * Endpoint for non-JaxRs methods annotated with {@link org.eclipse.microprofile.lra.annotation.AfterLRA @AfterLRA}.
     *
     * @param lraId      id of LRA
     * @param fqdn       fully qualified name of the LRA method's class
     * @param methodName name of the LRA method
     * @param status     {@link LRAStatus LRA status}
     * @return jax-rs {@link javax.ws.rs.core.Response response}
     */
    @PUT
    @Path("/afterlra/{fqdn}/{methodName}")
    public Response after(@HeaderParam(LRA_HTTP_ENDED_CONTEXT_HEADER) URI lraId,
                          @PathParam("fqdn") String fqdn,
                          @PathParam("methodName") String methodName,
                          LRAStatus status) {
        try {
            participantService.invoke(fqdn, methodName, lraId, status);
            return Response.ok().build();
        } catch (InvocationTargetException e) {
            if (e.getTargetException() instanceof WebApplicationException) {
                return ((WebApplicationException) e.getTargetException()).getResponse();
            } else {
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
            }
        }
    }

    /**
     * Endpoint for non-JaxRs methods annotated with {@link org.eclipse.microprofile.lra.annotation.Status @Status}.
     *
     * @param lraId      id of LRA
     * @param parentId   id of parent LRA
     * @param fqdn       fully qualified name of the LRA method's class
     * @param methodName name of the LRA method
     * @return jax-rs {@link javax.ws.rs.core.Response response}
     */
    @GET
    @Path("/status/{fqdn}/{methodName}")
    public CompletionStage<Response> status(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) String lraId,
                                            @HeaderParam(LRA_HTTP_PARENT_CONTEXT_HEADER) String parentId,
                                            @PathParam("fqdn") String fqdn,
                                            @PathParam("methodName") String methodName) {
        try {
            Object result = participantService.invoke(fqdn, methodName, UriBuilder.fromPath(lraId).build(), null);
            if (result == null) {
                // If the participant has already responded successfully to an @Compensate or @Complete
                // method invocation then it MAY report 410 Gone HTTP status code
                // or in the case of non-JAX-RS method returning ParticipantStatus null.
                return Single.just(Response.status(Response.Status.GONE).build());
            }
            if (result instanceof CompletionStage) {
                return ((CompletionStage<?>) result).thenApply(o -> {
                    if (o == null) {
                        return Response.status(Response.Status.GONE).build();
                    } else if (ParticipantStatus.class.isAssignableFrom(o.getClass())) {
                        return Response.ok(((ParticipantStatus) o).name()).build();
                    } else {
                        LOGGER.log(Level.WARNING,
                                "Unexpected type {0} returned within completable stage from cdi @Status method {1}",
                                new Object[] {result, methodName});
                        return Response.ok().build();
                    }
                });
            } else if (Response.class.isAssignableFrom(result.getClass())) {
                return Single.just((Response) result);
            } else if (ParticipantStatus.class.isAssignableFrom(result.getClass())) {
                return Single.just(Response.ok(((ParticipantStatus) result).name()).build());
            } else {
                LOGGER.log(Level.WARNING, "Unexpected type {0} returned from cdi @Status method {1}",
                        // spotbugs CRLF_INJECTION_LOGS
                        new Object[] {result.toString().replaceAll("[\r\n]", ""), methodName.replaceAll("[\r\n]", "")});
                return Single.just(Response.ok().build());
            }
        } catch (InvocationTargetException e) {
            return Single.just(Response.ok().build());
        }
    }

    /**
     * Endpoint for non-JaxRs methods annotated with {@link org.eclipse.microprofile.lra.annotation.Forget @Forget}.
     *
     * @param lraId      id of LRA
     * @param parentId   id of parent LRA
     * @param fqdn       fully qualified name of the LRA method's class
     * @param methodName name of the cdi method
     * @return jax-rs {@link javax.ws.rs.core.Response response}
     */
    @DELETE
    @Path("/forget/{fqdn}/{methodName}")
    public Response forget(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) String lraId,
                           @HeaderParam(LRA_HTTP_PARENT_CONTEXT_HEADER) String parentId,
                           @PathParam("fqdn") String fqdn,
                           @PathParam("methodName") String methodName) {
        try {
            URI lraIdUri = UriBuilder.fromPath(lraId).build();
            URI parentIdUri = Optional.ofNullable(parentId)
                    .map(UriBuilder::fromPath)
                    .map(UriBuilder::build)
                    .orElse(null);
            participantService.invoke(fqdn, methodName, lraIdUri, parentIdUri);
            return Response.ok().build();
        } catch (InvocationTargetException e) {
            LOGGER.log(Level.WARNING, "Error when invoking cdi @Forget method " + methodName, e);
            return Response.serverError().entity(e.getTargetException().getMessage()).build();
        }
    }

}
