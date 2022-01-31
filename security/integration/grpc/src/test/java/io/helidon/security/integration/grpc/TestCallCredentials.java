/*
 * Copyright (c) 2019, 2022 Oracle and/or its affiliates.
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

package io.helidon.security.integration.grpc;

import java.util.Base64;
import java.util.concurrent.Executor;

import io.helidon.grpc.core.ContextKeys;

import io.grpc.CallCredentials;
import io.grpc.Metadata;

/**
 * A {@link io.grpc.CallCredentials} that add a basic auth
 * authorization header to a request.
 */
public class TestCallCredentials
        extends CallCredentials {
    /**
     * The basic auth encoded user name and password.
     */
    private String basicAuth;

    /**
     * Create a {@link TestCallCredentials}.
     *
     * @param user     the user name
     * @param password the password
     */
    public TestCallCredentials(String user, String password) {
        basicAuth = createAuth(user, password);
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

    private static String createAuth(String user, String password) {
        String basic = user + ":" + password;
        return Base64.getEncoder().encodeToString(basic.getBytes());
    }
}
