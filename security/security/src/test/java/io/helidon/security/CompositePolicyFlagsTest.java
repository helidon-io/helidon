/*
 * Copyright (c) 2018, 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.helidon.common.CollectionsHelper;
import io.helidon.security.SecurityResponse.SecurityStatus;
import io.helidon.security.providers.PathBasedProvider;
import io.helidon.security.providers.ResourceBasedProvider;
import io.helidon.security.spi.AuthenticationProvider;
import io.helidon.security.spi.AuthorizationProvider;
import io.helidon.security.spi.ProviderSelectionPolicy;
import io.helidon.security.spi.SecurityProvider;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static io.helidon.security.SecurityResponse.SecurityStatus.FAILURE;
import static io.helidon.security.SecurityResponse.SecurityStatus.SUCCESS;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link CompositeOutboundProvider}.
 */
public class CompositePolicyFlagsTest {
    private static final PathBasedProvider PATH_BASED_PROVIDER = new PathBasedProvider();
    private static final ResourceBasedProvider RESOURCE_BASED_PROVIDER = new ResourceBasedProvider();

    private void testIt(TestConfig conf) {
        ProviderSelectionPolicy psp = getPsp(conf.firstFlag, conf.secondFlag);
        AuthenticationProvider atn = psp.selectProvider(AuthenticationProvider.class).get();
        AuthorizationProvider atz = psp.selectProvider(AuthorizationProvider.class).get();

        SecurityResponse res = SecurityResponse.get(atn.authenticate(request("/jack", "jack")));
        assertThat(res.status(), is(conf.okOk));
        res = SecurityResponse.get(atz.authorize(request("/atz/permit", "atz/permit")));
        assertThat(res.status(), is(conf.okOk));

        res = SecurityResponse.get(atn.authenticate(request("/jack", "abstain")));
        assertThat(res.status(), is(conf.okAbstain));
        res = SecurityResponse.get(atz.authorize(request("/atz/permit", "atz/abstain")));
        assertThat(res.status(), is(conf.okAbstain));

        res = SecurityResponse.get(atn.authenticate(request("/jack", "fail")));
        assertThat(res.status(), is(conf.okFail));
        res = SecurityResponse.get(atz.authorize(request("/atz/permit", "atz/fail")));
        assertThat(res.status(), is(conf.okFail));

        res = SecurityResponse.get(atn.authenticate(request("/abstain", "jack")));
        assertThat(res.status(), is(conf.abstainOk));
        res = SecurityResponse.get(atz.authorize(request("/atz/abstain", "atz/permit")));
        assertThat(res.status(), is(conf.abstainOk));

        res = SecurityResponse.get(atn.authenticate(request("/abstain", "abstain")));
        assertThat(res.status(), is(conf.abstainAbstain));
        res = SecurityResponse.get(atz.authorize(request("/atz/abstain", "atz/abstain")));
        assertThat(res.status(), is(conf.abstainAbstain));

        res = SecurityResponse.get(atn.authenticate(request("/abstain", "fail")));
        assertThat(res.status(), is(conf.abstainFail));
        res = SecurityResponse.get(atz.authorize(request("/atz/abstain", "atz/fail")));
        assertThat(res.status(), is(conf.abstainFail));

        res = SecurityResponse.get(atn.authenticate(request("/fail", "jack")));
        assertThat(res.status(), is(conf.failOk));
        res = SecurityResponse.get(atz.authorize(request("/atz/fail", "atz/permit")));
        assertThat(res.status(), is(conf.failOk));

        res = SecurityResponse.get(atn.authenticate(request("/fail", "abstain")));
        assertThat(res.status(), is(conf.failAbstain));
        res = SecurityResponse.get(atz.authorize(request("/atz/fail", "atz/abstain")));
        assertThat(res.status(), is(conf.failAbstain));

        res = SecurityResponse.get(atn.authenticate(request("/fail", "fail")));
        assertThat(res.status(), is(conf.failFail));
        res = SecurityResponse.get(atz.authorize(request("/atz/fail", "atz/fail")));
        assertThat(res.status(), is(conf.failFail));
    }

