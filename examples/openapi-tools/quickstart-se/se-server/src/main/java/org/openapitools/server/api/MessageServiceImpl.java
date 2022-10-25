package org.openapitools.server.api;

import io.helidon.common.http.Http;
import io.helidon.webserver.Handler;
import org.openapitools.server.model.Message;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;

public class MessageServiceImpl implements MessageService {

    private final AtomicReference<Message> defaultMessage = new AtomicReference<>();

    private static final int HTTP_CODE_NOT_IMPLEMENTED = 501;
    private static final Logger LOGGER = Logger.getLogger(MessageService.class.getName());
    private static final ObjectMapper MAPPER = JsonProvider.objectMapper();

    public MessageServiceImpl() {
        Message message = new Message();
        message.setMessage("World");
        message.setGreeting("Hello");
        defaultMessage.set(message);
    }

    public void getDefaultMessage(ServerRequest request, ServerResponse response) {
        response.send(defaultMessage.get());
    }

    public void getMessage(ServerRequest request, ServerResponse response) {
        String name = request.path().param("name");
        defaultMessage.get().setMessage(name);
        response.send(defaultMessage.get());
    }

    public void updateGreeting(ServerRequest request, ServerResponse response, Message message) {
        if (message.getGreeting() == null) {
            Message jsonError = new Message();
            jsonError.setMessage("No greeting provided");
            response.status(Http.Status.BAD_REQUEST_400)
                    .send(jsonError);
            return;
        }
        defaultMessage.set(message);
        response.status(Http.Status.NO_CONTENT_204).send();
    }

}
