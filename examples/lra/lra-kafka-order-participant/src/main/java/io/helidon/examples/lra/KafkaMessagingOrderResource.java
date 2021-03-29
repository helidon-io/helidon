package io.helidon.examples.lra;

import io.helidon.messaging.connectors.kafka.KafkaMessage;
import org.apache.kafka.common.header.Header;
import org.eclipse.microprofile.lra.annotation.*;
import org.eclipse.microprofile.lra.annotation.ws.rs.LRA;
import org.eclipse.microprofile.lra.annotation.ws.rs.Leave;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Outgoing;

import javax.enterprise.context.ApplicationScoped;
import javax.ws.rs.*;

import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_CONTEXT_HEADER;

@Path("/") //there are no actual Rest endpoints as the order itself is placed with a message
@ApplicationScoped
public class KafkaMessagingOrderResource {

    private ParticipantStatus participantStatus;

    @Incoming("frontendchannel")
    @Outgoing("orderchannel")
    @LRA(value = LRA.Type.REQUIRES_NEW, end = false)
    public Message placeOrder(KafkaMessage msg) throws Exception {
        System.out.println("------>KafkaMessagingOrderResource.placeOrder received. " +
                "Will send message to inventory service to check availability.");
        participantStatus = ParticipantStatus.Active;
        //order id is in both payload and header of message sent...
        KafkaMessage<Object, String> inventoryRequestKafkaMessage = KafkaMessage.of("placeOrder orderid" + msg.getPayload());
        inventoryRequestKafkaMessage.getHeaders().add("orderid", msg.getPayload().toString().getBytes());
        inventoryRequestKafkaMessage.getHeaders().add("itemid", "testitemid".getBytes());
        return inventoryRequestKafkaMessage;
    }

    @Incoming("inventorychannel")
    @Outgoing("frontendreplychannel")
    @LRA(value = LRA.Type.MANDATORY, end = true)
    public Message receiveInventoryStatusForOrder(KafkaMessage msg) throws Exception {
        String methodName = "receiveInventoryStatusForOrder";
        Header lraidheader = displayLRAId(msg, methodName);
        Header inventoryStatus = msg.getHeaders().lastHeader("inventoryStatus");
        Object inventoryPayload = msg.getPayload();
        System.out.println("------>KafkaMessagingOrderResource." + methodName + " received " +
                "lraidheader:" + lraidheader  + "inventoryStatusHeader:" + inventoryStatus + " inventoryPayload:" + inventoryPayload);
        if(inventoryPayload.equals("inventorydoesnotexist")) throw new Exception("intentional exception to cause cancel call as inventorydoesnotexist");
        return KafkaMessage.of("placeOrder success");
    }

    @Incoming("kafkacompletechannel")
    @Outgoing("kafkacompletereplychannel")
    @Complete
    public Message completeMethod(KafkaMessage msg) throws Exception {
        displayLRAId(msg, "complete");
        participantStatus = ParticipantStatus.Completed;
        return KafkaMessage.of(participantStatus.toString());
    }

    @Incoming("kafkacompensatechannel")
    @Outgoing("kafkacompensatereplychannel")
    @Compensate
    public Message compensateMethod(KafkaMessage msg) throws Exception {
        displayLRAId(msg, "compensate");
        participantStatus = ParticipantStatus.Compensated;
        return KafkaMessage.of(participantStatus.toString());
    }

    @Incoming("kafkastatuschannel")
    @Outgoing("kafkastatusreplychannel")
    @Status
    public Message statusMethod(KafkaMessage msg) throws Exception {
        displayLRAId(msg, "status");
        return KafkaMessage.of(participantStatus.toString());
    }

    @Incoming("kafkaafterlrachannel")
    @Outgoing("kafkaafterlrareplychannel")
    @AfterLRA
    public Message afterLRAMethod(KafkaMessage msg) throws Exception {
        displayLRAId(msg, "afterLRA");
        return KafkaMessage.of(participantStatus.toString());
    }

    @Incoming("kafkaforgetchannel")
    @Outgoing("kafkaforgetreplychannel")
    @Forget
    public Message forgetLRAMethod(KafkaMessage msg) throws Exception {
        displayLRAId(msg, "forget");
        return KafkaMessage.of(participantStatus.toString());
    }

    @Incoming("kafkaleavechannel")
    @Outgoing("kafkaleavereplychannel")
    @Leave
    public Message leaveLRAMethod(KafkaMessage msg) throws Exception {
        displayLRAId(msg, "leave");
        return KafkaMessage.of(participantStatus.toString());
    }

    private Header displayLRAId(KafkaMessage msg, String methodName) {
        Header lraidheader = msg.getHeaders().lastHeader(LRA_HTTP_CONTEXT_HEADER);
        System.out.println("------>KafkaMessagingOrderResource." + methodName + " received " +
                "lraidheader:" + lraidheader);
        return lraidheader;
    }

}