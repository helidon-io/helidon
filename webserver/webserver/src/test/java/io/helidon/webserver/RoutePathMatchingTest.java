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

package io.helidon.webserver;

import io.helidon.common.http.Http;
import io.helidon.webserver.utils.SocketHttpClient;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

class RoutePathMatchingTest extends BaseServerTest {

    @BeforeAll
    static void startServer() throws Exception {
        Routing routing = Routing.builder()
                .register("/public", rules -> rules.get("/{+path}", (req, res) -> res.send("weak-public-prefix")))
                .register("/admin", rules -> rules.get("/secret", (req, res) -> {
                    res.status(Http.Status.UNAUTHORIZED_401);
                    res.send("blocked-by-prefix-service");
                }))
                .get("/direct-admin/secret", (req, res) -> {
                    res.status(Http.Status.UNAUTHORIZED_401);
                    res.send("blocked-by-direct-handler");
                })
                .register("/direct-admin", rules -> rules.get("/secret",
                        (req, res) -> res.send("weak-prefix-service")))
                .register("/admin-extra", rules -> {
                    rules.get("/", (req, res) -> res.send("weak-root-handler"));
                    rules.get("/secret/{+rest}", (req, res) -> {
                        res.status(Http.Status.UNAUTHORIZED_401);
                        res.send("blocked-extra-path\n" + req.path().toString() + "\n" + req.path().toRawString());
                    });
                })
                .register("/nested", rules -> rules.get("/secret",
                        (req, res) -> res.send(req.path().toString() + "\n" + req.path().toRawString())))
                .register("/nested/base", rules -> rules.get("/secret",
                        (req, res) -> res.send(req.path().toString() + "\n" + req.path().toRawString())))
                .register("/nested-choice", rules -> rules.get("/secret",
                        (req, res) -> res.send("nested-choice\n" + req.path().toString()
                                                       + "\n" + req.path().toRawString())))
                .register("/nested-choice/base", rules -> rules.get("/secret",
                        (req, res) -> res.send("nested-choice-base\n" + req.path().toString()
                                                       + "\n" + req.path().toRawString())))
                .register("/nested-raw", rules -> rules.get("/secret",
                        (req, res) -> res.send(req.path().toString() + "\n" + req.path().toRawString())))
                .register("/outer/inner%3Bvalue", rules -> rules.get("/rest",
                        (req, res) -> res.send(req.path().toString() + "\n" + req.path().toRawString())))
                .register("/tenants/{tenant}", rules -> rules.get("/secret",
                        (req, res) -> res.send(req.path().absolute().param("tenant")
                                                       + "\n" + req.path().toString()
                                                       + "\n" + req.path().toRawString())))
                .get("/secret", (req, res) -> res.send("open-secret-reached"))
                .get("/admin/secret", (req, res) -> res.send("admin-secret-reached"))
                .get("/public/secret", (req, res) -> res.send("public-secret-reached"))
                .build();

        startServer(0, routing);
    }

    @Test
    void prefixedServiceMatchesMatrixParameterPath() throws Exception {
        assertBlocked("/admin/secret");
        assertBlocked("/admin;a=b/secret");
    }

    @Test
    void prefixedServiceMatchesLeadingDoubleSlashPath() throws Exception {
        assertBlocked("//admin/secret");
        assertBlocked("//admin/secret?x=1");
        assertBlocked("//admin;a=b/secret");
        assertBlocked("/////admin/secret");
    }

    @Test
    void prefixedServiceMatchesEncodedDotSegments() throws Exception {
        assertBlocked("/%2e%2e/admin/secret");
        assertBlocked("/%2E%2E/admin/secret");
        assertBlocked("/public/%2e%2e/admin/secret");
        assertBlocked("/public/%2E%2E/admin/secret");
        assertBlocked("/public/%2e%2e;a=b/admin/secret");
        assertBlocked("/%2f..%2fadmin/secret");
        assertBlocked("/%2F..%2Fadmin/secret");
        assertBlocked("/public%2f..%2fadmin/secret");
        assertBlocked("/public%2F..%2Fadmin/secret");
        assertBlocked("/%2fadmin/secret");
        assertBlocked("/%2Fadmin/secret");
        assertBlocked("/admin;a=b%2f..%2fpublic/secret");
    }

