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

import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.xml.ws.api.server.WSEndpoint;
import com.sun.xml.ws.transport.http.HttpAdapterList;

class HelidonAdapterList extends HttpAdapterList<HelidonAdapter> {

    private static final Logger LOGGER = Logger.getLogger(HelidonAdapterList.class.getName());

    @Override
    public HelidonAdapter createAdapter(String name, String urlPattern, WSEndpoint<?> endpoint) {
        com.sun.xml.ws.api.server.Module module = endpoint.getContainer().getSPI(com.sun.xml.ws.api.server.Module.class);
        String ctx = ((HelidonModule) module).getContextPath();
        return super.createAdapter(name, ctx + urlPattern, endpoint);
    }

    @Override
    protected HelidonAdapter createHttpAdapter(String name, String urlPattern, WSEndpoint<?> endpoint) {
        HelidonAdapter ha = new HelidonAdapter(name, endpoint, this, urlPattern);
        // registers adapter with the container
        com.sun.xml.ws.api.server.Module module = endpoint.getContainer().getSPI(com.sun.xml.ws.api.server.Module.class);
        if (module != null) {
            module.getBoundEndpoints().add(ha);
        } else {
            LOGGER.log(Level.WARNING, "Container {0} doesn''t support {1}",
                    new Object[]{endpoint.getContainer(), Module.class});
        }
        return ha;
    }

}
