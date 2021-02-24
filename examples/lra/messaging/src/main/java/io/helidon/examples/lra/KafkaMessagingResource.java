package io.helidon.examples.lra;

import io.helidon.messaging.connectors.kafka.KafkaMessage;
import org.eclipse.microprofile.lra.annotation.*;
import org.eclipse.microprofile.lra.annotation.ws.rs.LRA;
import org.eclipse.microprofile.lra.annotation.ws.rs.Leave;
import org.eclipse.microprofile.reactive.messaging.Incoming;
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
    public String requiresNew(KafkaMessage msg) throws Exception {
        return getResponse("requresNew", msg.getPayload());
    }

//    @Incoming("orderkafka")
//    @Outgoing("inventorykafka")
//    @LRA(value = LRA.Type.REQUIRED)
    public String required(KafkaMessage msg) throws Exception {
        return getResponse("required", msg.getPayload());
    }

//    @Incoming("orderkafka")
//    @Outgoing("inventorykafka")
//    @LRA(value = LRA.Type.MANDATORY)
    public String mandatory(KafkaMessage msg) throws Exception {
        return getResponse("mandatory", msg.getPayload());
    }

//    @Incoming("orderkafka")
//    @Outgoing("inventorykafka")
//    @LRA(value = LRA.Type.NEVER)
    public String never(KafkaMessage msg) throws Exception {
        return getResponse("never", msg.getPayload());
    }

//    @Incoming("orderkafka")
//    @Outgoing("inventorykafka")
//    @LRA(value = LRA.Type.NOT_SUPPORTED)
    public String notSupported(KafkaMessage msg) throws Exception {
        return getResponse("notSupported", msg.getPayload());
    }

//    @Incoming("orderkafka")
//    @Outgoing("inventorykafka")
//    @LRA(value = LRA.Type.SUPPORTS)
    public String supports(KafkaMessage msg) throws Exception {
        return getResponse("supports", msg.getPayload());
    }

//    @Incoming("orderkafka")
//    @Outgoing("inventorykafka")
//    @LRA(value = LRA.Type.NESTED)
    public String nested(KafkaMessage msg) throws Exception {
        return getResponse("nested", msg.getPayload());
    }


    @Incoming("kafkacompletechannel")
    @Outgoing("kafkacompletereplychannel")
    @Complete
    public String completeMethod(KafkaMessage msg) throws Exception {
        System.out.println("InventoryMessagingResource.complete");
        participantStatus = ParticipantStatus.Completed;
        return participantStatus.toString(); //todo append lra id
    }

    @Incoming("kafkacompensatechannel")
    @Outgoing("kafkacompensatereplychannel")
    @Compensate
    public String compensateMethod(KafkaMessage msg) throws Exception {
        System.out.println("InventoryMessagingResource.compensate");
        participantStatus = ParticipantStatus.Compensated;
        return participantStatus.toString(); //todo append lra id
    }


    @Incoming("kafkastatuschannel")
    @Outgoing("kafkastatusreplychannel")
    @Status
    public String statusMethod(KafkaMessage msg) throws Exception {
        System.out.println("InventoryMessagingResource.status");
        return participantStatus.toString(); //todo append lra id
    }

    @Incoming("kafkaafterlrachannel")
    @Outgoing("kafkaafterlrareplychannel")
    @AfterLRA
    public String afterLRAMethod(KafkaMessage msg) throws Exception {
        System.out.println("InventoryMessagingResource.afterLRA");
        return participantStatus.toString(); //todo append lra id
    }

    @Incoming("kafkaforgetchannel")
    @Outgoing("kafkaforgetreplychannel")
    @Forget
    public String forgetLRAMethod(KafkaMessage msg) throws Exception {
        System.out.println("InventoryMessagingResource.forget");
        return participantStatus.toString(); //todo append lra id
    }

    @Incoming("kafkaleavechannel")
    @Outgoing("kafkaleavereplychannel")
    @Leave
    public String leaveLRAMethod(KafkaMessage msg) throws Exception {
        System.out.println("InventoryMessagingResource.forget");
        return participantStatus.toString(); //todo append lra id
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