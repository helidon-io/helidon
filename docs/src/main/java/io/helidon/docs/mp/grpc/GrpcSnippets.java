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
package io.helidon.docs.mp.grpc;

import java.lang.String;
import java.io.InputStream;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Named;

import io.grpc.Channel;
import io.grpc.stub.StreamObserver;
import io.grpc.MethodDescriptor;

import io.helidon.grpc.api.Grpc;
import io.helidon.grpc.core.MarshallerSupplier;
import io.helidon.webserver.grpc.GrpcService;
import io.helidon.webserver.grpc.GrpcService.Routing;
import io.helidon.microprofile.grpc.server.spi.GrpcMpExtension;
import io.helidon.microprofile.grpc.server.spi.GrpcMpContext;

import com.google.protobuf.Descriptors;

@SuppressWarnings("ALL")
class GrpcSnippets {

    class Snippet1 {

        // tag::snippet_1[]
        @ApplicationScoped
        @Grpc.GrpcService
        public class StringService {

            @Grpc.Unary("Upper")
            public Strings.StringMessage upper(Strings.StringMessage request) {
                String text = request.getText().toUpperCase();
                return Strings.StringMessage.newBuilder().setText(text).build();
            }
        }
        // end::snippet_1[]


    }

    class Snnippet1_5 {

        // tag::snippet_1_5[]
        @ApplicationScoped
        @Grpc.GrpcService
        public class StringService {

            @Grpc.Proto
            public Descriptors.FileDescriptor proto() {
                return Strings.getDescriptor();
            }

            @Grpc.Unary("Upper")
            public Strings.StringMessage upper(Strings.StringMessage request) {
                String text = request.getText().toUpperCase();
                return Strings.StringMessage.newBuilder().setText(text).build();
            }
        }
        // end::snippet_1_5[]
    }

    class Snippet2 {

        // tag::snippet_2[]
        @ApplicationScoped
        @Grpc.GrpcService
        @Grpc.GrpcMarshaller("string")
        public class StringService {

            @Grpc.Unary("Upper")
            public String upper(String request) {
                return request.toLowerCase();
            }
        }
        // end::snippet_2[]
    }

    class Snippet3 {

        // tag::snippet_3[]
        public class StringMarshaller<String>
                implements MethodDescriptor.Marshaller<String> {

            @Override
            public InputStream stream(String obj) {
                InputStream stream = null;
                // convert to stream
                return stream;
            }

            @Override
            @SuppressWarnings("unchecked")
            public String parse(InputStream in) {
                String string = null;
                // parse from stream
                return string;
            }

        }
        // end::snippet_3[]

        // tag::snippet_3_5[]
        @Dependent
        @Named("string")
        public class StringSupplier implements MarshallerSupplier {
            @Override
            public <T> MethodDescriptor.Marshaller<T> get(Class<T> clazz) {
                return new StringMarshaller<>();
            }
        }

        // end::snippet_3_5[]
    }

    class Snippet4 {

        class StringService implements GrpcService {

            public Descriptors.FileDescriptor proto() {
                return null;
            }

            public void update(Routing routing) {
            }
        }

        // tag::snippet_4[]
        public class MyExtension implements GrpcMpExtension {

            @Override
            public void configure(GrpcMpContext context) {           // <1>
                context.routing().service(new StringService());      // <2>
            }
        }
        // end::snippet_4[]
    }

    class Snippet5 {

        // tag::snippet_5[]
        @ApplicationScoped
        @Grpc.GrpcService("StringService")  // <1>
        @Grpc.GrpcChannel("string-channel")  // <2>
        interface StringServiceClient {

            @Grpc.Unary
            String upper(String s);
        }
        // end::snippet_5[]
    }

    class Snippet6 {

        // tag::snippet_6[]
        @ApplicationScoped
        @Grpc.GrpcService("StringService")
        @Grpc.GrpcChannel("string-channel")
        interface StringServiceClient {

            @Grpc.Unary
            void upper(String s, StreamObserver<String> response);
        }
        // end::snippet_6[]
    }

    class Snippet7 {

        interface StringServiceClient {
        }

        // tag::snippet_7[]
        @ApplicationScoped
        public class MyAppBean {

            @Inject  // <1>
            @Grpc.GrpcProxy  // <2>
            private StringServiceClient stringServiceClient;
        }
        // end::snippet_7[]
    }

    class Snippet8 {

        // tag::snippet_8[]
        @Inject  // <1>
        @Grpc.GrpcChannel("string-channel")  // <2>
        private Channel channel;
        // end::snippet_8[]
    }
}
