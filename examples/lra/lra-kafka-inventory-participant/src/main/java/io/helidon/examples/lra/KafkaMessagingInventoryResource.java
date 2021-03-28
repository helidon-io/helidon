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

@Path("/kafkamessaging")
@ApplicationScoped
public class KafkaMessagingInventoryResource {

    private ParticipantStatus participantStatus;
    private boolean isCancel; //technically indicates whether to throw Exception
    String uriToCall;
    private Map lraStatusMap = new HashMap<String, ParticipantStatus>();

    @Incoming("orderchannel")
    @Outgoing("inventorychannel")
    @LRA(value = LRA.Type.MANDATORY, end = false)
    public Message checkInventory(KafkaMessage msg)  {
        Header lraidheader = msg.getHeaders().lastHeader(LRA_HTTP_CONTEXT_HEADER);
        System.out.println("------>KafkaMessagingInventoryResource.checkInventory  msg:" + msg +
                " msg.getPayload():" + msg.getPayload() + " isCancel:" + isCancel + " lraidheader:" + lraidheader);
        participantStatus = ParticipantStatus.Active;
        KafkaMessage<Object, String> kafkaMessage = KafkaMessage.of(isCancel?"inventorydoesnotexist":"inventoryexists");
            kafkaMessage.getHeaders().add("doesinventoryexist", (isCancel + "").getBytes());
            return kafkaMessage;
    }

    @Incoming("kafkacompletechannel")
    @Outgoing("kafkacompletereplychannel")
    @Complete
    public Message completeMethod(KafkaMessage msg)  {
        System.out.println("------>KafkaMessagingInventoryResource.complete msg:" + msg);
        participantStatus = ParticipantStatus.Completed;
        return KafkaMessage.of(participantStatus.toString());
    }

    @Incoming("kafkacompensatechannel")
    @Outgoing("kafkacompensatereplychannel")
    @Compensate
    public Message compensateMethod(KafkaMessage msg) throws Exception {
        System.out.println("------>KafkaMessagingInventoryResource.compensate msg:" + msg);
        participantStatus = ParticipantStatus.Compensated;
        return KafkaMessage.of(participantStatus.toString());
    }


    @Incoming("kafkastatuschannel")
    @Outgoing("kafkastatusreplychannel")
    @Status
    public Message statusMethod(KafkaMessage msg) throws Exception {
        System.out.println("------>KafkaMessagingInventoryResource.status msg:" + msg);
        return KafkaMessage.of(participantStatus.toString());
    }

    @Incoming("kafkaafterlrachannel")
    @Outgoing("kafkaafterlrareplychannel")
    @AfterLRA
    public Message afterLRAMethod(KafkaMessage msg) throws Exception {
        System.out.println("------>KafkaMessagingInventoryResource.afterLRA msg:" + msg);
        return KafkaMessage.of(participantStatus.toString());
    }

    @Incoming("kafkaforgetchannel")
    @Outgoing("kafkaforgetreplychannel")
    @Forget
    public Message forgetLRAMethod(KafkaMessage msg) throws Exception {
        System.out.println("------>KafkaMessagingInventoryResource.forget msg:" + msg);
        return KafkaMessage.of(participantStatus.toString());
    }

    @Incoming("kafkaleavechannel")
    @Outgoing("kafkaleavereplychannel")
    @Leave
    public Message leaveLRAMethod(KafkaMessage msg) throws Exception {
        System.out.println("------>KafkaMessagingInventoryResource.forget");
        return KafkaMessage.of(participantStatus.toString());
    }

    //methods to set complete/compensate action if/as result of throwing exception


    @GET
    @Path("/setCancel")
    public Response setCancel() {
        System.out.println("------>setCancel called. LRA method will throw exception (resulting in compensation if appropriate)");
        isCancel = true;
        return Response.ok()
                .entity("isCancel = true")
                .build();
    }

    @GET
    @Path("/setClose")
    public Response setClose() {
        System.out.println("------>setClose called. LRA method will NOT throw exception (resulting in complete if appropriate)");
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