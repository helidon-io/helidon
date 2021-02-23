/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.grpc.examples.client.standalone;

import io.helidon.grpc.examples.common.StringServiceGrpc;
import io.helidon.grpc.examples.common.Strings;

import io.grpc.Channel;
import io.grpc.ManagedChannelBuilder;

/**
 * A gRPC client using only client side libraries.
 * To test it, please setup a server, such as the one in "basics" example of Helidon grpc examples.
 */
public class StandaloneClient {
    private StandaloneClient() {
    }

    /**
     * Start the client with a single invocation to the server.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        Channel channel = ManagedChannelBuilder.forAddress("localhost", 1408)
                .usePlaintext()
                .build();

        StringServiceGrpc.StringServiceBlockingStub stub = StringServiceGrpc.newBlockingStub(channel);

        String text = "lower case original";
        Strings.StringMessage request = Strings.StringMessage.newBuilder().setText(text).build();
        Strings.StringMessage response = stub.upper(request);

        System.out.println("Text '" + text + "' to upper case is '" + response.getText() + "'");
    }
}
