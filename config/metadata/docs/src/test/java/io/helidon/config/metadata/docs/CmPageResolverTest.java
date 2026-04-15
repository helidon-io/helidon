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

package io.helidon.config.metadata.docs;

import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import io.helidon.config.metadata.model.CmModel;

import org.hamcrest.FeatureMatcher;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

/**
 * Tests {@link CmPageResolver}.
 */
class CmPageResolverTest {
    private static final String DUMMY_ROOT_PAGE = "dummy-root.html";
    private static final String PAGE_FILE_NAME_EXTENSION = ".html";

    @Test
    void testBaselinePages() throws Exception {
        var resolver = resolver();

        assertThat(resolver.rootPage(), allOf(
                hasProperty("kind", CmPage::kind, is(CmPage.Kind.ROOT)),
                hasProperty("key", CmPage::key, is("root/root")),
                hasProperty("typeName", CmPage::typeName, is("Config Reference")),
                hasProperty("fileName", CmPage::fileName, is(DUMMY_ROOT_PAGE)),
                hasProperty("tables", CmPage::tables, allOf(
                        hasProperty("standard", CmPage.Tables::standard,
                                hasProperty("rows", CmPage.Table::rows, not(empty()))),
                        hasProperty("experimental", CmPage.Tables::experimental,
                                hasProperty("isEmpty", CmPage.Table::isEmpty, is(true))),
                        hasProperty("deprecated", CmPage.Tables::deprecated,
                                hasProperty("isEmpty", CmPage.Table::isEmpty, is(true)))
                ))
        ));

        assertThat(resolver.pages(), hasSize(17));
        assertThat(resolver.pages(), allOf(List.of(
                hasItem(allOf(
                        hasProperty("kind", CmPage::kind, is(CmPage.Kind.CONFIG)),
                        hasProperty("key", CmPage::key, is("config/com.acme.AcmeServerConfig")),
                        hasProperty("tables", CmPage::tables,
                                hasProperty("standard", CmPage.Tables::standard,
                                        hasProperty("rows", CmPage.Table::rows, hasItem(allOf(
                                                hasProperty("key", CmPage.Row::key, is("sockets")),
                                                hasProperty("fileName", CmPage.Row::fileName,
                                                        is("com_acme_AcmeListenerConfig.html")),
                                                hasProperty("anchor", CmPage.Row::anchor, not(is("")))
                                        )))))
                )),
                hasItem(allOf(
                        hasProperty("kind", CmPage::kind, is(CmPage.Kind.CONFIG)),
                        hasProperty("key", CmPage::key, is("config/com.acme.AcmeListenerConfig")),
                        hasProperty("dependentTypes", CmPage::dependentTypes, allOf(
                                hasProperty("size", Map::size, is(1)),
                                hasEntry("com.acme.AcmeServerConfig", "com_acme_AcmeServerConfig.html")
                        )),
                        hasProperty("usages", CmPage::usages, hasItem(allOf(
                                hasProperty("path", CmPage.Usage::path, is("server.sockets")),
                                hasProperty("fileName", CmPage.Usage::fileName, is("com_acme_AcmeServerConfig.html"))
                        )))
                )),
                hasItem(allOf(
                        hasProperty("kind", CmPage::kind, is(CmPage.Kind.CONFIG)),
                        hasProperty("key", CmPage::key, is("config/com.acme.AcmeOptionsConfig")),
                        hasProperty("dependentTypes", CmPage::dependentTypes, allOf(
                                hasProperty("size", Map::size, is(1)),
                                hasEntry("com.acme.AcmeServerConfig", "com_acme_AcmeServerConfig.html")
                        ))
                )),
                hasItem(allOf(
                        hasProperty("kind", CmPage::kind, is(CmPage.Kind.CONFIG)),
                        hasProperty("key", CmPage::key, is("config/com.acme.AcmeLoggerConfig")),
                        hasProperty("dependentTypes", CmPage::dependentTypes, hasProperty("empty", Map::isEmpty, is(true))),
                        hasProperty("tables", CmPage::tables, allOf(
                                hasProperty("standard", CmPage.Tables::standard,
                                        hasProperty("isEmpty", CmPage.Table::isEmpty, is(true))),
                                hasProperty("experimental", CmPage.Tables::experimental, allOf(
                                        hasProperty("rows", CmPage.Table::rows, contains(
                                                hasProperty("key", CmPage.Row::key, is("logger"))
                                        )),
                                        hasProperty("hasTypes", CmPage.Table::hasTypes, is(true)),
                                        hasProperty("hasDefaults", CmPage.Table::hasDefaults, is(false))
                                )),
                                hasProperty("deprecated", CmPage.Tables::deprecated, allOf(
                                        hasProperty("rows", CmPage.Table::rows, contains(
                                                hasProperty("key", CmPage.Row::key, is("level"))
                                        )),
                                        hasProperty("hasTypes", CmPage.Table::hasTypes, is(true)),
                                        hasProperty("hasDefaults", CmPage.Table::hasDefaults, is(true))
                                ))
                        ))
                )),
                hasItem(allOf(
                        hasProperty("kind", CmPage::kind, is(CmPage.Kind.CONTRACT)),
                        hasProperty("key", CmPage::key, is("contract/com.acme.AcmeFeature")),
                        hasProperty("usages", CmPage::usages, contains(allOf(
                                hasProperty("path", CmPage.Usage::path, is("server.features")),
                                hasProperty("fileName", CmPage.Usage::fileName, is("com_acme_AcmeServerConfig.html"))
                        ))),
                        hasProperty("tables", CmPage::tables, allOf(
                                hasProperty("standard", CmPage.Tables::standard,
                                        hasProperty("rows", CmPage.Table::rows, not(empty()))),
                                hasProperty("experimental", CmPage.Tables::experimental,
                                        hasProperty("isEmpty", CmPage.Table::isEmpty, is(true))),
                                hasProperty("deprecated", CmPage.Tables::deprecated,
                                        hasProperty("isEmpty", CmPage.Table::isEmpty, is(true)))
                        ))
                )),
                hasItem(allOf(
                        hasProperty("kind", CmPage::kind, is(CmPage.Kind.ENUM)),
                        hasProperty("key", CmPage::key, is("enum/com.acme.AcmeLoggerConfigLevel")),
                        hasProperty("usages", CmPage::usages, contains(allOf(
                                hasProperty("path", CmPage.Usage::path, is("server.features.logging.loggers.level")),
                                hasProperty("fileName", CmPage.Usage::fileName, is("com_acme_AcmeLoggerConfig.html"))
                        )))
                )),
                hasItem(allOf(
                        hasProperty("kind", CmPage::kind, is(CmPage.Kind.CONFIG)),
                        hasProperty("key", CmPage::key, is("config/metrics")),
                        hasProperty("typeName", CmPage::typeName, is("io.helidon.MetricsConfig")),
                        hasProperty("mergedTypes", CmPage::mergedTypes, allOf(
                                hasProperty("size", Map::size, is(2)),
                                hasEntry("com.acme.AcmeMetricsConfig", "com_acme_AcmeMetricsConfig.html"),
                                hasEntry("com.acme.AcmeMetricsObserverConfig",
                                        "com_acme_AcmeMetricsObserverConfig.html")
                        ))
                )),
                hasItem(allOf(
                        hasProperty("kind", CmPage::kind, is(CmPage.Kind.CONFIG)),
                        hasProperty("key", CmPage::key, is("config/data.sources")),
                        hasProperty("typeName", CmPage::typeName, is("io.helidon.data.SourcesConfig"))
                ))
        )));
    }

    @Test
    void testSyntheticPages() throws Exception {
        var resolver = resolver();

        assertThat(resolver.syntheticTypes(), hasEntry("io.helidon.MetricsConfig", "io_helidon_MetricsConfig.html"));
        assertThat(resolver.configTypes(), hasEntry("com.acme.AcmeServerConfig", "com_acme_AcmeServerConfig.html"));
        assertThat(resolver.contracts(), hasEntry("com.acme.AcmeFeature", "com_acme_AcmeFeature.html"));
        assertThat(resolver.enumTypes(), hasEntry("com.acme.AcmeLoggerConfigLevel", "com_acme_AcmeLoggerConfigLevel.html"));
    }

    static CmPageResolver resolver() throws Exception {
        var testDir = CmDocCodegenTest.testDir("baseline");
        try (var input = Files.newInputStream(testDir.resolve("config-metadata.json"))) {
            var model = CmModel.fromJson(input);
            return new CmPageResolver(model,
                    DUMMY_ROOT_PAGE,
                    PAGE_FILE_NAME_EXTENSION);
        }
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
