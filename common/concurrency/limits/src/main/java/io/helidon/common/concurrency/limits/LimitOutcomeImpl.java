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

class LimitOutcomeImpl implements LimitOutcome {

    private final String originName;
    private final String algorithmType;
    private final Disposition disposition;

    protected LimitOutcomeImpl(String originName, String algorithmType, Disposition disposition) {
        this.originName = originName;
        this.algorithmType = algorithmType;
        this.disposition = disposition;
    }

    /**
     * Create an immediately-accepted outcome.
     *
     * @param originName limit origin
     * @param algorithmType limit algorithm type
     * @return immediately-accepted limit outcome
     */
    static LimitOutcomeImpl.Accepted createAccepted(String originName, String algorithmType) {
        return new ImmediateAccepted(originName, algorithmType);
    }

    /**
     * Create an immediately-rejected outcome.
     *
     * @param originName limit origin
     * @param algorithmType limit algorithm type
     * @return immediate-rejected limit outcome
     */
    static LimitOutcomeImpl createRejected(String originName, String algorithmType) {
        return new LimitOutcomeImpl(originName, algorithmType, Disposition.REJECTED);
    }

    /**
     * Create a deferred accepted outcome.
     *
     * @param originName limit origin
     * @param algorithmType limit algorithm type
     * @param waitStart nanoseconds wait start time
     * @param waitEnd nanoseconds wait end time
     * @return deferred accepted limit outcome
     */
    static LimitOutcomeImpl.Accepted createAccepted(String originName,
                                                    String algorithmType,
                                                    long waitStart,
                                                    long waitEnd) {
        return new DeferredAccepted(originName, algorithmType, waitStart, waitEnd);
    }

    /**
     * Create a deferred rejected outcome.
     *
     * @param originName limit origin
     * @param algorithmType limit algorithm type
     * @param waitStart nanoseconds wait start time
     * @param waitEnd nanoseconds wait end time
     * @return deferred rejected limit outcome
     */
    static LimitOutcomeImpl createRejected(String originName,
                                           String algorithmType,
                                           long waitStart,
                                           long waitEnd) {
        return new Deferred(originName, algorithmType, Disposition.REJECTED, waitStart, waitEnd);
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

    interface Accepted extends LimitOutcome.Accepted {

        void execResult(ExecResult execResult);

    }

    /**
     * A deferred rejected limit outcome and the superclass for a deferred accepted limit outcome.
     */
    private static class Deferred extends LimitOutcomeImpl implements LimitOutcome.Deferred {

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
    private static class ImmediateAccepted extends LimitOutcomeImpl implements Accepted {

        private ExecResult execResult;

        ImmediateAccepted(String originName, String algorithmType) {
            super(originName, algorithmType, Disposition.ACCEPTED);
        }

        @Override
        public ExecResult execResult() throws IllegalStateException {
            if (execResult == null) {
                throw new IllegalStateException("Execution result has not yet been set");
            }
            return execResult;
        }

        @Override
        public void execResult(ExecResult execResult) {
            this.execResult = execResult;
        }
    }

    /**
     * A deferred accepted limit outcome.
     */
    private static class DeferredAccepted extends Deferred implements Accepted {

        private ExecResult execResult;

        DeferredAccepted(String originName, String algorithmType, long waitStart, long waitEnd) {
            super(originName, algorithmType, Disposition.ACCEPTED, waitStart, waitEnd);
        }

        @Override
        public ExecResult execResult() throws IllegalStateException {
            if (execResult == null) {
                throw new IllegalStateException("Execution result has not yet been set");
            }
            return execResult;
        }

        @Override
        public void execResult(ExecResult execResult) {
            this.execResult = execResult;
        }
    }
}