    @Test
    void prefixedServiceMatchesAbsoluteFormPath() throws Exception {
        int port = webServer().port();

        assertBlocked("http://127.0.0.1:" + port + "//admin/secret");
        assertBlocked("http://127.0.0.1:" + port + "//admin/secret?x=1");
        assertBlocked("http://127.0.0.1:" + port + "//admin;a=b/secret");
        assertBlocked("http://127.0.0.1:" + port + "///admin/secret");
    }

    @Test
    void prefixedServicePreservesRemainingMatrixParameters() throws Exception {
        String response = SocketHttpClient.sendAndReceive("/nested;a=b/secret;c=d",
                                                          Http.Method.GET,
                                                          null,
                                                          null,
                                                          webServer());

        assertOkEntity(response, "/secret;c=d\n/secret;c=d");

        response = SocketHttpClient.sendAndReceive("/nested%3Ba=b/secret%3Bc=d",
                                                   Http.Method.GET,
                                                   null,
                                                   null,
                                                   webServer());

        assertOkEntity(response, "/secret;c=d\n/secret%3Bc=d");

        response = SocketHttpClient.sendAndReceive("/nested%3ba=b/secret%3bc=d",
                                                   Http.Method.GET,
                                                   null,
                                                   null,
                                                   webServer());

        assertOkEntity(response, "/secret;c=d\n/secret%3bc=d");

        response = SocketHttpClient.sendAndReceive("/public/%2e%2e;a=b/nested/secret%3Bc=d",
                                                   Http.Method.GET,
                                                   null,
                                                   null,
                                                   webServer());

        assertOkEntity(response, "/secret;c=d\n/secret%3Bc=d");

        response = SocketHttpClient.sendAndReceive("/public%2f..%2fnested/secret%3Bc=d",
                                                   Http.Method.GET,
                                                   null,
                                                   null,
                                                   webServer());

        assertOkEntity(response, "/secret;c=d\n/secret%3Bc=d");

        response = SocketHttpClient.sendAndReceive("//nested/secret;c=d",
                                                   Http.Method.GET,
                                                   null,
                                                   null,
                                                   webServer());

        assertOkEntity(response, "/secret;c=d\n/secret;c=d");

        int port = webServer().port();
        response = SocketHttpClient.sendAndReceive("http://127.0.0.1:" + port + "//nested/secret;c=d",
                                                   Http.Method.GET,
                                                   null,
                                                   null,
                                                   webServer());

        assertOkEntity(response, "/secret;c=d\n/secret;c=d");

        response = SocketHttpClient.sendAndReceive("/nested%3Ba=b/base/secret%3Bc=d",
                                                   Http.Method.GET,
                                                   null,
                                                   null,
                                                   webServer());

        assertOkEntity(response, "/secret;c=d\n/secret%3Bc=d");
    }

    @Test
    void prefixedServiceUsesRawPathWhenMatrixParamContainsEncodedSlash() throws Exception {
        String response = SocketHttpClient.sendAndReceive("/nested-choice;a=b%2fbase/secret",
                                                          Http.Method.GET,
                                                          null,
                                                          null,
                                                          webServer());

        assertOkEntity(response, "nested-choice\n/secret\n/secret");
    }

    @Test
    void matrixParamWithEncodedSlashDoesNotReachNormalizedSiblingRoute() throws Exception {
        String response = SocketHttpClient.sendAndReceive("/admin;a=b%2f..%2fpublic%2fsecret",
                                                          Http.Method.GET,
                                                          null,
                                                          null,
                                                          webServer());

        assertThat(response, containsString("HTTP/1.1 404 Not Found"));
        assertThat(response, containsString("No handler found for path: /admin"));

        response = SocketHttpClient.sendAndReceive("/admin;v=%2f..;x=y%2fpublic/secret",
                                                   Http.Method.GET,
                                                   null,
                                                   null,
                                                   webServer());

        assertThat(response, containsString("HTTP/1.1 401 Unauthorized"));
        assertThat(response, containsString("blocked-by-prefix-service"));

        response = SocketHttpClient.sendAndReceive("/public%2f..;a=b%2fadmin/secret",
                                                   Http.Method.GET,
                                                   null,
                                                   null,
                                                   webServer());

        assertOkEntity(response, "open-secret-reached");
    }

