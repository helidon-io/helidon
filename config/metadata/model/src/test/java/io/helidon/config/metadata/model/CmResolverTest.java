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
package io.helidon.config.metadata.model;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import io.helidon.config.metadata.model.CmModel.CmOption;
import io.helidon.config.metadata.model.CmModel.CmType;
import io.helidon.config.metadata.model.CmNode.CmOptionNode;
import io.helidon.config.metadata.model.CmNode.CmPathNode;
import io.helidon.metadata.hson.Hson;

import org.hamcrest.FeatureMatcher;
import org.hamcrest.Matcher;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

/**
 * Tests {@link CmResolver}.
 */
class CmResolverTest {

    @Test
    void testRoots() {
        var model = model("""
                [
                   {
                      "module": "com.acme",
                      "types": [
                         {
                            "type": "com.acme.AcmeServerConfig",
                            "prefix": "server",
                            "standalone": true
                         },
                         {
                            "type": "com.acme.AcmeBulkheadConfig",
                            "prefix": "fault-tolerance.bulkheads",
                            "standalone": true
                         },
                         {
                            "type": "com.acme.AcmeMetricsConfig",
                            "prefix": "metrics",
                            "standalone": true
                         },
                         {
                            "type": "com.acme.AcmeMetricsObserverConfig",
                            "prefix": "metrics",
                            "standalone": true,
                            "provides": [
                               "com.acme.AcmeFeature"
                            ]
                         },
                         {
                            "type": "com.acme.AcmeSqlDataSourceConfig",
                            "prefix": "data.sources.sql",
                            "standalone": true
                         }
                      ]
                   }
                ]
                """);

        var resolver = CmResolver.create(model);
        assertThat(resolver.roots(), contains(List.of(
                isNode(CmPathNode.class,
                        hasProperty("path", CmNode::path, is("data")),
                        hasProperty("types", CmNode::types, is(empty())),
                        hasProperty("children", CmNode::children, contains(isNode(CmNode.class,
                                hasProperty("path", CmNode::path, is("data.sources")),
                                hasProperty("types", CmNode::types, is(empty())),
                                hasProperty("children", CmNode::children, contains(isNode(CmPathNode.class,
                                        hasProperty("path", CmNode::path, is("data.sources.sql")),
                                        hasProperty("types", CmNode::types, contains(
                                                hasProperty("type", CmType::typeName, is("com.acme.AcmeSqlDataSourceConfig"))
                                        )),
                                        hasProperty("children", CmNode::children, is(empty()))
                                )))
                        )))
                ),
                isNode(CmPathNode.class, hasProperty("path", CmNode::path, is("fault-tolerance"))),
                isNode(CmPathNode.class,
                        hasProperty("path", CmNode::path, is("metrics")),
                        hasProperty("types", CmNode::types, contains(List.of(
                                hasProperty("type", CmType::typeName, is("com.acme.AcmeMetricsConfig")),
                                hasProperty("type", CmType::typeName, is("com.acme.AcmeMetricsObserverConfig"))
                        ))),
                        hasProperty("children", CmNode::children, is(empty()))),
                isNode(CmPathNode.class,
                        hasProperty("path", CmNode::path, is("server")),
                        hasProperty("types", CmNode::types, contains(
                                hasProperty("type", CmType::typeName, is("com.acme.AcmeServerConfig"))
                        )))
        )));
        assertThat(resolver.providers("com.acme.AcmeFeature"), hasProperty("metrics", m -> m.get("metrics"), contains(
                hasProperty("type", CmType::typeName, is("com.acme.AcmeMetricsObserverConfig")))));
    }

