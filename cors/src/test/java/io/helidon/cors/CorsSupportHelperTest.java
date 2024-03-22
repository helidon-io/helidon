/*
 * Copyright (c) 2020, 2024 Oracle and/or its affiliates.
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
package io.helidon.cors;

import java.util.Optional;

import io.helidon.common.testing.junit5.OptionalMatcher;
import io.helidon.common.uri.UriInfo;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.http.HeaderNames;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isEmptyString;

class CorsSupportHelperTest {

    private final static String YAML_PATH = "/configMapperTest.yaml";

    private static Config testConfig;
    private static CorsSupportHelper<TestCorsServerRequestAdapter, TestCorsServerResponseAdapter> secureCheckHelper;

    @BeforeAll
    public static void loadTestConfig() {
        testConfig = Config.builder()
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .addSource(ConfigSources.classpath(YAML_PATH))
                .build();
        secureCheckHelper =
                CorsSupportHelper.<TestCorsServerRequestAdapter, TestCorsServerResponseAdapter>builder()
                        .mappedConfig(testConfig.get("secure-check"))
                        .build();

    }

    @Test
    void testNormalize() {
        assertThat(CorsSupportHelper.normalize("something"), is("something"));
        assertThat(CorsSupportHelper.normalize("/something"), is("something"));
        assertThat(CorsSupportHelper.normalize("something/"), is("something"));
        assertThat(CorsSupportHelper.normalize("/something/"), is("something"));
        assertThat(CorsSupportHelper.normalize("/"), isEmptyString());
        assertThat(CorsSupportHelper.normalize(""), isEmptyString());
    }

    @Test
    void sameNodeDifferentPorts() {
        assertThat("Default different origin port",
                   CorsSupportHelper.requestType("http://ok.com",
                                                 uriInfo("ok.com", 8010, false)).isNormal(),
                   is(false));
        assertThat("Explicit different origin port",
                   CorsSupportHelper.requestType("http://ok.com:8080",
                                                 uriInfo("ok.com", false)).isNormal(),
                   is(false));
    }

    @Test
    void opaqueOrigin() {
        assertThat("Opaque origin",
                   CorsSupportHelper.requestType(CorsSupportHelper.OPAQUE_ORIGIN,
                                                 uriInfo("ok.com", 8010, false)).isNormal(),
                   is(true));
    }

    @Test
    void sameNodeSamePort() {
        assertThat("Default origin port",
                   CorsSupportHelper.requestType("http://ok.com",
                                                 uriInfo("ok.com", false)).isNormal(),
                   is(true));
        assertThat("Explicit origin port",
                   CorsSupportHelper.requestType("http://ok.com:80",
                                                 UriInfo.builder()
                                                                 .host("ok.com")
                                                                 .build()).isNormal(),
                   is(true));
    }

    @Test
    void differentNode() {
        assertThat("Different node, same (default) port",
                   CorsSupportHelper.requestType("http://bad.com",
                                                 uriInfo("ok.com", false)).isNormal(),
                   is(false));
        assertThat("Different node, same explicit port",
                   CorsSupportHelper.requestType("http://bad.com:80",
                                                 uriInfo("ok.com", false)).isNormal(),
                   is(false));

        assertThat("Different node, different explicit port",
                   CorsSupportHelper.requestType("http://bad.com:8080",
                                                 uriInfo("ok.com", false)).isNormal(),
                   is(false));
    }

    @Test
    void differentScheme() {
        assertThat("Same node, insecure origin, secure host",
                   CorsSupportHelper.requestType("http://foo.com",
                                                 uriInfo("foo.com", true)).isNormal(),
                   is(false));

        assertThat("Same node, secure origin, insecure host",
                   CorsSupportHelper.requestType("https://foo.com",
                                                 uriInfo("foo.com", false)).isNormal(),
                   is(false));

        assertThat("Different nodes, insecure origin, secure host",
                   CorsSupportHelper.requestType("http://foo.com",
                                                 uriInfo("other.com", true)).isNormal(),
                   is(false));

        assertThat("Different nodes, secure origin, insecure host",
                   CorsSupportHelper.requestType("https://foo.com",
                                                 uriInfo("other.com", false)).isNormal(),
                   is(false));
    }

    @Test
    void sameSecureScheme() {
        // Note that the real UriInfo instances from real requests will set the port according to whether the request is
        // secure or not.
        assertThat("Same node, secure origin, secure host",
                   CorsSupportHelper.requestType("https://foo.com",
                                                 uriInfo("foo.com", true)).isNormal(),
                   is(true));

        assertThat("Different node, secure origin, secure host",
                   CorsSupportHelper.requestType("https://foo.com",
                                                 uriInfo("other.com", true)).isNormal(),
                   is(false));

        assertThat("Same nodes, different ports, secure origin, secure host",
                   CorsSupportHelper.requestType("https://foo.com:1234",
                                                 uriInfo("foo.com",5678, true)).isNormal(),
                   is(false));

        assertThat("Same nodes, explicit origin port, secure origin, secure host",
                   CorsSupportHelper.requestType("https://foo.com:443",
                                                 uriInfo("foo.com", true)).isNormal(),
                   is(true));

        assertThat("Same nodes, explicit host port, secure origin, secure host",
                   CorsSupportHelper.requestType("https://foo.com",
                                                 uriInfo("foo.com", 443, true)).isNormal(),
                   is(true));
    }

    /*
    The CORS processing uses the "requested URI" which is trusted information that accounts for intermediary nodes between the
    original client and the server. The host for the requested URI is the actual server running the code (server.com in these
    tests). The Host header reflects the presence of a load balancer or other intermediary proxy immediately before the request
    reaches the server.
     */

    @Test
    void checkPlainRequestToPlainPath() {
        // Access the insecure path with an insecure host in the CORS config using an insecure request.
        // UriInfo contains the trusted information about the host.
        UriInfo uriInfo = UriInfo.builder()
                .scheme("http")
                .host("server.com")
                .path("/greet")
                .port(80)
                .build();
        WritableHeaders<?> headers = WritableHeaders.create();
        headers.add(HeaderNames.ORIGIN, "http://here.com")
                .add(HeaderNames.HOST, "someProxy.com")
                .add(HeaderNames.ACCESS_CONTROL_REQUEST_METHOD, "PUT");
        CorsRequestAdapter<TestCorsServerRequestAdapter> req = new TestCorsServerRequestAdapter("/greet",
                                                                                                uriInfo,
                                                                                                "OPTIONS",
                                                                                                headers);
        CorsResponseAdapter<TestCorsServerResponseAdapter> resp = new TestCorsServerResponseAdapter();

        Optional<TestCorsServerResponseAdapter> immediateResponse = secureCheckHelper.processRequest(req, resp);

        assertThat("Immediate response from insecure PUT", immediateResponse, OptionalMatcher.optionalPresent());
        assertThat("Immediate response status", immediateResponse.get().status(), is(Status.OK_200.code()));
    }

    @Test
    void checkSecureAccessToPlainPath() {
        WritableHeaders<?> headers = WritableHeaders.create();
        headers.add(HeaderNames.ORIGIN, "https://secure.com:443")
                .add(HeaderNames.HOST, "someProxy.com:443")
                .add(HeaderNames.ACCESS_CONTROL_REQUEST_METHOD, "PUT");
        UriInfo uriInfo = UriInfo.builder()
                .scheme("https")
                .host("server.com")
                .path("/greet")
                .port(443)
                .build();
        CorsRequestAdapter<TestCorsServerRequestAdapter> secureReq = new TestCorsServerRequestAdapter("/greet",
                                                                                                      uriInfo,
                                                                                                      "OPTIONS",
                                                                                                      headers);
        CorsResponseAdapter<TestCorsServerResponseAdapter> secureResp = new TestCorsServerResponseAdapter();

        Optional<TestCorsServerResponseAdapter> secureImmediateResponse = secureCheckHelper.processRequest(secureReq, secureResp);
        assertThat("Immediate response from secure PUT to unsecured path",
                   secureImmediateResponse,
                   OptionalMatcher.optionalPresent());
        assertThat("Immediate response status from secure PUT to unsecured path",
                   secureImmediateResponse.get().status(),
                   is(Status.FORBIDDEN_403.code()));
    }

    @Test
    void checkPlainAccessToSecurePath() {
        WritableHeaders<?> headers = WritableHeaders.create();
        headers.add(HeaderNames.ORIGIN, "http://here.com")
                .add(HeaderNames.HOST, "someProxy.com")
                .add(HeaderNames.ACCESS_CONTROL_REQUEST_METHOD, "PUT");
        UriInfo uriInfo = UriInfo.builder()
                .scheme("http")
                .host("server.com")
                .path("/secure-greet")
                .port(80)
                .build();
        CorsRequestAdapter<TestCorsServerRequestAdapter> secureReq = new TestCorsServerRequestAdapter("/secure-greet",
                                                                                                      uriInfo,
                                                                                                      "OPTIONS",
                                                                                                      headers);
        CorsResponseAdapter<TestCorsServerResponseAdapter> secureResp = new TestCorsServerResponseAdapter();

        Optional<TestCorsServerResponseAdapter> secureImmediateResponse = secureCheckHelper.processRequest(secureReq, secureResp);
        assertThat("Immediate response from secure PUT to unsecured path",
                   secureImmediateResponse,
                   OptionalMatcher.optionalPresent());
        assertThat("Immediate response status from secure PUT to unsecured path",
                   secureImmediateResponse.get().status(),
                   is(Status.FORBIDDEN_403.code()));
    }

    @Test
    void checkSecureAccessToSecurePath() {
        WritableHeaders<?> headers = WritableHeaders.create();
        headers.add(HeaderNames.ORIGIN, "https://secure.com:443")
                .add(HeaderNames.HOST, "someProxy.com:443")
                .add(HeaderNames.ACCESS_CONTROL_REQUEST_METHOD, "PUT");
        UriInfo uriInfo = UriInfo.builder()
                .scheme("https")
                .host("server.com")
                .path("/secure-greet")
                .port(443)
                .build();
        CorsRequestAdapter<TestCorsServerRequestAdapter> secureReq = new TestCorsServerRequestAdapter("/secure-greet",
                                                                                                      uriInfo,
                                                                                                      "OPTIONS",
                                                                                                      headers);
        CorsResponseAdapter<TestCorsServerResponseAdapter> secureResp = new TestCorsServerResponseAdapter();

        Optional<TestCorsServerResponseAdapter> secureImmediateResponse = secureCheckHelper.processRequest(secureReq, secureResp);
        assertThat("Immediate response from secure PUT to secured path",
                   secureImmediateResponse,
                   OptionalMatcher.optionalPresent());
        assertThat("Immediate response status from secure PUT to secured path",
                   secureImmediateResponse.get().status(),
                   is(Status.OK_200.code()));
    }

    private static UriInfo uriInfo(String host, boolean isSecure) {
        return UriInfo.builder()
                .host(host)
                .scheme(isSecure ? "https" : "http")
                .build();
    }
    private static UriInfo uriInfo(String host, int port, boolean isSecure) {
        return UriInfo.builder()
                .host(host)
                .port(port)
                .scheme(isSecure ? "https" : "http")
                .build();
    }
}
