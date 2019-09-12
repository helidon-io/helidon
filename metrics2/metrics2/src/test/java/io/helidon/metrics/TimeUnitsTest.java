/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.metrics;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Class TimeUnitsTest.
 */
public class TimeUnitsTest {

    @Test
    public void testBigDecimalNaNConversion() {
        assertThat(MetricImpl.TimeUnits.timeConverter(TimeUnit.NANOSECONDS).apply(Double.NaN),
                is(String.valueOf(Double.NaN)));
        assertThat(MetricImpl.TimeUnits.timeConverter(TimeUnit.MICROSECONDS).apply(Double.NaN),
                is(String.valueOf(Double.NaN)));
        assertThat(MetricImpl.TimeUnits.timeConverter(TimeUnit.MILLISECONDS).apply(Double.NaN),
                is(String.valueOf(Double.NaN)));
        assertThat(MetricImpl.TimeUnits.timeConverter(TimeUnit.SECONDS).apply(Double.NaN),
                is(String.valueOf(Double.NaN)));
        assertThat(MetricImpl.TimeUnits.timeConverter(TimeUnit.MINUTES).apply(Double.NaN),
                is(String.valueOf(Double.NaN)));
    }

    @Test
    public void testBigDecimalConversion() {
        assertThat(MetricImpl.TimeUnits.timeConverter(TimeUnit.NANOSECONDS).apply(1000000000L),
                is(String.valueOf(1.0d)));
        assertThat(MetricImpl.TimeUnits.timeConverter(TimeUnit.MICROSECONDS).apply(1000000),
                is(String.valueOf(1.0d)));
        assertThat(MetricImpl.TimeUnits.timeConverter(TimeUnit.MILLISECONDS).apply(1000),
                is(String.valueOf(1.0d)));
        assertThat(MetricImpl.TimeUnits.timeConverter(TimeUnit.SECONDS).apply(1),
                is(String.valueOf(1)));
        assertThat(MetricImpl.TimeUnits.timeConverter(TimeUnit.MINUTES).apply(1),
                is(String.valueOf(60)));
    }
}
