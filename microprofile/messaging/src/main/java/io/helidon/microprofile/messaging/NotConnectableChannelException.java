/*
 * Copyright (c)  2020 Oracle and/or its affiliates. All rights reserved.
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

import javax.enterprise.inject.spi.DeploymentException;

/**
 * Raised when channel hasn't candidate method or connector from both sides.
 */
class NotConnectableChannelException extends DeploymentException {

    private static final long serialVersionUID = -7635906078963196864L;

    /**
     * Create new {@link NotConnectableChannelException}.
     *
     * @param channelName name of un-connectable channel
     * @param type        incoming or outgoing {@link NotConnectableChannelException.Type}
     */
    NotConnectableChannelException(String channelName, Type type) {
        super(composeMessage(channelName, type));
    }

    private static String composeMessage(String channelName, Type type) {
        return String.format("No %s method or connector for channel %s found!", type, channelName);
    }

    /**
     * Incoming or outgoing method/connector.
     */
    enum Type {
        /**
         * Incoming method or connector.
         */
        INCOMING,
        /**
         * Outgoing method or connector.
         */
        OUTGOING;

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }
}
