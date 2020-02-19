/*
 * Copyright (c)  2020 Oracle and/or its affiliates. All rights reserved.
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

package org.eclipse.microprofile.reactive.streams.operators.tck.spi;

import java.util.Arrays;
import java.util.List;

import static org.testng.Assert.assertFalse;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscription;
import org.reactivestreams.tck.flow.support.TestException;

/**
 * Coupled operator needs to signal cancel to parallel stream's upstream on its completion,
 * but rule 2.3 forbids direct call from onComplete to cancel.
 * Since the streams are actually parallel is the rule 2.3 original TCK test is not applicable.
 *
 * Original TCK test for rule 2.3 doesn't care about originating stream,
 * it just looks for method called "onComplete"/"onError" in call stack.
 * This test is trying to improve detection, so it is possible to implement coupled operator.
 *
 * https://github.com/eclipse/microprofile-reactive-streams-operators/issues/131
 */
public class CustomCoupledStageVerification extends CoupledStageVerification {
    public CustomCoupledStageVerification(ReactiveStreamsSpiVerification.VerificationDeps deps) {
        super(deps);
    }

    @Override
    List<Object> reactiveStreamsTckVerifiers() {
        return Arrays.asList(
                new PublisherVerification(),
                new SubscriberVerification(),
                new ProcessorVerificationWithExtendedSCallStackDetection()
        );
    }

    public CoupledStageVerification.ProcessorVerification getProcessorVerification() {
        return new ProcessorVerificationWithExtendedSCallStackDetection();
    }

    public class ProcessorVerificationWithExtendedSCallStackDetection extends CoupledStageVerification.ProcessorVerification {

        @Override
        public void required_spec203_mustNotCallMethodsOnSubscriptionOrPublisherInOnComplete() throws Throwable {
            await(rs.fromPublisher((Publisher<Long>) s -> {
                s.onSubscribe(new Subscription() {
                    @Override
                    public void request(long n) {
                        assertFalse(isCalledByMethod("_upstreamMarkerMethod203"));
                    }

                    @Override
                    public void cancel() {
                        assertFalse(isCalledByMethod("_upstreamMarkerMethod203"));
                    }
                });
                _upstreamMarkerMethod203(s::onComplete);
            })
                    .via(
                            rs.coupled(rs.builder().ignore(),
                                    rs.fromPublisher((Publisher<Long>) s -> {
                                        s.onSubscribe(new Subscription() {
                                            @Override
                                            public void request(long n) {
                                                assertFalse(isCalledByMethod("_passedInMarkerMethod203"));
                                            }

                                            @Override
                                            public void cancel() {
                                                assertFalse(isCalledByMethod("_passedInMarkerMethod203"));
                                            }
                                        });
                                        _passedInMarkerMethod203(s::onComplete);
                                    }))
                    )
                    .ignore()
                    .run(getEngine()));
        }

        @Override
        public void required_spec203_mustNotCallMethodsOnSubscriptionOrPublisherInOnError() throws Throwable {
            rs.fromPublisher((Publisher<Long>) s -> {
                s.onSubscribe(new Subscription() {
                    @Override
                    public void request(long n) {
                        assertFalse(isCalledByMethod("_upstreamMarkerMethod203"));
                    }

                    @Override
                    public void cancel() {
                        assertFalse(isCalledByMethod("_upstreamMarkerMethod203"));
                    }
                });
                _upstreamMarkerMethod203(() -> s.onError(new TestException()));
            })
                    .via(
                            rs.coupled(rs.builder().ignore(),
                                    rs.fromPublisher((Publisher<Long>) s -> {
                                        s.onSubscribe(new Subscription() {
                                            @Override
                                            public void request(long n) {
                                                assertFalse(isCalledByMethod("_passedInMarkerMethod203"));
                                            }

                                            @Override
                                            public void cancel() {
                                                assertFalse(isCalledByMethod("_passedInMarkerMethod203"));
                                            }
                                        });
                                        _passedInMarkerMethod203(() -> s.onError(new TestException()));
                                    }))
                    )
                    .onErrorResume(throwable -> 1L)
                    .ignore()
                    .run(getEngine());
        }

        private boolean isCalledByMethod(String methodName) {
            return Arrays.stream(Thread.currentThread().getStackTrace())
                    .map(StackTraceElement::getMethodName)
                    .anyMatch(s -> s.equals(methodName));
        }

        private <T> void _upstreamMarkerMethod203(Runnable runnable) {
            runnable.run();
        }

        private <T> void _passedInMarkerMethod203(Runnable runnable) {
            runnable.run();
        }

    }
}