    @Test
    void testProviderBackedStandaloneRoot() {
        var model = model("""
                [
                   {
                      "module": "com.acme",
                      "types": [
                         {
                            "type": "com.acme.AcmeAuditConfig",
                            "prefix": "audit",
                            "standalone": true,
                            "provides": [
                               "com.acme.AcmeFeature"
                            ],
                            "options": [
                               {
                                  "key": "enabled",
                                  "type": "java.lang.Boolean",
                                  "description": "Enabled"
                               }
                            ]
                         }
                      ]
                   }
                ]
                """);

        var resolver = CmResolver.create(model);
        assertThat(resolver.roots(), contains(isNode(CmPathNode.class,
                hasProperty("path", CmNode::path, is("audit")),
                hasProperty("types", CmNode::types, contains(
                        hasProperty("type", CmType::typeName, is("com.acme.AcmeAuditConfig"))
                )),
                hasProperty("children", CmNode::children, contains(isNode(CmOptionNode.class,
                        hasProperty("path", CmNode::path, is("audit.enabled")),
                        hasProperty("key", CmNode::key, is("enabled"))
                )))
        )));
        assertThat(resolver.providers("com.acme.AcmeFeature"), hasProperty("audit", m -> m.get("audit"), contains(
                hasProperty("type", CmType::typeName, is("com.acme.AcmeAuditConfig")))));
    }

    @Test
    void testConcreteRoots() {
        var model = model("""
                [
                   {
                      "module": "com.acme",
                      "types": [
                         {
                            "type": "com.acme.AcmeClientsConfig",
                            "prefix": "clients",
                            "standalone": true
                         },
                         {
                            "type": "com.acme.AcmeHttpClientsConfig",
                            "prefix": "clients.http",
                            "standalone": true
                         }
                      ]
                   }
                ]
                """);

        var resolver = CmResolver.create(model);
        assertThat(resolver.roots(), contains(isNode(CmPathNode.class,
                hasProperty("path", CmNode::path, is("clients")),
                hasProperty("types", CmNode::types, contains(
                        hasProperty("type", CmType::typeName, is("com.acme.AcmeClientsConfig"))
                )),
                hasProperty("children", CmNode::children, contains(isNode(CmPathNode.class,
                        hasProperty("path", CmNode::path, is("clients.http")),
                        hasProperty("types", CmNode::types, contains(
                                hasProperty("type", CmType::typeName, is("com.acme.AcmeHttpClientsConfig"))
                        )),
                        hasProperty("children", CmNode::children, is(empty()))
                )))
        )));
    }

    @Test
    void testWildcardPrefixSegmentsSkipped() {
        var model = model("""
                [
                   {
                      "module": "com.acme",
                      "types": [
                         {
                            "type": "com.acme.AcmeHttpConfig",
                            "prefix": "server.*.http",
                            "standalone": true,
                            "options": [
                               {
                                  "key": "port",
                                  "type": "java.lang.Integer",
                                  "description": "Port"
                               }
                            ]
                         }
                      ]
                   }
                ]
                """);

        var resolver = CmResolver.create(model);
        assertThat(YamlGenerator.render(resolver.roots()), is("""
                server:
                  http:
                    port: <Integer>
                """));

        assertThat(resolver.roots(), contains(isNode(CmPathNode.class,
                hasProperty("path", CmNode::path, is("server")),
                hasProperty("children", CmNode::children, contains(isNode(CmPathNode.class,
                        hasProperty("path", CmNode::path, is("server.http")),
                        hasProperty("types", CmNode::types, contains(
                                hasProperty("type", CmType::typeName, is("com.acme.AcmeHttpConfig"))
                        )),
                        hasProperty("children", CmNode::children, contains(isNode(CmOptionNode.class,
                                hasProperty("path", CmNode::path, is("server.http.port")),
                                hasProperty("key", CmNode::key, is("port"))
                        )))
                )))
        )));
    }

