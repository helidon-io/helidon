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

package io.helidon.grpc.examples.security;


import io.helidon.config.Config;
import io.helidon.grpc.examples.common.StringServiceGrpc;
import io.helidon.grpc.examples.common.Strings;
import io.helidon.security.Security;
import io.helidon.security.integration.grpc.GrpcClientSecurity;
import io.helidon.security.providers.httpauth.HttpBasicAuthProvider;

import io.grpc.CallCredentials;
import io.grpc.Channel;
import io.grpc.ManagedChannelBuilder;

/**
 * A {@link io.helidon.grpc.examples.common.StringService} client that optionally
 * provides {@link CallCredentials} using basic auth.
 */
public class SecureStringClient {

    private SecureStringClient() {
    }

    /**
     * Program entry point.
     *
     * @param args  the program arguments - {@code arg[0]} is the user name
     *              and {@code arg[1] is the password}
     */
    public static void main(String[] args) {
        Channel channel = ManagedChannelBuilder.forAddress("localhost", 1408)
                .usePlaintext()
                .build();

        // Obtain the user name and password from the program arguments
        String user = args.length >= 2 ? args[0] : null;
        String password = args.length >= 2 ? args[1] : null;

        Config config = Config.create();

        // configure Helidon security and add the basic auth provider
        Security security = Security.builder()
                .addProvider(HttpBasicAuthProvider.create(config.get("http-basic-auth")))
                .build();

        // create the gRPC client security call credentials
        // setting the properties used by the basic auth provider for user name and password
        GrpcClientSecurity clientSecurity = GrpcClientSecurity.builder(security.createContext("test.client"))
                .property(HttpBasicAuthProvider.EP_PROPERTY_OUTBOUND_USER, user)
                .property(HttpBasicAuthProvider.EP_PROPERTY_OUTBOUND_PASSWORD, password)
                .build();

        // create the StringService client stub and use the GrpcClientSecurity call credentials
        StringServiceGrpc.StringServiceBlockingStub stub = StringServiceGrpc.newBlockingStub(channel)
                .withCallCredentials(clientSecurity);

        String text = "ABCDE";
        Strings.StringMessage request = Strings.StringMessage.newBuilder().setText(text).build();
        Strings.StringMessage response = stub.lower(request);

        System.out.println("Text '" + text + "' to lower is '" + response.getText() + "'");
    }
}
