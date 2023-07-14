/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.inject.tests.inject.stacking;

import java.util.Optional;

import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.inject.api.RunLevel;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@Weight(Weighted.DEFAULT_WEIGHT + 1)
@RunLevel(1)
@Named("InterceptedImpl")
public class CommonContractImpl implements CommonContract {

    private final CommonContract inner;

    @Inject
    public CommonContractImpl(Optional<CommonContract> inner) {
        this.inner = inner.orElse(null);
    }

    @Override
    public CommonContract getInner() {
        return inner;
    }

    @Override
    public String sayHello(String arg) {
        return getClass().getSimpleName() + ":" + (inner != null ? inner.sayHello(arg) : arg);
    }

}
