package io.helidon.lra.rest;

import io.helidon.lra.LRA;
import io.helidon.lra.Participant;
import org.eclipse.microprofile.lra.annotation.LRAStatus;
import org.eclipse.microprofile.lra.annotation.ParticipantStatus;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;

import java.net.URI;

import static org.eclipse.microprofile.lra.annotation.ParticipantStatus.Compensated;
import static org.eclipse.microprofile.lra.annotation.ParticipantStatus.Completed;
import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.*;
import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_RECOVERY_HEADER;

//todo async for most if not all...
public class RestParticipant extends Participant {

    private Client client = ClientBuilder.newBuilder().build();

    public void sendCompleteOrCancel(LRA lra, boolean isCancel) {
        URI endpointURI = isCancel ? getCompensateURI() : getCompleteURI();
        try {
            Response response = sendCompleteOrCompensate(lra, endpointURI, isCancel);
            int responsestatus = response.getStatus(); // expected codes 200, 202, 409, 410
            String readEntity = response.readEntity(String.class);
            log("RestParticipant " + lra.getConditionalStringValue(isCancel, "compensate", "complete") + " finished,  response:" + response + ":" + responsestatus + " readEntity:" + readEntity,
                    lra.nestedDepth);
            if (responsestatus == 503) { //  Service Unavailable, retriable - todo this should be the full range of invalid values
                lra.isRecovering = true;
            } else if (responsestatus == 409) { //conflict, retriable
                lra.isRecovering = true;
            } else if (responsestatus == 202) { //accepted
                lra.isRecovering = true;
            } else if (responsestatus == 404) {
                lra.isRecovering = true;
            } else if (responsestatus == 200 || responsestatus == 410) { // successful or gone (where presumption is complete or compensated)
                setParticipantStatus(isCancel ? Compensated : Completed);
            } else {
                lra.isRecovering = true;
            }
        } catch (Exception e) { // Exception:javax.ws.rs.ProcessingException: java.net.ConnectException: Connection refused (Connection refused)
            log("RestParticipant sendCompleteOrCancel Exception:" + e, lra.nestedDepth);
            lra.isRecovering = true;
        }
    }

    private Response sendCompleteOrCompensate(LRA lra, URI endpointURI, boolean isCompensate) {
        String path = "http://127.0.0.1:8080/lra-coordinator/";
        log("parentId:" + lra.parentId, lra.nestedDepth);
        return client.target(endpointURI)
                .request()
                .header(LRA_HTTP_CONTEXT_HEADER, path + lra.lraId)
                .header(LRA_HTTP_ENDED_CONTEXT_HEADER, path + lra.lraId)
                .header(LRA_HTTP_PARENT_CONTEXT_HEADER, lra.parentId)
                .header(LRA_HTTP_RECOVERY_HEADER, path + lra.lraId)
                .buildPut(Entity.text(lra.getConditionalStringValue(isCompensate, LRAStatus.Cancelled.name(), LRAStatus.Closed.name()))).invoke();
        //                       .buildPut(Entity.json("")).invoke();
        //                       .async().put(Entity.json("entity"));
    }

    public void sendAfterLRA(LRA lra) {
        try {
            URI afterURI = getAfterURI();
            if (afterURI != null) {
                if (isAfterLRASuccessfullyCalledIfEnlisted()) return;
                String path = "http://127.0.0.1:8080/lra-coordinator/";
                Response response = client.target(afterURI)
                        .request()
                        .header(LRA_HTTP_CONTEXT_HEADER, path + lra.lraId)
                        .header(LRA_HTTP_ENDED_CONTEXT_HEADER, path + lra.lraId)
                        .header(LRA_HTTP_PARENT_CONTEXT_HEADER, lra.parentId)
                        .header(LRA_HTTP_RECOVERY_HEADER, path + lra.lraId)
                        .buildPut(Entity.text(lra.getConditionalStringValue(lra.isCancel, LRAStatus.Cancelled.name(), LRAStatus.Closed.name()))).invoke();
                int responsestatus = response.getStatus();
                if (responsestatus == 200) setAfterLRASuccessfullyCalledIfEnlisted();
                log("RestParticipant afterLRA finished, response:" + response, lra.nestedDepth);
            }
        } catch (Exception e) {
            log("RestParticipant afterLRA Exception:" + e, lra.nestedDepth);
        }
    }



    public void sendStatus(LRA lra, URI statusURI) {
        Response response = null;
        int responsestatus = -1;
        String readEntity = null;
        ParticipantStatus participantStatus = null;
        try {
            String path = "http://127.0.0.1:8080/lra-coordinator/";
            response = client.target(statusURI)
                    .request()
                    .header(LRA_HTTP_CONTEXT_HEADER, path + lra.lraId)
                    .header(LRA_HTTP_ENDED_CONTEXT_HEADER, path + lra.lraId)
                    .header(LRA_HTTP_PARENT_CONTEXT_HEADER, lra.parentId)
                    .header(LRA_HTTP_RECOVERY_HEADER, path + lra.lraId)
                    .buildGet().invoke();
            responsestatus = response.getStatus();
            if (responsestatus == 503 || responsestatus == 202) { //todo include other retriables
            } else if (responsestatus != 410) {
                readEntity = response.readEntity(String.class);
                participantStatus = ParticipantStatus.valueOf(readEntity);
                setParticipantStatus(participantStatus);
            } else {
                setParticipantStatus(lra.isCancel ? Compensated : Completed); // not exactly accurate as it's GONE not explicitly completed or compensated
            }
            log("LRA sendStatus:" + statusURI + " finished  response:" +
                    response + ":" + responsestatus + " participantStatus:" + participantStatus +
                    " readEntity:" + readEntity, lra.nestedDepth);
        } catch (Exception e) { // IllegalArgumentException: No enum constant org.eclipse.microprofile.lra.annotation.ParticipantStatus.
            log("LRA sendStatus:" + statusURI + " finished  response:" +
                    response + ":" + responsestatus + " participantStatus:" + participantStatus +
                    " readEntity:" + readEntity + " Exception:" + e, lra.nestedDepth);
        }
    }

    @Override
    public boolean sendForget(LRA lra, boolean areAllThatNeedToBeForgottenForgotten) {
        try {
            String path = "http://127.0.0.1:8080/lra-coordinator/";
            Response response = client.target(getForgetURI())
                    .request()
                    .header(LRA_HTTP_CONTEXT_HEADER, path + lra.lraId)
                    .header(LRA_HTTP_ENDED_CONTEXT_HEADER, path + lra.lraId)
                    .header(LRA_HTTP_PARENT_CONTEXT_HEADER, lra.parentId)
                    .header(LRA_HTTP_RECOVERY_HEADER, path + lra.lraId)
                    .buildDelete().invoke();
            int responsestatus = response.getStatus();
            log("RestParticipant sendForget:" + getForgetURI() + " finished  response:" + response + ":" + responsestatus, lra.nestedDepth);
            if (responsestatus == 200 || responsestatus == 410) setForgotten();
            else areAllThatNeedToBeForgottenForgotten = false;
        } catch (Exception e) {
            log("RestParticipant sendForget Exception:" + e, lra.nestedDepth);
            areAllThatNeedToBeForgottenForgotten = false;
        }
        return areAllThatNeedToBeForgottenForgotten;
    }
}
