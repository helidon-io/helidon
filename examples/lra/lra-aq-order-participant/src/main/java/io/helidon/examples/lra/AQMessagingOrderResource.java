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
import oracle.jms.AQjmsFactory;
import oracle.jms.AQjmsSession;
import org.eclipse.microprofile.lra.annotation.*;
import org.eclipse.microprofile.lra.annotation.ws.rs.LRA;
import org.eclipse.microprofile.lra.annotation.ws.rs.Leave;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Outgoing;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.inject.Named;
import javax.jms.*;
import javax.sql.DataSource;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_CONTEXT_HEADER;

@Path("/") //there are no actual Rest endpoints as the order itself is placed with a message
@ApplicationScoped
public class AQMessagingOrderResource {

    @Inject
    @Named("lrapdb")
    private DataSource lrapdb;

    private ParticipantStatus participantStatus = ParticipantStatus.Active;

    @Incoming("frontendchannel")
    @Outgoing("orderchannel")
    @LRA(value = LRA.Type.REQUIRES_NEW, end = false)
    public Message placeOrder(AqMessage<String> msg) throws Exception {
        System.out.println("------>AQMessagingOrderResource.placeOrder received. " +
                "Will send message to inventory service to check availability." + " msg.getPayload():" + msg.getPayload() +
                " msg.getDbConnection():" + msg.getDbConnection()); //this is where the JDBC connection would be used to insert order, etc.
        participantStatus = ParticipantStatus.Active;
        return JmsMessage.of(participantStatus.toString());
    }

    @Incoming("inventorychannel")
    @Outgoing("frontendreplychannel")
    @LRA(value = LRA.Type.MANDATORY)
    public Message receiveInventoryStatusForOrder(AqMessage<String> msg) throws Exception {
        String methodName = "receiveInventoryStatusForOrder";
        String lraidheader = displayLRAId(msg, methodName);
        System.out.println("------>AQMessagingOrderResource.receiveInventoryStatusForOrder msg.getPayload():" + msg.getPayload() +
                " msg.getDbConnection():" + msg.getDbConnection()); //this is where the JDBC connection would be used to update order, shipping, etc.
        String inventoryStatus = msg.getJmsMessage().getStringProperty("inventoryStatus");
        String inventoryPayload = msg.getPayload();
        System.out.println("------>KafkaMessagingOrderResource." + methodName + " received " +
                "lraidheader:" + lraidheader  + "inventoryStatusHeader:" + inventoryStatus + " inventoryPayload:" + inventoryPayload);
        if(inventoryPayload.equals("inventorydoesnotexist")) throw new Error("intentional exception to cause cancel call as inventorydoesnotexist");
        return JmsMessage.of("placeOrder success");
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
        displayLRAId(msg, "complensate");
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
        System.out.println("------>AQMessagingOrderResource." + methodName + " received " +
                "lraidheader:" + lraidheader);
        return lraidheader;
    }




    @Path("/placeOrder")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public Response placeOrder() throws JMSException {
        QueueSession session;
        QueueConnectionFactory q_cf = AQjmsFactory.getQueueConnectionFactory(lrapdb);
        try (QueueConnection q_conn = q_cf.createQueueConnection()){
            System.out.println("------>AQMessagingOrderResource.placeOrder send order message...");
            session = q_conn.createQueueSession(true, Session.CLIENT_ACKNOWLEDGE);
            Queue queue = ((AQjmsSession) session).getQueue("frank", "FRONTENDQUEUE");
            System.out.println(" about to sendTestMessageToQueue to queue:" + queue);
            MessageProducer producer = session.createProducer(queue);
            TextMessage objmsg = session.createTextMessage();
            objmsg.setStringProperty("placeOrder", "order66");
            producer.send(objmsg);
            session.commit();
            System.out.println("------>AQMessagingOrderResource.placeOrder send order complete. Check logs or reply queue for outcome.");
            return Response.ok()
                    .entity("placeOrder request complete.  Check logs or reply queue for outcome.")
                    .build();
        }
    }
}