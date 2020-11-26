

package io.helidon.lra;

//import io.helidon.lra.messaging.MessageProcessing;
import org.eclipse.microprofile.lra.annotation.LRAStatus;
import org.eclipse.microprofile.lra.annotation.ParticipantStatus;
//import org.eclipse.microprofile.openapi.annotations.Operation;
//import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
//import org.eclipse.microprofile.openapi.annotations.headers.Header;
//import org.eclipse.microprofile.openapi.annotations.media.Content;
//import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
//import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
//import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import javax.enterprise.context.ApplicationScoped;
//import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import java.net.*;
import java.util.*;

import static javax.ws.rs.core.Response.Status.*;
import static org.eclipse.microprofile.lra.annotation.LRAStatus.Closed;
import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_CONTEXT_HEADER;
import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_RECOVERY_HEADER;

@ApplicationScoped
@Path("lra-coordinator")
@Tag(name = "LRA Coordinator")
public class Coordinator {
    public static final String COORDINATOR_PATH_NAME = "lra-coordinator";
    public static final String RECOVERY_COORDINATOR_PATH_NAME = "lra-recovery-coordinator";

    public static final String COMPLETE = "complete";
    public static final String COMPENSATE = "compensate";
    public static final String STATUS = "status";
    public static final String LEAVE = "leave";
    public static final String AFTER = "after";
    public static final String FORGET = "forget";

    public static final String STATUS_PARAM_NAME = "Status";
    public static final String CLIENT_ID_PARAM_NAME = "ClientID";
    public static final String TIMELIMIT_PARAM_NAME = "TimeLimit";
    public static final String PARENT_LRA_PARAM_NAME = "ParentLRA";
    public static final String RECOVERY_PARAM = "recoveryCount";
    public static final String HTTP_METHOD_NAME = "method";


    @Context
    private UriInfo context;

