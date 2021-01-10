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

import io.helidon.lra.messaging.SendMessage;
import org.eclipse.microprofile.lra.annotation.LRAStatus;

import javax.ws.rs.client.*;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.*;

public class LRA {

    public long timeout;
    String lraId;
    private URI parentId;
    List<String> compensatorLinks = new ArrayList<>();
    List<LRA> children = new ArrayList<>();

    List<URI> completeURIs = new ArrayList<>();
    List<URI> compensateURIs = new ArrayList<>();
    List<URI> afterURIs = new ArrayList<>();
    List<URI> forgetURIs = new ArrayList<>();
    List<URI> statusURIs = new ArrayList<>();

    List<String> completeMessagingURIs = new ArrayList<>();
    List<String> compensateMessagingURIs = new ArrayList<>();
    List<String> afterMessagingURIs = new ArrayList<>();
    List<String> forgetMessagingURIs = new ArrayList<>();
    List<String> statusMessagingURIs = new ArrayList<>();
    private boolean isEndCompleteForAfterLRAEnlistmentDuringClosingPhase = false;
    boolean isRecovering = false;
    private boolean isRecoveringFromConnectionExceptionOr202 = false;
    private boolean isCompensate;
    private boolean isChild;
    private boolean isParent;
    private boolean isProcessing;

    public LRA(String lraUUID) {
        lraId = lraUUID;
    }

    public LRA(String lraUUID, URI parentId) {
        lraId = lraUUID;
        this.parentId = parentId;
    }

    void removeParticipant(String compensatorLink, boolean isMessaging, boolean isToBeLogged) {
        //todo remove only the provided participant
        completeURIs = new ArrayList<>();
        compensateURIs = new ArrayList<>();
    }

