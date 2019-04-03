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

package io.helidon.grpc.examples.security.outbound;

import java.util.Base64;
import java.util.concurrent.Executor;

import io.helidon.grpc.core.ContextKeys;

import io.grpc.CallCredentials2;
import io.grpc.Metadata;

/**
 * A {@link io.grpc.CallCredentials2} that add a basic auth
 * authorization header to a request.
 *
 * @author Jonathan Knight
 */
public class BasicAuthCallCredentials
        extends CallCredentials2 {
    /**
     * The basic auth encoded user name and password.
     */
    private String basicAuth;

    /**
     * Create a {@link BasicAuthCallCredentials}.
     *
     * @param args the values to use for the basic auth header
     */
    public BasicAuthCallCredentials(String... args) {
        basicAuth = createAuth(args);
    }

    @Override
    public void applyRequestMetadata(RequestInfo requestInfo, Executor executor, MetadataApplier applier) {
        Metadata metadata = new Metadata();

        metadata.put(ContextKeys.AUTHORIZATION, "Basic " + basicAuth);

        applier.apply(metadata);
    }

    @Override
    public void thisUsesUnstableApi() {
    }

    /**
     * Create a basic auth header value from a username password pair.
     *
     * @param args the String array containing the username and password
     * @return the basic auth header value
     */
    private static String createAuth(String[] args) {
        if (args == null || args.length == 0) {
            return null;
        }

        String user = args[0];
        String pass = args.length > 1 ? args[1] : "";
        String basic = user + ":" + pass;

        return Base64.getEncoder().encodeToString(basic.getBytes());
    }
}
