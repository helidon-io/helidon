/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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

package io.helidon.microprofile.metrics;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.Metric;

/**
 * Class ProducerBean.
 */
@Dependent
public class ProducerBean {

    static final long FIELD_PRODUCED_EXPECTED_VALUE = 3L;
    @Inject
    private MetricRegistry metricRegistry;

    @Produces @Red
    final Counter counter1 = new LongCounter(FIELD_PRODUCED_EXPECTED_VALUE);

    @PostConstruct
    private void init() {
        metricRegistry.register("counter1", counter1);
    }

    @Produces @Green
    public Counter getCounter() {
        LongCounter counter = new LongCounter();
        metricRegistry.register("counter2", counter);
        counter.inc();
        return counter;
    }

    static class LongCounter implements Counter {

        LongCounter(long initialValue) {
            count = initialValue;
        }

        LongCounter() {
        }

        private long count;

        @Override
        public void inc() {
            count++;
        }

        @Override
        public void inc(long n) {
            count += n;
        }

        @Override
        public long getCount() {
            return count;
        }
    }
}
