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

import io.grpc.stub.StreamObserver;
import io.helidon.microprofile.grpc.server.test.Random;
import io.helidon.microprofile.grpc.server.test.RandomServiceGrpc;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.client.WebTarget;
import org.junit.jupiter.api.Test;

import static io.helidon.grpc.core.ResponseHelper.complete;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.MatcherAssert.assertThat;

class RandomServiceTest extends BaseServiceTest {

    @Inject
    public RandomServiceTest(WebTarget webTarget) {
        super(webTarget);
    }

    @Test
    void testRandom() {
        RandomServiceGrpc.RandomServiceBlockingStub service = RandomServiceGrpc.newBlockingStub(grpcClient().channel());
        int seed = (int) System.currentTimeMillis();
        Random.IntValue res = service.random(Random.Seed.newBuilder().setN(seed).build());;
        assertThat(res.getN(), is(lessThan(1000)));
    }

    /**
     * A service that implements the {@link io.grpc.BindableService} interface. Should be
     * discovered by {@link GrpcMpCdiExtension}.
     */
    @ApplicationScoped
    public static class RandomService implements io.grpc.BindableService, RandomServiceGrpc.AsyncService {

        @Override
        public io.grpc.ServerServiceDefinition bindService() {
            return RandomServiceGrpc.bindService(this);
        }

        @Override
        public void random(Random.Seed request, StreamObserver<Random.IntValue> observer) {
            int seed = request.getN();
            java.util.Random random = new java.util.Random();
            random.setSeed(seed);
            complete(observer, Random.IntValue.newBuilder().setN(random.nextInt(1000)).build());
        }
    }
}
