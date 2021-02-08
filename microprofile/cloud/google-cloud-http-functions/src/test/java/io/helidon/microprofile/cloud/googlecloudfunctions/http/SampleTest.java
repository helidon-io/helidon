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

package io.helidon.microprofile.cloud.googlecloudfunctions.http;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;

import java.io.IOException;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;

import io.helidon.microprofile.cloud.common.CloudFunction;

import org.junit.jupiter.api.Test;

@CloudFunction
@ApplicationScoped
public class SampleTest implements HttpFunction {

    @Inject
    private MyService myService;

    @Override
    public void service(HttpRequest request, HttpResponse response) throws Exception {
        String value = request.getReader().readLine();
        response.getWriter().write(myService.toUpperCase(value));
    }

    @Test
    public void example() throws IOException, InterruptedException {
        try (LocalServerTestSupport.ServerProcess process = LocalServerTestSupport.startServer(GoogleCloudHttpFunction.class, "http")) {
            Response response = ClientBuilder.newClient().target("http://localhost:8080/").request().post(Entity.json("test"));
            assertEquals("TEST", response.readEntity(String.class));
            assertEquals(200, response.getStatus());
            response = ClientBuilder.newClient().target("http://localhost:8080/").request().post(Entity.json("test2"));
            assertEquals("TEST2", response.readEntity(String.class));
            assertEquals(200, response.getStatus());
        }
    }

    @ApplicationScoped
    public static class MyService {

        public String toUpperCase(String str) {
            return str.toUpperCase();
        }

    }

}