    @Test
    public void testMustFail() {
        TestConfig tc = new TestConfig(CompositeProviderFlag.MUST_FAIL, CompositeProviderFlag.REQUIRED);

        tc.okOk = FAILURE;
        tc.okAbstain = FAILURE;
        tc.okFail = FAILURE;

        tc.abstainOk = FAILURE;
        tc.abstainAbstain = FAILURE;
        tc.abstainFail = FAILURE;

        tc.failOk = SUCCESS;
        tc.failAbstain = FAILURE;
        tc.failFail = FAILURE;

        testIt(tc);

        tc = new TestConfig(CompositeProviderFlag.REQUIRED, CompositeProviderFlag.MUST_FAIL);

        tc.okOk = FAILURE;
        tc.okAbstain = FAILURE;
        tc.okFail = SUCCESS;

        tc.abstainOk = FAILURE;
        tc.abstainAbstain = FAILURE;
        tc.abstainFail = FAILURE;

        tc.failOk = FAILURE;
        tc.failAbstain = FAILURE;
        tc.failFail = FAILURE;

        testIt(tc);
    }

    @Test
    public void testForbiddenFirst() {
        TestConfig tc = new TestConfig(CompositeProviderFlag.FORBIDDEN, CompositeProviderFlag.REQUIRED);

        tc.okOk = FAILURE;
        tc.okAbstain = FAILURE;
        tc.okFail = FAILURE;

        tc.abstainOk = SUCCESS;
        tc.abstainAbstain = FAILURE;
        tc.abstainFail = FAILURE;

        tc.failOk = SUCCESS;
        tc.failAbstain = FAILURE;
        tc.failFail = FAILURE;

        testIt(tc);

        tc = new TestConfig(CompositeProviderFlag.REQUIRED, CompositeProviderFlag.FORBIDDEN);

        tc.okOk = FAILURE;
        tc.okAbstain = SUCCESS;
        tc.okFail = SUCCESS;

        tc.abstainOk = FAILURE;
        tc.abstainAbstain = FAILURE;
        tc.abstainFail = FAILURE;

        tc.failOk = FAILURE;
        tc.failAbstain = FAILURE;
        tc.failFail = FAILURE;

        testIt(tc);
    }

    @Test
    public void testForbiddenLast() {
        TestConfig tc = new TestConfig(CompositeProviderFlag.REQUIRED, CompositeProviderFlag.FORBIDDEN);

        tc.okOk = FAILURE;
        tc.okAbstain = SUCCESS;
        tc.okFail = SUCCESS;

        tc.abstainOk = FAILURE;
        tc.abstainAbstain = FAILURE;
        tc.abstainFail = FAILURE;

        tc.failOk = FAILURE;
        tc.failAbstain = FAILURE;
        tc.failFail = FAILURE;

        testIt(tc);
    }

    @Test
    public void testRequired() {
        TestConfig tc = new TestConfig(CompositeProviderFlag.REQUIRED, CompositeProviderFlag.REQUIRED);

        tc.okOk = SUCCESS;
        tc.okAbstain = FAILURE;
        tc.okFail = FAILURE;

        tc.abstainOk = FAILURE;
        tc.abstainAbstain = FAILURE;
        tc.abstainFail = FAILURE;

        tc.failOk = FAILURE;
        tc.failAbstain = FAILURE;
        tc.failFail = FAILURE;

        testIt(tc);
    }

    @Test
    public void testOptional() {
        TestConfig tc = new TestConfig(CompositeProviderFlag.OPTIONAL, CompositeProviderFlag.REQUIRED);

        tc.okOk = SUCCESS;
        tc.okAbstain = FAILURE;
        tc.okFail = FAILURE;

        tc.abstainOk = SUCCESS;
        tc.abstainAbstain = FAILURE;
        tc.abstainFail = FAILURE;

        tc.failOk = FAILURE;
        tc.failAbstain = FAILURE;
        tc.failFail = FAILURE;

        testIt(tc);

        tc = new TestConfig(CompositeProviderFlag.REQUIRED, CompositeProviderFlag.OPTIONAL);

        tc.okOk = SUCCESS;
        tc.okAbstain = SUCCESS;
        tc.okFail = FAILURE;

        tc.abstainOk = FAILURE;
        tc.abstainAbstain = FAILURE;
        tc.abstainFail = FAILURE;

        tc.failOk = FAILURE;
        tc.failAbstain = FAILURE;
        tc.failFail = FAILURE;

        testIt(tc);
    }