    @Test
    void testOptionNodeExactType() {
        var model = model("""
                [
                   {
                      "module": "com.acme",
                      "types": [
                         {
                            "type": "com.acme.AcmeStoreConfig",
                            "prefix": "store",
                            "standalone": true,
                            "options": [
                               {
                                  "key": "http",
                                  "type": "com.acme.AcmeHttpConfig",
                                  "description": "HTTP"
                               }
                            ]
                         },
                         {
                            "type": "com.acme.AcmeHttpConfig",
                            "prefix": "store.http",
                            "standalone": true
                         }
                      ]
                   }
                ]
                """);

        var resolver = CmResolver.create(model);
        assertThat(resolver.roots(), contains(isNode(CmPathNode.class,
                hasProperty("path", CmNode::path, is("store")),
                hasProperty("children", CmNode::children, contains(isNode(CmOptionNode.class,
                        hasProperty("path", CmNode::path, is("store.http")),
                        hasProperty("types", CmNode::types, contains(
                                hasProperty("type", CmType::typeName, is("com.acme.AcmeHttpConfig"))
                        )),
                        hasProperty("option", CmOptionNode::option, allOf(
                                hasProperty("type", CmOption::typeName, is("com.acme.AcmeHttpConfig")),
                                hasProperty("description", CmOption::description, isOptional("HTTP"))
                        ))
                )))
        )));
    }

    @Test
    void testDottedOptionKeys() {
        var model = model("""
                [
                   {
                      "module": "com.acme",
                      "types": [
                         {
                            "type": "com.acme.AcmeServerConfig",
                            "prefix": "server",
                            "standalone": true,
                            "options": [
                               {
                                  "key": "http.port",
                                  "type": "java.lang.Integer",
                                  "description": "Port"
                               },
                               {
                                  "key": "http.tls.enabled",
                                  "type": "java.lang.Boolean",
                                  "description": "Enabled"
                               }
                            ]
                         }
                      ]
                   }
                ]
                """);

        var resolver = CmResolver.create(model);
        assertThat(resolver.roots(), contains(isNode(CmPathNode.class,
                hasProperty("path", CmNode::path, is("server")),
                hasProperty("children", CmNode::children, contains(isNode(CmPathNode.class,
                        hasProperty("path", CmNode::path, is("server.http")),
                        hasProperty("key", CmNode::key, is("http")),
                        hasProperty("children", CmNode::children, contains(List.of(
                                isNode(CmOptionNode.class,
                                        hasProperty("path", CmNode::path, is("server.http.port")),
                                        hasProperty("key", CmNode::key, is("port")),
                                        hasProperty("option", CmOptionNode::option,
                                                hasProperty("key", CmOption::key, isOptional("http.port")))),
                                isNode(CmPathNode.class,
                                        hasProperty("path", CmNode::path, is("server.http.tls")),
                                        hasProperty("key", CmNode::key, is("tls")),
                                        hasProperty("children", CmNode::children, contains(isNode(CmOptionNode.class,
                                                hasProperty("path", CmNode::path, is("server.http.tls.enabled")),
                                                hasProperty("key", CmNode::key, is("enabled")),
                                                hasProperty("option", CmOptionNode::option,
                                                        hasProperty("key", CmOption::key, isOptional("http.tls.enabled"))))
                                        )))
                        )))
                )))
        )));
    }

    @Test
    void testWildcardOptionSegmentsSkipped() {
        var model = model("""
                [
                   {
                      "module": "com.acme",
                      "types": [
                         {
                            "type": "com.acme.AcmeServerConfig",
                            "prefix": "server",
                            "standalone": true,
                            "options": [
                               {
                                  "key": "features.*.enabled",
                                  "type": "java.lang.Boolean",
                                  "description": "Enabled"
                               },
                               {
                                  "key": "*.*",
                                  "type": "java.lang.String",
                                  "description": "Ignored"
                               }
                            ]
                         }
                      ]
                   }
                ]
                """);

        var resolver = CmResolver.create(model);
        assertThat(YamlGenerator.render(resolver.roots()), is("""
                server:
                  features:
                    enabled: <Boolean>
                """));

        var server = resolver.roots().getFirst();
        assertThat(server.children(), contains(isNode(CmPathNode.class,
                hasProperty("path", CmNode::path, is("server.features")),
                hasProperty("children", CmNode::children, contains(isNode(CmOptionNode.class,
                        hasProperty("path", CmNode::path, is("server.features.enabled")),
                        hasProperty("key", CmNode::key, is("enabled")),
                        hasProperty("option", CmOptionNode::option,
                                hasProperty("key", CmOption::key, isOptional("features.*.enabled")))
                )))
        )));
    }

