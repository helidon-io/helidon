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

package io.helidon.microprofile.cdi;

import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.spi.HelidonStartupProvider;

/**
 * {@link java.util.ServiceLoader} implementation of a Helidon startup provider.
 */
@Weight(Weighted.DEFAULT_WEIGHT + 100) // must have higher than default, to start CDI and not Helidon Injection
public class CdiStartupProvider implements HelidonStartupProvider {
    /**
     * Default constructor required by {@link java.util.ServiceLoader}.
     *
     * @deprecated please do not use directly
     */
    @Deprecated
    public CdiStartupProvider() {
    }

    @Override
    public void start(String[] arguments) {
        Main.main(arguments);
    }
}
