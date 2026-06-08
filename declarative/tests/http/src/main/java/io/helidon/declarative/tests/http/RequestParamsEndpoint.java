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

package io.helidon.declarative.tests.http;

import java.util.List;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.http.Http;
import io.helidon.service.registry.Service;
import io.helidon.webserver.http.RestServer;

@Http.Path("/request-params")
@RestServer.Listener("@default")
@RestServer.Endpoint
@Service.Singleton
class RequestParamsEndpoint {

    @Http.POST
    @Http.Path("/grouped")
    @Http.Consumes(MediaTypes.TEXT_PLAIN_VALUE)
    @Http.Produces(MediaTypes.TEXT_PLAIN_VALUE)
    String grouped(@Http.RequestParams CustomRequestParams params) {
        return params.customHeader()
                + "|" + params.queryParams().orElseGet(List::of)
                + "|" + params.cookie()
                + "|" + params.entity();
    }

    @Http.GET
    @Http.Path("/path/{value}")
    @Http.Produces(MediaTypes.TEXT_PLAIN_VALUE)
    String path(@Http.RequestParams CustomPathParams params) {
        return params.value();
    }

    @Http.POST
    @Http.Path("/form")
    @Http.Consumes(MediaTypes.APPLICATION_FORM_URLENCODED_VALUE)
    @Http.Produces(MediaTypes.TEXT_PLAIN_VALUE)
    String form(@Http.FormParam("first") String first,
                @Http.FormParam("second") String second) {
        return first + "|" + second;
    }

    @Http.POST
    @Http.Path("/form-list")
    @Http.Consumes(MediaTypes.APPLICATION_FORM_URLENCODED_VALUE)
    @Http.Produces(MediaTypes.TEXT_PLAIN_VALUE)
    String formList(@Http.FormParam("tag") List<String> tags) {
        return tags.toString();
    }

    @Http.POST
    @Http.Path("/grouped-form")
    @Http.Consumes(MediaTypes.APPLICATION_FORM_URLENCODED_VALUE)
    @Http.Produces(MediaTypes.TEXT_PLAIN_VALUE)
    String groupedForm(@Http.RequestParams CustomFormParams params) {
        return params.first() + "|" + params.second();
    }

    @Http.GET
    @Http.Path("/cookies")
    @Http.Produces(MediaTypes.TEXT_PLAIN_VALUE)
    String cookies(@Http.CookieParam("first") String first,
                   @Http.CookieParam("second") String second) {
        return first + "|" + second;
    }

    @Http.GET
    @Http.Path("/cookies-static")
    @Http.Produces(MediaTypes.TEXT_PLAIN_VALUE)
    String cookiesStatic(@Http.CookieParam("static") String staticCookie,
                         @Http.CookieParam("first") String first,
                         @Http.CookieParam("second") String second) {
        return staticCookie + "|" + first + "|" + second;
    }

    @Http.GET
    @Http.Path("/cookies-list")
    @Http.Produces(MediaTypes.TEXT_PLAIN_VALUE)
    String cookiesList(@Http.CookieParam("tag") List<String> tags) {
        return tags.toString();
    }
}
