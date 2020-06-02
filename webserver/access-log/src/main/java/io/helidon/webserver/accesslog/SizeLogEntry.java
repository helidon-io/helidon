/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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
package io.helidon.webserver.accesslog;

import java.util.concurrent.Flow;
import java.util.concurrent.atomic.LongAdder;

import io.helidon.common.context.Context;
import io.helidon.common.http.DataChunk;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;

/**
 * Access log entry for entity size.
 */
public final class SizeLogEntry extends AbstractLogEntry {
    private static final String SIZE_CONTEXT_CLASSIFIER = SizeLogEntry.class.getName() + ".size";

    private SizeLogEntry(Builder builder) {
        super(builder);
    }

    /**
     * Create a new size log entry instance.
     * @return a new access log entry for entity size
     * @see io.helidon.webserver.accesslog.AccessLogSupport.Builder#add(AccessLogEntry)
     */
    public static SizeLogEntry create() {
        return builder().build();
    }

    /**
     * Create a new fluent API builder.
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder().defaults();
    }

    @Override
    public void accept(ServerRequest req, ServerResponse res) {
        Context context = req.context();
        res.registerFilter(originalPublisher -> new ByteCountingPublisher(originalPublisher, context));
    }

    @Override
    public String doApply(AccessLogContext context) {
        return context.serverRequest()
                .context()
                .get(SIZE_CONTEXT_CLASSIFIER, Long.class)
                .map(String::valueOf)
                .orElse(NOT_AVAILABLE);
    }

    /**
     * A fluent API builder for {@link io.helidon.webserver.accesslog.SizeLogEntry}.
     */
    public static final class Builder extends AbstractLogEntry.Builder<SizeLogEntry, Builder> {
        private Builder() {
        }

        @Override
        public SizeLogEntry build() {
            return new SizeLogEntry(this);
        }

        private Builder defaults() {
            return super.sanitize(false);
        }
    }

    private static final class ByteCountingPublisher implements Flow.Publisher<DataChunk> {
        private final Flow.Publisher<DataChunk> originalPublisher;
        private final Context context;

        private ByteCountingPublisher(Flow.Publisher<DataChunk> originalPublisher, Context context) {
            this.originalPublisher = originalPublisher;
            this.context = context;
        }

        @Override
        public void subscribe(Flow.Subscriber<? super DataChunk> subscriber) {
            originalPublisher.subscribe(new ByteCountingSubscriber(subscriber, context));
        }
    }

    private static final class ByteCountingSubscriber implements Flow.Subscriber<DataChunk> {
        private final Flow.Subscriber<? super DataChunk> subscriber;
        private final Context context;
        private final LongAdder sizeAdder = new LongAdder();

        private ByteCountingSubscriber(Flow.Subscriber<? super DataChunk> subscriber, Context context) {
            this.subscriber = subscriber;
            this.context = context;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            subscriber.onSubscribe(subscription);
        }

        @Override
        public void onNext(DataChunk item) {
            sizeAdder.add(item.remaining());
            subscriber.onNext(item);
        }

        @Override
        public void onError(Throwable throwable) {
            sizeAdder.reset();
            sizeAdder.decrement();
            subscriber.onError(throwable);
        }

        @Override
        public void onComplete() {
            long sum = sizeAdder.sum();
            if (sum != -1L) {
                context.register(SIZE_CONTEXT_CLASSIFIER, sizeAdder.sum());
            }
            subscriber.onComplete();
        }
    }
}
