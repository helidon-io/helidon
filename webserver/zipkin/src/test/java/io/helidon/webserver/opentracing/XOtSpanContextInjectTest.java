/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.webserver.opentracing;

import java.util.TreeMap;

import org.hamcrest.core.Is;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;

/**
 * The xOtSpanContextInjectTest.
 */
public class XOtSpanContextInjectTest {

    @Test
    public void name() throws Exception {

        TreeMap<String, String> map = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER) {{
            put(OpentracingClientFilter.X_OT_SPAN_CONTEXT, "0000816c055dc421;0000816c055dc421;0000000000000000;sr");

            put(OpentracingClientFilter.X_B3_PARENT_SPAN_ID, "51b3b1a413dce011");
            put(OpentracingClientFilter.X_B3_SPAN_ID, "521c61ede905945f");
            put(OpentracingClientFilter.X_B3_TRACE_ID, "0000816c055dc421");
        }};

        OpentracingClientFilter.fixXOtSpanContext(map);

        assertThat(map.get(OpentracingClientFilter.X_OT_SPAN_CONTEXT), Is.is("0000816c055dc421;521c61ede905945f;51b3b1a413dce011;sr"));
    }
}