    Map<String, LRA> lraRecordMap = new HashMap();

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
        return Response.noContent().build(); // 204 means the LRA is still active
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
        return true;
    }

    @POST
    @Path("start")
    @Produces(MediaType.TEXT_PLAIN)
    public Response startLRA(
            @Parameter(name = CLIENT_ID_PARAM_NAME,
                    description = "Each client is expected to have a unique identity (which can be a URL).",
                    required = true)
            @QueryParam(CLIENT_ID_PARAM_NAME) @DefaultValue("") String clientId,
            @Parameter(name = TIMELIMIT_PARAM_NAME,
                    description = "Specifies the maximum time in milli seconds that the LRA will exist for.\n"
                            + "If the LRA is terminated because of a timeout, the LRA URL is deleted.\n"
                            + "All further invocations on the URL will return 404.\n"
                            + "The invoker can assume this was equivalent to a compensate operation.")
            @QueryParam(TIMELIMIT_PARAM_NAME) @DefaultValue("0") Long timelimit,
            @Parameter(name = PARENT_LRA_PARAM_NAME,
                    description = "The enclosing LRA if this new LRA is nested")
            @QueryParam(PARENT_LRA_PARAM_NAME) @DefaultValue("") String parentLRA,
            @HeaderParam(LRA_HTTP_CONTEXT_HEADER) String parentId) throws WebApplicationException {
        System.out.println("Coordinator.startLRA " +
                "clientId = " + clientId + ", timelimit = " + timelimit + ", parentLRA = " + parentLRA + ", parentId = " + parentId);
        String coordinatorUrl = String.format("%s%s", context.getBaseUri(), COORDINATOR_PATH_NAME);
        URI lraId = null;
        try {
            String lraUUID = "LRAID" + UUID.randomUUID().toString();
            lraId = new URI(String.format("%s/%s", coordinatorUrl, lraUUID));
            lraRecordMap.put(lraUUID, new LRA(lraUUID));
            System.out.println("Coordinator.startLRA lraUUID:" + lraUUID + " lraId:" + lraId);
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
    @Path("nested/{NestedLraId}/complete")
    public Response completeNestedLRA(@PathParam("NestedLraId") String nestedLraId) {
        System.out.println("Coordinator.completeNestedLRA");
        return Response.ok(mapToParticipantStatus(endLRA(toURI(nestedLraId), false, true)).name()).build();
    }

    @PUT
    @Path("nested/{NestedLraId}/compensate")
    public Response compensateNestedLRA(@PathParam("NestedLraId") String nestedLraId) {
        System.out.println(" compensateNestedLRA nestedLraId = " + nestedLraId);
        return Response.ok(mapToParticipantStatus(endLRA(toURI(nestedLraId), true, true)).name()).build();
    }

    @PUT
    @Path("nested/{NestedLraId}/forget")
    public Response forgetNestedLRA(@PathParam("NestedLraId") String nestedLraId) {
        System.out.println(" forgetNestedLRA nestedLraId = " + nestedLraId);
        return Response.ok().build();
    }

    @PUT
    @Path("{LraId}/close")
    @Produces(MediaType.TEXT_PLAIN)
    public Response closeLRA(
            @Parameter(name = "LraId", description = "The unique identifier of the LRA", required = true)
            @PathParam("LraId") String txId) throws NotFoundException {
        LRA lra = lraRecordMap.get(txId);
        System.out.println("closeLRA txId:" + txId + " lraRecord:" + lra);
        if (lra == null) {
            System.out.println("Coordinator.closeLRA lraRecord == null lraRecordMap.size():" + lraRecordMap.size());
            for (String lraid : lraRecordMap.keySet()) {
                System.out.println(
                        "Coordinator.closeLRA uri:" + lraid + " lraRecordMap.get(lraid):" + lraRecordMap.get(lraid));
            }
            return Response.serverError().build();
        }
        lra.tryDoEnd(false, false);
        return Response.ok().build();
    }

    @PUT
    @Path("closeMessaging")
//    @Path("{LraId}/close")
    @Produces(MediaType.TEXT_PLAIN)
    public Response closeMessaging(
            @Parameter(name = "LraId", description = "The unique identifier of the LRA", required = true)
            @PathParam("LraId") String txId) throws NotFoundException {
        System.out.println("closeMessagingLRA");
        LRA lra = lraRecordMap.get(txId);
        System.out.println("closeMessaging txId = " + txId + " lraRecord:" + lra);
        if (lra == null) return Response.ok().build(); //this is currently true if no participants joined
        lra.tryDoEnd(false, true);
        return Response.ok().build();
    }

    @PUT
    @Path("{LraId}/cancel")
    @Produces(MediaType.APPLICATION_JSON)
    public Response cancelLRA(
            @Parameter(name = "LraId", description = "The unique identifier of the LRA", required = true)
            @PathParam("LraId") String lraId) throws NotFoundException {
        System.out.println("cancelLRA");
        System.out.println("cancelLRA lraId:" + lraId);
        LRA lra = lraRecordMap.get(lraId);
        if (lra == null) return Response.ok().build();
        lra.tryDoEnd(true, false);
        return Response.ok().build();
    }

    @PUT
    @Path("cancelMessaging")
//    @Path("{LraId}/cancel")
    @Produces(MediaType.APPLICATION_JSON)
    public Response cancelMessaging(
            @Parameter(name = "LraId", description = "The unique identifier of the LRA", required = true)
            @PathParam("LraId") String lraId) throws NotFoundException {
        System.out.println("cancelMessagingLRA");
        LRA lra = lraRecordMap.get(lraId);
        if (lra == null) return Response.ok().build();
        lra.tryDoEnd(true, true);
        return Response.ok().build();
    }


    private LRAStatus endLRA(URI lraId, boolean compensate, boolean fromHierarchy) throws NotFoundException {
        String lraString = lraId.toString().substring(lraId.toString().indexOf("LRAID"), lraId.toString().length() - 1);
        LRA lra = lraRecordMap.get(lraString);
        System.out.println("Coordinator.endLRA lraRecord:" + " lraRecord for lraId:" + lraString);
        if (lra == null) throw new NotFoundException("LRA not found:" + lraString);
        lra.tryDoEnd(compensate, false);  //todo nested has hardcoded isMessaging false
        return Closed;
    }

    @PUT
    @Path("{LraId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response joinLRAViaBody(
            @Parameter(name = "LraId", description = "The unique identifier of the LRA", required = true)
            @PathParam("LraId") String lraId,
            @Parameter(name = TIMELIMIT_PARAM_NAME,
                    description = "The time limit (in seconds) that the Compensator can guarantee that it can compensate "
                            + "the work performed by the service. After this time period has elapsed, it may no longer be "
                            + "possible to undo the work within the scope of this (or any enclosing) LRA. It may therefore "
                            + "be necessary for the application or service to start other activities to explicitly try to "
                            + "compensate this work. The application or coordinator may use this information to control the "
                            + "lifecycle of a LRA.")
            @QueryParam(TIMELIMIT_PARAM_NAME) @DefaultValue("0") long timeLimit,
            @Parameter(name = "Link",
                    description = "The resource paths that the coordinator will use to complete or compensate and to request"
                            + " the status of the participant. The link rel names are"
                            + " complete, compensate and status.")
            @HeaderParam("Link") @DefaultValue("") String compensatorLink,
            @RequestBody(name = "Compensator data",
                    description = "opaque data that will be stored with the coordinator and passed back to\n"
                            + "the participant when the LRA is closed or cancelled.\n")
                    String compensatorData) throws NotFoundException {
        System.out.println("Coordinator.joinLRA lraId = " + lraId + ", compensatorLink = " + compensatorLink);
        System.out.println("Coordinator.joinLRA lraId = " + lraId + ", compensatorData = " + compensatorData);
        System.out.println("Coordinator.joinLRA lraId = " + lraId + ", timeLimit = " + timeLimit);

        // test to see if the join request contains any participant specific data
        boolean isLink = isLink(compensatorData);
        if (compensatorLink != null && !compensatorLink.isEmpty()) {
            return joinLRA(toURI(lraId), timeLimit, null, compensatorLink, compensatorData);
        }
        if (!isLink) {
            compensatorData += "/";
            Map<String, String> terminateURIs = new HashMap<>();
            try {
                terminateURIs.put(COMPENSATE, new URL(compensatorData + "compensate").toExternalForm());
                terminateURIs.put(COMPLETE, new URL(compensatorData + "complete").toExternalForm());
                terminateURIs.put(STATUS, new URL(compensatorData + "status").toExternalForm());
            } catch (MalformedURLException e) {
                System.out.println("Coordinator.joinLRAViaBody MalformedURLException:" + e);
                e.printStackTrace();
                return Response.status(PRECONDITION_FAILED).build();
            }
            StringBuilder linkHeaderValue = new StringBuilder();
            compensatorData = linkHeaderValue.toString();
        }
        return joinLRA(toURI(lraId), timeLimit, null, compensatorData, null);
    }

    private boolean isLink(String linkString) {
        try {
            Link.valueOf(linkString);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private Response joinLRA(URI lraId, long timeLimit, String compensatorUrl, String compensatorLink, String compensatorData)
            throws NotFoundException {
        final String recoveryUrlBase = String.format("http://%s/%s/",
                context.getRequestUri().getAuthority(), RECOVERY_COORDINATOR_PATH_NAME);

        StringBuilder recoveryUrl = new StringBuilder();

        int status = Response.Status.OK.getStatusCode();
        System.out.println("Coordinator.joinLRA lraId:" + lraId + " timeout:" + timeLimit);
        String lraIdString = lraId.toString().substring(lraId.toString().indexOf("LRAID"));
        LRA lra = lraRecordMap.get(lraIdString);
        if (lra == null) {
            System.out.println("Coordinator.joinLRA lraRecord == null for lraIdString:" + lraIdString +
                    "lraRecordMap.size():" + lraRecordMap.size());
            for (String uri : lraRecordMap.keySet()) {
                System.out.println(
                        "Coordinator.joinLRA uri:" + uri + " lraRecordMap.get(uri):" + lraRecordMap.get(uri));
            }
            return Response.serverError().build();
        } else {
            if (lra.timeout == 0)
                lra.timeout = System.currentTimeMillis() + timeLimit; //todo convert to whatever measurement
            if (timeoutRunnable == null) {
                timeoutRunnable = new TimeoutRunnable();
                new Thread(timeoutRunnable).start();
            }
            long currentTime = System.currentTimeMillis();
            System.out.println("Coordinator.joinLRA currentTime:" + currentTime + " lra.timeout:" + lra.timeout);
            if (currentTime > lra.timeout) {
                //todo remove etc it
//                return Response.status(412).build(); // or 410
            }
        }
        System.out.println("initParticipantURIs compensatorLink = " + compensatorLink);
        if (compensatorLink == null || compensatorLink.trim().equals("")) {
            System.out.println("Coordinator.initParticipantURIs no compensatorLink information");
        }
        lra.addParticipant(compensatorLink, true, true);
        lra.addParticipant(compensatorLink, false, true);

        try {
            return Response.status(status)
                    .entity(recoveryUrl.toString())
                    .location(new URI(recoveryUrl.toString()))
                    .header(LRA_HTTP_RECOVERY_HEADER, recoveryUrl)
                    .build();
        } catch (URISyntaxException e) {
            throw new RuntimeException("todo  runtime exception from joinLRA");
        }
    }


    private TimeoutRunnable timeoutRunnable;

    class TimeoutRunnable implements Runnable {

        @Override
        public void run() {
            while (true) {
                if (lraRecordMap != null) {
                    for (String uri : lraRecordMap.keySet()) {
                        LRA lra = lraRecordMap.get(uri);
                        long currentTime = System.currentTimeMillis();
                        System.out.println(
                                "Timeout thread uri:" + uri + " lraRecordMap.get(uri):" + lra);
                        System.out.println(
                                "Timeout thread lra.timeout:" + lra.timeout + " currentTime:" + currentTime);
                        if (lra.timeout > currentTime) lra.tryDoEnd(true, false);
                    }
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    @PUT
    @Path("{LraId}/remove")
    @Produces(MediaType.APPLICATION_JSON)
    public Response leaveLRA(
            @Parameter(name = "LraId", description = "The unique identifier of the LRA", required = true)
            @PathParam("LraId") String lraId,
            String compensatorUrl) throws NotFoundException, URISyntaxException {
        String reqUri = context.getRequestUri().toString();

        reqUri = reqUri.substring(0, reqUri.lastIndexOf('/'));

        String lraIdString = lraId.toString().substring(lraId.toString().indexOf("LRAID"));
        LRA lra = lraRecordMap.get(lraIdString);
        if (lra != null) {
            lra.removeParticipant(compensatorUrl, false, true);
        }
        int status = 200; //lraService.leave(new URI(reqUri), compensatorUrl);

        return Response.status(status).build();
    }

    private URI toURI(String lraId) {
        return toURI(lraId, "Invalid LRA id format");
    }


    private URI toURI(String lraId, String message) {
        URL url;
        try {
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
}