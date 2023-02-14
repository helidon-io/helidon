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

import io.helidon.nima.sse.webclient.SseSourceHandlerProvider;
import io.helidon.nima.webclient.http.spi.SourceHandlerProvider;

/**
 * Nima SSE webclient package.
 */
module io.helidon.nima.sse.webclient {
    requires transitive io.helidon.common;
    requires transitive io.helidon.common.media.type;
    requires transitive io.helidon.nima.sse;
    requires io.helidon.nima.webclient;

    provides SourceHandlerProvider with SseSourceHandlerProvider;

    exports io.helidon.nima.sse.webclient;
}
