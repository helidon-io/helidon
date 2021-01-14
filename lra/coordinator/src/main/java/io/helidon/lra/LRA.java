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

import javax.ws.rs.client.*;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.eclipse.microprofile.lra.annotation.ParticipantStatus.*;
import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.*;

public class LRA {

    /**
     * LRA state model....
     * Active -----------------------------------------------------------------> Cancelling --> FailedToCancel
     * --> Closing --> FailedToClose                                     /                  --> Cancelled
     * --> Closed --> (only if nested can go to cancelling) /
     */
    public long timeout;
    String lraId;
    private URI parentId;
    List<String> compensatorLinks = new ArrayList<>();
    List<LRA> children = new ArrayList<>();
    List<Participant> participants = new ArrayList<>();
    boolean hasStatusEndpoints;

    boolean isRecovering = false;
    boolean isCompensate;
    private boolean isChild;
    private boolean isParent;
    private boolean isProcessing;
    private boolean isReadyToDelete;

    public LRA(String lraUUID) {
        lraId = lraUUID;
    }

    public LRA(String lraUUID, URI parentId) {
        lraId = lraUUID;
        this.parentId = parentId;
    }

    void addParticipant(String compensatorLink, boolean isMessaging, boolean isToBeLogged) {
        if (compensatorLinks.contains(compensatorLink)) return;
        else compensatorLinks.add(compensatorLink);
        String uriPrefix = isMessaging ? "<messaging://" : "<http://";
        // <messaging://completeinventorylra>; rel="complete"; title="complete URI"; type="text/plain",
        // <messaging://compensate>; rel="compensate"; title="compensate URI"; type="text/plain"
        // <http://127.0.0.1:8091/inventory/completeInventory?method=javax.ws.rs.PUT>; rel="complete"; title="complete URI"; type="text/plain",
//        Participant existingparticipant = participants.contains(compensatorLink)
        if (compensatorLink.indexOf(uriPrefix) > -1) {
            Participant participant = new Participant();
            participants.add(participant);
            String endpoint = "";
            Pattern linkRelPattern = Pattern.compile("(\\w+)=\"([^\"]+)\"|([^\\s]+)");
            Matcher relMatcher = linkRelPattern.matcher(compensatorLink);
            while (relMatcher.find()) {
                String group0 = relMatcher.group(0);
//                log("LRA.initParticipantURIs isMessaging = " + isMessaging + " group0:" + group0);
                if (group0.indexOf(uriPrefix) > -1) { // <messaging://complete>;
//                    endpoint = isMessaging ? group0.substring(uriPrefix.length(), group0.indexOf(";") - 1) :
//                            group0.substring(1, group0.indexOf(";") - 1);
                    endpoint = isMessaging ? group0.substring(group0.indexOf(uriPrefix) + uriPrefix.length(), group0.indexOf(";") - 1) :
                            group0.substring(group0.indexOf(uriPrefix) + 1, group0.indexOf(";") - 1);
                }
                String key = relMatcher.group(1);
                if (key != null && key.equals("rel")) {
                    String rel = relMatcher.group(2) == null ? relMatcher.group(3) : relMatcher.group(2);
//                    log("LRA.initParticipantURIs " + rel + " is " + endpoint);
                    try {
                        if (rel.equals("complete")) {
                            participant.setCompleteURI(new URI(endpoint));
                        }
                        if (rel.equals("compensate")) {
                            participant.setCompensateURI(new URI(endpoint));
                        }
                        if (rel.equals("after")) {
                            participant.setAfterURI(new URI(endpoint));
                        }
                        if (rel.equals("status")) {
                            participant.setStatusURI(new URI(endpoint));
                            hasStatusEndpoints = true;
                        }
                        if (rel.equals("forget")) {
                            participant.setForgetURI(new URI(endpoint));
                        }
                    } catch (URISyntaxException e) {
                        e.printStackTrace();
                    }
                }
            }
            log("participant joined/added:" + participant);
        } else {
            log("no address found in compensatorLink:" + compensatorLink);
        }
//        if (isToBeLogged) RecoveryManager.getInstance().log(this, compensatorLink);
    }

    public void addChild(String lraUUID, LRA lra) {
        children.add(lra);
        lra.isChild = true;
        isParent = true;
    }


