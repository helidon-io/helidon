/*
 * Copyright (c) 2023, 2026 Oracle and/or its affiliates.
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

package io.helidon.webserver.cors;

import io.helidon.common.Weighted;
import io.helidon.webserver.http.HttpFeature;
import io.helidon.webserver.http.HttpRouting;

class CorsHttpFeature implements HttpFeature, Weighted {
    private final CorsConfig config;
    private final String socketName;

    CorsHttpFeature(CorsConfig config, String socketName) {
        this.config = config;
        this.socketName = socketName;
    }

    @Override
    public void setup(HttpRouting.Builder routing) {
        routing.addFilter(new CorsHttpFilter(CorsValidator.create(config, socketName), socketName));
    }

    @Override
    public double weight() {
        return config.weight();
    }

    @Override
    public String toString() {
        return "CORS HTTP Feature for " + socketName;
    }
}
