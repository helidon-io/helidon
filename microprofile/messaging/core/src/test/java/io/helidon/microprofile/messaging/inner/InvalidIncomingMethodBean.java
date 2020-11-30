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

package io.helidon.microprofile.messaging.inner;

import java.util.concurrent.CountDownLatch;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.spi.DeploymentException;

import io.helidon.microprofile.messaging.AssertThrowException;
import io.helidon.microprofile.messaging.CountableTestBean;

import org.eclipse.microprofile.reactive.messaging.Acknowledgment;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.eclipse.microprofile.reactive.streams.operators.PublisherBuilder;
import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;

@ApplicationScoped
@AssertThrowException(DeploymentException.class)
public class InvalidIncomingMethodBean implements CountableTestBean {

    @Outgoing("invalid-signature-1")
    public PublisherBuilder<String> produceMessage() {
        return ReactiveStreams.empty();
    }

    @Incoming("invalid-signature-1")
    @Acknowledgment(Acknowledgment.Strategy.MANUAL)
    public void receiveMethod(org.eclipse.microprofile.reactive.messaging.Message<String> msg) {

    }

    @Override
    public CountDownLatch getTestLatch() {
        return new CountDownLatch(0);
    }
}
