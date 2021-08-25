/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.microprofile.lra;

import javax.ws.rs.core.UriBuilder;


import org.junit.jupiter.api.Test;


import java.net.URI;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;


import io.helidon.microprofile.lra.resources.DontEnd;

import static org.junit.jupiter.api.Assertions.assertLinesMatch;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ParticipantTest {

    private static final Logger LOGGER = Logger.getLogger(LoadBalancedCoordinatorTest.class.getName());

    @Test
    void name() {
        logFine("Participant {0} left LRA - ", () -> List.of("part1"));
    }

    void logFine(String msg, Supplier<List<?>> params) {
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.log(Level.FINE, msg, params.get().toArray());
        }
    }

    @Test
    void methodScan() throws NoSuchMethodException {
        ParticipantImpl p = new ParticipantImpl(UriBuilder.fromPath("http://localhost:8888").build(), DontEnd.class);
        assertTrue(p.isLraMethod(DontEnd.class.getMethod("startDontEndLRA", URI.class)));
        assertTrue(p.isLraMethod(DontEnd.class.getMethod("endLRA", URI.class)));
    }
}
