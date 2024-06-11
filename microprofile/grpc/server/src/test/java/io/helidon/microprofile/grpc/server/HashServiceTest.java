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

package io.helidon.microprofile.grpc.server;

import io.helidon.microprofile.grpc.server.test.Hash;
import io.helidon.microprofile.grpc.server.test.HashServiceGrpc;
import io.helidon.webserver.grpc.GrpcService;

import com.google.protobuf.Descriptors;
import io.grpc.stub.StreamObserver;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.client.WebTarget;
import org.junit.jupiter.api.Test;

import static io.helidon.grpc.core.ResponseHelper.complete;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.is;

class HashServiceTest extends BaseServiceTest {

    @Inject
    public HashServiceTest(WebTarget webTarget) {
        super(webTarget);
    }

    @Test
    void testHash() {
        HashServiceGrpc.HashServiceBlockingStub service = HashServiceGrpc.newBlockingStub(grpcClient().channel());
        Hash.Value res = service.hash(Hash.Message.newBuilder().setText("hello world").build());
        assertThat(res.getText(), is(String.valueOf("hello world".hashCode())));
    }

    @ApplicationScoped
    public static class HashService implements GrpcService {
        @Override
        public Descriptors.FileDescriptor proto() {
            return Hash.getDescriptor();
        }

        @Override
        public void update(Routing routing) {
            routing.unary("Hash", this::hash);
        }

        public void hash(Hash.Message request, StreamObserver<Hash.Value> observer) {
            complete(observer, Hash.Value.newBuilder().setText(
                    String.valueOf(request.getText().hashCode())).build());
        }
    }
}
