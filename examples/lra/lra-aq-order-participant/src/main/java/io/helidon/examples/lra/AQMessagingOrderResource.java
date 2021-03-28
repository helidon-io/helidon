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
package io.helidon.examples.lra;

import io.helidon.messaging.connectors.aq.*;
import io.helidon.messaging.connectors.jms.JmsMessage;
import io.helidon.messaging.connectors.kafka.KafkaMessage;
import oracle.jms.AQjmsConstants;
import oracle.jms.AQjmsSession;
import org.eclipse.microprofile.lra.annotation.*;
import org.eclipse.microprofile.lra.annotation.ws.rs.LRA;
import org.eclipse.microprofile.lra.annotation.ws.rs.Leave;
import org.eclipse.microprofile.reactive.messaging.Acknowledgment;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Outgoing;

import javax.enterprise.context.ApplicationScoped;
import javax.jms.*;
import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.Map;

@Path("/aqmessaging")
@ApplicationScoped
public class AQMessagingOrderResource {

    private ParticipantStatus participantStatus = ParticipantStatus.Active;
    private boolean isCancel; //technically indicates whether to throw Exception
    private String uriToCall;
    private Map lraStatusMap = new HashMap<String, ParticipantStatus>();

    @Incoming("order-requiresnew")
//    @Outgoing("inventory")
    @LRA(value = LRA.Type.REQUIRES_NEW)
    public void requiresNew(AqMessage<String> msg) throws Exception {
        System.out.println("------>AQMessagingOrderResource.requiresNew msg.getPayload():" + msg.getPayload() +
                " msg.getDbConnection():" + msg.getDbConnection());
        if (isCancel) throw new Exception("requiresNew throws intentional exception as isCancel is true");
//        return () -> "requiresNew success";
    }

//    @Incoming("order-required")
//    @Outgoing("inventory")
    @LRA(value = LRA.Type.MANDATORY)
    public Message required(AqMessage<String> msg) throws Exception {
        System.out.println("------>AQMessagingOrderResource.required msg.getPayload():" + msg.getPayload() +
                " msg.getDbConnection():" + msg.getDbConnection());
        if (isCancel) throw new Exception("required throws intentional exception as isCancel is true");
        return () -> "required success";
    }

    @Incoming("completechannel")
    @Outgoing("completereplychannel")
    @Complete
    public Message completeMethod(AqMessage<String> msg) throws Exception {
        System.out.println("------>AQMessagingOrderResource.complete");
        String lraID = getLRAID(msg);
        participantStatus = ParticipantStatus.Completed;
        return JmsMessage.builder(participantStatus.toString()).build();
    }

    @Incoming("compensatechannel")
    @Outgoing("compensatereplychannel")
    @Compensate
    public Message compensateMethod(AqMessage<String> msg) throws Exception {
        System.out.println("------>AQMessagingOrderResource.compensate");
        String lraID = getLRAID(msg);
        participantStatus = ParticipantStatus.Compensated;
        return JmsMessage.of(participantStatus.toString());
    }

    @Incoming("afterlrachannel")
    @Outgoing("afterlrareplychannel")
    @AfterLRA
    public Message afterLRAMethod(AqMessage<String> msg) throws Exception {
        System.out.println("------>AQMessagingOrderResource.afterLRA");
        String lraID = getLRAID(msg);
        return JmsMessage.of(participantStatus.toString());
    }

    @Incoming("forgetchannel")
    @Outgoing("forgetreplychannel")
    @Forget
    public Message forgetLRAMethod(AqMessage<String> msg) throws Exception {
        System.out.println("------>AQMessagingOrderResource.forget");
        String lraID = getLRAID(msg);
        return JmsMessage.of(participantStatus.toString());
    }

    @Incoming("leavechannel")
    @Outgoing("leavereplychannel")
    @Leave
    public Message leaveLRAMethod(AqMessage<String> msg) throws Exception {
        System.out.println("------>AQMessagingOrderResource.leave");
        String lraID = getLRAID(msg);
        return JmsMessage.of(participantStatus.toString());
    }

    //no status method as AQ is guaranteed delivery

    private String getLRAID(AqMessage<String> msg) {
        return "testid";
    }


    //methods to set complete/compensate action if/as result of throwing exception

    private String getResponse(String lraType, Object payload) throws Exception {
        System.out.println("------>AQMessagingOrderResource.getResponse lraType:" + lraType + " msg.getPayload():" + payload + " isCancel:" + isCancel);
        participantStatus = ParticipantStatus.Active;
        if(isCancel) throw new Exception("Intentional exceptio");
        else return "success";
    }


    @GET
    @Path("/setCancel")
    public Response setCancel() {
        System.out.println("setCancel called. LRA method will throw exception (resulting in compensation if appropriate)");
        isCancel = true;
        return Response.ok()
                .entity("isCancel = true")
                .build();
    }

    @GET
    @Path("/setClose")
    public Response setClose() {
        System.out.println("setClose called. LRA method will NOT throw exception (resulting in complete if appropriate)");
        isCancel = false;
        return Response.ok()
                .entity("isCancel = false")
                .build();
    }

    @GET
    @Path("/setURIToCall")
    public Response setURIToCall(@QueryParam("uri") String uri) {
        System.out.println("setURIToCall:" + uri);
        uriToCall = uri;
        isCancel = false;
        return Response.ok()
                .entity("setURIToCall:" + uri)
                .build();
    }
}