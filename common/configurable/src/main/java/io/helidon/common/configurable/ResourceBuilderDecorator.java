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

package io.helidon.common.configurable;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.Optional;

import io.helidon.builder.api.Prototype;

class ResourceBuilderDecorator implements Prototype.BuilderDecorator<ResourceConfig.BuilderBase<?, ?>> {
    @Override
    public void decorate(ResourceConfig.BuilderBase<?, ?> target) {
        boolean useProxy = target.useProxy();
        if (!useProxy) {
            target.proxy(Optional.empty());
            return;
        }
        if (target.proxy().isPresent()) {
            return;
        }
        if (target.proxyHost().isPresent()) {
            String proxyHost = target.proxyHost().get();
            int port = target.proxyPort();
            Proxy proxy = new Proxy(Proxy.Type.HTTP,
                                    new InetSocketAddress(proxyHost, port));
            target.proxy(proxy);
        }
    }
}
