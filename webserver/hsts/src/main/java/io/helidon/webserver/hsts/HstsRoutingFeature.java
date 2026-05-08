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

package io.helidon.webserver.hsts;

import io.helidon.common.Weighted;
import io.helidon.http.Header;
import io.helidon.webserver.http.HttpFeature;
import io.helidon.webserver.http.HttpRouting;

class HstsRoutingFeature implements HttpFeature, Weighted {
    private final Header header;
    private final double weight;

    HstsRoutingFeature(HstsFeatureConfig config) {
        this.header = HstsHeader.create(config);
        this.weight = config.weight();
    }

    @Override
    public void setup(HttpRouting.Builder routing) {
        routing.addFilter(new HstsFilter(header));
    }

    @Override
    public double weight() {
        return weight;
    }

    @Override
    public String toString() {
        return "HSTS HTTP Feature";
    }
}
