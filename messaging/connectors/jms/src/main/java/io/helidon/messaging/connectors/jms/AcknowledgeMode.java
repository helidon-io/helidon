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

enum AcknowledgeMode {
    AUTO_ACKNOWLEDGE(1),
    CLIENT_ACKNOWLEDGE(2),
    DUPS_OK_ACKNOWLEDGE(3);

    private int ackMode;

    AcknowledgeMode(int ackMode) {
        this.ackMode = ackMode;
    }

    static AcknowledgeMode parse(String name){
        return AcknowledgeMode.valueOf(name.trim().toUpperCase());
    }

    public int getAckMode() {
        return ackMode;
    }
}
