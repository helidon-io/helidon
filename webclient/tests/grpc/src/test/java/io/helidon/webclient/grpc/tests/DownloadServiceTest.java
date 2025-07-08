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

import java.util.Arrays;

import io.helidon.common.tls.TlsConfig;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.grpc.GrpcClient;
import io.helidon.webserver.Router;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.grpc.GrpcRouting;
import io.helidon.webserver.grpc.GrpcService;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Empty;
import io.grpc.stub.StreamObserver;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class DownloadServiceTest {

    private final WebClient webClient;

    DownloadServiceTest(WebServer server) {
        this.webClient = WebClient.builder()
                .baseUri("http://localhost:" + server.port())
                .tls(TlsConfig.builder().enabled(false).build())
                .build();
    }

    @SetUpRoute
    static void routing(Router.RouterBuilder<?> router) {
        router.addRouting(GrpcRouting.builder().service(new DownloadService()));
    }

    @Test
    void testDownload() {
        GrpcClient grpcClient = webClient.client(GrpcClient.PROTOCOL);
        DownloadServiceGrpc.DownloadServiceBlockingStub stub = DownloadServiceGrpc.newBlockingStub(grpcClient.channel());
        Downloads.DownloadResponse res = stub.download(Empty.getDefaultInstance());
        MatcherAssert.assertThat(res.getDataCount(), is(1));
        ByteString byteString = res.getData(0);
        assertThat(byteString.size(), is(40 * 1024));
    }

    static class DownloadService implements GrpcService {

        private static final byte[] DATA = new byte[40 * 1024];

        static {
            Arrays.fill(DATA, (byte) 'A');
        }

        @Override
        public Descriptors.FileDescriptor proto() {
            return Downloads.getDescriptor();
        }

        @Override
        public void update(Routing router) {
            router.unary("Download", this::download);
        }

        private void download(Empty request, StreamObserver<Downloads.DownloadResponse> observer) {
            Downloads.DownloadResponse.Builder builder = Downloads.DownloadResponse.newBuilder();
            builder.addData(ByteString.copyFrom(DATA));
            observer.onNext(builder.build());
            observer.onCompleted();
        }
    }
}
