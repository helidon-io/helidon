/*
 * Copyright (c) 2021, 2023 Oracle and/or its affiliates.
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
package io.helidon.microprofile.metrics;

import org.eclipse.microprofile.metrics.annotation.Counted;

public class CountedConstructorTestBean {

    static final String CONSTRUCTOR_COUNTER = "ctorCounted";

    private int count = 0;

    @Counted(name = CONSTRUCTOR_COUNTER)
    public CountedConstructorTestBean() {

    }

    public void inc() {
        count++;
    }
}
