/*
 * Copyright (c)  2020 Oracle and/or its affiliates.
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
 *
 */

package io.helidon.microprofile.messaging;

final class ExceptionUtils {

    private ExceptionUtils() {
    }

    /**
     * Raised when no connector of given name has been found.
     *
     * @param connectorName name of missing connector
     */
    static MessagingDeploymentException createNoConnectorFound(String connectorName) {
        return new MessagingDeploymentException(String.format("No connector %s found!", connectorName));
    }

    /**
     * Raised when channel hasn't candidate method or connector from both sides.
     *
     * @param channelName name of the incomplete channel
     */
    static MessagingDeploymentException createNoIncomingMethodForChannel(String channelName) {
        return new MessagingDeploymentException(
                String.format("No incoming method or connector for channel %s found!", channelName));
    }

    /**
     * Raised when channel hasn't candidate method or connector from both sides.
     *
     * @param channelName name of the incomplete channel
     */
    static MessagingDeploymentException createNoOutgoingMethodForChannel(String channelName) {
        return new MessagingDeploymentException(
                String.format("No outgoing method or connector for channel %s found!", channelName));
    }

    /**
     * Raised when {@link io.helidon.microprofile.messaging.CompletableQueue} max size is reached.
     *
     * @param maxSize maxim allowed size of queue
     */
    static MessagingException createCompletableQueueOverflow(long maxSize) {
        return new MessagingException(String.format("Maximum size %d of queue overflow!", maxSize));
    }
}
