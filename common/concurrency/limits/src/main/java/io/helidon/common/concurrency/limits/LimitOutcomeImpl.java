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

import java.util.function.Consumer;
import java.util.function.Supplier;

class LimitOutcomeImpl implements LimitOutcome {

    private final String originName;
    private final String algorithmType;
    private final Disposition disposition;

    LimitOutcomeImpl(String originName, String algorithmType, Disposition disposition) {
        this.originName = originName;
        this.algorithmType = algorithmType;
        this.disposition = disposition;
    }

    /**
     * Process an immediately-accepted outcome.
     *
     * @param originName limit origin
     * @param algorithmType limit algorithm type
     */
    static void processImmediateAcceptance(String originName,
                                           String algorithmType,
                                           LimitAlgorithm.Token token,
                                           Consumer<LimitOutcome> limitOutcomeConsumer) {
        processAccepted(() -> new ImmediateAccepted(originName, algorithmType),
                        token,
                        limitOutcomeConsumer);

    }

    /**
     * Process an immediately-rejected outcome.
     *
     * @param originName limit origin
     * @param algorithmType limit algorithm type
     */
    static void processImmediateRejection(String originName,
                                          String algorithmType,
                                          Consumer<LimitOutcome> limitOutcomeConsumer) {
        processRejected(() -> new LimitOutcomeImpl(originName, algorithmType, Disposition.REJECTED),
                        limitOutcomeConsumer);
    }

    /**
     * Process a deferred accepted outcome.
     *
     * @param originName limit origin
     * @param algorithmType limit algorithm type
     * @param waitStart nanoseconds wait start time
     * @param waitEnd nanoseconds wait end time
     */
    static void processDeferredAcceptance(String originName,
                                          String algorithmType,
                                          LimitAlgorithm.Token token,
                                          long waitStart,
                                          long waitEnd,
                                          Consumer<LimitOutcome> limitOutcomeConsumer) {
        processAccepted(() -> new DeferredAccepted(originName,
                                                   algorithmType,
                                                   waitStart,
                                                   waitEnd),
                        token,
                        limitOutcomeConsumer);

    }

    /**
     * Process a deferred rejected outcome.
     *
     * @param originName limit origin
     * @param algorithmType limit algorithm type
     * @param waitStart nanoseconds wait start time
     * @param waitEnd nanoseconds wait end time
     */
    static void processDeferredRejection(String originName,
                                         String algorithmType,
                                         long waitStart,
                                         long waitEnd,
                                         Consumer<LimitOutcome> limitOutcomeConsumer) {
        processRejected(() -> new Deferred(originName, algorithmType, Disposition.REJECTED, waitStart, waitEnd),
                        limitOutcomeConsumer);
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

    private static void processAccepted(Supplier<Accepted> acceptedFactory,
                                        LimitAlgorithm.Token token,
                                        Consumer<LimitOutcome> limitOutcomeConsumer) {
        if (limitOutcomeConsumer == null) {
            return;
        }
        var result = acceptedFactory.get();
        if (token instanceof OutcomeAwareToken outcomeAwareToken) {
            outcomeAwareToken.outcome(result);
        }
        limitOutcomeConsumer.accept(result);
    }

    private static void processRejected(Supplier<LimitOutcomeImpl> rejectedFactory,
                                        Consumer<LimitOutcome> limitOutcomeConsumer) {
        if (limitOutcomeConsumer == null) {
            return;
        }
        limitOutcomeConsumer.accept(rejectedFactory.get());
    }

    interface Accepted extends LimitOutcome.Accepted {

        void execResult(ExecutionResult execResult);

    }

    /**
     * A deferred rejected limit outcome and the superclass for a deferred accepted limit outcome.
     */
    static class Deferred extends LimitOutcomeImpl implements LimitOutcome.Deferred {

        private final long waitStart;
        private final long waitEnd;

        Deferred(String originName, String algorithmType, Disposition disposition, long waitStart, long waitEnd) {
            super(originName, algorithmType, disposition);
            this.waitStart = waitStart;
            this.waitEnd = waitEnd;
        }

        @Override
        public long waitStart() {
            return waitStart;
        }

        @Override
        public long waitEnd() {
            return waitEnd;
        }
    }

    /**
     * An immediately-accepted limit outcome.
     */
    static class ImmediateAccepted extends LimitOutcomeImpl implements Accepted {

        private ExecutionResult execResult;

        ImmediateAccepted(String originName, String algorithmType) {
            super(originName, algorithmType, Disposition.ACCEPTED);

        }

        @Override
        public ExecutionResult executionResult() throws IllegalStateException {
            if (execResult == null) {
                throw new IllegalStateException("Execution result has not yet been set");
            }
            return execResult;
        }

        @Override
        public void execResult(ExecutionResult execResult) {
            this.execResult = execResult;
        }
    }

    /**
     * A deferred accepted limit outcome.
     */
    static class DeferredAccepted extends Deferred implements Accepted {

        private ExecutionResult execResult;

        DeferredAccepted(String originName, String algorithmType, long waitStart, long waitEnd) {
            super(originName, algorithmType, Disposition.ACCEPTED, waitStart, waitEnd);
        }

        @Override
        public ExecutionResult executionResult() throws IllegalStateException {
            if (execResult == null) {
                throw new IllegalStateException("Execution result has not yet been set");
            }
            return execResult;
        }

        @Override
        public void execResult(ExecutionResult execResult) {
            this.execResult = execResult;
        }
    }
}
