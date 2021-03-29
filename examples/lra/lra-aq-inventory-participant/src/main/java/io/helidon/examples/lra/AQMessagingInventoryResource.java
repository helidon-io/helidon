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
import org.eclipse.microprofile.lra.annotation.*;
import org.eclipse.microprofile.lra.annotation.ws.rs.LRA;
import org.eclipse.microprofile.lra.annotation.ws.rs.Leave;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Outgoing;

import javax.enterprise.context.ApplicationScoped;
import javax.jms.*;
import javax.ws.rs.*;
import javax.ws.rs.core.Response;

import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_CONTEXT_HEADER;

@Path("/")
@ApplicationScoped
public class AQMessagingInventoryResource {

    private ParticipantStatus participantStatus;
    private int inventoryCount = 1;

    @Incoming("orderchannel")
    @Outgoing("inventorychannel")
    @LRA(value = LRA.Type.MANDATORY, end = false)
    public Message checkInventory(AqMessage<String> msg)  {
        String lraidheader = displayLRAId(msg, "checkInventory");
        System.out.println("------>AQMessagingOrderResource.checkInventory msg.getPayload():" + msg.getPayload() +
                " inventoryCount:" + inventoryCount + " lraidheader:" + lraidheader +
                " msg.getDbConnection():" + msg.getDbConnection()); //this is where the JDBC connection would be used to check inventory
        participantStatus = ParticipantStatus.Active;
        String inventoryStatus = inventoryCount < 1 ?"inventorydoesnotexist":"inventoryexists";
        return JmsMessage.builder(inventoryStatus).property("inventoryStatus", inventoryStatus).build();
    }

    @Incoming("completechannel")
    @Outgoing("completereplychannel")
    @Complete
    public Message completeMethod(AqMessage<String> msg) throws Exception {
        displayLRAId(msg, "complete");
        participantStatus = ParticipantStatus.Completed;
        return JmsMessage.builder(participantStatus.toString()).build();
    }

    @Incoming("compensatechannel")
    @Outgoing("compensatereplychannel")
    @Compensate
    public Message compensateMethod(AqMessage<String> msg) throws Exception {
        displayLRAId(msg, "compensate");
        participantStatus = ParticipantStatus.Compensated;
        return JmsMessage.of(participantStatus.toString());
    }

    @Incoming("afterlrachannel")
    @Outgoing("afterlrareplychannel")
    @AfterLRA
    public Message afterLRAMethod(AqMessage<String> msg) throws Exception {
        displayLRAId(msg, "afterLRA");
        return JmsMessage.of(participantStatus.toString());
    }

    @Incoming("forgetchannel")
    @Outgoing("forgetreplychannel")
    @Forget
    public Message forgetLRAMethod(AqMessage<String> msg) throws Exception {
        displayLRAId(msg, "forget");
        return JmsMessage.of(participantStatus.toString());
    }

    @Incoming("leavechannel")
    @Outgoing("leavereplychannel")
    @Leave
    public Message leaveLRAMethod(AqMessage<String> msg) throws Exception {
        displayLRAId(msg, "leave");
        return JmsMessage.of(participantStatus.toString());
    }

    //no status method as AQ is guaranteed delivery

    private String  displayLRAId(AqMessage<String> msg, String methodName) {
        String lraidheader = null;
        try {
            lraidheader = msg.getJmsMessage().getStringProperty(LRA_HTTP_CONTEXT_HEADER);
        } catch (JMSException e) {
            e.printStackTrace();
        }
        System.out.println("------>AQMessagingInventoryResource." + methodName + " received " +
                "lraidheader:" + lraidheader);
        return lraidheader;
    }

    @GET
    @Path("/addInventory")
    public Response addInventory() {
        System.out.println("------>AQMessagingInventoryResource.addInventory called");
        inventoryCount++;
        return Response.ok()
                .entity("inventoryCount:" + inventoryCount)
                .build();
    }

    @GET
    @Path("/removeInventory")
    public Response removeInventory() {
        System.out.println("------>AQMessagingInventoryResource.removeInventory called");
        inventoryCount--;
        return Response.ok()
                .entity("inventoryCount:" + inventoryCount)
                .build();
    }
}