    @Test
    void prefixedServicePreservesEncodedSlashRemainingPartAfterRawFallback() throws Exception {
        String response = SocketHttpClient.sendAndReceive("/public/%2e%2e;a=b/admin-extra/secret%2Fextra",
                                                          Http.Method.GET,
                                                          null,
                                                          null,
                                                          webServer());

        assertThat(response, containsString("HTTP/1.1 401 Unauthorized"));
        assertThat(SocketHttpClient.entityFromResponse(response, true),
                   is("blocked-extra-path\n/secret/extra\n/secret%2Fextra"));
    }

    @Test
    void directRouteMatchesRawMatrixParamFallbackBeforeLaterPrefixService() throws Exception {
        assertDirectBlocked("/public%2f..%2fdirect-admin;a=b/secret");
        assertDirectBlocked("/public%2f..%2fdirect-admin%3Ba=b/secret");
        assertDirectBlocked("/public/%2e%2e;a=b/direct-admin/secret");

        String response = SocketHttpClient.sendAndReceive("/public%2f..;a=b%2fdirect-admin/secret",
                                                          Http.Method.GET,
                                                          null,
                                                          null,
                                                          webServer());

        assertOkEntity(response, "open-secret-reached");
    }

    @Test
    void earlierPublicPrefixDoesNotClaimNormalizedMatrixParamAdminPath() throws Exception {
        assertBlocked("/public%2f..%2fadmin%3Ba=b/secret");
    }

    @Test
    void prefixedServiceDoesNotStripDecodedEncodedSemicolonData() throws Exception {
        String response = SocketHttpClient.sendAndReceive("/outer;a=b/inner%253Bvalue/rest",
                                                          Http.Method.GET,
                                                          null,
                                                          null,
                                                          webServer());

        assertOkEntity(response, "/rest\n/rest");
    }

    @Test
    void prefixedServicePreservesPrefixParams() throws Exception {
        String response = SocketHttpClient.sendAndReceive("/tenants/acme;a=b/secret",
                                                          Http.Method.GET,
                                                          null,
                                                          null,
                                                          webServer());

        assertOkEntity(response, "acme;a=b\n/secret\n/secret");

        response = SocketHttpClient.sendAndReceive("/public%2f..%2ftenants/acme/secret",
                                                   Http.Method.GET,
                                                   null,
                                                   null,
                                                   webServer());

        assertOkEntity(response, "acme\n/secret\n/secret");
    }

    @Test
    void encodedSemicolonIsNotDecodedTwice() throws Exception {
        String response = SocketHttpClient.sendAndReceive("/nested%253Ba=b/secret",
                                                          Http.Method.GET,
                                                          null,
                                                          null,
                                                          webServer());

        assertThat(response, containsString("HTTP/1.1 404 Not Found"));

        response = SocketHttpClient.sendAndReceive("/admin%253Ba=b/secret",
                                                   Http.Method.GET,
                                                   null,
                                                   null,
                                                   webServer());

        assertThat(response, containsString("HTTP/1.1 404 Not Found"));
    }

    @Test
    void prefixedServicePreservesNormalizedRawPath() throws Exception {
        String response = SocketHttpClient.sendAndReceive("/public/%2e%2e/nested-raw/secret",
                                                          Http.Method.GET,
                                                          null,
                                                          null,
                                                          webServer());

        assertOkEntity(response, "/secret\n/secret");

        response = SocketHttpClient.sendAndReceive("/public%2f..%2fnested-raw/secret",
                                                   Http.Method.GET,
                                                   null,
                                                   null,
                                                   webServer());

        assertOkEntity(response, "/secret\n/secret");
    }

    private static void assertBlocked(String path) throws Exception {
        String response = SocketHttpClient.sendAndReceive(path, Http.Method.GET, null, null, webServer());

        assertThat(response, containsString("HTTP/1.1 401 Unauthorized"));
        assertThat(response, containsString("blocked-by-prefix-service"));
    }

    private static void assertDirectBlocked(String path) throws Exception {
        String response = SocketHttpClient.sendAndReceive(path, Http.Method.GET, null, null, webServer());

        assertThat(response, containsString("HTTP/1.1 401 Unauthorized"));
        assertThat(response, containsString("blocked-by-direct-handler"));
    }

    private static void assertOkEntity(String response, String expectedEntity) {
        assertThat(response, containsString("HTTP/1.1 200 OK"));
        assertThat(SocketHttpClient.entityFromResponse(response, true), is(expectedEntity));
    }
}
