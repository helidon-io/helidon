/*
 * Copyright (c) 2024, 2025 Oracle and/or its affiliates.
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
package io.helidon.docs.se.grpc;

import io.helidon.config.Config;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.grpc.GrpcRouting;
import io.helidon.webserver.grpc.GrpcService;
import io.helidon.grpc.core.InterceptorWeights;

import com.google.protobuf.Descriptors;
import io.grpc.stub.StreamObserver;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ServerCall;
import io.grpc.Metadata;

import static io.helidon.grpc.core.ResponseHelper.complete;

@SuppressWarnings("ALL")
class ServerSnippets {

    // stub
    static class GreetService implements GrpcService {

        GreetService(Config config) {
        }

        @Override
        public Descriptors.FileDescriptor proto() {
            return null;
        }

        @Override
        public void update(Routing routing) {
        }
    }

    // stub
    static class EchoService implements GrpcService {

        @Override
        public Descriptors.FileDescriptor proto() {
            return null;
        }

        @Override
        public void update(Routing routing) {
        }
    }

    // stub
    static class MathService implements GrpcService {

        @Override
        public Descriptors.FileDescriptor proto() {
            return null;
        }

        @Override
        public void update(Routing routing) {
        }
    }

    // stub
    static class Strings {

        static Descriptors.FileDescriptor getDescriptor() {
            return null;
        }

        static class StringMessage {
        }
    }

    // stub
    static class Main {
        static void grpcUpper(Strings.StringMessage msg, StreamObserver<Strings.StringMessage> stream) {
        }
    }

    // tag::snippet_1[]
    private static GrpcRouting.Builder createRouting(Config config) {
        return GrpcRouting.builder()
                .service(new GreetService(config)) // <1>
                .service(new EchoService())        // <2>
                .service(new MathService())        // <3>
                .unary(Strings.getDescriptor(),    // <4>
                       "StringService",
                       "Upper",
                       Main::grpcUpper);
    }
    // end::snippet_1[]

    static class Echo {
        static Descriptors.FileDescriptor getDescriptor() {
            return null;
        }

        static class EchoRequest {
            String getMessage() {
                return null;
            }
        }

        static class EchoResponse {

            static EchoResponseBuilder newBuilder() {
                return new EchoResponseBuilder();
            }
        }

        static class EchoResponseBuilder {

            EchoResponseBuilder setMessage(String msg) {
                return this;
            }

            EchoResponse build() {
                return new EchoResponse();
            }
        }
    }

    class Snippet2 {

        // tag::snippet_2[]
        class EchoService implements GrpcService {
            @Override
            public Descriptors.FileDescriptor proto() {
                return Echo.getDescriptor(); // <1>
            }

            @Override
            public void update(Routing routing) {
                routing.unary("Echo", this::echo); // <2>
            }

            /**
             * Echo the message back to the caller.
             *
             * @param request  the echo request containing the message to echo
             * @param observer the response observer
             */
            public void echo(Echo.EchoRequest request, StreamObserver<Echo.EchoResponse> observer) {  // <3>
                String message = request.getMessage();  // <4>
                Echo.EchoResponse response = Echo.EchoResponse.newBuilder().setMessage(message).build();  // <5>
                complete(observer, response);  // <6>
            }
        }
        // end::snippet_2[]
    }

    void snippet_3() {
        // tag::snippet_3[]
        WebServer.builder()
                .port(8080)
                .routing(httpRouting -> httpRouting.get("/greet", (req, res) -> res.send("Hi!"))) // <1>
                .addRouting(GrpcRouting.builder()  // <2>
                                    .unary(Strings.getDescriptor(),
                                           "StringService",
                                           "Upper",
                                           Main::grpcUpper))
                .build()
                .start();
        // end::snippet_3[]
    }

    static class Interceptor1 implements ServerInterceptor {
        @Override
        public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
                                                                     Metadata headers,
                                                                     ServerCallHandler<ReqT, RespT> next) {
            return next.startCall(call, headers);
        }
    }

    static class Interceptor2 implements ServerInterceptor {
        @Override
        public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
                                                                     Metadata headers,
                                                                     ServerCallHandler<ReqT, RespT> next) {
            return next.startCall(call, headers);
        }
    }

    private static void snippet4(Config config) {
        // tag::snippet_4[]
        GrpcRouting.builder()
                .service(new GreetService(config))
                .intercept(new Interceptor1())
                .service(new EchoService())
                .intercept(InterceptorWeights.USER + 100, new Interceptor2())
                .service(new MathService())
                .build();
        // end::snippet_4[]
    }
}
