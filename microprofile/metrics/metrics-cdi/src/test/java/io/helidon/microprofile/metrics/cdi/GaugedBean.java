/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microprofile.metrics.cdi;

import javax.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.metrics.MetricUnits;
import org.eclipse.microprofile.metrics.annotation.Gauge;

/**
 *
 */
@ApplicationScoped
public class GaugedBean {

    static final String LOCAL_INJECTABLE_GAUGE_NAME = "gaugeForInjectionTest";
    static final String INJECTABLE_GAUGE_NAME = "io.helidon.microprofile.metrics.cdi.GaugedBean." + LOCAL_INJECTABLE_GAUGE_NAME;
    static final String INJECTABLE_GAUGE_UNIT = MetricUnits.MINUTES;
    static final String TAGS = "tag1=valA,tag2=valB";

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

    @Gauge(unit = INJECTABLE_GAUGE_UNIT, name = LOCAL_INJECTABLE_GAUGE_NAME, tags = TAGS)
    public int fetch() {
        return measuredValue;
    }

}
