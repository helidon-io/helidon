/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates.
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

package io.helidon.grpc.examples.security.abac;


import io.helidon.grpc.examples.common.StringServiceGrpc;
import io.helidon.grpc.examples.common.Strings;

import io.grpc.Channel;
import io.grpc.ManagedChannelBuilder;

/**
 * A {@link io.helidon.grpc.examples.common.StringService} client that optionally
 * provides {@link io.grpc.CallCredentials} using basic auth.
 */
public class SecureStringClient {

    private SecureStringClient() {
    }

    /**
     * Program entry point.
     *
     * @param args  program arguments
     */
    public static void main(String[] args) {
        Channel channel = ManagedChannelBuilder.forAddress("localhost", 1408)
                .usePlaintext()
                .build();

        StringServiceGrpc.StringServiceBlockingStub stub = StringServiceGrpc.newBlockingStub(channel);

        String text = "abcde";
        Strings.StringMessage request = Strings.StringMessage.newBuilder().setText(text).build();
        Strings.StringMessage response = stub.upper(request);

        System.out.println("Text '" + text + "' to upper is '" + response.getText() + "'");
    }
}
