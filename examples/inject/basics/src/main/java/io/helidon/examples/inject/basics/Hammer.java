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

package io.helidon.examples.inject.basics;

import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.inject.service.Injection;

/**
 * By adding the {@link io.helidon.inject.service.Injection.Singleton} annotation results in Hammer becoming a service. Services can be looked up
 * programmatically or declaratively injected via {@link io.helidon.inject.service.Injection.Inject}.
 * <p>
 * Here {@link Weight} is used that is higher than the default, making it more preferred in weighted rankings.
 */
@Injection.Singleton
@Weight(Weighted.DEFAULT_WEIGHT + 1)
class Hammer implements Tool {

    @Override
    public String name() {
        return "Hammer";
    }

    @Override
    public String toString() {
        return name();
    }

}
