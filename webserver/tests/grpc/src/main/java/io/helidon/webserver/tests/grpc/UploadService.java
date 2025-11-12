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

package io.helidon.webserver.tests.grpc;

import io.helidon.webserver.grpc.GrpcService;
import io.helidon.webserver.grpc.uploads.Uploads;
import io.helidon.webserver.grpc.uploads.Uploads.Ack;
import io.helidon.webserver.grpc.uploads.Uploads.Data;

import com.google.protobuf.Descriptors;
import io.grpc.stub.StreamObserver;

class UploadService implements GrpcService {

    static final Ack TRUE_ACK = Ack.newBuilder().setOk(true).build();

    @Override
    public Descriptors.FileDescriptor proto() {
        return Uploads.getDescriptor();
    }

    @Override
    public void update(Routing router) {
        router.bidi("Upload", this::grpcUpload);
    }

    private StreamObserver<Data> grpcUpload(StreamObserver<Ack> result) {
        return new StreamObserver<>() {
            @Override
            public void onNext(Data data) {
                result.onNext(TRUE_ACK);
            }

            @Override
            public void onError(Throwable t) {
                result.onError(t);
            }

            @Override
            public void onCompleted() {
                result.onCompleted();
            }
        };
    }
}
