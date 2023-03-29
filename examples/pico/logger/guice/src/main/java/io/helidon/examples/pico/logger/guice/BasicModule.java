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

package io.helidon.examples.pico.logger.guice;

import io.helidon.examples.pico.logger.common.AnotherCommunicationMode;
import io.helidon.examples.pico.logger.common.Communication;
import io.helidon.examples.pico.logger.common.CommunicationMode;
import io.helidon.examples.pico.logger.common.Communicator;
import io.helidon.examples.pico.logger.common.DefaultCommunicator;
import io.helidon.examples.pico.logger.common.EmailCommunicationMode;
import io.helidon.examples.pico.logger.common.ImCommunicationMode;
import io.helidon.examples.pico.logger.common.SmsCommunicationMode;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;

public class BasicModule extends AbstractModule {

    @Override
    protected void configure() {
        try {
            bind(Communicator.class).toConstructor(DefaultCommunicator.class.getConstructor(CommunicationMode.class));
            bind(Boolean.class).toInstance(true);
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
        bind(CommunicationMode.class).to(AnotherCommunicationMode.class);
        bind(CommunicationMode.class).annotatedWith(Names.named("default")).to(AnotherCommunicationMode.class);
        bind(CommunicationMode.class).annotatedWith(Names.named("im")).to(ImCommunicationMode.class);
        bind(CommunicationMode.class).annotatedWith(Names.named("im")).to(ImCommunicationMode.class);
        bind(CommunicationMode.class).annotatedWith(Names.named("email")).to(EmailCommunicationMode.class);
        bind(CommunicationMode.class).annotatedWith(Names.named("sms")).to(SmsCommunicationMode.class);
        bind(Communication.class).asEagerSingleton();
    }

}
