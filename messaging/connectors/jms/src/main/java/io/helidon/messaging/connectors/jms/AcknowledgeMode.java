/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.messaging.connectors.jms;

/**
 * Enumeration equivalent for JMS api's {@link javax.jms.Session#AUTO_ACKNOWLEDGE},
 * {@link javax.jms.Session#CLIENT_ACKNOWLEDGE} and {@link javax.jms.Session#DUPS_OK_ACKNOWLEDGE} constants.
 */
public enum AcknowledgeMode {
    /**
     * Acknowledges automatically after message reception over JMS api.
     */
    AUTO_ACKNOWLEDGE(1),
    /**
     * Message is acknowledged when {@link org.eclipse.microprofile.reactive.messaging.Message#ack} is invoked either
     * manually or by {@link org.eclipse.microprofile.reactive.messaging.Acknowledgment} policy.
     */
    CLIENT_ACKNOWLEDGE(2),
    /**
     * Messages are acknowledged lazily which can result in duplicate messages being delivered.
     */
    DUPS_OK_ACKNOWLEDGE(3);

    private int ackMode;

    AcknowledgeMode(int ackMode) {
        this.ackMode = ackMode;
    }

    static AcknowledgeMode parse(String name) {
        return AcknowledgeMode.valueOf(name.trim().toUpperCase());
    }

    /**
     * Returns JMS api constant equivalent of this ack mode as specified in
     * {@link javax.jms.Connection#createSession(boolean, int)}.
     *
     * @return 1 for AUTO_ACKNOWLEDGE or 2 for CLIENT_ACKNOWLEDGE or 3 for DUPS_OK_ACKNOWLEDGE
     */
    public int getAckMode() {
        return ackMode;
    }
}
