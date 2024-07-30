/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

package io.helidon.webclient.http2;

import java.time.Duration;

/**
 * Configuration for an HTTP2 stream.
 */
public interface Http2StreamConfig {

    /**
     * Prior knowledge setting.
     *
     * @return prior knowledge setting
     */
    boolean priorKnowledge();

    /**
     * Stream priority.
     *
     * @return the stream priority
     */
    int priority();

    /**
     * Read timeout for this stream.
     *
     * @return the timeout
     */
    Duration readTimeout();
}
