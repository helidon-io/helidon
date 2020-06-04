/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microprofile.grpc.example.client;

import java.util.stream.Stream;

import io.helidon.microprofile.grpc.core.Bidirectional;
import io.helidon.microprofile.grpc.core.ClientStreaming;
import io.helidon.microprofile.grpc.core.Grpc;
import io.helidon.microprofile.grpc.core.ServerStreaming;
import io.helidon.microprofile.grpc.core.Unary;

import io.grpc.stub.StreamObserver;

/**
 * The gRPC StringService.
 * <p>
 * This class has the {@link io.helidon.microprofile.grpc.core.Grpc} annotation
 * so that it will be discovered and loaded using CDI when the MP gRPC server starts.
 */
@Grpc
@SuppressWarnings("CdiManagedBeanInconsistencyInspection")
public interface StringService {

    /**
     * Convert a string value to upper case.
     *
     * @param request  the request containing the string to convert
     * @return the request value converted to upper case
     */
    @Unary
    String upper(String request);

    /**
     * Convert a string value to lower case.
     *
     * @param request  the request containing the string to convert
     * @return  the request converted to lower case
     */
    @Unary
    String lower(String request);

    /**
     * Split a space delimited string value and stream back the split parts.
     * @param request  the request containing the string to split
     * @return  a {@link java.util.stream.Stream} containing the split parts
     */
    @ServerStreaming
    Stream<String> split(String request);

    /**
     * Join a stream of string values and return the result.
     * @param observer  the request containing the string to split
     * @return  a {@link java.util.stream.Stream} containing the split parts
     */
    @ClientStreaming
    StreamObserver<String> join(StreamObserver<String> observer);

    /**
     * Echo each value streamed from the client back to the client.
     * @param observer  the {@link io.grpc.stub.StreamObserver} to send responses to
     * @return  the {@link io.grpc.stub.StreamObserver} to receive requests from
     */
    @Bidirectional
    StreamObserver<String> echo(StreamObserver<String> observer);
}
