/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.webserver.http2;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * HTTP/2 streams bound to single connection.
 * Only methods implemented from {@link io.helidon.webserver.http2.Http2ConcurrentConnectionStreams}
 * are thread safe, rest needs to be called from connection dispatch thread only!
 */
final class Http2ConnectionStreams implements Http2ConcurrentConnectionStreams {
    private final Map<Integer, Http2Connection.StreamContext> streams = new HashMap<>(1000);
    private final Queue<Integer> forRemoval = new ConcurrentLinkedQueue<>();

    @Override
    public void remove(int streamId) {
        forRemoval.add(streamId);
    }

    void put(Http2Connection.StreamContext ctx) {
        streams.put(ctx.stream().streamId(), ctx);
    }

    Http2Connection.StreamContext get(int streamId) {
        return streams.get(streamId);
    }

    Collection<Http2Connection.StreamContext> contexts() {
        return streams.values();
    }

    boolean isEmpty() {
        return streams.isEmpty();
    }

    int size() {
        return streams.size();
    }

    void doMaintenance(long maxConcurrentStreams) {
        if (streams.size() < maxConcurrentStreams) {
            return;
        }
        for (Integer streamId = forRemoval.poll();
                streamId != null;
                streamId = forRemoval.poll()) {
            streams.remove(streamId);
        }
    }
}
