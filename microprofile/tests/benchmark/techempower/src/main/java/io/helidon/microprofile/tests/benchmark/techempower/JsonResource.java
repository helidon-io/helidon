package io.helidon.microprofile.tests.benchmark.techempower;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@ApplicationScoped
@Path("/json")
public class JsonResource {
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response json() {
        return Response.ok(newMsg())
                .header("Server", "helidon")
                .build();
    }

    private Message newMsg() {
        return new Message("Hello, World!");
    }

    /**
     * Message to be serialized as JSON.
     */
    public static final class Message {

        private final String message;

        /**
         * Construct a new message.
         *
         * @param message message string
         */
        public Message(String message) {
            super();
            this.message = message;
        }

        /**
         * Get message string.
         *
         * @return message string
         */
        public String getMessage() {
            return message;
        }
    }
}
