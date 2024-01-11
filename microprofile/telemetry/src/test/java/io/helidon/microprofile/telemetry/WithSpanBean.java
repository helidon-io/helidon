/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
package io.helidon.microprofile.telemetry;

import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
class WithSpanBean {

    static final String TEST_SPAN_NAME_SCALAR = "withAttrsScalar";
    static final String TEST_SPAN_NAME_OBJECT = "withAttrsObject";
    static final String STRING_ATTR_NAME = "arg0";
    static final String LONG_ATTR_NAME = "myLong";

    @WithSpan(TEST_SPAN_NAME_SCALAR)
    void runWithAttrsScalar(@SpanAttribute String aString,
                            @SpanAttribute(LONG_ATTR_NAME) long aLong) {

    }

    @WithSpan(TEST_SPAN_NAME_OBJECT)
    void runWithAttrsObject(@SpanAttribute String bString,
                            @SpanAttribute(LONG_ATTR_NAME) Long bLong) {

    }
}
