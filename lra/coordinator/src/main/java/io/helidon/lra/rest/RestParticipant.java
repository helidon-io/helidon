package io.helidon.lra.rest;

import io.helidon.lra.LRA;
import io.helidon.lra.Participant;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.Response;

import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.*;
import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_RECOVERY_HEADER;

//todo async for most if not all...
public class RestParticipant extends Participant {

    private Client client = ClientBuilder.newBuilder().build();

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
