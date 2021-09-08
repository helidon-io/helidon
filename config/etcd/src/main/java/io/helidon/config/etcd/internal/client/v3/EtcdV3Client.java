/*
 * Copyright (c) 2017, 2021 Oracle and/or its affiliates.
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

package io.helidon.config.etcd.internal.client.v3;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.config.etcd.internal.client.EtcdClient;
import io.helidon.config.etcd.internal.client.EtcdClientException;
import io.helidon.config.etcd.internal.client.proto.KVGrpc;
import io.helidon.config.etcd.internal.client.proto.PutRequest;
import io.helidon.config.etcd.internal.client.proto.RangeRequest;
import io.helidon.config.etcd.internal.client.proto.RangeResponse;
import io.helidon.config.etcd.internal.client.proto.WatchCreateRequest;
import io.helidon.config.etcd.internal.client.proto.WatchGrpc;
import io.helidon.config.etcd.internal.client.proto.WatchRequest;
import io.helidon.config.etcd.internal.client.proto.WatchResponse;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;

/**
 * Etcd API v3 client.
 */
public class EtcdV3Client implements EtcdClient {

    private static final Logger LOGGER = Logger.getLogger(EtcdV3Client.class.getName());

    private final Map<String, SubmissionPublisher<Long>> publishers = new ConcurrentHashMap<>();

    private final ManagedChannel channel;
    private final KVGrpc.KVBlockingStub kvStub;
    private final WatchGrpc.WatchStub watchStub;

    /**
     * Init client with specified target Etcd uri.
     *
     * @param uris target Etcd uris
     */
    public EtcdV3Client(URI... uris) {
        if (uris.length != 1) {
            throw new IllegalArgumentException("EtcdV3Client only supports a single URI");
        }
        URI uri = uris[0];
        ManagedChannelBuilder mcb = ManagedChannelBuilder.forAddress(uri.getHost(), uri.getPort());
        this.channel = mcb.usePlaintext().build();

        kvStub = KVGrpc.newBlockingStub(channel);
        watchStub = WatchGrpc.newStub(channel);
    }

    @Override
    public Long revision(String key) throws EtcdClientException {
        RangeRequest.Builder builder = RangeRequest.newBuilder().setKey(ByteString.copyFromUtf8(key));
        try {
            RangeResponse rangeResponse = kvStub.range(builder.build());
            return (rangeResponse.getCount() == 0 ? null : rangeResponse.getKvs(0).getModRevision());
        } catch (StatusRuntimeException e) {
            throw new EtcdClientException("Cannot retrieve a value for the key: " + key, e);
        }
    }

    @Override
    public String get(String key) throws EtcdClientException {
        RangeRequest.Builder builder = RangeRequest.newBuilder().setKey(ByteString.copyFromUtf8(key));
        try {
            RangeResponse rangeResponse = kvStub.range(builder.build());
            return (rangeResponse.getCount() == 0 ? null : rangeResponse.getKvs(0).getValue().toStringUtf8());
        } catch (StatusRuntimeException e) {
            throw new EtcdClientException("Cannot retrieve a value for the key: " + key, e);
        }
    }

    @Override
    public void put(String key, String value) throws EtcdClientException {
        PutRequest.Builder builder = PutRequest.newBuilder()
                .setKey(ByteString.copyFromUtf8(key))
                .setValue(ByteString.copyFromUtf8(value));
        kvStub.put(builder.build());
    }

    @Override
    public Flow.Publisher<Long> watch(String key, Executor executor) throws EtcdClientException {
        final SubmissionPublisher<Long> publisher = publishers.computeIfAbsent(
                key,
                (k) -> new SubmissionPublisher<>(executor,
                                                 Flow.defaultBufferSize()));

        StreamObserver<WatchResponse> responseObserver = new StreamObserver<WatchResponse>() {
            @Override
            public void onNext(WatchResponse value) {
                value.getEventsList().forEach(e -> publisher.submit(e.getKv().getVersion()));
            }

            @Override
            public void onError(Throwable t) {
                publisher.closeExceptionally(t);
            }

            @Override
            public void onCompleted() {
            }
        };

        WatchCreateRequest.Builder builder = WatchCreateRequest.newBuilder().setKey(ByteString.copyFromUtf8(key));
        WatchRequest watchRequest = WatchRequest.newBuilder().setCreateRequest(builder).build();

        StreamObserver<WatchRequest> requestObserver = watchStub.watch(responseObserver);
        requestObserver.onNext(watchRequest);

        return publisher;
    }

    @Override
    public Flow.Publisher<Long> watch(String key) throws EtcdClientException {
        return watch(key, Runnable::run //deliver events on current thread
        );
    }

    @Override
    public void close() throws EtcdClientException {
        publishers.values().forEach(SubmissionPublisher::close);
        if (!channel.isShutdown() && !channel.isTerminated()) {
            try {
                channel.awaitTermination(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                LOGGER.log(Level.CONFIG, "Error closing gRPC channel, reason: " + e.getLocalizedMessage(), e);
            } finally {
                channel.shutdown();
            }
        }
    }
}
