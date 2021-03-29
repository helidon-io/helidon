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
import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.Map;

import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_CONTEXT_HEADER;

@Path("/")
@ApplicationScoped
public class KafkaMessagingInventoryResource {

    private ParticipantStatus participantStatus;
    private int inventoryCount = 1;

    @Incoming("orderchannel")
    @Outgoing("inventorychannel")
    @LRA(value = LRA.Type.MANDATORY, end = false)
    public Message checkInventory(KafkaMessage msg)  {
        Header lraidheader = displayLRAId(msg, "checkInventory");
        System.out.println("------>KafkaMessagingInventoryResource.checkInventory  msg:" + msg +
                " msg.getPayload():" + msg.getPayload() + " inventoryCount:" + inventoryCount + " lraidheader:" + lraidheader);
        participantStatus = ParticipantStatus.Active;
        String inventoryStatus = inventoryCount < 1 ?"inventorydoesnotexist":"inventoryexists";
        KafkaMessage<Object, String> kafkaMessage = KafkaMessage.of(inventoryStatus);
            kafkaMessage.getHeaders().add("inventoryStatus", (inventoryStatus).getBytes());
            return kafkaMessage;
    }

    @Incoming("kafkacompletechannel")
    @Outgoing("kafkacompletereplychannel")
    @Complete
    public Message completeMethod(KafkaMessage msg)  {
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
        displayLRAId(msg, "statusMethod");
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

    @GET
    @Path("/addInventory")
    public Response addInventory() {
        System.out.println("------>RestInventoryResource.addInventory called");
        inventoryCount++;
        return Response.ok()
                .entity("inventoryCount:" + inventoryCount)
                .build();
    }

    @GET
    @Path("/removeInventory")
    public Response removeInventory() {
        System.out.println("------>RestInventoryResource.removeInventory called");
        inventoryCount--;
        return Response.ok()
                .entity("inventoryCount:" + inventoryCount)
                .build();
    }

}