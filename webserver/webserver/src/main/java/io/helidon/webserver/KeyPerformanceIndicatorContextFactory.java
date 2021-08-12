/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
package io.helidon.webserver;

import io.helidon.webserver.KeyPerformanceIndicatorSupport.Context;
import io.helidon.webserver.KeyPerformanceIndicatorSupport.Metrics;

class KeyPerformanceIndicatorContextFactory {

    static Context immediateRequestContext() {
        return new ImmediateRequestContext();
    }

    static DeferrableRequestContext deferrableRequestContext() {
        return new DeferrableRequestContext();
    }

    private KeyPerformanceIndicatorContextFactory() {
    }

    private static class ImmediateRequestContext implements Context {

        // kpiMetrics is set from MetricsSupport, so in apps without metrics kpiMetrics will be null in this context.
        private Metrics kpiMetrics;

        private long requestStartTime;

        @Override
        public void requestHandlingStarted(Metrics kpiMetrics) {
            recordStartTime();
            kpiMetrics(kpiMetrics);
            kpiMetrics.onRequestReceived();
            kpiMetrics.onRequestStarted();
        }

        @Override
        public void requestProcessingCompleted(boolean isSuccessful) {
            if (kpiMetrics != null) {
                kpiMetrics.onRequestCompleted(isSuccessful, System.currentTimeMillis() - requestStartTime);
            }
        }

        protected void recordStartTime() {
            requestStartTime = System.currentTimeMillis();
        }

        protected void kpiMetrics(Metrics kpiMetrics) {
            this.kpiMetrics = kpiMetrics;
        }

        protected Metrics kpiMetrics() {
            return kpiMetrics;
        }
    }

    private static class DeferrableRequestContext extends ImmediateRequestContext
            implements KeyPerformanceIndicatorSupport.DeferrableRequestContext {

        private boolean isStartRecorded = false;

        @Override
        public void requestHandlingStarted(Metrics kpiMetrics) {
            kpiMetrics(kpiMetrics);
            recordStartTime(); // In case no handler in the chain manages the start-of-processing moment.
            kpiMetrics.onRequestReceived();
        }

        @Override
        public void requestProcessingStarted() {
            recordStartTime(); // Overwrite the previously-recorded, provisional start time, now that we have a real one.
            recordProcessingStarted();
        }

        @Override
        public void requestProcessingCompleted(boolean isSuccessful) {
            // No handler explicitly dealt with start-of-processing, so approximate it based on request receipt time.
            if (!isStartRecorded) {
                recordProcessingStarted();
            }
            super.requestProcessingCompleted(isSuccessful);
        }

        private void recordProcessingStarted() {
            isStartRecorded = true;
            Metrics kpiMetrics = kpiMetrics();
            if (kpiMetrics != null) {
                kpiMetrics().onRequestStarted();
            }
        }
    }
}
