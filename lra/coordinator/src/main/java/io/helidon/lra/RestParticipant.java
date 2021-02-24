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

    public  void init(){
        //no op
    }

    String getParticipantType() {
        return "Rest";
    }

    void sendCompleteOrCancel(LRA lra, boolean isCancel) {
        URI endpointURI = isCancel ? getCompensateURI() : getCompleteURI();
        try {
            Response response = sendCompleteOrCompensate(lra, endpointURI, isCancel);
            int responsestatus = response.getStatus(); // expected codes 200, 202, 409, 410
            String readEntity = response.readEntity(String.class);
            logParticipantMessageWithTypeAndDepth("RestParticipant " + lra.getConditionalStringValue(isCancel, "compensate", "complete") +
                            " finished,  response:" + response + ":" + responsestatus + " readEntity:" + readEntity,
                    lra.nestedDepth);
            if (responsestatus == 503) { //  Service Unavailable, retriable - todo this should be the full range of invalid/retriable values
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
            logParticipantMessageWithTypeAndDepth("RestParticipant sendCompleteOrCancel Exception:" + e, lra.nestedDepth);
            lra.isRecovering = true;
        }
    }

    private Response sendCompleteOrCompensate(LRA lra, URI endpointURI, boolean isCompensate) {
        logParticipantMessageWithTypeAndDepth("parentId:" + lra.parentId, lra.nestedDepth);
        return client.target(endpointURI)
                .request()
                .header(LRA_HTTP_CONTEXT_HEADER, Coordinator.coordinatorURL + lra.lraId)
                .header(LRA_HTTP_ENDED_CONTEXT_HEADER, Coordinator.coordinatorURL + lra.lraId)
                .header(LRA_HTTP_PARENT_CONTEXT_HEADER, lra.parentId)
                .header(LRA_HTTP_RECOVERY_HEADER, Coordinator.coordinatorURL + lra.lraId)
                .buildPut(Entity.text(lra.getConditionalStringValue(isCompensate, LRAStatus.Cancelled.name(), LRAStatus.Closed.name()))).invoke();
        //                       .buildPut(Entity.json("")).invoke();
        //                       .async().put(Entity.json("entity"));
    }

    public void sendAfterLRA(LRA lra) {
        try {
            URI afterURI = getAfterURI();
            if (afterURI != null) {
                if (isAfterLRASuccessfullyCalledIfEnlisted()) return;
                Response response = client.target(afterURI)
                        .request()
                        .header(LRA_HTTP_CONTEXT_HEADER, Coordinator.coordinatorURL + lra.lraId)
                        .header(LRA_HTTP_ENDED_CONTEXT_HEADER, Coordinator.coordinatorURL + lra.lraId)
                        .header(LRA_HTTP_PARENT_CONTEXT_HEADER, lra.parentId)
                        .header(LRA_HTTP_RECOVERY_HEADER, Coordinator.coordinatorURL + lra.lraId)
                        .buildPut(Entity.text(lra.getConditionalStringValue(lra.isCancel, LRAStatus.Cancelled.name(), LRAStatus.Closed.name()))).invoke();
                int responsestatus = response.getStatus();
                if (responsestatus == 200) setAfterLRASuccessfullyCalledIfEnlisted();
                logParticipantMessageWithTypeAndDepth("RestParticipant afterLRA finished, response:" + response, lra.nestedDepth);
            }
        } catch (Exception e) {
            logParticipantMessageWithTypeAndDepth("RestParticipant afterLRA Exception:" + e, lra.nestedDepth);
        }
    }



    public void sendStatus(LRA lra, URI statusURI) {
        Response response = null;
        int responsestatus = -1;
        String readEntity = null;
        ParticipantStatus participantStatus = null;
        try {
            response = client.target(statusURI)
                    .request()
                    .header(LRA_HTTP_CONTEXT_HEADER, Coordinator.coordinatorURL + lra.lraId)
                    .header(LRA_HTTP_ENDED_CONTEXT_HEADER, Coordinator.coordinatorURL + lra.lraId)
                    .header(LRA_HTTP_PARENT_CONTEXT_HEADER, lra.parentId)
                    .header(LRA_HTTP_RECOVERY_HEADER, Coordinator.coordinatorURL + lra.lraId)
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
            logParticipantMessageWithTypeAndDepth("LRA sendStatus:" + statusURI + " finished  response:" +
                    response + ":" + responsestatus + " participantStatus:" + participantStatus +
                    " readEntity:" + readEntity, lra.nestedDepth);
        } catch (Exception e) { // IllegalArgumentException: No enum constant org.eclipse.microprofile.lra.annotation.ParticipantStatus.
            logParticipantMessageWithTypeAndDepth("LRA sendStatus:" + statusURI + " finished  response:" +
                    response + ":" + responsestatus + " participantStatus:" + participantStatus +
                    " readEntity:" + readEntity + " Exception:" + e, lra.nestedDepth);
        }
    }

    @Override
    public boolean sendForget(LRA lra) {
        boolean areAllThatNeedToBeForgottenForgotten = true;
        try {
            Response response = client.target(getForgetURI())
                    .request()
                    .header(LRA_HTTP_CONTEXT_HEADER, Coordinator.coordinatorURL + lra.lraId)
                    .header(LRA_HTTP_ENDED_CONTEXT_HEADER, Coordinator.coordinatorURL + lra.lraId)
                    .header(LRA_HTTP_PARENT_CONTEXT_HEADER, lra.parentId)
                    .header(LRA_HTTP_RECOVERY_HEADER, Coordinator.coordinatorURL + lra.lraId)
                    .buildDelete().invoke();
            int responsestatus = response.getStatus();
            logParticipantMessageWithTypeAndDepth("RestParticipant sendForget:" + getForgetURI() + " finished  response:" + response + ":" + responsestatus, lra.nestedDepth);
            if (responsestatus == 200 || responsestatus == 410) setForgotten();
            else areAllThatNeedToBeForgottenForgotten = false;
        } catch (Exception e) {
            logParticipantMessageWithTypeAndDepth("RestParticipant sendForget Exception:" + e, lra.nestedDepth);
            areAllThatNeedToBeForgottenForgotten = false;
        }
        return areAllThatNeedToBeForgottenForgotten;
    }
}
