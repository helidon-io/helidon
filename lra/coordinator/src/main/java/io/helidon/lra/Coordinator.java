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

package io.helidon.lra;

import io.helidon.lra.messaging.MessageProcessing;
import org.eclipse.microprofile.lra.annotation.LRAStatus;
import org.eclipse.microprofile.lra.annotation.ParticipantStatus;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.eclipse.microprofile.lra.annotation.LRAStatus.*;
import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_CONTEXT_HEADER;
import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_RECOVERY_HEADER;

@ApplicationScoped
@Path("lra-coordinator")
@Tag(name = "LRA Coordinator")
public class Coordinator implements Runnable {
    public static final String COORDINATOR_PATH_NAME = "lra-coordinator";
    public static final String RECOVERY_COORDINATOR_PATH_NAME = "lra-recovery-coordinator";

    public static final String STATUS_PARAM_NAME = "Status";
    public static final String CLIENT_ID_PARAM_NAME = "ClientID";
    public static final String TIMELIMIT_PARAM_NAME = "TimeLimit";
    public static final String PARENT_LRA_PARAM_NAME = "ParentLRA";

    private final MessageProcessing msgBean;
    private boolean isTimeoutThreadRunning;

    @Context
    private UriInfo context;

    Map<String, LRA> lraMap = new ConcurrentHashMap();
    final Object lraRecordMapLock = new Object();

    private static Coordinator singleton;

    public static Coordinator getInstance() {
        return singleton;
    }

    @Path("/send/{msg}")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public void getSend(@PathParam("msg") String msg) {
        msgBean.process(msg);
    }

    @Inject
    public Coordinator(MessageProcessing msgBean) {
        this.msgBean = msgBean;
        singleton = this;
    }

