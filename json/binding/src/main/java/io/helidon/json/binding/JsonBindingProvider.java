/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.json.binding;

import java.util.function.Supplier;

import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.service.registry.Service;

@Service.Singleton
@Weight(Weighted.DEFAULT_WEIGHT - 20)
class JsonBindingProvider implements Supplier<JsonBinding> {

    private final Supplier<JsonBindingConfig> bindingConfig;

    @Service.Inject
    JsonBindingProvider(Supplier<JsonBindingConfig> bindingConfig) {
        this.bindingConfig = bindingConfig;
    }

    @Override
    public JsonBinding get() {
        return JsonBinding.create(bindingConfig.get());
    }

}