    void addParticipant(String compensatorLink, boolean isMessaging, boolean isToBeLogged) {
        if (compensatorLinks.contains(compensatorLink)) return;
        else compensatorLinks.add(compensatorLink);
        String uriPrefix = isMessaging ? "<messaging://" : "<http://";
        // <messaging://completeinventorylra>; rel="complete"; title="complete URI"; type="text/plain",
        // <messaging://compensate>; rel="compensate"; title="compensate URI"; type="text/plain"
        // <http://127.0.0.1:8091/inventory/completeInventory?method=javax.ws.rs.PUT>; rel="complete"; title="complete URI"; type="text/plain",
        if (compensatorLink.indexOf(uriPrefix) > -1) {
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
//                    log("LRA.initParticipantURIs isMessaging = " + isMessaging + " endpoint:" + endpoint);
                }
                String key = relMatcher.group(1);
                if (key != null && key.equals("rel")) {
                    String rel = relMatcher.group(2) == null ? relMatcher.group(3) : relMatcher.group(2);
//                    log("LRA.initParticipantURIs " + rel + " is " + endpoint);
                    try {
                        if (rel.equals("complete")) {
                            if (isMessaging) completeMessagingURIs.add(endpoint);
                            else completeURIs.add(new URI(endpoint));
                        }
                        if (rel.equals("compensate")) {
                            if (isMessaging) compensateMessagingURIs.add(endpoint);
                            else compensateURIs.add(new URI(endpoint));
                        }
                        if (rel.equals("after")) {
                            if (isMessaging) afterMessagingURIs.add(endpoint);
                            else afterURIs.add(new URI(endpoint));
                        }
                        if (rel.equals("status")) {
                            if (isMessaging) statusMessagingURIs.add(endpoint);
                            else statusURIs.add(new URI(endpoint));
                        }
                        if (rel.equals("after")) {
                            if (isMessaging) afterMessagingURIs.add(endpoint);
                            else afterURIs.add(new URI(endpoint));
                        }
                        if (rel.equals("forget")) {
                            if (isMessaging) forgetMessagingURIs.add(endpoint);
                            else forgetURIs.add(new URI(endpoint));
                        }
                    } catch (URISyntaxException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        if (isToBeLogged) RecoveryManager.getInstance().log(this, compensatorLink);
    }

    public void addChild(String lraUUID, LRA lra) {
        children.add(lra);
        lra.isChild = true;
        isParent = true;
    }


    void tryDoEnd(boolean compensate, boolean isMessaging) {
        setProcessing(true);
        isCompensate = compensate;
        log("LRA End compensate:" + compensate + "+ isMessaging:" + isMessaging);
        if (isMessaging) SendMessage.send(compensate ? compensateMessagingURIs : completeMessagingURIs);
        else {
            if (isParent) sendCompletion(compensateURIs, compensate);
            for (LRA nestedLRA : children) {
                log("LRA.endChildren nestedLRA.compensateURIs.size():" + nestedLRA.compensateURIs.size());
                nestedLRA.sendCompletion(nestedLRA.compensateURIs, compensate);
//                nestedLRA.tryDoEnd(compensate, false);
            }
            send(compensate);
        }
        cleanup();
        setProcessing(false);
    }

    void cleanup() {
        if (!isRecoveringFromConnectionExceptionOr202) {
            completeURIs = new ArrayList<>();
            compensateURIs = new ArrayList<>();
            afterURIs = new ArrayList<>();
            completeMessagingURIs = new ArrayList<>();
            compensateMessagingURIs = new ArrayList<>();
        }
    }

    private void send(boolean compensate) {
        List<URI> endpointURIs = compensate ? compensateURIs : completeURIs;
        sendCompletion(endpointURIs, compensate);
        for (URI endpointURI : afterURIs) {
            try {
                Response response = sendCompletion(endpointURI, isCompensate);
                int responsestatus = response.getStatus();
                log("LRA REST.send:" + endpointURI + " finished  response:" + response + ":" + responsestatus);
                isEndCompleteForAfterLRAEnlistmentDuringClosingPhase = true;
            } catch (Exception e) {
                log("LRA REST.send Exception:" + e);
                isRecoveringFromConnectionExceptionOr202 = true;
            }
        }
    }

    void callAfterLRAForEnlistmentDuringClosingPhase() {
        for (URI endpointURI : afterURIs) {
            log("LRA REST.callAfterLRAForEnlistmentDuringClosingPhase:" + endpointURI + " lraId:" + lraId);
            try {
                Response response = sendCompletion(endpointURI, false);
                int responsestatus = response.getStatus();
                log("LRA REST.callAfterLRAForEnlistmentDuringClosingPhase:" + endpointURI + " finished  response:" + response + ":" + responsestatus);
            } catch (Exception e) {
                log("LRA REST.callAfterLRAForEnlistmentDuringClosingPhase Exception:" + e);
            }
        }
    }

    Response sendStatus() {
        Response response = null;
        for (URI endpointURI : statusURIs) {
            log("LRA REST.sendStatus:" + endpointURI + " lraId:" + lraId);
            try {
                Client client = ClientBuilder.newBuilder()
                        .build();
                String path = "http://127.0.0.1:8080/lra-coordinator/";
                response = client.target(endpointURI)
                        .request() //http://localhost:8080/deployment/lra-coordinator/0_ffff0a28054b_9133_5f855916_a7
                        .header(LRA_HTTP_CONTEXT_HEADER, path + lraId)
                        .header(LRA_HTTP_ENDED_CONTEXT_HEADER, path + lraId)
                        .header(LRA_HTTP_PARENT_CONTEXT_HEADER, parentId) // make the context available to participants
                        .header(LRA_HTTP_RECOVERY_HEADER, path + lraId)
                        .buildGet().invoke();
                int responsestatus = response.getStatus();
                log("LRA REST.sendStatus:" + endpointURI + " finished  response:" + response + ":" + responsestatus);
            } catch (Exception e) {
                log("LRA REST.sendStatus Exception:" + e);
            }
        }
        return response;
    }

    void sendForget() {
        for (URI endpointURI : forgetURIs) {
            log("LRA REST.sendForget:" + endpointURI + " lraId:" + lraId);
            try {
                Client client = ClientBuilder.newBuilder()
                        .build();
                String path = "http://127.0.0.1:8080/lra-coordinator/";
                Response response = client.target(endpointURI)
                        .request()
                        .header(LRA_HTTP_CONTEXT_HEADER, path + lraId)
                        .header(LRA_HTTP_ENDED_CONTEXT_HEADER, path + lraId)
                        .header(LRA_HTTP_PARENT_CONTEXT_HEADER, parentId)
                        .header(LRA_HTTP_RECOVERY_HEADER, path + lraId)
                        .buildDelete().invoke();
                int responsestatus = response.getStatus();
                log("LRA REST.sendForget:" + endpointURI + " finished  response:" + response + ":" + responsestatus);
            } catch (Exception e) {
                log("LRA REST.sendForget Exception:" + e);
                isRecoveringFromConnectionExceptionOr202 = true;
            }
        }
    }

    private void sendCompletion(List<URI> endpointURIs, boolean isCompensate) {
        for (URI endpointURI : endpointURIs) {
            log("LRA REST.send:" + endpointURI + " lraId:" + lraId);
            try {
                Response response = sendCompletion(endpointURI, isCompensate);
                int responsestatus = response.getStatus();
                log("LRA REST.send:" + endpointURI + " finished  response:" + response + ":" + responsestatus);
                if (responsestatus == 503) {
                    sendStatus();
//                    RecoveryManager.getInstance().add(lraId, this);
                    isRecovering=true;
                } else if (responsestatus == 202) {
                    Response statusResponse = sendStatus();
// isRecoveringFromConnectionExceptionOr202 equal to true fixes TckUnknownTests.compensate_retry and TckUnknownTests.complete_retry but introduces these ...
/**
 [ERROR] Failures:
 [ERROR]   TckContextTests.testAfterLRAEnlistmentDuringClosingPhase:313 Resource '/required-lra' is not completed expected:<1> but was:<2>

 [ERROR]   TckParticipantTests.validSignaturesChainTest:126 Non JAX-RS @Forget method should have been called
 Expected: a value equal to or greater than <1>
 but: <0> was less than <1>

 [ERROR]   TckRecoveryTests.testCancelWhenParticipantIsUnavailable:199->assertMetricCallbackCalled:214 Expecting the metric Compensated callback was called
 Expected: a value equal to or greater than <1>
 but: <0> was less than <1>

 [ERROR]   TckTests.timeLimit:334 The LRA should have timed out but complete was called instead of compensate.
 Expecting the number of complete call before test matches the ones after LRA timed out. The test call went to http://localhost:8180/lraresource/timeLimit expected:<0> but was:<1>
 [ERROR] Tests run: 133, Failures: 7, Errors: 0, Skipped: 0
 */
                    isRecoveringFromConnectionExceptionOr202 = true;
//                    RecoveryManager.getInstance().add(lraId, this);
                    isRecovering=true;
//                    isRecoveringFromConnectionException = true; //this allows TckUnknownTests.complete_retry but causes hang in tck
                } else if (responsestatus == 404) {
                } else if (responsestatus != 200) {
                    Response statusResponse = sendStatus();
                    if (statusResponse == null) {
                        log("LRA.send status:" + null);
                    } else {
                        log("LRA.send status:" + statusResponse.getStatus());
                    }
                    sendForget();  // handles TckParticipantTests.validSignaturesChainTest  but not TckContextTests.testForget
//                    RecoveryManager.getInstance().add(lraId, this);
                    isRecovering=true;
                } else if (responsestatus == 200) {
//                    endpointURIs.remove(endpointURI);
                    isRecoveringFromConnectionExceptionOr202 = false;
                    isEndCompleteForAfterLRAEnlistmentDuringClosingPhase = true; //not accurate
                } else {
                    isRecoveringFromConnectionExceptionOr202 = false;  //todo verify if necessary as recovery method would reset this if true (false is default)
                }
                isEndCompleteForAfterLRAEnlistmentDuringClosingPhase = true; //not accurate
            } catch (Exception e) { // Exception:javax.ws.rs.ProcessingException: java.net.ConnectException: Connection refused (Connection refused)
                log("LRA REST.sendCompletion Exception:" + e); //todo afterLRA is currently called regardless
                isRecoveringFromConnectionExceptionOr202 = true;
            }
        }
    }

    //called by recoverymanager
    Response sendCompletion() {
        Response response = null; //the last response
        for (URI endpointURI : isCompensate ? compensateURIs : completeURIs) {
            response = sendCompletion(endpointURI, true);
            if (response != null && response.getStatus() == 200) {
                isRecoveringFromConnectionExceptionOr202 = false;
                cleanup();
            }
        }
        return response;
    }

    private Response sendCompletion(URI endpointURI, boolean isCompensate) {
        Client client = ClientBuilder.newBuilder()
                .build();
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

    //used to determine/gate callAfterLRAForEnlistmentDuringClosingPhase
    public boolean isEndCompleteForAfterLRAEnlistmentDuringClosingPhase() {
        return isEndCompleteForAfterLRAEnlistmentDuringClosingPhase;
    }


    void log(String message) {
        System.out.println("ischild:" + isChild + " isParent:" + isParent + " " + message);
    }

    public void setProcessing(boolean isProcessing) {
        this.isProcessing = isProcessing;
    }

    public boolean isProcessing() {
        return isProcessing;
    }
}
