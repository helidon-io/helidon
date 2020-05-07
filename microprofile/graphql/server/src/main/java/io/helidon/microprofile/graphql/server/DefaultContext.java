/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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

package io.helidon.microprofile.graphql.server;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * A default implementation of {@link Context} to be supplied to {@link ExecutionContext}.
 * Any other implementations should extend this.
 */
public class DefaultContext
        implements Context {

    /**
     * Defines a {@link Map} keyed by a {@link Pair} of full method name and argument name with value the formatter.
     */
    private Map<Pair<String, String>, FormattingProvider> mapFormatting = new HashMap<>();

    /**
     * No-args constructor.
     */
    public DefaultContext() {
    }

    /**
     * {@inheritDoc}
     */
    public Object getFormatter(Method method, String argumentName) {
        return mapFormatting.get(generatePair(method, argumentName));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addFormatter(Method method, String argumentName, FormattingProvider formatter) {
        mapFormatting.put(generatePair(method, argumentName), formatter);
    }

    /**
     * Generate a {@link Pair} from the given method and argument.
     *
     * @param method       {@link Method} method
     * @param argumentName argument name
     * @return a new {@link Pair}
     */
    private Pair<String, String> generatePair(Method method, String argumentName) {
        return new Pair<>(method.getDeclaringClass() + "_" + method.getName(), argumentName);
    }
}
