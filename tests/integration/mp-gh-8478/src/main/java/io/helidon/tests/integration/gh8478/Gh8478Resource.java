/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.tests.integration.gh8478;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;

@Path("/greet")
@Retry
@Timeout
public class Gh8478Resource {
    private static final Logger LOGGER = Logger.getLogger(Gh8478Resource.class.getName());

    static final AtomicInteger COUNTER = new AtomicInteger();

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String getDefaultMessage() throws InterruptedException {
        COUNTER.incrementAndGet();

        LOGGER.info("Attempt #" + COUNTER.get() + " before sleep.");
        Thread.sleep(100);
        if (COUNTER.get() == 3) {
            LOGGER.info("Attempt #" + COUNTER.get() + " returning response.");
            return "Hello World!";
        }

        LOGGER.info("Attempt #" + COUNTER.get() + " throwing exception.");
        throw new ForbiddenException("Intentional exception");
    }
}
