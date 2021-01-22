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

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import javax.enterprise.context.ApplicationScoped;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_CONTEXT_HEADER;
import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_RECOVERY_HEADER;

@ApplicationScoped
@Path("lra-coordinator")
@Tag(name = "LRA Coordinator")
public class Coordinator implements Runnable {
    public static final String COORDINATOR_PATH_NAME = "lra-coordinator";

    public static final String STATUS_PARAM_NAME = "Status";
    public static final String CLIENT_ID_PARAM_NAME = "ClientID";
    public static final String TIMELIMIT_PARAM_NAME = "TimeLimit";
    public static final String PARENT_LRA_PARAM_NAME = "ParentLRA";

    private boolean isTimeoutThreadRunning;

    @Context
    private UriInfo context;

    Map<String, LRA> lraMap = new ConcurrentHashMap();

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
            String lraUUID = "LRAID" + UUID.randomUUID().toString(); //todo better UUID
            lraId = new URI(String.format("%s/%s", coordinatorUrl, lraUUID)); //todo verify
            String rootParentOrChild = "parent(root)";
            if (parentLRA != null && !parentLRA.isEmpty()) {
                LRA parent = lraMap.get(parentLRA.replace("http://127.0.0.1:8080/lra-coordinator/", ""));
                if (parent != null) { // todo null would be unexpected and cause to compensate or exit entirely
                    LRA childLRA = new LRA(lraUUID, new URI(parentLRA));
                    lraMap.put(lraUUID, childLRA);
                    parent.addChild(lraUUID, childLRA);
                    rootParentOrChild = "nested(" + childLRA.nestingDetail() + ")";
                }
            } else {
                lraMap.put(lraUUID, new LRA(lraUUID));
            }
            log("[start] " + rootParentOrChild + " clientId = " + clientId + ", timelimit = " + timelimit +
                    ", parentLRA = " + parentLRA + ", parentId = " + parentId + " lraId:" + lraId);
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
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

    @PUT
    @Path("{LraId}/close")
    @Produces(MediaType.TEXT_PLAIN)
    public Response closeLRA(
            @Parameter(name = "LraId", description = "The unique identifier of the LRA", required = true)
            @PathParam("LraId") String lraId) throws NotFoundException {
        LRA lra = lraMap.get(lraId);
        log("[close] " + getParentChildDebugString(lra) + " lraId:" + lraId );
        if (lra == null) {
            log("[close] lraRecord == null lraRecordMap.size():" + lraMap.size());
            for (String lraid : lraMap.keySet()) {
                log("[close] uri:" + lraid + " lraRecordMap.get(lraid):" + lraMap.get(lraid));
            }
            return Response.serverError().build();
        }
        lra.terminate(false, true);
        return Response.ok().build();
    }


    @PUT
    @Path("{LraId}/cancel")
    public Response cancelLRA(
            @Parameter(name = "LraId", description = "The unique identifier of the LRA", required = true)
            @PathParam("LraId") String lraId) throws NotFoundException {
        LRA lra = lraMap.get(lraId);
        log("[cancel] " + getParentChildDebugString(lra) + " lraId:" + lraId );
        if (lra == null) {
            log("[cancel] lraRecord == null lraRecordMap.size():" + lraMap.size());
            for (String lraid : lraMap.keySet()) {
                log("[close] uri:" + lraid + " lraRecordMap.get(lraid):" + lraMap.get(lraid));
            }
            return Response.serverError().build();
        }
        lra.terminate(true, true);
        return Response.ok().build();
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
        String lraIdString = lraId.toString().substring(lraId.toString().indexOf("LRAID"));
        LRA lra = lraMap.get(lraIdString);
        if (lra == null) {
            log("[join] lraRecord == null for lraIdString:" + lraIdString +
                    "lraRecordMap.size():" + lraMap.size());
            return Response.ok().build(); //todo this is actually error
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
                log("[join]] expired");
                return Response.status(412).build(); // 410 also acceptable/equivalent behavior
            }
        }
        if (compensatorData == null || compensatorData.trim().equals("")) {
            log("[join] no compensatorLink information");
        }
        String debugString = lra.addParticipant(compensatorLink, false);
        log("[join] " + debugString + " to " + getParentChildDebugString(lra) +
                " lraIdParam = " + lraIdParam + ", timeLimit = " + timeLimit );
        StringBuilder recoveryUrl = new StringBuilder(); //todo
        try {
            return Response.status(status)
                    .entity(recoveryUrl.toString())
                    .location(new URI(recoveryUrl.toString()))
                    .header(LRA_HTTP_RECOVERY_HEADER, "http://127.0.0.1:8080/lra-coordinator/" + lraIdString)
                    .build();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private String getParentChildDebugString(LRA lra) {
        return (lra.isParent ? "parent" : "") + (lra.isParent && lra.isChild ? " and " : "") +
                (lra.isChild ? "child" : "") + (!lra.isParent && !lra.isChild ? "currently flat LRA" : "");
    }

    @Override
    public void run() {
        while (true) {
            for (String uri : lraMap.keySet()) {
                LRA lra = lraMap.get(uri);
                if (lra.isProcessing()) continue;
                doRun(lra, uri); //todo add exponential backoff
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
            if (!lra.areAllInEndState()) lra.terminate(lra.isCancel, false); // this should purge if areAllAfterLRASuccessfullyCalled
            //todo push all of the following into LRA terminate...
            lra.sendAfterLRA(); //this method gates so no need to do check here
            if(lra.areAllInEndState() && (lra.areAnyInFailedState() ) ) { // || (lra.isChild && lra.isUnilateralCallIfNested && lra.isCancel == false)
                lra.sendForget();
                if(lra.areAllAfterLRASuccessfullyCalledOrForgotten()) {
                    if(lra.areAllAfterLRASuccessfullyCalledOrForgotten()) lraMap.remove(uri);
                }
            }
        } else {
            long currentTime = System.currentTimeMillis();
            if (lra.timeout < currentTime) {
                log("[timeout], will end uri:" + uri +
                        " timeout:" + lra.timeout + " currentTime:" + currentTime +
                        " ms over:" + (currentTime - lra.timeout));
                lra.terminate(true, false);
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
            lra.removeParticipant(compensatorUrl, false, true);
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
            url = new URL(lraId);
            return url.toURI();
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
        System.out.println("[coordinator]" + message);
    }


    void printStack(String message) {
        new Throwable(message).printStackTrace();
    }
}
