/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

import jakarta.enterprise.context.ApplicationScoped;

import io.helidon.grpc.api.Grpc;
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

            @Grpc.Unary
            public String upper(String s) {
                return s == null ? null : s.toUpperCase();
            }
        }
        // end::snippet_1[]
    }

    class Snippet2 {

        // tag::snippet_2[]
        @ApplicationScoped                             // <1>
        @Grpc.GrpcService                              // <2>
        public class StringService {
            /* code is omitted */
        }
        // end::snippet_2[]
    }

    class Snippet3 {

        // tag::snippet_3[]
        @ApplicationScoped
        @Grpc.GrpcService("Strings")                    // <1>
        public class StringService {
            /* code is omitted */
        }
        // end::snippet_3[]
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
            public void configure(GrpcMpContext context) {       // <1>
                    context.routing()
                            .service(new StringService());      // <2>
            }
        }
        // end::snippet_4[]
    }
}
