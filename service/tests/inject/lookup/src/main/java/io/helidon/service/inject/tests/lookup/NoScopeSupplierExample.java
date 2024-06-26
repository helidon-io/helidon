/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.service.inject.tests.lookup;

import java.util.function.Supplier;

import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.service.inject.api.Injection;

@Injection.Instance
@Weight(Weighted.DEFAULT_WEIGHT + 1) // higher than other no-scope, lower than singleton supplier
class NoScopeSupplierExample implements Supplier<ContractNoScopeNoIpProvider> {

    @Override
    public ContractNoScopeNoIpProvider get() {
        return new First();
    }

    static class First implements ContractNoScopeNoIpProvider {
    }
}
