/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

package services;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.Response;

import io.helidon.grpc.server.GrpcService;
import io.helidon.grpc.server.ServiceDescriptor;
import io.helidon.grpc.server.test.Echo;
import io.helidon.security.SecurityContext;
import io.helidon.security.integration.grpc.GrpcSecurity;
import io.helidon.security.integration.jersey.client.ClientSecurity;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;

import static io.helidon.grpc.core.ResponseHelper.complete;

/**
 * A simple test gRPC echo service that
 * makes a secure outbound http request.
 */
public class SecuredOutboundEchoService
        implements GrpcService {

    private final Client client;

    private final String url;

    public SecuredOutboundEchoService(String url) {
        this.url = url;
        this.client = ClientBuilder.newClient();
    }

    @Override
    public void update(ServiceDescriptor.Rules rules) {
        rules.name("EchoService")
                .proto(Echo.getDescriptor())
                .unary("Echo", this::echo);
    }

    /**
     * Make a web request passing this method's message parameter and send
     * the web response back to the caller .
     *
     * @param request   the echo request containing the message to echo
     * @param observer  the call response
     */
    public void echo(Echo.EchoRequest request, StreamObserver<Echo.EchoResponse> observer) {
        try {
            SecurityContext securityContext = GrpcSecurity.SECURITY_CONTEXT.get();
            String message = request.getMessage();

            Response webResponse = client.target(url)
                    .path("/test")
                    .queryParam("message", message)
                    .request()
                    .property(ClientSecurity.PROPERTY_CONTEXT, securityContext)
                    .get();

            if (webResponse.getStatus() == 200) {
                String value = webResponse.readEntity(String.class);

                Echo.EchoResponse echoResponse = Echo.EchoResponse.newBuilder().setMessage(value).build();
                complete(observer, echoResponse);
            } else if (webResponse.getStatus() == Response.Status.FORBIDDEN.getStatusCode()
                    || webResponse.getStatus() == Response.Status.UNAUTHORIZED.getStatusCode()) {

                observer.onError(Status.PERMISSION_DENIED.asException());
            } else {
                observer.onError(Status.UNKNOWN.withDescription("Received http response " + webResponse).asException());
            }
        } catch (Throwable thrown) {
            observer.onError(Status.UNKNOWN.withCause(thrown).asException());
        }
    }
}
