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

import javax.enterprise.context.Dependent;
import javax.enterprise.inject.Produces;

import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.annotation.Metric;

/**
 * Class ProducerBean.
 */
@Dependent
public class ProducerBean {

    @Produces
    @Metric(name = "counter1", absolute = true) final Counter counter1 = new LongCounter();

    @Produces
    @Metric(name = "counter2", absolute = true)
    public Counter getCounter() {
        LongCounter counter = new LongCounter();
        counter.inc();
        return counter;
    }

    class LongCounter implements Counter {

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
