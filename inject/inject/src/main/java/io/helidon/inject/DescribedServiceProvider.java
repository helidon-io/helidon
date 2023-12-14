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

package io.helidon.inject;

import io.helidon.inject.service.ServiceInfo;

/**
 * A service provider based on a descriptor.
 * The descriptor will be used to obtain all values inherited from {@link io.helidon.inject.service.ServiceInfo}.
 *
 * @param <T> type of the provided service
 */
public abstract class DescribedServiceProvider<T> implements ServiceProvider<T> {
    private final ServiceInfo serviceInfo;

    /**
     * Creates a new instance with the delegate descriptor.
     *
     * @param serviceInfo descriptor to delegate to
     */
    protected DescribedServiceProvider(ServiceInfo serviceInfo) {
        this.serviceInfo = serviceInfo;
    }

    @Override
    public ServiceInfo serviceInfo() {
        return serviceInfo;
    }
}
