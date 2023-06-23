/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.builder.test.testsubjects;

import java.util.ArrayList;
import java.util.List;

import io.helidon.common.Builder;

/**
 * Here for testing.
 */
@Deprecated
@SuppressWarnings("unchecked")
public class GeneralInterceptor {
    private static final List<Builder> INTERCEPT_CALLS = new ArrayList<>();

    /**
     * Gets all interceptor calls ever made.
     *
     * @return all interceptor calls
     */
    public static List<Builder> getInterceptCalls() {
        return INTERCEPT_CALLS;
    }

    /**
     * Generic interceptor.
     *
     * @param builder generic builder
     * @return the builder
     */
    public Object intercept(Builder builder) {
        INTERCEPT_CALLS.add(builder);
        return builder;
    }

}
