/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
 *
 */
package io.helidon.webserver.cors;

import java.util.List;

import io.helidon.common.http.Http;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;


class CorsTestServices {

    static final CORSTestService SERVICE_1 = new CORSTestService("/cors1");
    static final CORSTestService SERVICE_2 = new CORSTestService("/cors2");
    static final CORSTestService SERVICE_3 = new CORSTestService("/cors3");
    static final CORSTestService SERVICE_4 = new CORSTestService("/cors4");

    static final List<CORSTestService> SERVICES = List.of(SERVICE_1, SERVICE_2, SERVICE_3, SERVICE_4);

    static class CORSTestService implements Service {

        private final String path;

        CORSTestService(String path) {
            this.path = path;
        }

        String path() {
            return path;
        }

        void ok(ServerRequest request, ServerResponse response) {
            response.status(Http.Status.OK_200.code());
            response.send();
        }

        @Override
        public void update(Routing.Rules rules) {
            rules
                    .delete(this::ok)
                    .put(this::ok)
            ;
        }
    }
}
