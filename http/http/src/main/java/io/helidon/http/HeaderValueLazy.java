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

package io.helidon.http;

import java.util.ArrayList;
import java.util.List;

import io.helidon.common.buffers.LazyString;

class HeaderValueLazy extends HeaderWritableValueBase {
    private final LazyString value;
    private List<String> values;

    HeaderValueLazy(HeaderName name, boolean changing, boolean sensitive, LazyString value) {
        super(name, changing, sensitive, null);

        this.value = value;
    }

    @Override
    public HeaderWriteable addValue(String value) {
        if (values == null) {
            values = new ArrayList<>(2);
            values.add(this.value.stripOws());
        }
        values.add(value);
        return this;
    }

    @Override
    public String get() {
        return value.stripOws();
    }

    @Override
    public List<String> allValues() {
        if (values == null) {
            values = new ArrayList<>(2);
            values.add(value.stripOws());
        }
        return values;
    }

    @Override
    public int valueCount() {
        if (values == null) {
            return 1;
        }

        return values.size();
    }
}
