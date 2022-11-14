/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.pico.testsubjects.ext.tbox.impl;

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.common.Weight;
import io.helidon.common.Weighted;

import jakarta.inject.Provider;
import jakarta.inject.Singleton;

@Weight(Weighted.DEFAULT_WEIGHT + 100)
@Singleton
public class ASerialProviderImpl implements Provider<Serializable> {

    static final Integer testMe = 1;
    static {
        System.out.println("in static init");
    }

    private final AtomicInteger counter = new AtomicInteger();

    @Override
    public Serializable get() {
        return String.valueOf(counter.incrementAndGet());
    }

}