    @Test
    void testProviderDuplicateKeys() {
        var model = model("""
                [
                   {
                      "module": "com.acme",
                      "types": [
                         {
                            "type": "com.acme.AcmeServerConfig",
                            "prefix": "server",
                            "standalone": true,
                            "options": [
                               {
                                  "key": "features",
                                  "type": "com.acme.AcmeFeature",
                                  "description": "Features",
                                  "provider": true
                               }
                            ]
                         },
                         {
                            "type": "com.acme.AcmeAuditConfig",
                            "prefix": "audit",
                            "standalone": true,
                            "provides": [
                               "com.acme.AcmeFeature"
                            ]
                         },
                         {
                            "type": "com.acme.AcmeFileFeatureConfig",
                            "prefix": "shared",
                            "standalone": true,
                            "provides": [
                               "com.acme.AcmeFeature"
                            ]
                         },
                         {
                            "type": "com.acme.AcmeSocketFeatureConfig",
                            "prefix": "shared",
                            "standalone": true,
                            "provides": [
                               "com.acme.AcmeFeature"
                            ]
                         }
                      ]
                   }
                ]
                """);

        var resolver = CmResolver.create(model);

        var providers = resolver.providers("com.acme.AcmeFeature");
        assertThat(providers, hasProperty("shared", m -> m.get("shared"), contains(List.of(
                hasProperty("type", CmType::typeName, is("com.acme.AcmeFileFeatureConfig")),
                hasProperty("type", CmType::typeName, is("com.acme.AcmeSocketFeatureConfig"))
        ))));

        assertThat(resolver.roots(), contains(List.of(
                isNode(CmPathNode.class,
                        hasProperty("path", CmNode::path, is("audit")),
                        hasProperty("types", CmNode::types, contains(
                                hasProperty("type", CmType::typeName, is("com.acme.AcmeAuditConfig"))
                        ))),
                isNode(CmPathNode.class,
                        hasProperty("path", CmNode::path, is("server")),
                        hasProperty("children", CmNode::children, contains(isNode(CmOptionNode.class,
                                hasProperty("path", CmNode::path, is("server.features")),
                                hasProperty("provider", CmOptionNode::option,
                                        hasProperty("provider", CmOption::provider, is(true))),
                                hasProperty("types", CmNode::types, is(empty())),
                                hasProperty("children", CmNode::children, contains(List.of(
                                        isNode(CmPathNode.class,
                                                hasProperty("path", CmNode::path, is("server.features.audit")),
                                                hasProperty("types", CmNode::types, contains(
                                                        hasProperty("type", CmType::typeName, is("com.acme.AcmeAuditConfig"))
                                                ))),
                                        isNode(CmPathNode.class,
                                                hasProperty("path", CmNode::path, is("server.features.shared")),
                                                hasProperty("types", CmNode::types, contains(List.of(
                                                        hasProperty("type", CmType::typeName, is("com.acme.AcmeFileFeatureConfig")),
                                                        hasProperty("type", CmType::typeName, is("com.acme.AcmeSocketFeatureConfig"))
                                                )))
                                        )))
                                ))))),
                isNode(CmPathNode.class,
                        hasProperty("path", CmNode::path, is("shared")),
                        hasProperty("types", CmNode::types, contains(List.of(
                                hasProperty("type", CmType::typeName, is("com.acme.AcmeFileFeatureConfig")),
                                hasProperty("type", CmType::typeName, is("com.acme.AcmeSocketFeatureConfig"))
                        ))))
        )));
    }

