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

import static reactor.core.publisher.SignalType.CANCEL;
import static reactor.core.publisher.SignalType.ON_COMPLETE;
import static reactor.core.publisher.SignalType.ON_ERROR;

import io.rsocket.Payload;
import io.rsocket.RSocket;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.Tag;
import org.eclipse.microprofile.metrics.Timer;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

/**
 * A proxy class to collect metrics from RSocket usage.
 */
final class MetricsRSocket implements RSocket {

    private final RSocket delegate;

    private final InteractionCounters metadataPush;

    private final InteractionCounters requestChannel;

    private final InteractionCounters requestFireAndForget;

    private final InteractionTimers requestResponse;

    private final InteractionCounters requestStream;


    MetricsRSocket(RSocket delegate, MetricRegistry metricRegistry, Tag... tags) {
        this.delegate = Objects.requireNonNull(delegate, "Delegate must not be null");
        Objects.requireNonNull(metricRegistry, "MetricRegistry must not be null");

        this.metadataPush = new InteractionCounters(metricRegistry, "MetadataPush", tags);
        this.requestChannel = new InteractionCounters(metricRegistry, "RequestChannel", tags);
        this.requestFireAndForget = new InteractionCounters(metricRegistry, "RequestFnF", tags);
        this.requestResponse = new InteractionTimers(metricRegistry, "RequestResponse", tags);
        this.requestStream = new InteractionCounters(metricRegistry, "RequestStream", tags);
    }

    @Override
    public void dispose() {
        delegate.dispose();
    }

    @Override
    public Mono<Void> fireAndForget(Payload payload) {
        return delegate.fireAndForget(payload).doFinally(requestFireAndForget);
    }

    @Override
    public Mono<Void> metadataPush(Payload payload) {
        return delegate.metadataPush(payload).doFinally(metadataPush);
    }

    @Override
    public Mono<Void> onClose() {
        return delegate.onClose();
    }

    @Override
    public Flux<Payload> requestChannel(Publisher<Payload> payloads) {
        return delegate.requestChannel(payloads).doFinally(requestChannel);
    }

    @Override
    public Mono<Payload> requestResponse(Payload payload) {
        return Mono.defer(
                () -> {
                    Timer sample = requestResponse.start();

                    return delegate
                            .requestResponse(payload)
                            .doFinally(signalType -> requestResponse.accept(sample, signalType));
                });
    }

    @Override
    public Flux<Payload> requestStream(Payload payload) {
        return delegate.requestStream(payload).doFinally(requestStream);
    }

    private static final class InteractionCounters implements Consumer<SignalType> {

        private final Counter cancel;

        private final Counter onComplete;

        private final Counter onError;

        private InteractionCounters(MetricRegistry metricRegistry, String interactionModel, Tag... tags) {
            this.cancel = counter(metricRegistry, interactionModel, CANCEL, tags);
            this.onComplete = counter(metricRegistry, interactionModel, ON_COMPLETE, tags);
            this.onError = counter(metricRegistry, interactionModel, ON_ERROR, tags);
        }

        @Override
        public void accept(SignalType signalType) {
            switch (signalType) {
                case CANCEL:
                    cancel.inc();
                    break;
                case ON_COMPLETE:
                    onComplete.inc();
                    break;
                case ON_ERROR:
                    onError.inc();
                    break;
            }
        }

        private static Counter counter(
                MetricRegistry meterRegistry, String interactionModel, SignalType signalType, Tag... tags) {

            Tag[] resultTags = Stream.concat(Arrays.stream(tags), Stream.of(new Tag("signalType", signalType.name())))
                    .toArray(Tag[]::new);
            return meterRegistry.counter(
                    "rsocket." + interactionModel, resultTags);
        }
    }

    private static final class InteractionTimers implements BiConsumer<Timer, SignalType> {

        private final Timer cancel;

        private final MetricRegistry metricRegistry;

        private final Timer onComplete;

        private final Timer onError;

        private InteractionTimers(MetricRegistry metricRegistry, String interactionModel, Tag... tags) {
            this.metricRegistry = metricRegistry;

            this.cancel = timer(metricRegistry, interactionModel, CANCEL, tags);
            this.onComplete = timer(metricRegistry, interactionModel, ON_COMPLETE, tags);
            this.onError = timer(metricRegistry, interactionModel, ON_ERROR, tags);
        }

        @Override
        public void accept(Timer sample, SignalType signalType) {
            sample.time().stop();

        }

        Timer start() {
            return metricRegistry.timer("RequestTimer");
        }

        private static Timer timer(
                MetricRegistry metricRegistry, String interactionModel, SignalType signalType, Tag... tags) {

            Tag[] resultTags = Stream.concat(Arrays.stream(tags), Stream.of(new Tag("signalType", signalType.name())))
                    .toArray(Tag[]::new);
            return metricRegistry.timer(
                    "rsocket." + interactionModel, resultTags);
        }
    }
}
