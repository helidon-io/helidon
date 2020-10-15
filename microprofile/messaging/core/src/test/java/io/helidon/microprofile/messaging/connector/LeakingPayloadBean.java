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
 */

package io.helidon.microprofile.messaging.connector;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Any;
import javax.inject.Inject;

import io.helidon.common.reactive.Multi;

import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.eclipse.microprofile.reactive.streams.operators.ProcessorBuilder;
import org.eclipse.microprofile.reactive.streams.operators.PublisherBuilder;
import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;
import org.reactivestreams.FlowAdapters;
import org.reactivestreams.Processor;
import org.reactivestreams.Publisher;

@ApplicationScoped
public class LeakingPayloadBean {

    @Inject
    @PostConstruct
    public void reset(@Any IterableConnector c) {
        c.reset(12);
    }

    @Outgoing("to-processor-builder")
    public Publisher<String> produceString() {
        return FlowAdapters.toPublisher(Multi.just(IterableConnector.TEST_DATA));
    }

    @Incoming("to-processor-builder")
    @Outgoing("to-connector-1")
    public ProcessorBuilder<String, String> proc() {
        return ReactiveStreams.builder();
    }


    @Outgoing("to-processor")
    public Publisher<String> produceString2() {
        return FlowAdapters.toPublisher(Multi.just(IterableConnector.TEST_DATA));
    }

    @Incoming("to-processor")
    @Outgoing("to-connector-2")
    public Processor<String, String> proc2() {
        return ReactiveStreams.<String>builder().buildRs();
    }

    @Outgoing("to-connector-3")
    public PublisherBuilder<String> produceString3() {
        return ReactiveStreams.of(IterableConnector.TEST_DATA);
    }

    @Outgoing("to-connector-4")
    public Publisher<String> produceString4() {
        return FlowAdapters.toPublisher(Multi.just(IterableConnector.TEST_DATA));
    }
}
