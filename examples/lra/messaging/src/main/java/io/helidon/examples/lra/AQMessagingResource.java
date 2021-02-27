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
import io.helidon.messaging.connectors.kafka.KafkaMessage;
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
public class AQMessagingResource {

    private ParticipantStatus participantStatus;
    private boolean isCancel; //technically indicates whether to throw Exception
    private String uriToCall;
    private Map lraStatusMap = new HashMap<String, ParticipantStatus>();

    @Incoming("order")
    @Outgoing("inventory")
    @LRA(value = LRA.Type.REQUIRES_NEW)
    public Message requiresNew(AqMessage<String> msg) throws Exception {
        System.out.println("AQMessagingResource.requiresNew msg.getPayload():" + msg.getPayload() +
                " msg.getDbConnection():" + msg.getDbConnection());
        if (isCancel) throw new Exception("requiresNew throws intentional exception as isCancel is true");
        return () -> "requiresNew success";
    }

//    @Incoming("order-required")
//    @Outgoing("inventory-required")
//    @LRA(value = LRA.Type.REQUIRED)
    public Message required(AqMessage<String> msg) throws Exception {
        System.out.println("AQMessagingResource.required msg.getPayload():" + msg.getPayload() +
                " msg.getDbConnection():" + msg.getDbConnection());
        if (isCancel) throw new Exception("required throws intentional exception as isCancel is true");
        return () -> "required success";
    }

//    @Incoming("order")
//    @Outgoing("inventory")
//    @LRA(value = LRA.Type.MANDATORY)
    public Message mandatory(AqMessage<String> msg) throws Exception {
        System.out.println("AQMessagingResource.mandatory msg.getPayload():" + msg.getPayload() +
                " msg.getDbConnection():" + msg.getDbConnection());
        if (isCancel) throw new Exception("mandatory throws intentional exception as isCancel is true");
        return () -> "mandatory success";
    }

//    @Incoming("order")
//    @Outgoing("inventory")
//    @LRA(value = LRA.Type.NEVER)
    public Message never(AqMessage<String> msg) throws Exception {
        System.out.println("AQMessagingResource.never msg.getPayload():" + msg.getPayload() +
                " msg.getDbConnection():" + msg.getDbConnection());
        if (isCancel) throw new Exception("never throws intentional exception as isCancel is true");
        return () -> "never success";
    }

//    @Incoming("order")
//    @Outgoing("inventory")
//    @LRA(value = LRA.Type.NOT_SUPPORTED)
    public Message notSupported(AqMessage<String> msg) throws Exception {
        System.out.println("AQMessagingResource.notSupported msg.getPayload():" + msg.getPayload() +
                " msg.getDbConnection():" + msg.getDbConnection());
        if (isCancel) throw new Exception("notSupported throws intentional exception as isCancel is true");
        return () -> "notSupported success";
    }

//    @Incoming("order")
//    @Outgoing("inventory")
//    @LRA(value = LRA.Type.SUPPORTS)
    public Message supports(AqMessage<String> msg) throws Exception {
        System.out.println("AQMessagingResource.supports msg.getPayload():" + msg.getPayload() +
                " msg.getDbConnection():" + msg.getDbConnection());
        if (isCancel) throw new Exception("supports throws intentional exception as isCancel is true");
        return () -> "supports success";
    }

//    @Incoming("order")
//    @Outgoing("inventory")
//    @LRA(value = LRA.Type.NESTED)
    public Message nested(AqMessage<String> msg) throws Exception {
        System.out.println("AQMessagingResource.nested msg.getPayload():" + msg.getPayload() +
                " msg.getDbConnection():" + msg.getDbConnection());
        if (isCancel) throw new Exception("nested throws intentional exception as isCancel is true");
        return () -> "nested success";
    }

    @Incoming("completechannel")
    @Outgoing("completereplychannel")
    @Complete
    public Message completeMethod(AqMessage<String> msg) throws Exception {
        System.out.println("AQMessagingResource.complete");
        String lraID = getLRAID(msg);
        participantStatus = ParticipantStatus.Completed;
        return () ->  participantStatus.toString(); //todo append lra id
    }

    @Incoming("compensatechannel")
    @Outgoing("compensatereplychannel")
    @Compensate
    public String compensateMethod(AqMessage<String> msg) throws Exception {
        System.out.println("AQMessagingResource.compensate");
        String lraID = getLRAID(msg);
        participantStatus = ParticipantStatus.Compensated;
        return participantStatus.toString(); //todo append lra id
    }

    @Incoming("afterlrachannel")
    @Outgoing("afterlrareplychannel")
    @AfterLRA
    public String afterLRAMethod(AqMessage<String> msg) throws Exception {
        System.out.println("AQMessagingResource.afterLRA");
        String lraID = getLRAID(msg);
        return participantStatus.toString(); //todo append lra id
    }

    @Incoming("forgetchannel")
    @Outgoing("forgetreplychannel")
    @Forget
    public String forgetLRAMethod(AqMessage<String> msg) throws Exception {
        System.out.println("AQMessagingResource.forget");
        String lraID = getLRAID(msg);
        return participantStatus.toString(); //todo append lra id
    }

    @Incoming("leavechannel")
    @Outgoing("leavereplychannel")
    @Leave
    public String leaveLRAMethod(AqMessage<String> msg) throws Exception {
        System.out.println("AQMessagingResource.leave");
        String lraID = getLRAID(msg);
        return participantStatus.toString(); //todo append lra id
    }

    //no status method as AQ is guaranteed delivery

    private String getLRAID(AqMessage<String> msg) {
        return "testid";
    }


    //methods to set complete/compensate action if/as result of throwing exception

    private String getResponse(String lraType, Object payload) throws Exception {
        System.out.println("AQMessagingResource.getResponse lraType:" + lraType + " msg.getPayload():" + payload + " isCancel:" + isCancel);
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