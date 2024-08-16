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
package io.helidon.microprofile.grpc.client;

import io.helidon.grpc.api.Grpc;
import io.helidon.microprofile.testing.junit5.HelidonTest;

import io.grpc.Channel;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

@HelidonTest
class ChannelInjectionTest {

    @Inject
    @Grpc.GrpcChannel("echo-channel")
    private Channel echoChannel1;

    private final Channel echoChannel2;

    @Inject
    ChannelInjectionTest(@Grpc.GrpcChannel("echo-channel") Channel echoChannel2) {
        this.echoChannel2 = echoChannel2;
    }

    @Test
    void testInjection() {
        assertThat(echoChannel1, notNullValue());
        assertThat(echoChannel2, notNullValue());
        assertEquals(echoChannel1.getClass(), echoChannel2.getClass());
        assertEquals(echoChannel1.authority(), echoChannel2.authority());
    }
}
