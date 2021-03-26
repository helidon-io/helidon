package io.helidon.examples.lra;

import io.helidon.messaging.connectors.kafka.KafkaMessage;
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

@Path("/kafkamessaging")
@ApplicationScoped
public class KafkaMessagingResource {

    private ParticipantStatus participantStatus;
    private boolean isCancel; //technically indicates whether to throw Exception
    String uriToCall;
    private Map lraStatusMap = new HashMap<String, ParticipantStatus>();

    @Incoming("orderkafka")
    @Outgoing("inventorykafka")
    @LRA(value = LRA.Type.REQUIRES_NEW)
    public Message requiresNew(KafkaMessage msg) throws Exception {
        return getResponse("requiresNew", msg.getPayload());
    }

//    @Incoming("orderkafka")
//    @Outgoing("inventorykafka")
//    @LRA(value = LRA.Type.REQUIRED)
    public Message required(KafkaMessage msg) throws Exception {
        return getResponse("required", msg.getPayload());
    }

//    @Incoming("orderkafka")
//    @Outgoing("inventorykafka")
//    @LRA(value = LRA.Type.MANDATORY)
    public Message mandatory(KafkaMessage msg) throws Exception {
        return getResponse("mandatory", msg.getPayload());
    }

//    @Incoming("orderkafka")
//    @Outgoing("inventorykafka")
//    @LRA(value = LRA.Type.NEVER)
    public Message never(KafkaMessage msg) throws Exception {
        return getResponse("never", msg.getPayload());
    }

//    @Incoming("orderkafka")
//    @Outgoing("inventorykafka")
//    @LRA(value = LRA.Type.NOT_SUPPORTED)
    public Message notSupported(KafkaMessage msg) throws Exception {
        return getResponse("notSupported", msg.getPayload());
    }

//    @Incoming("orderkafka")
//    @Outgoing("inventorykafka")
//    @LRA(value = LRA.Type.SUPPORTS)
    public Message supports(KafkaMessage msg) throws Exception {
        System.out.println("KafkaMessagingResource.complete msg:" + msg);
        return getResponse("supports", msg.getPayload());
    }

//    @Incoming("orderkafka")
//    @Outgoing("inventorykafka")
//    @LRA(value = LRA.Type.NESTED)
    public Message nested(KafkaMessage msg) throws Exception {
        System.out.println("KafkaMessagingResource.complete msg:" + msg);
        return getResponse("nested", msg.getPayload());
    }


    @Incoming("kafkacompletechannel")
    @Outgoing("kafkacompletereplychannel")
    @Complete
    public Message completeMethod(KafkaMessage msg) throws Exception {
        System.out.println("KafkaMessagingResource.complete msg:" + msg);
        participantStatus = ParticipantStatus.Completed;
        return KafkaMessage.of(participantStatus.toString());
    }

    @Incoming("kafkacompensatechannel")
    @Outgoing("kafkacompensatereplychannel")
    @Compensate
    public Message compensateMethod(KafkaMessage msg) throws Exception {
        System.out.println("KafkaMessagingResource.compensate msg:" + msg);
        participantStatus = ParticipantStatus.Compensated;
        return KafkaMessage.of(participantStatus.toString());
    }


    @Incoming("kafkastatuschannel")
    @Outgoing("kafkastatusreplychannel")
    @Status
    public Message statusMethod(KafkaMessage msg) throws Exception {
        System.out.println("KafkaMessagingResource.status msg:" + msg);
        return KafkaMessage.of(participantStatus.toString());
    }

    @Incoming("kafkaafterlrachannel")
    @Outgoing("kafkaafterlrareplychannel")
    @AfterLRA
    public Message afterLRAMethod(KafkaMessage msg) throws Exception {
        System.out.println("KafkaMessagingResource.afterLRA msg:" + msg);
        return KafkaMessage.of(participantStatus.toString());
    }

    @Incoming("kafkaforgetchannel")
    @Outgoing("kafkaforgetreplychannel")
    @Forget
    public Message forgetLRAMethod(KafkaMessage msg) throws Exception {
        System.out.println("KafkaMessagingResource.forget msg:" + msg);
        return KafkaMessage.of(participantStatus.toString());
    }

    @Incoming("kafkaleavechannel")
    @Outgoing("kafkaleavereplychannel")
    @Leave
    public Message leaveLRAMethod(KafkaMessage msg) throws Exception {
        System.out.println("KafkaMessagingResource.forget");
        return KafkaMessage.of(participantStatus.toString());
    }

    //methods to set complete/compensate action if/as result of throwing exception

    //todo the payload and headers will be different if the outgoing is calling another service rather than just replying to the same queue and/or caller
    private Message getResponse(String lraType, Object payload) throws Exception {
        System.out.println("AQMessagingResource.getResponse lraType:" + lraType + " msg.getPayload():" + payload + " isCancel:" + isCancel);
        participantStatus = ParticipantStatus.Active;
        if(isCancel) throw new Exception("Intentional exception");
        else  {
            KafkaMessage<Object, String> kafkaMessage = KafkaMessage.of(lraType + " success");
            kafkaMessage.getHeaders().add("testheader","testvalue".getBytes());
            return kafkaMessage;
        }
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