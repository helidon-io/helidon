/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.db.metrics;

import java.util.function.BiFunction;

import org.eclipse.microprofile.metrics.Metadata;

/**
 * TODO javadoc.
 */
public abstract class DbMetricBuilder<T extends DbMetricBuilder<T>> {
    private Metadata meta;
    private BiFunction<String, String, String> namedFormat;
    private boolean measureErrors = false;
    private boolean measureSuccess = true;


    public T name(String metricName) {
        namedFormat = (s, s2) -> metricName;
        return me();
    }

    public T metadata(Metadata meta) {
        this.meta = meta;
        return me();
    }

    public T nameFormat(String format) {
        return nameFormat((name, statement) -> String.format(format, name));
    }

    public T nameFormat(BiFunction<String, String, String> function) {
        this.namedFormat = function;
        return me();
    }

    public T measureErrors(boolean shouldWe) {
        this.measureErrors = shouldWe;
        return me();
    }

    public T measureSuccess(boolean shouldWe) {
        this.measureSuccess = shouldWe;
        return me();
    }

    @SuppressWarnings("unchecked")
    protected T me() {
        return (T) this;
    }

    protected Metadata meta() {
        return meta;
    }

    protected BiFunction<String, String, String> namedFormat() {
        return namedFormat;
    }

    protected boolean measureErrors() {
        return measureErrors;
    }

    protected boolean measureSuccess() {
        return measureSuccess;
    }
}
