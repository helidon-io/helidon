/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.webclient.grpc.tests;

import java.util.Iterator;

import io.helidon.metrics.api.Tag;
import io.helidon.webclient.grpc.GrpcClient;

import org.junit.jupiter.api.RepeatedTest;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

abstract class GrpcBaseMetricsTest extends GrpcBaseTest {

    static final Tag OK_TAG = Tag.create("grpc.status", "OK");
    static final Tag[] METHOD_TAGS = {
            Tag.create("grpc.method", "StringService/Upper"),
            Tag.create("grpc.method", "StringService/Split")
    };
    static final String ATTEMPT_STARTED = "grpc.client.attempt.started";
    static final String ATTEMPT_DURATION = "grpc.client.attempt.duration";
    static final String SENT_MESSAGE_SIZE = "grpc.client.attempt.sent_total_compressed_message_size";
    static final String RCVD_MESSAGE_SIZE = "grpc.client.attempt.rcvd_total_compressed_message_size";

    static GrpcClient grpcClient;

    @RepeatedTest(20)
    void testUnaryUpper() {
        StringServiceGrpc.StringServiceBlockingStub service = StringServiceGrpc.newBlockingStub(grpcClient.channel());
        Strings.StringMessage res = service.upper(newStringMessage("hello"));
        assertThat(res.getText(), is("HELLO"));
    }

    @RepeatedTest(20)
    void testServerStreamingSplit() {
        StringServiceGrpc.StringServiceBlockingStub service = StringServiceGrpc.newBlockingStub(grpcClient.channel());
        Iterator<Strings.StringMessage> res = service.split(newStringMessage("hello world"));
        assertThat(res.next().getText(), is("hello"));
        assertThat(res.next().getText(), is("world"));
        assertThat(res.hasNext(), is(false));
    }
}
