package io.helidon.declarative.tests.websocket;

import io.helidon.http.Http;
import io.helidon.webclient.websocket.WebSocketClient;
import io.helidon.websocket.WebSocket;
import io.helidon.websocket.WsSession;

@SuppressWarnings("deprecation")
@WebSocketClient.Endpoint("${echo-endpoint.client.uri:http://localhost:8080/{user}}")
interface ClientEchoEndpoint {
    @WebSocket.OnOpen
    WsSession open(@Http.PathParam("user") String user);

    @WebSocket.OnClose
    void close(String reason, int closeCode);

    @WebSocket.OnClose
    void close();


}