    @Test
    public void testSufficient() {
        TestConfig tc = new TestConfig(CompositeProviderFlag.SUFFICIENT, CompositeProviderFlag.REQUIRED);

        tc.okOk = SUCCESS;
        tc.okAbstain = SUCCESS;
        tc.okFail = SUCCESS;

        tc.abstainOk = SUCCESS;
        tc.abstainAbstain = FAILURE;
        tc.abstainFail = FAILURE;

        tc.failOk = FAILURE;
        tc.failAbstain = FAILURE;
        tc.failFail = FAILURE;

        testIt(tc);

        tc = new TestConfig(CompositeProviderFlag.REQUIRED, CompositeProviderFlag.SUFFICIENT);

        tc.okOk = SUCCESS;
        tc.okAbstain = SUCCESS;
        tc.okFail = FAILURE;

        tc.abstainOk = FAILURE;
        tc.abstainAbstain = FAILURE;
        tc.abstainFail = FAILURE;

        tc.failOk = FAILURE;
        tc.failAbstain = FAILURE;
        tc.failFail = FAILURE;

        testIt(tc);
    }

    @Test
    public void testMayFail() {
        TestConfig tc = new TestConfig(CompositeProviderFlag.MAY_FAIL, CompositeProviderFlag.REQUIRED);

        tc.okOk = SUCCESS;
        tc.okAbstain = FAILURE;
        tc.okFail = FAILURE;

        tc.abstainOk = SUCCESS;
        tc.abstainAbstain = FAILURE;
        tc.abstainFail = FAILURE;

        tc.failOk = SUCCESS;
        tc.failAbstain = FAILURE;
        tc.failFail = FAILURE;

        testIt(tc);

        tc = new TestConfig(CompositeProviderFlag.REQUIRED, CompositeProviderFlag.MAY_FAIL);

        tc.okOk = SUCCESS;
        tc.okAbstain = SUCCESS;
        tc.okFail = SUCCESS;

        tc.abstainOk = FAILURE;
        tc.abstainAbstain = FAILURE;
        tc.abstainFail = FAILURE;

        tc.failOk = FAILURE;
        tc.failAbstain = FAILURE;
        tc.failFail = FAILURE;

        testIt(tc);
    }

    private ProviderSelectionPolicy getPsp(CompositeProviderFlag firstFlag, CompositeProviderFlag secondFlag) {
        return CompositeProviderSelectionPolicy.builder()
                .addOutboundProvider("first")
                .addOutboundProvider("second")
                .addAuthenticationProvider("first", firstFlag)
                .addAuthenticationProvider("second", secondFlag)
                .addAuthorizationProvider("first", firstFlag)
                .addAuthorizationProvider("second", secondFlag)
                .build()
                .apply(new ProviderSelectionPolicy.Providers() {
                    @Override
                    public <T extends SecurityProvider> List<NamedProvider<T>> getProviders(Class<T> providerType) {
                        List<NamedProvider<T>> result = new ArrayList<>();
                        result.add(new NamedProvider<>("first", providerType.cast(PATH_BASED_PROVIDER)));
                        result.add(new NamedProvider<>("second", providerType.cast(RESOURCE_BASED_PROVIDER)));
                        return result;
                    }
                });
    }

    private ProviderRequest request(String path, String resource) {
        ProviderRequest mock = Mockito.mock(ProviderRequest.class);
        SecurityEnvironment se = Mockito.mock(SecurityEnvironment.class);

        when(se.path()).thenReturn(Optional.of(path));
        when(se.headers()).thenReturn(CollectionsHelper.mapOf());
        when(se.abacAttribute("resourceType")).thenReturn(Optional.of(resource));

        when(mock.env()).thenReturn(se);

        return mock;
    }

    private class TestConfig {
        private CompositeProviderFlag firstFlag;
        private CompositeProviderFlag secondFlag;
        private SecurityStatus okOk;
        private SecurityStatus okAbstain;
        private SecurityStatus okFail;
        private SecurityStatus abstainOk;
        private SecurityStatus abstainAbstain;
        private SecurityStatus abstainFail;
        private SecurityStatus failOk;
        private SecurityStatus failAbstain;
        private SecurityStatus failFail;

        private TestConfig(CompositeProviderFlag firstFlag, CompositeProviderFlag secondFlag) {
            this.firstFlag = firstFlag;
            this.secondFlag = secondFlag;
        }
    }
}