    void terminate(boolean isCancel) {
        setProcessing(true);
        isCompensate = isCancel;
        log("LRA terminate isCancel:" + isCancel + " participants.size():" + participants.size());
//            if (isParent) sendCompletion(compensateURIs, isCancel);
//            for (LRA nestedLRA : children) {
//                log("LRA.endChildren nestedLRA.compensateURIs.size():" + nestedLRA.compensateURIs.size());
//                nestedLRA.sendCompletion(nestedLRA.compensateURIs, isCancel);
//            }
        sendCompleteOrCancel(isCancel);
        sendAfterLRA();
        if(areAllInEndState() && areAllAfterLRASuccessfullyCalledOrForgotten()) isReadyToDelete = true;
        setProcessing(false);
    }

    private void sendCompleteOrCancel(boolean isCancel) {
        for (Participant participant : participants) {
            if (participant.isInEndStateOrListenerOnly()) {
                continue;
            }
            URI endpointURI = isCancel ? participant.getCompensateURI() : participant.getCompleteURI();
            try {
                Response response = sendCompleteOrCompensate(endpointURI, isCancel);
                int responsestatus = response.getStatus(); // expected codes 200, 202, 409, 410
                String readEntity = response.readEntity(String.class);
                log("LRA REST.sendCompleteOrCancel:" + endpointURI +
                        " finished  response:" + response + ":" + responsestatus + " readEntity:" + readEntity);
                if (responsestatus == 503) { //  Service Unavailable, retriable
                    isRecovering = true;
                } else if (responsestatus == 409) { //  Conflict, retriable
                    isRecovering = true;
                } else if (responsestatus == 202) { //accepted
                    isRecovering = true;
                } else if (responsestatus == 404) {
                    isRecovering = true;
                } else if (responsestatus == 200 || responsestatus == 410) { // successful or gone (where assumption is complete)
                    participant.setParticipantStatus(isCancel?Compensated:Completed);
//                    lraStatus = isCancel ? Cancelled : Closed; //this may be unnecessary/redundant
                } else {
                    isRecovering = true;
                }
            } catch (Exception e) { // Exception:javax.ws.rs.ProcessingException: java.net.ConnectException: Connection refused (Connection refused)
                log("LRA.sendCompleteOrCancel Exception:" + e);
                isRecovering = true;
            }
        }
    }

    private Response sendCompleteOrCompensate(URI endpointURI, boolean isCompensate) {
        Client client = ClientBuilder.newBuilder().build();
        String path = "http://127.0.0.1:8080/lra-coordinator/";
        return client.target(endpointURI)
                .request()
                .header(LRA_HTTP_CONTEXT_HEADER, path + lraId)
                .header(LRA_HTTP_ENDED_CONTEXT_HEADER, path + lraId)
                .header(LRA_HTTP_PARENT_CONTEXT_HEADER, parentId)
                .header(LRA_HTTP_RECOVERY_HEADER, path + lraId)
                .buildPut(Entity.text(isCompensate ? LRAStatus.Cancelled.name() : LRAStatus.Closed.name())).invoke();
        //                       .buildPut(Entity.json("")).invoke();
        //                       .async().put(Entity.json("entity"));
    }

    void sendAfterLRA() {
        if (areAllInEndState()) {
            for (Participant participant : participants) {
                try {
                    URI afterURI = participant.getAfterURI();
                    if (afterURI != null) {
                        if (participant.isAfterLRASuccessfullyCalledIfEnlisted()) continue;
                        Client client = ClientBuilder.newBuilder().build();
                        String path = "http://127.0.0.1:8080/lra-coordinator/";
                        Response response = client.target(afterURI)
                                .request()
                                .header(LRA_HTTP_CONTEXT_HEADER, path + lraId)
                                .header(LRA_HTTP_ENDED_CONTEXT_HEADER, path + lraId)
                                .header(LRA_HTTP_PARENT_CONTEXT_HEADER, parentId)
                                .header(LRA_HTTP_RECOVERY_HEADER, path + lraId)
                                .buildPut(Entity.text(isCompensate ? LRAStatus.Cancelled.name() : LRAStatus.Closed.name())).invoke();
                        int responsestatus = response.getStatus();
                        if (responsestatus == 200) participant.setAfterLRASuccessfullyCalledIfEnlisted();
                        log("LRA sendAfterLRA:" + afterURI + " finished  response:" + response + ":" + responsestatus);
                    }
                } catch (Exception e) {
                    log("LRA sendAfterLRA Exception:" + e);
                }
            }
        }
    }

