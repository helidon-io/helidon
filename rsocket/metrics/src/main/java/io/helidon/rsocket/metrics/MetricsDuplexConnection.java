/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.rsocket.metrics;

import static io.rsocket.frame.FrameType.*;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.rsocket.DuplexConnection;
import io.rsocket.RSocketErrorException;
import io.rsocket.frame.FrameHeaderCodec;
import io.rsocket.frame.FrameType;
import io.rsocket.plugins.DuplexConnectionInterceptor.Type;

import java.net.SocketAddress;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.Tag;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Proxy Duplex Connection to collect metrics.
 */
final class MetricsDuplexConnection implements DuplexConnection {

    private final Counter close;

    private final DuplexConnection delegate;

    private final Counter dispose;

    private final FrameCounters frameCounters;

    MetricsDuplexConnection(
            Type connectionType, DuplexConnection delegate, MetricRegistry metricsRegistry, Tag... tags) {

        Objects.requireNonNull(connectionType, "connectionType must not be null");
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        Objects.requireNonNull(metricsRegistry, "metricsRegistry must not be null");

        Tag[] closeTags = Stream.concat(Arrays.stream(tags),Stream.of(new Tag("connectionType", connectionType.name()))).toArray(Tag[]::new);
        this.close =
                metricsRegistry.counter(
                        "rsocketDuplexConnectionClose",
                        closeTags);
        this.dispose =
                metricsRegistry.counter(
                        "rsocketDuplexConnectionDispose",
                        closeTags);
        this.frameCounters = new FrameCounters(connectionType, metricsRegistry, tags);
    }

    @Override
    public ByteBufAllocator alloc() {
        return delegate.alloc();
    }

    @Override
    public SocketAddress remoteAddress() {
        return delegate.remoteAddress();
    }

    @Override
    public void dispose() {
        delegate.dispose();
        dispose.inc();
    }

    @Override
    public Mono<Void> onClose() {
        return delegate.onClose().doAfterTerminate(close::inc);
    }

    @Override
    public Flux<ByteBuf> receive() {
        return delegate.receive().doOnNext(frameCounters);
    }

    @Override
    public void sendFrame(int streamId, ByteBuf frame) {
        frameCounters.accept(frame);
        delegate.sendFrame(streamId, frame);
    }

    @Override
    public void sendErrorAndClose(RSocketErrorException e) {
        delegate.sendErrorAndClose(e);
    }

    private static final class FrameCounters implements Consumer<ByteBuf> {

        private final Counter cancel;

        private final Counter complete;

        private final Counter error;

        private final Counter extension;

        private final Counter keepalive;

        private final Counter lease;

        private final Counter metadataPush;

        private final Counter next;

        private final Counter nextComplete;

        private final Counter payload;

        private final Counter requestChannel;

        private final Counter requestFireAndForget;

        private final Counter requestN;

        private final Counter requestResponse;

        private final Counter requestStream;

        private final Counter resume;

        private final Counter resumeOk;

        private final Counter setup;

        private final Counter unknown;

        private FrameCounters(Type connectionType, MetricRegistry metricRegistry, Tag... tags) {
            this.cancel = counter(connectionType, metricRegistry, CANCEL, tags);
            this.complete = counter(connectionType, metricRegistry, COMPLETE, tags);
            this.error = counter(connectionType, metricRegistry, ERROR, tags);
            this.extension = counter(connectionType, metricRegistry, EXT, tags);
            this.keepalive = counter(connectionType, metricRegistry, KEEPALIVE, tags);
            this.lease = counter(connectionType, metricRegistry, LEASE, tags);
            this.metadataPush = counter(connectionType, metricRegistry, METADATA_PUSH, tags);
            this.next = counter(connectionType, metricRegistry, NEXT, tags);
            this.nextComplete = counter(connectionType, metricRegistry, NEXT_COMPLETE, tags);
            this.payload = counter(connectionType, metricRegistry, PAYLOAD, tags);
            this.requestChannel = counter(connectionType, metricRegistry, REQUEST_CHANNEL, tags);
            this.requestFireAndForget = counter(connectionType, metricRegistry, REQUEST_FNF, tags);
            this.requestN = counter(connectionType, metricRegistry, REQUEST_N, tags);
            this.requestResponse = counter(connectionType, metricRegistry, REQUEST_RESPONSE, tags);
            this.requestStream = counter(connectionType, metricRegistry, REQUEST_STREAM, tags);
            this.resume = counter(connectionType, metricRegistry, RESUME, tags);
            this.resumeOk = counter(connectionType, metricRegistry, RESUME_OK, tags);
            this.setup = counter(connectionType, metricRegistry, SETUP, tags);
            this.unknown = counter(connectionType, metricRegistry, "UNKNOWN", tags);
        }

        private static Counter counter(
                Type connectionType, MetricRegistry metricRegistry, FrameType frameType, Tag... tags) {

            return counter(connectionType, metricRegistry, frameType.name(), tags);
        }

        private static Counter counter(
                Type connectionType, MetricRegistry metricRegistry, String frameType, Tag... tags) {


            Tag[] allTags = Stream.concat(Arrays.stream(tags), Stream.of(new Tag("connectionType", connectionType.name()), new Tag("frameType", frameType)))
                    .toArray(Tag[]::new);


            return metricRegistry.counter(
                    "rsocketFrame", allTags);
        }

        @Override
        public void accept(ByteBuf frame) {
            FrameType frameType = FrameHeaderCodec.frameType(frame);

            switch (frameType) {
                case SETUP:
                    this.setup.inc();
                    break;
                case LEASE:
                    this.lease.inc();
                    break;
                case KEEPALIVE:
                    this.keepalive.inc();
                    break;
                case REQUEST_RESPONSE:
                    this.requestResponse.inc();
                    break;
                case REQUEST_FNF:
                    this.requestFireAndForget.inc();
                    break;
                case REQUEST_STREAM:
                    this.requestStream.inc();
                    break;
                case REQUEST_CHANNEL:
                    this.requestChannel.inc();
                    break;
                case REQUEST_N:
                    this.requestN.inc();
                    break;
                case CANCEL:
                    this.cancel.inc();
                    break;
                case PAYLOAD:
                    this.payload.inc();
                    break;
                case ERROR:
                    this.error.inc();
                    break;
                case METADATA_PUSH:
                    this.metadataPush.inc();
                    break;
                case RESUME:
                    this.resume.inc();
                    break;
                case RESUME_OK:
                    this.resumeOk.inc();
                    break;
                case NEXT:
                    this.next.inc();
                    break;
                case COMPLETE:
                    this.complete.inc();
                    break;
                case NEXT_COMPLETE:
                    this.nextComplete.inc();
                    break;
                case EXT:
                    this.extension.inc();
                    break;
                default:
                    this.unknown.inc();
            }
        }
    }
}
