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

package io.helidon.webclient.spi;

/**
 * A listener for arbitrary events.
 *
 * @param <T> event type
 */
@FunctionalInterface
public interface Source<T> {

    /**
     * Handler for event stream creation.
     */
    default void onOpen() {
    }

    /**
     * Handler for a newly received event.
     *
     * @param event the event
     */
    void onEvent(T event);

    /**
     * Handler for event stream termination.
     */
    default void onClose() {
    }

    /**
     * Handler for errors encountered during processing of source.
     *
     * @param t a throwable
     */
    default void onError(Throwable t){
    }
}
