/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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
package io.helidon.logging.log4j;

import java.util.Map;

import io.helidon.common.context.spi.DataPropagationProvider;

import org.apache.logging.log4j.ThreadContext;

/**
 * This is propagator of Log4j MDC values between different threads.
 * This class is loaded and used via SPI.
 */
public class Log4jMdcPropagator implements DataPropagationProvider<Map<String, String>> {

    @Override
    public Map<String, String> data() {
        return ThreadContext.getContext();
    }

    @Override
    public void propagateData(Map<String, String> data) {
        ThreadContext.putAll(data);
    }

    @Override
    public void clearData(Map<String, String> data) {
        ThreadContext.clearAll();
    }
}
