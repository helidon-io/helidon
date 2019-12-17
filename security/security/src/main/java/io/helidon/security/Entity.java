/*
 * Copyright (c) 2018, 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.security;

import java.nio.ByteBuffer;
import java.util.concurrent.Flow;
import java.util.function.Function;

/**
 * Access to message content (entity).
 * Use cases:
 * <ol>
 * <li>You do not need to access entity at all: do nothing (do not call methods on this interface)</li>
 * <li>Need to process entity (no modifications): call {@link #filter(Function)} method and just copy bytes you
 * receive to your subscribers; or buffer them and publish them once finished</li>
 * <li>Need to process entity (modifications): call {@link #filter(Function)}, process the bytes you receive and
 * publish modified bytes</li>
 * </ol>
 * <p>
 * <strong>WARNING: when buffering entity, make sure you do not run out of memory - it is strongly recommended
 * to limit the number of bytes to be stored in memory. Once that is reached, you will need to offload to file system or
 * other non-JVM heap storage.</strong>
 * <p>
 * <strong>Document fully cases when entity is buffered, so developers understand that when using your
 * security provider, and can assess impact - e.g. if we would validate signatures on uploaded video, a very big
 * performance impact has to be expected</strong>
 */
@FunctionalInterface
public interface Entity {
    /**
     * Call this method if your security provider needs access to entity bytes. Your processor MUST provide the bytes
     * back to us, as otherwise the entity would not be processed at all.
     *
     * @param filterFunction function that will get a publisher (that will publish bytes from external source for inbound
     *                       request/outbound response or bytes from business logic for inbound response/outbound request)
     *                       and that returns your publisher of modified/different bytes
     */
    void filter(Function<Flow.Publisher<ByteBuffer>, Flow.Publisher<ByteBuffer>> filterFunction);
}
