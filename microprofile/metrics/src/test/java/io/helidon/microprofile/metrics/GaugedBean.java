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

import javax.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.metrics.MetricUnits;
import org.eclipse.microprofile.metrics.annotation.Gauge;

/**
 * Class GaugedBean.
 */
@ApplicationScoped
public class GaugedBean {

    static final String LOCAL_INJECTABLE_GAUGE_NAME = "gaugeForInjectionTest";
    static final String INJECTABLE_GAUGE_NAME = "io.helidon.microprofile.metrics.GaugedBean." + LOCAL_INJECTABLE_GAUGE_NAME;
    static final String INJECTABLE_GAUGE_UNIT = MetricUnits.MINUTES;

    private int measuredValue = 1;

    @Gauge(unit = MetricUnits.NONE)
    public int reportValue() {
        return measuredValue;
    }

    public void setValue(int value) {
        measuredValue = value;
    }

    @Gauge(unit = MetricUnits.NONE, name = "retrieveValue")
    public int retrieve() {
        return measuredValue;
    }

    @Gauge(unit = INJECTABLE_GAUGE_UNIT, name = LOCAL_INJECTABLE_GAUGE_NAME)
    public int fetch() {
        return measuredValue;
    }

    @Gauge(unit = MetricUnits.HOURS)
    public MyValue getMyValue() {
        return new MyValue(measuredValue);
    }

    public static class MyValue extends Number {

        private Double value;

        public MyValue(double value) {
            this.value = Double.valueOf(value);
        }

        @Override
        public int intValue() {
            return value.intValue();
        }

        @Override
        public long longValue() {
            return value.longValue();
        }

        @Override
        public float floatValue() {
            return value.floatValue();
        }

        @Override
        public double doubleValue() {
            return value.doubleValue();
        }
    }

}
