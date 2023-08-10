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

package io.helidon.webclient.api;

/**
 * A resource that can be released or closed.
 * This is used to handle HTTP/1.1 connection, for example. In other cases (such as HTTP/2 streams),
 * the release call also closes the resource.
 */
public interface ReleasableResource {
    /**
     * Releases the resource, and if this resource is re-usable, enabled reuse.
     */
    default void releaseResource() {
        closeResource();
    }

    /**
     * Closes the resource, we cannot use name {@code close}, as that would conflict with {@link java.lang.AutoCloseable},
     * as we do not want to have a checked exception thrown.
     */
    void closeResource();
}
