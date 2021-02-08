/*
 * Copyright (c) 2021 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microprofile.cloud.googlecloudfunctions.background;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;

import io.helidon.microprofile.cloud.common.CloudFunction;

import com.google.cloud.functions.BackgroundFunction;
import com.google.cloud.functions.Context;

import org.glassfish.jersey.logging.LoggingFeature;
import org.junit.jupiter.api.Test;

@CloudFunction
@ApplicationScoped
public class SampleTest implements BackgroundFunction<Map<String, List<String>>> {

    private static final int SOCKET_PORT = 18888;
    
    @Inject
    private MyService myService;

    @Override
    public void accept(Map<String, List<String>> event, Context context) throws Exception {
        // Let know the client that the event was received
        try (Socket client = new Socket()) {
            client.connect(new InetSocketAddress("localhost", SOCKET_PORT));
            ObjectOutputStream oos = new ObjectOutputStream(client.getOutputStream());
            oos.writeObject(myService.first(event));
            oos.flush();
        }
    }

    @Test
    public void example() throws IOException, InterruptedException {
        EventConfirmation confirmation = new EventConfirmation();
        ExecutorService service = Executors.newSingleThreadExecutor();
        service.submit(confirmation);
        try (LocalServerTestSupport.ServerProcess process = LocalServerTestSupport
                .startServer(GoogleCloudBackgroundFunction.class, "event")) {
            Map<String, Map<String, List<String>>> event = new HashMap<>();
            Map<String, List<String>> data = new HashMap<>();
            data.put("0", Arrays.asList("test 0"));
            data.put("1", Arrays.asList("test 1"));
            event.put("data", data);
            Response response = ClientBuilder.newClient().register(LoggingFeature.class)
                    .target("http://localhost:8080/").request().post(Entity.json(event));
            assertEquals(200, response.getStatus());
        }
        assertEquals("test 0", confirmation.result);
    }

    @ApplicationScoped
    public static class MyService {

        public String first(Map<String, List<String>> event) {
            return event.get("0").get(0);
        }

    }

    private static class EventConfirmation implements Runnable {

        private volatile String result;
        
        @Override
        public void run() {
            try(ServerSocket server = new ServerSocket(SOCKET_PORT)) {
                try (Socket socket = server.accept() ){
                    ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());
                    String message = (String) ois.readObject();
                    result = message;
                }
            } catch (IOException | ClassNotFoundException e) {
                result = e.getMessage();
            }
        }
        
    }

}
