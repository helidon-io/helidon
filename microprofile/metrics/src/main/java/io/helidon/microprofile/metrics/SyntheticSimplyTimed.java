/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

import io.helidon.metrics.Registry;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.annotation.SimplyTimed;

import javax.enterprise.util.AnnotationLiteral;
import java.lang.annotation.Annotation;

/**
 * Local, private implementation of {@link SimplyTimed} that we can add dynamically to JAX-RS methods (GET, PUT, etc.)
 * to support the MicroProfile REST.request inferred metric.
 */
class SyntheticSimplyTimed extends AnnotationLiteral<SimplyTimed> implements SimplyTimed, SyntheticMetric {

    private static String UNIT_DEFAULT = getSimplyTimedDefaultValue("unit", String.class);
    private static boolean REUSABLE_DEFAULT = getSimplyTimedDefaultValue("reusable", Boolean.class);

    private static <T> T getSimplyTimedDefaultValue(String methodName, Class<? extends T> type) {
        try {
            return type.cast(SimplyTimed.class.getMethod(methodName).getDefaultValue());
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    private final String[] tags = new String[2];

    SyntheticSimplyTimed(String className, String methodName) {
        tags[0] = "class=" + className;
        tags[1] = "method=" + methodName;
    }

    @Override
    public String name() {
        return "REST.request";
    }

    @Override
    public String[] tags() {
        return tags;
    }

    @Override
    public boolean absolute() {
        return true;
    }

    @Override
    public String displayName() {
        return "Total Requests and Response Time";
    }

    @Override
    public String description() {
        return "The number of invocations and total response time of this RESTful resource method since the start of the server.";
    }

    @Override
    public String unit() {
        return UNIT_DEFAULT;
    }

    @Override
    public boolean reusable() {
        return REUSABLE_DEFAULT;
    }

    @Override
    public Class<? extends Annotation> annotationType() {
        return SimplyTimed.class;
    }

    @Override
    public MetricRegistry.Type registryType() {
        return MetricRegistry.Type.BASE;
    }
}
