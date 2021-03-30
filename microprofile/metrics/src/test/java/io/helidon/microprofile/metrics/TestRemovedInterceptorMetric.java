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
 *
 */
package io.helidon.microprofile.metrics;

import io.helidon.microprofile.tests.junit5.AddBean;
import io.helidon.microprofile.tests.junit5.HelidonTest;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import static org.junit.jupiter.api.Assertions.fail;

@HelidonTest
public class TestRemovedInterceptorMetric extends MetricsBaseTest {

    @Inject
    private MetricRegistry registry;

    @Test
    public void ensureExceptionThrown() {
        CountedBean bean = newBean(CountedBean.class);

        Counter counter = registry.counter(CountedBean.DOOMED_COUNTER);
        bean.method4();
        assertThat(counter.getCount(), is(1L));
        assertThat("Could not remove bean during test", registry.remove(CountedBean.DOOMED_COUNTER), is(true));
        try {
            bean.method4();
        } catch (RuntimeException cause) {
            assertThat(cause, is(instanceOf(IllegalStateException.class)));
            // Make sure that the simpleTimer hasn't been called
            assertThat("Counted count is incorrect", counter.getCount(), is(1L));
            return;
        }
        fail("Use of removed bean from interceptor was incorrectly permitted");
    }
}
