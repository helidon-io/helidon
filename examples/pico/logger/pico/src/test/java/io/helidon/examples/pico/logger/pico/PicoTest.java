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

package io.helidon.examples.pico.logger.pico;

import io.helidon.examples.pico.logger.common.AnotherCommunicationMode;
import io.helidon.examples.pico.logger.common.DefaultCommunicator;
import io.helidon.examples.pico.logger.common.EmailCommunicationMode;
import io.helidon.examples.pico.logger.common.ImCommunicationMode;
import io.helidon.examples.pico.logger.common.SmsCommunicationMode;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PicoTest {

    @Test
    public void testMain() {
        final long memStart = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        final long start = System.currentTimeMillis();

        Main.main(new String[] {"Hello World!"});

        final long finish = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        final long memFinish = Runtime.getRuntime().totalMemory();
        DefaultCommunicator comms = ((DefaultCommunicator) Main.comms.communicator);
        assertEquals(SmsCommunicationMode.class, comms.sms.getClass());
        assertEquals(EmailCommunicationMode.class, comms.email.getClass());
        assertEquals(ImCommunicationMode.class, comms.im.getClass());
        assertEquals(AnotherCommunicationMode.class, comms.defaultCommunication.getClass());
        System.out.println("Pico Junit memory consumption = " + (memFinish - memStart) + " bytes");
        System.out.println("Pico Junit elapsed time = " + (finish - start) + " ms");
    }

}
