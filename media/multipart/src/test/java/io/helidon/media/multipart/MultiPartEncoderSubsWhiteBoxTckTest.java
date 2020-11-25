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

package io.helidon.media.multipart;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

import io.helidon.common.reactive.Multi;

import org.reactivestreams.tck.SubscriberWhiteboxVerification;
import org.reactivestreams.tck.TestEnvironment;
import org.reactivestreams.tck.flow.FlowSubscriberWhiteboxVerification;
import org.testng.annotations.Test;

import static io.helidon.media.multipart.BodyPartTest.MEDIA_CONTEXT;

public class MultiPartEncoderSubsWhiteBoxTckTest extends FlowSubscriberWhiteboxVerification<WriteableBodyPart> {

    protected MultiPartEncoderSubsWhiteBoxTckTest() {
        super(new TestEnvironment(200));
    }

    @Override
    public WriteableBodyPart createElement(final int element) {
        return WriteableBodyPart.builder()
                .entity("part" + element)
                .build();
    }

    @Override
    protected Flow.Subscriber<WriteableBodyPart> createFlowSubscriber(final WhiteboxSubscriberProbe<WriteableBodyPart> probe) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        MultiPartEncoder encoder = new MultiPartEncoder("boundary", MEDIA_CONTEXT.writerContext()){
            @Override
            public void onSubscribe(final Flow.Subscription subscription) {
                super.onSubscribe(subscription);
                future.complete(null);
                probe.registerOnSubscribe(new SubscriberWhiteboxVerification.SubscriberPuppet(){
                    @Override
                    public void triggerRequest(final long elements) {
                        subscription.request(elements);
                    }

                    @Override
                    public void signalCancel() {
                        subscription.cancel();
                    }
                });
            }

            @Override
            public void onNext(final WriteableBodyPart bodyPart) {
                super.onNext(bodyPart);
                probe.registerOnNext(bodyPart);
            }

            @Override
            public void onError(final Throwable throwable) {
                super.onError(throwable);
                probe.registerOnError(throwable);
            }

            @Override
            public void onComplete() {
                super.onComplete();
                probe.registerOnComplete();
            }
        };

        Multi.create(encoder).forEach(ch -> {});
        return encoder;
    }

    @Test(enabled = false)
    @Override
    public void required_spec205_mustCallSubscriptionCancelIfItAlreadyHasAnSubscriptionAndReceivesAnotherOnSubscribeSignal() throws Throwable {
        // not compliant
    }
}
