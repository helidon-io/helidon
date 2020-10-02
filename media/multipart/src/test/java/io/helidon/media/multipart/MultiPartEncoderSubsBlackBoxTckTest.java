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

import io.helidon.common.reactive.Multi;

import org.reactivestreams.tck.TestEnvironment;
import org.reactivestreams.tck.flow.FlowSubscriberBlackboxVerification;
import org.testng.annotations.Test;

import static io.helidon.media.multipart.BodyPartTest.MEDIA_CONTEXT;

public class MultiPartEncoderSubsBlackBoxTckTest extends FlowSubscriberBlackboxVerification<WriteableBodyPart> {

    protected MultiPartEncoderSubsBlackBoxTckTest() {
        super(new TestEnvironment(200));
    }

    @Override
    public Flow.Subscriber<WriteableBodyPart> createFlowSubscriber() {
        MultiPartEncoder encoder = MultiPartEncoder.create("boundary", MEDIA_CONTEXT.writerContext());
        Multi.create(encoder).forEach(ch -> {});
        return encoder;
    }

    @Override
    public WriteableBodyPart createElement(final int element) {
        return WriteableBodyPart.builder()
                .entity("part" + element)
                .build();
    }

    @Test(enabled = false)
    @Override
    public void required_spec205_blackbox_mustCallSubscriptionCancelIfItAlreadyHasAnSubscriptionAndReceivesAnotherOnSubscribeSignal() throws Exception {
        // not compliant
    }
}
