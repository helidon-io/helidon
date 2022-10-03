/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

import io.helidon.webserver.ServerResponseSubscription.Unbounded;
import io.helidon.webserver.ServerResponseSubscription.WatermarkAutoFlush;
import io.helidon.webserver.ServerResponseSubscription.WatermarkLinear;
import io.helidon.webserver.ServerResponseSubscription.WatermarkPrefetch;

/**
 * Strategy for applying backpressure to the reactive stream of response data.
 */
public enum BackpressureStrategy {
    /**
     * Data chunks are requested one-by-one after previous data chunk has been given to Netty for writing.
     * When backpressure-buffer-size watermark is reached new chunks are not requested until buffer size
     * decrease under the watermark value.
     */
    LINEAR(1),
    /**
     * Data are requested one-by-one, in case buffer reaches watermark,
     * no other data is requested and extra flush is initiated.
     */
    AUTO_FLUSH(2),
    /**
     * After first data chunk arrives, expected number of chunks needed
     * to fill the buffer up to watermark is calculated and requested.
     */
    PREFETCH(3),
    /**
     * No backpressure is applied, Long.MAX_VALUE(unbounded) is requested from upstream.
     */
    UNBOUNDED(4);

    private final int type;

    BackpressureStrategy(int type) {
        this.type = type;
    }

    ServerResponseSubscription createSubscription(Flow.Subscription subscription,
                                                  long backpressureBufferSize) {
        switch (type) {
            case 1: return new WatermarkLinear(subscription, backpressureBufferSize);
            case 2: return new WatermarkAutoFlush(subscription, backpressureBufferSize);
            case 3: return new WatermarkPrefetch(subscription, backpressureBufferSize);
            case 4: return new Unbounded(subscription);
            default: throw new IllegalStateException("Unknown backpressure strategy.");
        }
    }
}
