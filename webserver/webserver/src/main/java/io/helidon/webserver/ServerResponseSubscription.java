/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.webserver;

import java.util.concurrent.Flow;
import java.util.concurrent.atomic.LongAdder;

interface ServerResponseSubscription {

    void tryRequest();

    void onSubscribe();

    void cancel();

    void inc(NettyChannel channel, int byteSize);

    void dec(int byteSize);

    class WatermarkLinear implements ServerResponseSubscription {

        private final long waterMark;
        private final LongAdder actualBuffer = new LongAdder();
        private final Flow.Subscription subscription;

        WatermarkLinear(Flow.Subscription subscription, long waterMark) {
            this.subscription = subscription;
            this.waterMark = waterMark;
        }

        @Override
        public void onSubscribe() {
            tryRequest();
        }

        @Override
        public void tryRequest() {
            if (watermarkNotReached()) {
                subscription().request(1);
            }
        }

        @Override
        public void cancel() {
            subscription().cancel();
        }

        @Override
        public void inc(NettyChannel channel, int byteSize) {
            actualBuffer.add(byteSize);
        }

        @Override
        public void dec(int byteSize) {
            actualBuffer.add(-byteSize);
        }

        protected boolean watermarkNotReached(){
            return actualBuffer.sum() <= waterMark();
        }

        protected Flow.Subscription subscription() {
            return this.subscription;
        }

        protected long waterMark() {
            return this.waterMark;
        }
    }

    class WatermarkPrefetch extends WatermarkLinear {
        private int firstChunkSize = 0;
        private long nextRequest = 1;

        WatermarkPrefetch(Flow.Subscription subscription, long watermark) {
            super(subscription, watermark);
        }

        @Override
        public void onSubscribe() {
            tryRequest();
        }

        @Override
        public void tryRequest() {
            if (watermarkNotReached()) {
                subscription().request(nextRequest);
                nextRequest = 1;
            }
        }

        @Override
        public void inc(NettyChannel channel, int byteSize) {
            if (firstChunkSize == 0) {
                firstChunkSize = byteSize;
                nextRequest = waterMark() / firstChunkSize;
            }
            super.inc(channel, byteSize);
        }
    }

    class WatermarkAutoFlush extends WatermarkLinear {
        WatermarkAutoFlush(Flow.Subscription subscription, long watermark) {
            super(subscription, watermark);
        }

        @Override
        public void inc(NettyChannel channel, int byteSize) {
            super.inc(channel, byteSize);
            if (!watermarkNotReached()) {
                channel.flush();
            }
        }
    }

    class Unbounded implements ServerResponseSubscription {

        private final Flow.Subscription subscription;

        Unbounded(Flow.Subscription subscription) {
            this.subscription = subscription;
        }

        @Override
        public void onSubscribe() {
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void tryRequest() {
            //noop
        }

        @Override
        public void cancel() {
            subscription.cancel();
        }

        @Override
        public void inc(NettyChannel channel, int byteSize) {
            //noop
        }

        @Override
        public void dec(int byteSize) {
            //noop
        }
    }
}
