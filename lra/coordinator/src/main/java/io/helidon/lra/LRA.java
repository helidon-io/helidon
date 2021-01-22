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

import io.helidon.lra.rest.RestParticipant;
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
    public String lraId;
    public URI parentId;
    List<String> compensatorLinks = new ArrayList<>();
    LRA parent;
    List<LRA> children = new ArrayList<>();
    List<Participant> participants = new ArrayList<>();
    boolean hasStatusEndpoints;

    public boolean isRecovering = false;
    public boolean isCancel;
    boolean isRoot = false;
    boolean isParent;
    boolean isChild;
    boolean isUnilateralCallIfNested = false;
    boolean isNestedThatShouldBeForgottenAfterParentEnds = false;
    public int nestedDepth;
    private boolean isProcessing;
    private boolean isReadyToDelete;

    private Client client = ClientBuilder.newBuilder().build();

    public LRA(String lraUUID) {
        lraId = lraUUID;
        isRoot = true;
    }

    public LRA(String lraUUID, URI parentId) {
        lraId = lraUUID;
        this.parentId = parentId;
    }

    // debug path to root
    // level = root(0 - lraID) --> child(1 - lraID) --> child(2 - lraID) ...
    public String nestingDetail() {
        int depth = 0;
        LRA lra = this;
        while (lra.isChild) {
            depth++;
            lra = lra.parent;
        }
        nestedDepth = depth;
        String nestingDetail = "";
        lra = this;
        while (lra.isChild) {
            nestingDetail = " depth[" + depth + "] = " + lra.lraId + nestingDetail;
            depth--;
            lra = lra.parent;
        }
        return nestingDetail;
    }

    //return debug string
    String addParticipant(String compensatorLink, boolean isMessaging) {
        if (compensatorLinks.contains(compensatorLink)) return "participant already enlisted";
        else compensatorLinks.add(compensatorLink);
        String uriPrefix = getConditionalStringValue(isMessaging, "<messaging://", "<http://");
        // <messaging://completeinventorylra>; rel="complete"; title="complete URI"; type="text/plain",
        // <messaging://compensate>; rel="compensate"; title="compensate URI"; type="text/plain"
        // <http://127.0.0.1:8091/inventory/completeInventory?method=javax.ws.rs.PUT>; rel="complete"; title="complete URI"; type="text/plain",
//        Participant existingparticipant = participants.contains(compensatorLink)
        if (compensatorLink.indexOf(uriPrefix) > -1) {
            Participant participant = new RestParticipant();
            participants.add(participant);
            String endpoint = "";
            Pattern linkRelPattern = Pattern.compile("(\\w+)=\"([^\"]+)\"|([^\\s]+)");
            Matcher relMatcher = linkRelPattern.matcher(compensatorLink);
            while (relMatcher.find()) {
                String group0 = relMatcher.group(0);
                if (group0.indexOf(uriPrefix) > -1) { // <messaging://complete>;
//                    endpoint = isMessaging ? group0.substring(uriPrefix.length(), group0.indexOf(";") - 1) :
//                            group0.substring(1, group0.indexOf(";") - 1);
                    endpoint = isMessaging ? group0.substring(group0.indexOf(uriPrefix) + uriPrefix.length(), group0.indexOf(";") - 1) :
                            group0.substring(group0.indexOf(uriPrefix) + 1, group0.indexOf(";") - 1);
                }
                String key = relMatcher.group(1);
                if (key != null && key.equals("rel")) {
                    String rel = getConditionalStringValue(relMatcher.group(2) == null, relMatcher.group(3), relMatcher.group(2));
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
            return "LRA joined/added:" + (getConditionalStringValue(participant.isListenerOnly(), "listener", "participant"));
        } else {
            return "no address found in compensatorLink:" + compensatorLink;
        }
//        if (isToBeLogged) RecoveryManager.getInstance().log(this, compensatorLink);
    }

    public void removeParticipant(String compensatorUrl, boolean b, boolean b1) {
        participants = new ArrayList<>(); //todo remove just the participant specified
    }

    public void addChild(String lraUUID, LRA lra) {
        children.add(lra);
        lra.isChild = true;
        lra.parent = this;
        isParent = true;
    }

    void terminate(boolean isCancel, boolean isUnilateralCallIfNested) {
        setProcessing(true);
        this.isCancel = isCancel;
        if (isUnilateralCallIfNested && isChild && !isCancel)  isNestedThatShouldBeForgottenAfterParentEnds = true;
        if (isChild && !isCancel && areAllInEndStateCompensatedOrFailedToCompensate()) return; //todo this only check this child, not children of this child
        if (isParent) for (LRA nestedLRA : children) {   //  && nestedTerminate++ == 1
            log("LRA.endChildren nestedLRA.participants.size():" + nestedLRA.participants.size());
            if (!nestedLRA.areAllInEndStateOrListenerOnlyForTerminationType(isCancel)) {
                nestedLRA.terminate(isCancel, false); //todo this is the classic afterLRA/tx sync scenario - need to check if we traverse the tree twice or couple end and listener calls
            }
        }
        sendCompleteOrCancel(isCancel);
        sendAfterLRA(); log("areAllInEndState():" + areAllInEndState() + " areAllAfterLRASuccessfullyCalledOrForgotten():" + areAllAfterLRASuccessfullyCalledOrForgotten() +
                " !areAllForgotten():" + !areAllForgotten() + " isUnilateralCallIfNested:" + isUnilateralCallIfNested);
        if (areAllInEndState() && areAllAfterLRASuccessfullyCalledOrForgotten()) {
            if (forgetAnyUnilaterallyCompleted()) isReadyToDelete = true;
        }
        setProcessing(false);
    }

    public boolean forgetAnyUnilaterallyCompleted() {
        boolean isAllThatNeedsToBeForgottenForgotten = true;
        for (LRA nestedLRA : children) {
            log("LRA.forgetAnyUnilaterallyCompleted");
            if (nestedLRA.isNestedThatShouldBeForgottenAfterParentEnds) {
                if (!nestedLRA.sendForget()) return false;
            }
        }
        return isAllThatNeedsToBeForgottenForgotten;
    }

    private void sendCompleteOrCancel(boolean isCancel) {
        for (Participant participant : participants) {
            if (participant.isInEndStateOrListenerOnly() && !isChild) { //todo check ramifications of !isChild re timeout processing
                continue;
            }
            participant.sendCompleteOrCancel(this, isCancel);
        }
    }


    void sendAfterLRA() { // todo should set isRecovering or needsAfterLRA calls if this fails
        if (areAllInEndState()) {
            for (Participant participant : participants) {
                participant.sendAfterLRA(this);
            }
        }
    }

    void sendStatus() {
        for (Participant participant : participants) {
            URI statusURI = participant.getStatusURI();
            if (statusURI == null || participant.isInEndStateOrListenerOnly()) continue;
            Response response = null;
            int responsestatus = -1;
            String readEntity = null;
            ParticipantStatus participantStatus = null;
            try {
                String path = "http://127.0.0.1:8080/lra-coordinator/";
                response = client.target(statusURI)
                        .request()
                        .header(LRA_HTTP_CONTEXT_HEADER, path + lraId)
                        .header(LRA_HTTP_ENDED_CONTEXT_HEADER, path + lraId)
                        .header(LRA_HTTP_PARENT_CONTEXT_HEADER, parentId)
                        .header(LRA_HTTP_RECOVERY_HEADER, path + lraId)
                        .buildGet().invoke();
                responsestatus = response.getStatus();
                if (responsestatus == 503 || responsestatus == 202) { //todo include other retriables
                } else if (responsestatus != 410) {
                    readEntity = response.readEntity(String.class);
                    participantStatus = ParticipantStatus.valueOf(readEntity);
                    participant.setParticipantStatus(participantStatus);
                } else {
                    participant.setParticipantStatus(isCancel ? Compensated : Completed); // not exactly accurate as it's GONE not explicitly completed or compensated
                }
                log("LRA sendStatus:" + statusURI + " finished  response:" +
                        response + ":" + responsestatus + " participantStatus:" + participantStatus +
                        " readEntity:" + readEntity);
            } catch (Exception e) { // IllegalArgumentException: No enum constant org.eclipse.microprofile.lra.annotation.ParticipantStatus.
                log("LRA sendStatus:" + statusURI + " finished  response:" +
                        response + ":" + responsestatus + " participantStatus:" + participantStatus +
                        " readEntity:" + readEntity + " Exception:" + e);
            }
        }
    }

    boolean sendForget() { //todo could gate with isprocessing here as well
        boolean areAllThatNeedToBeForgottenForgotten = true;
        for (Participant participant : participants) {
            if (participant.getForgetURI() == null || participant.isForgotten()) continue;
            areAllThatNeedToBeForgottenForgotten = participant.sendForget(this, areAllThatNeedToBeForgottenForgotten);
        }
        return areAllThatNeedToBeForgottenForgotten;
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

    public boolean areAllCompletedOrCompensatedSuccessfully() {
        for (Participant participant : participants) {
            if (participant.getParticipantStatus() != ParticipantStatus.Completed &&
                    participant.getParticipantStatus() != Compensated) return false;
        }
        return true;
    }

    public boolean areAnyInFailedState() {
        for (Participant participant : participants) {
            if (participant.getParticipantStatus() == ParticipantStatus.FailedToComplete ||
                    participant.getParticipantStatus() == FailedToCompensate) return true;
        }
        return false;
    }

    public boolean areAllAfterLRASuccessfullyCalledOrForgotten() {
        for (Participant participant : participants) {
            if (!participant.isAfterLRASuccessfullyCalledIfEnlisted() && !participant.isForgotten()) return false;
        }
        return true;
    }

    public boolean areAllInEndState() {
        for (Participant participant : participants) {
            if (!participant.isInEndStateOrListenerOnly()) return false;
        }
        return true;
    }

    public boolean areAllInEndStateCompletedOrFailedToComplete() {
        for (Participant participant : participants) {
            if (participant.getParticipantStatus() != Completed && participant.getParticipantStatus() != FailedToComplete) return false;
        }
        return true;
    }

    public boolean areAllInEndStateCompensatedOrFailedToCompensate() {
        for (Participant participant : participants) {
            if (participant.getParticipantStatus() != Compensated && participant.getParticipantStatus() != FailedToCompensate) return false;
        }
        return true;
    }

    public boolean areAllInEndStateOrListenerOnlyForTerminationType(boolean isCompensate) {
        for (Participant participant : participants) {
            if (!participant.isInEndStateOrListenerOnlyForTerminationType(isCompensate)) return false;
        }
        return true;
    }

    private boolean areAllForgotten() { //todo should be isForgottenOrNoForgetMethodExists
        for (Participant participant : participants) {
            if (!participant.isForgotten()) return false;
        }
        return true;
    }

    public boolean areAllAfterLRASuccessfullyCalled() {
        for (Participant participant : participants) {
            if (!participant.isAfterLRASuccessfullyCalledIfEnlisted()) return false;
        }
        return true;
    }

    void log(String message) {
        System.out.println("[lra][depth:" + nestedDepth + "] " + message);
    }

    public String getConditionalStringValue(boolean isTrue, String first, String second) {
        return isTrue ? first : second;
    }

    void printStack(String message) {
        new Throwable(message).printStackTrace();
    }
}
