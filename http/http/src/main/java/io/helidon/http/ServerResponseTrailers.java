/*
 * Copyright (c) 2023, 2025 Oracle and/or its affiliates.
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

package io.helidon.http;

/**
 * Mutable trailers of a server response.
 */
public interface ServerResponseTrailers extends WritableHeaders<ServerResponseTrailers> {

    /**
     * Create a new instance of mutable server response trailers.
     *
     * @return new server response trailers
     */
    static ServerResponseTrailers create() {
        return new ServerResponseTrailersImpl(WritableHeaders.create());
    }

    /**
     * Create a new instance of mutable server response trailers.
     *
     * @param existing trailers to add to these response trailers
     * @return new server response trailers
     */
    static ServerResponseTrailers create(Headers existing) {
        return new ServerResponseTrailersImpl(WritableHeaders.create(existing));
    }

    /**
     * Create a new instance of mutable server response trailers by wrapping
     * existing writable trailers. Any updates to this class will also impact
     * the underlying trailers.
     *
     * @param existing trailers to wrap
     * @return new server response trailers
     */
    static ServerResponseTrailers wrap(WritableHeaders<?> existing) {
        return new ServerResponseTrailersImpl(existing);
    }
}
