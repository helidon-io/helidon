/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.security.abac.scope;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import io.helidon.common.Errors;
import io.helidon.security.Grant;
import io.helidon.security.Principal;
import io.helidon.security.ProviderRequest;
import io.helidon.security.Subject;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link ScopeValidator}.
 */
public class ScopeValidatorTest {
    @Test
    public void testScopesAndPermit() {
        ScopeValidator validator = ScopeValidator.create();
        List<ScopeValidator.Scope> annots = new LinkedList<>();
        ScopeValidator.Scope annot = mock(ScopeValidator.Scope.class);
        when(annot.value()).thenReturn("calendar_get");
        annots.add(annot);
        annot = mock(ScopeValidator.Scope.class);
        when(annot.value()).thenReturn("calendar_update");
        annots.add(annot);

        ScopeValidator.ScopesConfig sConfig = validator.fromAnnotations(annots);

        Errors.Collector collector = Errors.collector();
        ProviderRequest request = mock(ProviderRequest.class);
        when(request.getSubject()).thenReturn(Optional.of(Subject.builder()
                                                                  .principal(Principal.create("myAdmin"))
                                                                  .addGrant(Grant.builder()
                                                                                    .type("scope")
                                                                                    .name("calendar_get")
                                                                                    .build())
                                                                  .addGrant(Grant.builder()
                                                                                    .type("scope")
                                                                                    .name("calendar_update")
                                                                                    .build())
                                                                  .build()));
        when(request.getService()).thenReturn(Optional.empty());
        validator.validate(sConfig, collector, request);

        collector.collect().checkValid();
    }

    @Test
    public void testScopesAndDeny() {
        ScopeValidator validator = ScopeValidator.create();
        List<ScopeValidator.Scope> annots = new LinkedList<>();
        ScopeValidator.Scope annot = mock(ScopeValidator.Scope.class);
        when(annot.value()).thenReturn("calendar_get");
        annots.add(annot);
        annot = mock(ScopeValidator.Scope.class);
        when(annot.value()).thenReturn("calendar_update");
        annots.add(annot);

        ScopeValidator.ScopesConfig sConfig = validator.fromAnnotations(annots);

        Errors.Collector collector = Errors.collector();
        ProviderRequest request = mock(ProviderRequest.class);
        when(request.getSubject()).thenReturn(Optional.of(Subject.builder()
                                                                  .principal(Principal.create("myAdmin"))
                                                                  .addGrant(Grant.builder()
                                                                                    .type("scope")
                                                                                    .name("calendar_get")
                                                                                    .build())
                                                                  .build()));
        when(request.getService()).thenReturn(Optional.empty());
        validator.validate(sConfig, collector, request);

        if (collector.collect().isValid()) {
            fail("User does not have calendar_update scope, so this should have failed");
        }
    }

    @Test
    public void testScopesOrPermit() {
        ScopeValidator validator = ScopeValidator.builder()
                .useOrOperator(true)
                .build();

        List<ScopeValidator.Scope> annots = new LinkedList<>();
        ScopeValidator.Scope annot = mock(ScopeValidator.Scope.class);
        when(annot.value()).thenReturn("calendar_get");
        annots.add(annot);
        annot = mock(ScopeValidator.Scope.class);
        when(annot.value()).thenReturn("calendar_update");
        annots.add(annot);

        ScopeValidator.ScopesConfig sConfig = validator.fromAnnotations(annots);

        Errors.Collector collector = Errors.collector();
        ProviderRequest request = mock(ProviderRequest.class);
        when(request.getSubject()).thenReturn(Optional.of(Subject.builder()
                                                                  .principal(Principal.create("myAdmin"))
                                                                  .addGrant(Grant.builder()
                                                                                    .type("scope")
                                                                                    .name("calendar_get")
                                                                                    .build())
                                                                  .build()));
        when(request.getService()).thenReturn(Optional.empty());
        validator.validate(sConfig, collector, request);

        collector.collect().checkValid();
    }

    @Test
    public void testScopesOrDeny() {
        ScopeValidator validator = ScopeValidator.builder()
                .useOrOperator(true)
                .build();

        List<ScopeValidator.Scope> annots = new LinkedList<>();
        ScopeValidator.Scope annot = mock(ScopeValidator.Scope.class);
        when(annot.value()).thenReturn("calendar_get");
        annots.add(annot);
        annot = mock(ScopeValidator.Scope.class);
        when(annot.value()).thenReturn("calendar_update");
        annots.add(annot);

        ScopeValidator.ScopesConfig sConfig = validator.fromAnnotations(annots);

        Errors.Collector collector = Errors.collector();
        ProviderRequest request = mock(ProviderRequest.class);
        when(request.getSubject()).thenReturn(Optional.of(Subject.builder()
                                                                  .principal(Principal.create("myAdmin"))
                                                                  .addGrant(Grant.builder()
                                                                                    .type("scope")
                                                                                    .name("calendar_other")
                                                                                    .build())
                                                                  .build()));
        when(request.getService()).thenReturn(Optional.empty());
        validator.validate(sConfig, collector, request);

        if (collector.collect().isValid()) {
            fail("User does not have any of the required scopes, should have failed");
        }
    }
}
