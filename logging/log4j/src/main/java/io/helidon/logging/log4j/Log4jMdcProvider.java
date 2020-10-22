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
 */
package io.helidon.logging.log4j;

import io.helidon.logging.spi.MdcProvider;

import org.apache.logging.log4j.ThreadContext;

/**
 * Provider for setting MDC values to the Log4j MDC support.
 */
public class Log4jMdcProvider implements MdcProvider {
    @Override
    public void put(String key, Object value) {
        ThreadContext.put(key, String.valueOf(value));
    }

    @Override
    public void remove(String key) {
        ThreadContext.remove(key);
    }

    @Override
    public void clear() {
        ThreadContext.clearAll();
    }

    @Override
    public String get(String key) {
        return ThreadContext.get(key);
    }
}
