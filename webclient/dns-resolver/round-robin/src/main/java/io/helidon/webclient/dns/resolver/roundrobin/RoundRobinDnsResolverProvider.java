/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.webclient.dns.resolver.roundrobin;

import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.webclient.spi.DnsResolver;
import io.helidon.webclient.spi.DnsResolverProvider;

/**
 * Provider of the {@link RoundRobinDnsResolver} instance.
 */
@Weight(Weighted.DEFAULT_WEIGHT + 50)
public final class RoundRobinDnsResolverProvider implements DnsResolverProvider {

    /**
     * Create new instance of the {@link RoundRobinDnsResolverProvider}.
     * This should be used only for purposes of SPI.
     */
    public RoundRobinDnsResolverProvider() {
    }

    @Override
    public String resolverName() {
        return "round-robin";
    }

    @Override
    public DnsResolver createDnsResolver() {
        return RoundRobinDnsResolver.create();
    }
}
