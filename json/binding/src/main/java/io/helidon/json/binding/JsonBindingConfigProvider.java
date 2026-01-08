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

import java.util.List;
import java.util.function.Supplier;

import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.service.registry.Service;

@Service.Singleton
@Weight(Weighted.DEFAULT_WEIGHT - 20)
class JsonBindingConfigProvider implements Supplier<JsonBindingConfig> {
    /*
    Load everything from the service registry, as there is no interaction here
     */
    private final Supplier<List<JsonSerializer<?>>> serializers;
    private final Supplier<List<JsonDeserializer<?>>> deserializers;
    private final Supplier<List<JsonBindingFactory<?>>> factories;

    @Service.Inject
    JsonBindingConfigProvider(Supplier<List<JsonSerializer<?>>> serializers,
                              Supplier<List<JsonDeserializer<?>>> deserializers,
                              Supplier<List<JsonBindingFactory<?>>> factories) {

        this.serializers = serializers;
        this.deserializers = deserializers;
        this.factories = factories;
    }

    @Override
    public JsonBindingConfig get() {
        return JsonBinding.builder()
                .serializersDiscoverServices(false)
                .deserializersDiscoverServices(false)
                .bindingFactoriesDiscoverServices(false)
                .serializers(serializers.get())
                .deserializers(deserializers.get())
                .bindingFactories(factories.get())
                .buildPrototype();
    }

}