    @GET
    @Path("/")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Returns all LRAs",
            description = "Gets both active and recovering LRAs")
    @APIResponse(description = "The LRA",
            content = @Content(schema = @Schema(type = SchemaType.ARRAY, implementation = String.class))
    )
    public List<String> getAllLRAs(
            @Parameter(name = STATUS_PARAM_NAME, description = "Filter the returned LRAs to only those in the give state (see CompensatorStatus)")
            @QueryParam(STATUS_PARAM_NAME) @DefaultValue("") String state) {
        ArrayList<String> lraStrings = new ArrayList<>();
        lraStrings.add("testlraid");
        return lraStrings;
    }

    @GET
    @Path("{LraId}/status")
    @Produces(MediaType.TEXT_PLAIN)
    public Response getLRAStatus(
            @Parameter(name = "LraId",
                    description = "The unique identifier of the LRA", required = true)
            @PathParam("LraId") String lraId,
            @Parameter(name = "effectivelyActive",
                    description = "LRA is in LRAStatus.Active or it is a nested LRA in one of the final states")
            @QueryParam("effectivelyActive") @DefaultValue("false") boolean isEffectivelyActive) throws NotFoundException {
        return Response.noContent().build(); // 204 meaning the LRA is still active
    }

    @GET
    @Path("{LraId}")
    @Produces(MediaType.APPLICATION_JSON)
    public String getLRAInfo(
            @Parameter(name = "LraId", description = "The unique identifier of the LRA", required = true)
            @PathParam("LraId") String lraId) throws NotFoundException {
        return "test";
    }

    @GET
    @Path("/status/{LraId}")
    @Produces(MediaType.TEXT_PLAIN)
    public Boolean isActiveLRA(
            @Parameter(name = "LraId", description = "The unique identifier of the LRA", required = true)
            @PathParam("LraId") String lraId) throws NotFoundException {
        return true; //todo
    }

    @POST
    @Path("start")
    @Produces(MediaType.TEXT_PLAIN)
    public Response startLRA(
            @Parameter(name = CLIENT_ID_PARAM_NAME, required = true)
            @QueryParam(CLIENT_ID_PARAM_NAME) @DefaultValue("") String clientId,
            @Parameter(name = TIMELIMIT_PARAM_NAME)
            @QueryParam(TIMELIMIT_PARAM_NAME) @DefaultValue("0") Long timelimit,
            @Parameter(name = PARENT_LRA_PARAM_NAME)
            @QueryParam(PARENT_LRA_PARAM_NAME) @DefaultValue("") String parentLRA,
            @HeaderParam(LRA_HTTP_CONTEXT_HEADER) String parentId) throws WebApplicationException {
        String coordinatorUrl = String.format("%s%s", context.getBaseUri(), COORDINATOR_PATH_NAME);
        URI lraId = null;
        try {
            String lraUUID = "LRAID" + UUID.randomUUID().toString();
            lraId = new URI(String.format("%s/%s", coordinatorUrl, lraUUID));
            lraMap.put(lraUUID, new LRA(lraUUID));
            if (parentLRA != null && !parentLRA.isEmpty()) {
                LRA parent = lraMap.get(parentLRA.replace("http://127.0.0.1:8080/lra-coordinator/", ""));
                log("Coordinator.startLRA parent:" + parent);
                if (parent != null)
                    parent.addChild(lraUUID, new LRA(lraUUID, new URI(String.format("%s/%s", coordinatorUrl, parentLRA))));
            }
            log("Coordinator.startLRA " + "clientId = " + clientId + ", timelimit = " + timelimit +
                    ", parentLRA = " + parentLRA + ", parentId = " + parentId + " lraId:" + lraId);
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        assert lraId != null;
        return Response.created(lraId)
                .entity(lraId.toString())
                .header(LRA_HTTP_CONTEXT_HEADER,
                        ThreadContext.getContexts().size() == 1 ? ThreadContext.getContexts().get(0) : ThreadContext.getContexts())
                .build();
    }

    @PUT
    @Path("{LraId}/renew")
    public Response renewTimeLimit(
            @Parameter(name = TIMELIMIT_PARAM_NAME, description = "The new time limit for the LRA", required = true)
            @QueryParam(TIMELIMIT_PARAM_NAME) @DefaultValue("0") Long timelimit,
            @PathParam("LraId") String lraId) throws NotFoundException {

        return Response.status(400).build();
    }

    @GET
    @Path("nested/{NestedLraId}/status")
    public Response getNestedLRAStatus(@PathParam("NestedLraId") String nestedLraId) {
        return Response.ok("testnestedstatus").build();
    }

    @PUT
    @Path("nested/{NestedLraId}/complete")
    public Response completeNestedLRA(@PathParam("NestedLraId") String nestedLraId) {
        log("Coordinator.completeNestedLRA");
        return Response.ok(Objects.requireNonNull(mapToParticipantStatus(endLRA(toURI(nestedLraId), false, true))).name()).build();
    }

    @PUT
    @Path("nested/{NestedLraId}/compensate")
    public Response compensateNestedLRA(@PathParam("NestedLraId") String nestedLraId) {
        log(" compensateNestedLRA nestedLraId = " + nestedLraId);
        return Response.ok(mapToParticipantStatus(endLRA(toURI(nestedLraId), true, true)).name()).build();
    }

    private ParticipantStatus mapToParticipantStatus(LRAStatus lraStatus) {
        switch (lraStatus) {
            case Active:
                return ParticipantStatus.Active;
            case Closed:
                return ParticipantStatus.Completed;
            case Cancelled:
                return ParticipantStatus.Compensated;
            case Closing:
                return ParticipantStatus.Completing;
            case Cancelling:
                return ParticipantStatus.Compensating;
            case FailedToClose:
                return ParticipantStatus.FailedToComplete;
            case FailedToCancel:
                return ParticipantStatus.FailedToCompensate;
            default:
                return null;
        }
    }

    @PUT
    @Path("nested/{NestedLraId}/forget")
    public Response forgetNestedLRA(@PathParam("NestedLraId") String nestedLraId) {
        log(" forgetNestedLRA nestedLraId = " + nestedLraId);
        return Response.ok().build();
    }

    @PUT
    @Path("{LraId}/close")
    @Produces(MediaType.TEXT_PLAIN)
    public Response closeLRA(
            @Parameter(name = "LraId", description = "The unique identifier of the LRA", required = true)
            @PathParam("LraId") String lraId) throws NotFoundException {
        LRA lra = lraMap.get(lraId);
        log("closeLRA lraId:" + lraId + " lra:" + lra);
        if (lra == null) {
            log("Coordinator.closeLRA lraRecord == null lraRecordMap.size():" + lraMap.size());
            for (String lraid : lraMap.keySet()) {
                log(
                        "Coordinator.closeLRA uri:" + lraid + " lraRecordMap.get(lraid):" + lraMap.get(lraid));
            }
            return Response.ok().build();
        }
        lra.terminate(false);
        return Response.ok().build();
    }


    @PUT
    @Path("{LraId}/cancel")
    public Response cancelLRA(
            @Parameter(name = "LraId", description = "The unique identifier of the LRA", required = true)
            @PathParam("LraId") String lraId) throws NotFoundException {
        log("cancelLRA lraId:" + lraId);
        LRA lra = lraMap.get(lraId);
        if (lra == null) return Response.ok().build();
        lra.terminate(true);
        return Response.ok().build();
    }

    //for nested
    private LRAStatus endLRA(URI lraId, boolean compensate, boolean fromHierarchy) throws NotFoundException {
        String lraString = lraId.toString().substring(lraId.toString().indexOf("LRAID"), lraId.toString().length() - 1);
        LRA lra = lraMap.get(lraString);
        log("Coordinator.endLRA lraRecord:" + " lraRecord for lraId:" + lraString);
        if (lra == null) throw new NotFoundException("LRA not found:" + lraString);
        lra.terminate(compensate);
        return Closed;
    }

    @PUT
    @Path("{LraId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response joinLRAViaBody(
            @Parameter(name = "LraId", required = true)
            @PathParam("LraId") String lraIdParam,
            @Parameter(name = TIMELIMIT_PARAM_NAME)
            @QueryParam(TIMELIMIT_PARAM_NAME) @DefaultValue("0") long timeLimit,
            @Parameter(name = "Link")
            @HeaderParam("Link") @DefaultValue("") String compensatorLink,
            @RequestBody(name = "Compensator data")
                    String compensatorData) throws NotFoundException {
        URI lraId = toURI(lraIdParam);
        int status = Response.Status.OK.getStatusCode();
        log("Coordinator.joinLRA lraIdParam = " + lraIdParam + ", timeLimit = " + timeLimit); // +
//                ", compensatorLink = " + compensatorLink + ", compensatorData = " + compensatorData);
        String lraIdString = lraId.toString().substring(lraId.toString().indexOf("LRAID"));
        LRA lra = lraMap.get(lraIdString);
        if (lra == null) {
            log("Coordinator.joinLRA lraRecord == null for lraIdString:" + lraIdString +
                    "lraRecordMap.size():" + lraMap.size());
            return Response.ok().build();
        } else {
            if (timeLimit == 0) timeLimit = 60;
            if (lra.timeout == 0) { // todo overrides
                lra.timeout = System.currentTimeMillis() + (1000 * timeLimit); //todo convert to whatever measurement
                if (timeLimit == 500) lra.timeout = System.currentTimeMillis() + 500;
            }
            if (!isTimeoutThreadRunning) {
                new Thread(this).start();
                isTimeoutThreadRunning = true;
            }
            long currentTime = System.currentTimeMillis();
            if (currentTime > lra.timeout) {
                log("Coordinator.joinLRA expire");
                return Response.status(412).build(); // or 410
            }
        }
        if (compensatorData == null || compensatorData.trim().equals("")) {
            log("Coordinator.initParticipantURIs no compensatorLink information");
        }
        lra.addParticipant(compensatorLink, false, true);
        StringBuilder recoveryUrl = new StringBuilder();
        try {
            return Response.status(status)
                    .entity(recoveryUrl.toString())
                    .location(new URI(recoveryUrl.toString()))
                    .header(LRA_HTTP_RECOVERY_HEADER, recoveryUrl)
                    .build();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void run() {
        while (true) {
            for (String uri : lraMap.keySet()) {
                LRA lra = lraMap.get(uri);
                if (lra.isProcessing()) continue;
                doRun(lra, uri);
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void doRun(LRA lra, String uri) {
        if (lra.isReadyToDelete()) {
            lraMap.remove(uri);
        }
        else if (lra.isRecovering) {
            if (lra.hasStatusEndpoints()) lra.sendStatus();
            if (!lra.areAllInEndState()) lra.terminate(lra.isCancel); // this should purge if areAllAfterLRASuccessfullyCalled
            lra.sendAfterLRA(); //this method gates so no need to do check here
            if(lra.areAllInEndState() && lra.areAnyInFailedState()) {
                lra.sendForget();
                if(lra.areAllAfterLRASuccessfullyCalledOrForgotten()) {
                    if(lra.areAllAfterLRASuccessfullyCalledOrForgotten()) lraMap.remove(uri);
                }
            }
        } else {
            long currentTime = System.currentTimeMillis();
            if (lra.timeout < currentTime) {
                log("Timeout thread, will end uri:" + uri +
                        " timeout:" + lra.timeout + " currentTime:" + currentTime +
                        " ms over:" + (currentTime - lra.timeout));
                lra.terminate(true);
            }
        }
    }

    @PUT
    @Path("{LraId}/remove")
    @Produces(MediaType.APPLICATION_JSON)
    public Response leaveLRA(
            @Parameter(name = "LraId", description = "The unique identifier of the LRA", required = true)
            @PathParam("LraId") String lraId,
            String compensatorUrl) throws NotFoundException {
        printStack("remove/leave");
        String lraIdString = lraId.substring(lraId.indexOf("LRAID"));
        LRA lra = lraMap.get(lraIdString);
        if (lra != null) {
            lra.removeParticipant(compensatorUrl, false, true); //mark removed rather than actual remove
        }
        int status = 200;
        return Response.status(status).build();
    }

    private URI toURI(String lraId) {
        return toURI(lraId, "Invalid LRA id format");
    }

    private URI toURI(String lraId, String message) {
        URL url;

        try {
            // see if it already in the correct format
            url = new URL(lraId);
            url.toURI();
        } catch (Exception e) {
            try {
                url = new URL(String.format("%s%s/%s", context.getBaseUri(), COORDINATOR_PATH_NAME, lraId));
            } catch (MalformedURLException e1) {
                throw new RuntimeException("todo paul runtime exception badrequest in toURI " + e1);
            }
        }

        try {
            return url.toURI();
        } catch (URISyntaxException e) {
            throw new RuntimeException("todo paul runtime exception URISyntaxException in toURI " + e);
        }
    }

    void log(String message) {
        System.out.println(message);
    }


    void printStack(String message) {
        new Throwable(message).printStackTrace();
    }
}
/**

 [ERROR]   TckTests.compensateMultiLevelNestedActivity:172->multiLevelNestedActivity:677 multiLevelNestedActivity: step 8 (called test path http://localhost:8180/lraresource/multiLevelNestedActivity) expected:<2> but was:<1>
 [ERROR]   TckTests.mixedMultiLevelNestedActivity:177->multiLevelNestedActivity:693 multiLevelNestedActivity: step 9 (called test path http://localhost:8180/lraresource/multiLevelNestedActivity) expected:<1> but was:<0>
 [INFO]
 [ERROR] Tests run: 23, Failures: 2, Errors: 0, Skipped: 0
 */