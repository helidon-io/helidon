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

package io.helidon.examples.pico.logger.common;

public class DefaultCommunicator implements Communicator {

    @javax.inject.Inject
    @javax.inject.Named("sms")
    public CommunicationMode sms;

    @javax.inject.Inject
    @javax.inject.Named("email")
    public CommunicationMode email;

    @javax.inject.Inject
    @javax.inject.Named("im")
    public CommunicationMode im;

    public CommunicationMode defaultCommunication;

    @javax.inject.Inject
    public DefaultCommunicator(CommunicationMode defaultCommunication) {
        this.defaultCommunication = defaultCommunication;
    }

    @Override
    public int sendMessage(String message, String preferredMode) {
        if ("sms".equals(preferredMode)) {
            return sms.sendMessage(message);
        } else if ("email".equals(preferredMode)) {
            return email.sendMessage(message);
        } else if ("im".equals(preferredMode)) {
            return im.sendMessage(message);
        } else {
            return defaultCommunication.sendMessage(message);
        }
    }

}
