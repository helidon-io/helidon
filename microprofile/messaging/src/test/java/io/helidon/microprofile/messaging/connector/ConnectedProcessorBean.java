/*
 * Copyright (c)  2020 Oracle and/or its affiliates.
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

package io.helidon.microprofile.messaging.connector;

import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Outgoing;

import javax.enterprise.context.ApplicationScoped;

import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.isIn;

/**
 * This test is modified version of official tck test in version 1.0
 * https://github.com/eclipse/microprofile-reactive-messaging
 */
@ApplicationScoped
public class ConnectedProcessorBean {

    public static final CountDownLatch LATCH = new CountDownLatch(IterableConnector.TEST_DATA.length);
    private static final Set<String> PROCESSED_DATA =
            Arrays.stream(IterableConnector.TEST_DATA)
                    .map(ConnectedProcessorBean::reverseString)
                    .collect(Collectors.toSet());

    @Incoming("iterable-channel-in")
    @Outgoing("inner-channel")
    public String process(String msg) {
        return reverseString(msg);
    }

    @Incoming("inner-channel")
    public void receive(String msg) {
        assertThat(msg, isIn(PROCESSED_DATA));
        LATCH.countDown();
    }

    private static String reverseString(String msg) {
        return new StringBuilder(msg).reverse().toString();
    }


}
