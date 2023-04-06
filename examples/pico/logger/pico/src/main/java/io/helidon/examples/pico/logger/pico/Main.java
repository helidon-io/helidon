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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.examples.pico.logger.common.Communication;
import io.helidon.pico.api.PicoServices;
import io.helidon.pico.api.Services;

public class Main {

    static Services services;
    static Communication comms;

    public static void main(String[] args){
        final long memStart = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        final long start = System.currentTimeMillis();

        PicoServices picoServices = PicoServices.picoServices().get();
        services = picoServices.services();
        comms = services.lookupFirst(Communication.class).get();

        List<String> messages = Arrays.asList(args);
        if (messages.isEmpty()) {
            messages = Collections.singletonList("Hello World!");
        }

        AtomicInteger sent = new AtomicInteger();
        messages.forEach((message) -> {
            sent.addAndGet(comms.sendMessageViaAllModes(message));
        });
        int count = sent.get();
        System.out.println("finished sending: " + count);

        final long finish = System.currentTimeMillis();
        final long memFinish = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        System.out.println("Pico Main memory consumption = " + (memFinish - memStart) + " bytes");
        System.out.println("Pico Main elapsed time = " + (finish - start) + " ms");
    }
}