    @Test
    void testCanonicalYaml() {
        var model = model("""
                [
                   {
                      "module": "com.acme",
                      "types": [
                         {
                            "type": "com.acme.AcmeSqlDataSourceConfig",
                            "prefix": "data.sources.sql",
                            "standalone": true,
                            "options": [
                               {
                                  "key": "username",
                                  "type": "java.lang.String",
                                  "description": "Username"
                               },
                               {
                                  "key": "pool",
                                  "type": "com.acme.AcmePoolConfig",
                                  "description": "Pool"
                               }
                            ]
                         },
                         {
                            "type": "com.acme.AcmePoolConfig",
                            "options": [
                               {
                                  "key": "size",
                                  "type": "java.lang.Integer",
                                  "description": "Pool size"
                               }
                            ]
                         },
                         {
                            "type": "com.acme.AcmeServerConfig",
                            "prefix": "server",
                            "standalone": true,
                            "options": [
                               {
                                  "key": "features",
                                  "type": "com.acme.AcmeFeature",
                                  "description": "Features",
                                  "provider": true
                               }
                            ]
                         },
                         {
                            "type": "com.acme.AcmeAuditFeatureConfig",
                            "prefix": "audit",
                            "provides": [
                               "com.acme.AcmeFeature"
                            ],
                            "options": [
                               {
                                  "key": "enabled",
                                  "type": "java.lang.Boolean",
                                  "description": "Enabled"
                               }
                            ]
                         }
                      ]
                   }
                ]
                """);

        var resolver = CmResolver.create(model);
        var yaml = YamlGenerator.render(resolver.roots());
        assertThat(yaml, is("""
                data:
                  sources:
                    sql:
                      pool:
                        size: <Integer>
                      username: <String>
                server:
                  features:
                    audit:
                      enabled: <Boolean>
                """));
    }

    @Test
    void testContracts() {
        var model = model("""
                [
                   {
                      "module": "com.acme",
                      "types": [
                         {
                            "type": "com.acme.AcmeServerConfig",
                            "prefix": "server",
                            "standalone": true,
                            "options": [
                               {
                                  "key": "features",
                                  "type": "com.acme.AcmeFeature",
                                  "description": "Features",
                                  "provider": true
                               }
                            ]
                         },
                         {
                            "type": "com.acme.AcmeHelperConfig",
                            "description": "Helper",
                            "options": [
                               {
                                  "key": "services",
                                  "type": "com.acme.AcmeService",
                                  "description": "Services",
                                  "provider": true
                               }
                            ]
                         }
                      ]
                   }
                ]
                """);

        var resolver = CmResolver.create(model);
        assertThat(resolver.contracts(), contains(
                "com.acme.AcmeFeature",
                "com.acme.AcmeService"));
        assertThat(resolver.usage("com.acme.AcmeFeature"), contains(isNode(CmOptionNode.class)));
        assertThat(resolver.usage("com.acme.AcmeService"), is(empty()));
    }

    @Test
    void testKnownTypes() {
        var model = model("""
                [
                   {
                      "module": "com.acme",
                      "types": [
                         {
                            "type": "com.acme.AcmeServerConfig",
                            "prefix": "server",
                            "standalone": true,
                            "options": [
                               {
                                  "key": "mode",
                                  "type": "com.acme.AcmeMode",
                                  "description": "Mode",
                                  "allowedValues": [
                                     {
                                        "value": "dev",
                                        "description": "Dev"
                                     }
                                  ]
                               },
                               {
                                  "key": "features",
                                  "type": "com.acme.AcmeFeature",
                                  "description": "Features",
                                  "provider": true
                               }
                            ]
                         },
                         {
                            "type": "com.acme.AcmeAuditFeatureConfig",
                            "prefix": "audit",
                            "standalone": true,
                            "provides": [
                               "com.acme.AcmeFeature"
                            ]
                         }
                      ]
                   }
                ]
                """);

        var resolver = CmResolver.create(model);
        assertThat(resolver.isKnownType("com.acme.AcmeServerConfig"), is(true));
        assertThat(resolver.isKnownType("com.acme.AcmeMode"), is(true));
        assertThat(resolver.isKnownType("com.acme.AcmeFeature"), is(true));
        assertThat(resolver.isKnownType("java.lang.String"), is(false));
    }

