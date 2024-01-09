/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

package io.helidon.inject.tests.inject;

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.inject.service.Injection;

/**
 * Testing.
 */
@Weight(Weighted.DEFAULT_WEIGHT + 100)
@Injection.Service
public class ASerialProviderImpl implements Supplier<Serializable> {

    static {
        System.getLogger(ASerialProviderImpl.class.getName()).log(System.Logger.Level.DEBUG, "in static init");
    }

    private static final AtomicInteger COUNTER = new AtomicInteger();

    @Override
    public Serializable get() {
        return String.valueOf(COUNTER.incrementAndGet());
    }

}
