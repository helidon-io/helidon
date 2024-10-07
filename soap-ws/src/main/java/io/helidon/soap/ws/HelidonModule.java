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

package io.helidon.soap.ws;

import java.util.ArrayList;
import java.util.List;

import com.sun.xml.ws.api.server.BoundEndpoint;
import com.sun.xml.ws.api.server.WebModule;

class HelidonModule extends WebModule {

    private final List<BoundEndpoint> enpoints = new ArrayList<>();
    private final String ctx;

    HelidonModule(String context) {
        this.ctx = context;
    }

    @Override
    public String getContextPath() {
        return ctx;
    }

    @Override
    public List<BoundEndpoint> getBoundEndpoints() {
        return enpoints;
    }

}
