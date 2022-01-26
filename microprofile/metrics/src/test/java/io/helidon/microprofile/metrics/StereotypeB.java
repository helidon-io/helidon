/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.enterprise.inject.Stereotype;
import org.eclipse.microprofile.metrics.annotation.Gauge;
import org.eclipse.microprofile.metrics.annotation.SimplyTimed;

@Stereotype
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
@Gauge(name = StereotypeB.GAUGE_NAME, unit = "MPH", absolute = true)
@SimplyTimed(name = StereotypeB.SIMPLE_TIMER_NAME, absolute = true)

@interface StereotypeB {

    String GAUGE_NAME = "speedB";
    String SIMPLE_TIMER_NAME = "simplyTimedB";

}
