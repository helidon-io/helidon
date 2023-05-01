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

package io.helidon.nima.udp;

/**
 * A UDP endpoint that can receive messages.
 */
@FunctionalInterface
public interface UdpEndpoint {

    /**
     * Called when a new message is received.
     *
     * @param message the message
     */
    void onMessage(UdpMessage message);

    /**
     * Called when an error is encountered.
     *
     * @param throwable the error
     */
    default void onError(Throwable throwable) {
        throw throwable instanceof RuntimeException rt ? rt : new RuntimeException(throwable);
    }
}
