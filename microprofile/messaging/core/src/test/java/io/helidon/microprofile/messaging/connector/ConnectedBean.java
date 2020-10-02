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

import javax.enterprise.context.ApplicationScoped;

import java.util.concurrent.CountDownLatch;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItemInArray;

/**
 * This test is modified version of official tck test in version 1.0
 * https://github.com/eclipse/microprofile-reactive-messaging
 */
@ApplicationScoped
public class ConnectedBean {

    public static final CountDownLatch LATCH = new CountDownLatch(IterableConnector.TEST_DATA.length);

    @Incoming("iterable-channel-in")
    public void receiveMethod(String msg) {
        assertThat(IterableConnector.TEST_DATA, hasItemInArray(msg));
        LATCH.countDown();
    }
}
