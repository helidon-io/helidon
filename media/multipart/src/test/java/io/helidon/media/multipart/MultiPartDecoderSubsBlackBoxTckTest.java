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

package io.helidon.media.multipart;

import java.util.concurrent.Flow;

import io.helidon.common.http.DataChunk;
import io.helidon.common.reactive.Multi;

import org.reactivestreams.FlowAdapters;
import org.reactivestreams.Publisher;
import org.reactivestreams.tck.TestEnvironment;
import org.reactivestreams.tck.flow.FlowSubscriberBlackboxVerification;
import org.testng.annotations.Test;

import static io.helidon.media.multipart.BodyPartTest.MEDIA_CONTEXT;

public class MultiPartDecoderSubsBlackBoxTckTest extends FlowSubscriberBlackboxVerification<DataChunk> {

    protected MultiPartDecoderSubsBlackBoxTckTest() {
        super(new TestEnvironment(200));
    }

    @Override
    public Publisher<DataChunk> createHelperPublisher(long l) {
        return FlowAdapters.toPublisher(MultiPartDecoderTckTest.upstream(l));
    }

    @Override
    public DataChunk createElement(final int element) {
        return null;
    }

    @Override
    public Flow.Subscriber<DataChunk> createFlowSubscriber() {
        MultiPartDecoder decoder = MultiPartDecoder.create("boundary", MEDIA_CONTEXT.readerContext());
        Multi.create(decoder).forEach(part -> {});
        return decoder;
    }


    @Test(enabled = false)
    @Override
    public void required_spec205_blackbox_mustCallSubscriptionCancelIfItAlreadyHasAnSubscriptionAndReceivesAnotherOnSubscribeSignal() throws Exception {
        // not compliant
    }
}
