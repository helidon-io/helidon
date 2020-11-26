package io.helidon.lra;

//import io.helidon.lra.messaging.SendMessage;
import org.eclipse.microprofile.lra.annotation.LRAStatus;
//import org.eclipse.microprofile.lra.annotation.ParticipantStatus;

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
    private boolean isInit = false;

    public LRA(String lraUUID) {
        lraId = lraUUID;
    }

    void removeParticipant(String compensatorLink, boolean isMessaging, boolean isToBeLogged) {
        //todo remove only the provided participant
        completeURIs = new ArrayList<>();
        compensateURIs = new ArrayList<>();
    }

    void addParticipant(String compensatorLink, boolean isMessaging, boolean isToBeLogged) {
        String uriPrefix = isMessaging ? "<messaging://" : "<http://";
        if (compensatorLink.indexOf(uriPrefix) > -1) {
            String endpoint = "";
            Pattern linkRelPattern = Pattern.compile("(\\w+)=\"([^\"]+)\"|([^\\s]+)");
            Matcher relMatcher = linkRelPattern.matcher(compensatorLink);
            while (relMatcher.find()) {
                String group0 = relMatcher.group(0);
                if (group0.contains(uriPrefix)) { // <messaging://complete>;
                    endpoint = isMessaging ? group0.substring(group0.indexOf(uriPrefix) + uriPrefix.length(), group0.indexOf(";") - 1) :
                            group0.substring(group0.indexOf(uriPrefix) + 1, group0.indexOf(";") - 1);
                }
                String key = relMatcher.group(1);
                if (key != null && key.equals("rel")) {
                    String rel = relMatcher.group(2) == null ? relMatcher.group(3) : relMatcher.group(2);
                    System.out.println("Coordinator.initParticipantURIs " + rel + " is " + endpoint);
                    try {
                        if (rel.equals("complete")) {
                            if (isMessaging) completeMessagingURIs.add(endpoint);
                            else {
                                completeURIs.add(new URI(endpoint));
                            }
                        }
                        if (rel.equals("compensate")) {
                            if (isMessaging) compensateMessagingURIs.add(endpoint);
                            else compensateURIs.add(new URI(endpoint));
                        }
                        if (rel.equals("after")) if (isMessaging) afterMessagingURIs.add(endpoint);
                        else afterURIs.add(new URI(endpoint));
                    } catch (URISyntaxException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        if (isToBeLogged) RecoveryManager.log(this, compensatorLink);
    }

    void tryDoEnd(boolean compensate, boolean isMessaging) {
        System.out.println("LRA End compensate:" + compensate);
        System.out.println("LRA End isMessaging:" + isMessaging);
//        if (isMessaging) SendMessage.send(compensate ? compensateMessagingURIs : completeMessagingURIs );
//        else   send(compensate);
         send(compensate);
        //cleanup...
       completeURIs = new ArrayList<>();
       compensateURIs = new ArrayList<>();
        afterURIs = new ArrayList<>();
        completeMessagingURIs = new ArrayList<>();
      compensateMessagingURIs = new ArrayList<>();
    }

    private void send(boolean compensate) {
        List<URI> endpointURIs = compensate ? compensateURIs : completeURIs;
        send(endpointURIs);
        send(afterURIs);
    }

    private void send(List<URI> endpointURIs) {
        for (URI endpointURI : endpointURIs) {
            System.out.println("LRARecord REST.send:" + endpointURI + " lraId:" + lraId);
            try {
                Client client = ClientBuilder.newBuilder()
                        .build();
                String path = "http://127.0.0.1:8080/lra-coordinator/";
//                Future<Response> response = client.target(endpointURI)
                Response response = client.target(endpointURI)
                        .request() //http://localhost:8080/deployment/lra-coordinator/0_ffff0a28054b_9133_5f855916_a7
                        .header(LRA_HTTP_CONTEXT_HEADER, path + lraId)
                        .header(LRA_HTTP_ENDED_CONTEXT_HEADER, path + lraId)
//                        .header(LRA_HTTP_PARENT_CONTEXT_HEADER, parentId)
                        .header(LRA_HTTP_RECOVERY_HEADER, path + lraId)
                        .buildPut(Entity.text(LRAStatus.Closed.name())).invoke();
//                        .buildPut(Entity.json("")).invoke();
//                        .async().put(Entity.json("entity"));
                System.out.println("LRARecord REST.send:" + endpointURI + " finished");
                System.out.println("LRARecord REST.send response:" + response + " : " + response.getStatus());
//                Future<Response> responseFuture =  client.target.equals(forgetURI) ? builder.async().delete()
//                        : builder.async().put(Entity.text(payload));
//                Response response = responseFuture.get(PARTICIPANT_TIMEOUT, TimeUnit.SECONDS);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


}

