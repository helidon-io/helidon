/*
 * Copyright (c) 2018, 2022 Oracle and/or its affiliates.
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

package io.helidon.microprofile.faulttolerance;

import java.time.temporal.ChronoUnit;

import jakarta.enterprise.inject.spi.AnnotatedMethod;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.faulttolerance.exceptions.FaultToleranceDefinitionException;

class TimeoutAntn extends MethodAntn implements Timeout {

    /**
     * Constructor.
     *
     * @param annotatedMethod The annotated method.
     */
    TimeoutAntn(AnnotatedMethod<?> annotatedMethod) {
        super(annotatedMethod);
    }

    @Override
    public void validate() {
        if (value() < 0) {
            throw new FaultToleranceDefinitionException("Invalid @Timeout annotation, "
                                                        + "value must be >= 0");
        }
    }

    @Override
    public long value() {
        LookupResult<Timeout> lookupResult = lookupAnnotation(Timeout.class);
        final String override = getParamOverride("value", lookupResult.getType());
        return override != null ? Long.parseLong(override) : lookupResult.getAnnotation().value();
    }

    @Override
    public ChronoUnit unit() {
        LookupResult<Timeout> lookupResult = lookupAnnotation(Timeout.class);
        final String override = getParamOverride("unit", lookupResult.getType());
        return override != null ? ChronoUnit.valueOf(override) : lookupResult.getAnnotation().unit();
    }
}
