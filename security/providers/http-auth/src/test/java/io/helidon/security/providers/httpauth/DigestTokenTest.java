/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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

package io.helidon.security.providers.httpauth;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Unit test for {@link DigestToken}.
 */
public class DigestTokenTest {
    @Test
    public void rfcExampleTest() {
        String header = "username=\"Mufasa\", realm=\"testrealm@host.com\", nonce=\"dcd98b7102dd2f0e8b11d0f600bfb0c093\", "
                + "uri=\"/dir/index.html\", qop=auth, nc=00000001, cnonce=\"0a4f113b\", "
                + "response=\"6629fae49393a05397450978507c4ef1\", opaque=\"5ccc069c403ebaf9f0171e9517f40e41\"";

        String method = "GET";
        DigestToken dt = DigestToken.fromAuthorizationHeader(header, method);

        char[] password = "Circle Of Life".toCharArray();

        assertThat(dt.getUsername(), is("Mufasa"));
        assertThat(dt.getRealm(), is("testrealm@host.com"));
        assertThat(dt.getUri(), is("/dir/index.html"));
        assertThat(dt.getAlgorithm(), is(HttpDigest.Algorithm.MD5));
        assertThat(dt.getResponse(), is("6629fae49393a05397450978507c4ef1"));
        assertThat(dt.getOpaque(), is("5ccc069c403ebaf9f0171e9517f40e41"));
        assertThat(dt.getQop(), is(HttpDigest.Qop.AUTH));
        assertThat(dt.getNc(), is("00000001"));
        assertThat(dt.getCnonce(), is("0a4f113b"));
        assertThat(dt.getMethod(), is(method));
        assertThat(dt.getNonce(), is("dcd98b7102dd2f0e8b11d0f600bfb0c093"));

        assertThat(dt.digest(password), is("6629fae49393a05397450978507c4ef1"));

        TestUser user = new TestUser("Mufasa", password);

        assertThat(dt.validateLogin(user), is(true));
    }

    @Test
    public void chromeBrowserTest() {
        // this is an actual request from a browser (Chrome) generated for a nonce. The timestamp is obviously invalid,
        // fortunatelly the DigestToken does not validate timestamp...
        String header = "username=\"jack\", realm=\"mic\", nonce=\"ADm0cNeFcVZBE4el5LHrXD1VGvw3f7XgFsQEk0sLa2A=\", "
                + "uri=\"/digest\", algorithm=MD5, response=\"943217664b97f16ddbde11c44f0ee980\", "
                + "opaque=\"y52cjfKNa+X2UC05MXnlT+5icnkunfsFBpY3n9E0k+M=\", qop=auth, nc=00000001, cnonce=\"4f7353deb0f2d452\"";

        String method = "GET";

        // service password
        DigestToken dt = DigestToken.fromAuthorizationHeader(header, method);

        assertThat(dt.getUsername(), is("jack"));
        assertThat(dt.getRealm(), is("mic"));
        assertThat(dt.getUri(), is("/digest"));
        assertThat(dt.getAlgorithm(), is(HttpDigest.Algorithm.MD5));
        assertThat(dt.getResponse(), is("943217664b97f16ddbde11c44f0ee980"));
        assertThat(dt.getOpaque(), is("y52cjfKNa+X2UC05MXnlT+5icnkunfsFBpY3n9E0k+M="));
        assertThat(dt.getQop(), is(HttpDigest.Qop.AUTH));
        assertThat(dt.getNc(), is("00000001"));
        assertThat(dt.getCnonce(), is("4f7353deb0f2d452"));
        assertThat(dt.getMethod(), is(method));
        assertThat(dt.getNonce(), is("ADm0cNeFcVZBE4el5LHrXD1VGvw3f7XgFsQEk0sLa2A="));

        // user's password
        char[] goodPassword = "kleslo".toCharArray();
        TestUser goodUser = new TestUser("jack", goodPassword);

        assertThat(dt.digest(goodPassword), is("943217664b97f16ddbde11c44f0ee980"));
        assertThat(dt.validateLogin(goodUser), is(true));

        TestUser badUser =new TestUser("jack", "other".toCharArray());
        assertThat(dt.validateLogin(badUser), is(false));
    }
}
