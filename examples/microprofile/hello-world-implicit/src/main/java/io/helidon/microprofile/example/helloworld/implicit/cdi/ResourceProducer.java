/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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

package io.helidon.microprofile.example.helloworld.implicit.cdi;

import java.util.concurrent.atomic.AtomicInteger;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.enterprise.inject.spi.InjectionPoint;

/**
 * Producer for various resources required by this example.
 */
@ApplicationScoped
public class ResourceProducer {
    private static final AtomicInteger COUNTER = new AtomicInteger();

    /**
     * Each injection will increase the COUNTER.
     *
     * @return increased COUNTER value
     */
    @Produces
    @RequestId
    public int produceRequestId() {
        return COUNTER.incrementAndGet();
    }

    /**
     * Create/get a logger instance for the class that the logger is being injected into.
     *
     * @param injectionPoint injection point
     * @return a logger instance
     */
    @Produces
    @LoggerQualifier
    public java.util.logging.Logger produceLogger(final InjectionPoint injectionPoint) {
        return java.util.logging.Logger.getLogger(injectionPoint.getMember().getDeclaringClass().getName());
    }
}
