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

/**
 * Operations with expected contention over connection stream collection,
 * which are going to be deferred to connection dispatch thread to be executed on.
 */
sealed interface Http2ConcurrentConnectionStreams permits Http2ConnectionStreams {
    /**
     * Contention expected between connection dispatch thread and stream threads.
     *
     * @param streamId id of the stream to be removed at nearest maintenance
     */
    void remove(int streamId);
}