    @Test
    void testDetachedProviders() {
        var model = model("""
                [
                   {
                      "module": "com.acme",
                      "types": [
                         {
                            "type": "com.acme.AcmeServerConfig",
                            "prefix": "server",
                            "standalone": true,
                            "options": [
                               {
                                  "key": "features",
                                  "type": "com.acme.AcmeFeature",
                                  "description": "Features",
                                  "provider": true
                               }
                            ]
                         },
                         {
                            "type": "com.acme.AcmeHelperConfig",
                            "description": "Helper",
                            "options": [
                               {
                                  "key": "services",
                                  "type": "com.acme.AcmeService",
                                  "description": "Services",
                                  "provider": true
                               }
                            ]
                         },
                         {
                            "type": "com.acme.AcmeAuditFeatureConfig",
                            "prefix": "audit",
                            "standalone": true,
                            "provides": [
                               "com.acme.AcmeFeature"
                            ]
                         },
                         {
                            "type": "com.acme.AcmeAuditServiceConfig",
                            "prefix": "audit",
                            "standalone": true,
                            "provides": [
                               "com.acme.AcmeService"
                            ]
                         }
                      ]
                   }
                ]
                """);

        var resolver = CmResolver.create(model);
        assertThat(resolver.providers("com.acme.AcmeFeature"), hasProperty("audit", m -> m.get("audit"), contains(
                hasProperty("type", CmType::typeName, is("com.acme.AcmeAuditFeatureConfig")))));
        assertThat(resolver.providers("com.acme.AcmeService"), hasProperty("audit", m -> m.get("audit"), contains(
                hasProperty("type", CmType::typeName, is("com.acme.AcmeAuditServiceConfig")))));
    }

    @Test
    void testMergeValue() {
        var model = model("""
                [
                   {
                      "module": "com.acme",
                      "types": [
                         {
                            "type": "com.acme.AcmeConfig",
                            "prefix": "acme",
                            "standalone": true,
                            "options": [
                               {
                                  "key": "option1",
                                  "type": "java.lang.Integer",
                                  "description": "Option1",
                                  "merge": true
                               }
                            ]
                         }
                      ]
                   }
                ]
                """);

        var resolver = CmResolver.create(model);
        assertThat(resolver.type("com.acme.AcmeConfig"), isOptional(allOf(
                hasProperty("typeName", CmType::typeName, is("com.acme.AcmeConfig")),
                hasProperty("options", CmType::options, contains(allOf(List.of(
                        hasProperty("key", CmOption::key, isOptional("option1")),
                        hasProperty("description", CmOption::description, isOptional("Option1")),
                        hasProperty("typeName", CmOption::typeName, is("java.lang.Integer"))
                ))))
        )));
    }

    static CmModel model(@Language("json") String json) {
        var is = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
        return CmModel.fromJson(Hson.parse(is).asArray());
    }

    @SafeVarargs
    @SuppressWarnings("unchecked")
    static <T extends CmNode> Matcher<CmNode> isNode(Class<T> type, Matcher<T>... matchers) {
        var list = new ArrayList<Matcher<? super CmNode>>();
        list.add(instanceOf(type));
        for (var matcher : matchers) {
            list.add((Matcher<CmNode>) matcher);
        }
        return allOf(list);
    }

    static <T> Matcher<Optional<T>> isOptional(T value) {
        return isOptional(equalTo(value));
    }

    static <T> Matcher<Optional<T>> isOptional(Matcher<T> matcher) {
        return allOf(hasProperty("present", Optional::isPresent, is(true)),
                hasProperty("get", Optional::get, matcher));
    }

    static <T, U> Matcher<T> hasProperty(String name, Function<T, U> extractor, Matcher<U> subMatcher) {
        return new FeatureMatcher<>(subMatcher, "has property " + name, name) {
            @Override
            protected U featureValueOf(T target) {
                return extractor.apply(target);
            }
        };
    }
}
