/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.common.concurrency.limits;

import java.util.Optional;

import io.helidon.common.concurrency.limits.LimitAlgorithm.Outcome;

/**
 * Implementation class for {@link Outcome}.
 * <p>
 * Implementations need to cover several independent categories:
 * <ul>
 *     <li>disposition: accepted/rejected</li>
 *     <li>timing: immediate/deferred</li>
 * </ul>
 * We do not actually <em>need</em> to have a distinct implementation class for each of the combinatorial possibilities. For
 * example, the {@code Immediate} subclasses within {@code Accepted} and {@code Deferred} offer no additional behavior beyond
 * their immediate superclasses. But we have them for clarity, the hope being that doing so makes this class easier to understand
 * and, for debugging, shows clearer class names identifying what particular variant of {@code Outcome} was returned to callers
 * of the {@link io.helidon.common.concurrency.limits.LimitAlgorithm} implementation.
 */
class LimitAlgorithmOutcomeImpl implements Outcome {

    private final String originName;
    private final String algorithmType;
    private final Outcome.Disposition disposition;
    private final Outcome.Timing timing;

    private LimitAlgorithmOutcomeImpl(String originName,
                                      String algorithmType,
                                      Disposition disposition,
                                      Timing timing) {
        this.originName = originName;
        this.algorithmType = algorithmType;
        this.disposition = disposition;
        this.timing = timing;
    }

    /**
     * Creates a new outcome for compatibility with older algorithms.
     *
     * @param token token indicating acceptance or rejection of the work item
     * @return outcome
     * @deprecated Remove when legacy methods on {@link LimitAlgorithm} are removed.
     */
    @Deprecated(since = "4.3.0", forRemoval = true)
    static Outcome create(Optional<LimitAlgorithm.Token> token) {
        return token.isPresent()
                ? new LimitAlgorithmOutcomeImpl.Accepted("unknown", "unknown", token.get(), Timing.UNKNOWN)
                : new LimitAlgorithmOutcomeImpl("unknown", "unknown", Disposition.REJECTED, Timing.UNKNOWN);
    }

    /**
     * Creates an immediate acceptance outcome.
     *
     * @param originName origin name
     * @param algorithmType algorithm type
     * @param token {@link io.helidon.common.concurrency.limits.LimitAlgorithm.Token} created by the algorithm
     * @return immediate acceptance outcome
     */
    static Accepted.Immediate immediateAcceptance(String originName,
                                                               String algorithmType,
                                                               LimitAlgorithm.Token token) {
        return new LimitAlgorithmOutcomeImpl.Accepted.Immediate(originName, algorithmType, token);
    }

    /**
     * Creates a deferred acceptance outcome.
     *
     * @param originName origin name
     * @param algorithmType algorithm type
     * @param token {@link io.helidon.common.concurrency.limits.LimitAlgorithm.Token} created by the algorithm
     * @param waitStartNanos when the work item began waiting (system nanoseconds)
     * @param waitEndNanos when the work item was accepted for execution (system nanoseconds)
     * @return deferred acceptance outcome
     */
    static Outcome.Deferred deferredAcceptance(String originName,
                                                                    String algorithmType,
                                                                    LimitAlgorithm.Token token,
                                                                    long waitStartNanos,
                                                                    long waitEndNanos) {
        return new Accepted.Deferred(originName, algorithmType, token, waitStartNanos, waitEndNanos);
    }

    /**
     * Creates an immediate rejection outcome.
     *
     * @param originName origin name
     * @param algorithmType algorithm type
     * @return immediate rejection outcome
     */
    static Outcome immediateRejection(String originName, String algorithmType) {
        return new Rejected.Immediate(originName, algorithmType);
    }

    /**
     * Creates a deferred rejection outcome.
     *
     * @param originName origin name
     * @param algorithmType algorithm type
     * @param waitStartNanos when the work item began waiting (system nanoseconds)
     * @param waitEndNanos when the work item was rejected (system nanoseconds)
     * @return deferred rejection outcome
     */
    static Outcome deferredRejection(String originName,
                                                    String algorithmType,
                                                    long waitStartNanos,
                                                    long waitEndNanos) {
        return new Rejected.Deferred(originName, algorithmType, waitStartNanos, waitStartNanos);
    }

    @Override
    public String originName() {
        return originName;
    }

    @Override
    public String algorithmType() {
        return algorithmType;
    }

    @Override
    public Disposition disposition() {
        return disposition;
    }

    @Override
    public Timing timing() {
        return timing;
    }

    static class Accepted extends LimitAlgorithmOutcomeImpl implements Outcome.Accepted {

        private final LimitAlgorithm.Token token;

        Accepted(String originName, String algorithmType, LimitAlgorithm.Token token, Timing timing) {
            super(originName, algorithmType, Disposition.ACCEPTED, timing);
            this.token = token;
        }

        @Override
        public LimitAlgorithm.Token token() {
            return token;
        }

        static class Immediate extends LimitAlgorithmOutcomeImpl.Accepted {

            Immediate(String originName, String algorithmType, LimitAlgorithm.Token token) {
                super(originName, algorithmType, token, Timing.IMMEDIATE);
            }
        }

        static class Deferred extends LimitAlgorithmOutcomeImpl.Accepted implements Outcome.Deferred {

            private final long waitStartNanoTime;
            private final long waitEndNanoTime;

            Deferred(String originName,
                     String algorithmType,
                     LimitAlgorithm.Token token,
                     long waitStartNanoTime,
                     long waitEndNanoTime) {
                super(originName, algorithmType, token, Timing.DEFERRED);
                this.waitStartNanoTime = waitStartNanoTime;
                this.waitEndNanoTime = waitEndNanoTime;
            }

            @Override
            public long waitStartNanoTime() {
                return waitStartNanoTime;
            }

            @Override
            public long waitEndNanoTime() {
                return waitEndNanoTime;
            }
        }
    }

    static class Rejected extends LimitAlgorithmOutcomeImpl implements Outcome {

        Rejected(String originName, String algorithmType, Timing timing) {
            super(originName, algorithmType, Disposition.REJECTED, timing);
        }

        static class Immediate extends LimitAlgorithmOutcomeImpl.Rejected {

            Immediate(String originName, String algorithmType) {
                super(originName, algorithmType, Timing.IMMEDIATE);
            }
        }

        static class Deferred extends LimitAlgorithmOutcomeImpl.Rejected implements Outcome.Deferred {

            private final long waitStartNanoTime;
            private final long waitEndNanoTime;

            Deferred(String originName, String algorithmType, long waitStartNanoTime, long waitEndNanoTime) {
                super(originName, algorithmType, Timing.DEFERRED);
                this.waitStartNanoTime = waitStartNanoTime;
                this.waitEndNanoTime = waitEndNanoTime;
            }

            @Override
            public long waitStartNanoTime() {
                return waitStartNanoTime;
            }

            @Override
            public long waitEndNanoTime() {
                return waitEndNanoTime;
            }
        }
    }
}