    void sendStatus() {
        Response response;
        for (Participant participant : participants) {
            URI statusURI = participant.getStatusURI();
            if (statusURI == null) continue;
            try {
                Client client = ClientBuilder.newBuilder()
                        .build();
                String path = "http://127.0.0.1:8080/lra-coordinator/";
                response = client.target(statusURI)
                        .request()
                        .header(LRA_HTTP_CONTEXT_HEADER, path + lraId)
                        .header(LRA_HTTP_ENDED_CONTEXT_HEADER, path + lraId)
                        .header(LRA_HTTP_PARENT_CONTEXT_HEADER, parentId) // make the context available to participants
                        .header(LRA_HTTP_RECOVERY_HEADER, path + lraId)
                        .buildGet().invoke();
                int responsestatus = response.getStatus();
                String readEntity = response.readEntity(String.class);
                ParticipantStatus participantStatus =  ParticipantStatus.valueOf(readEntity);
                participant.setParticipantStatus(participantStatus);
                log("LRA REST.sendStatus:" + statusURI + " finished  response:" +
                        response + ":" + responsestatus + " participantStatus:" + participantStatus +
                        " readEntity:" + readEntity);
            } catch (Exception e) {
                log("LRA.sendStatus:" + statusURI + " Exception:" + e);
            }
        }
    }

    void sendForget() { //todo could gate with isprocessing here as well
        for (Participant participant : participants) {
            if (participant.getForgetURI() == null || participant.isForgotten()) continue;
            try {
                Client client = ClientBuilder.newBuilder()
                        .build();
                String path = "http://127.0.0.1:8080/lra-coordinator/";
                Response response = client.target(participant.getForgetURI())
                        .request()
                        .header(LRA_HTTP_CONTEXT_HEADER, path + lraId)
                        .header(LRA_HTTP_ENDED_CONTEXT_HEADER, path + lraId)
                        .header(LRA_HTTP_PARENT_CONTEXT_HEADER, parentId)
                        .header(LRA_HTTP_RECOVERY_HEADER, path + lraId)
                        .buildDelete().invoke();
                int responsestatus = response.getStatus();
                log("LRA sendForget:" + participant.getForgetURI() + " finished  response:" + response + ":" + responsestatus);
                if (responsestatus == 200 || responsestatus == 410) participant.setForgotten();
            } catch (Exception e) {
                log("LRA sendForget Exception:" + e);
            }
        }
    }

    public void setProcessing(boolean isProcessing) {
        this.isProcessing = isProcessing;
    }

    public boolean isProcessing() {
        return isProcessing;
    }

    public boolean isReadyToDelete() {
        return isReadyToDelete;
    }

    public boolean hasStatusEndpoints() {
        return hasStatusEndpoints;
    }

    public String toString() {
        String participantsString = "";
        for (Participant participant : participants) participantsString += participant;
        return "lraId:" + lraId + " participants' status:" + participantsString;
    }

    public boolean areAllClosedAndCancelledSuccessfully() {
        for (Participant participant : participants) {
            if(participant.getParticipantStatus() != ParticipantStatus.Completed &&
                    participant.getParticipantStatus() != Compensated) return false;
        }
        return true;
    }

    public boolean areAnyInFailedState() {
        for (Participant participant : participants) {
            if(participant.getParticipantStatus() == ParticipantStatus.FailedToComplete ||
                    participant.getParticipantStatus() == FailedToCompensate) return true;
        }
        return false;
    }

    public boolean areAllAfterLRASuccessfullyCalledOrForgotten() {
        for (Participant participant : participants) {
            if(!participant.isAfterLRASuccessfullyCalledIfEnlisted() && !participant.isForgotten()) return false;
        }
        return true;
    }

    public boolean areAllInEndState() {
        for (Participant participant : participants) {
            if(!participant.isInEndStateOrListenerOnly()) return false;
        }
        return true;
    }

    public boolean areAllAfterLRASuccessfullyCalled() {
        for (Participant participant : participants) {
            if(!participant.isAfterLRASuccessfullyCalledIfEnlisted()) return false;
        }
        return true;
    }

    void log(String message) {
        System.out.println(message);
//        System.out.println("ischild:" + isChild + " isParent:" + isParent + " " + message);
    }

    void printStack(String message) {
        new Throwable(message).printStackTrace();
    }

    public void removeParticipant(String compensatorUrl, boolean b, boolean b1) {
        participants = new ArrayList<>(); //todo remove just the participant specified
    }
}
