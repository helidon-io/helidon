/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

package io.helidon.service.tests.inject.toolbox.impl;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import io.helidon.service.inject.api.Injection;
import io.helidon.service.registry.Service;
import io.helidon.service.tests.inject.toolbox.Hammer;
import io.helidon.service.tests.inject.toolbox.Preferred;
import io.helidon.service.tests.inject.toolbox.Tool;
import io.helidon.service.tests.inject.toolbox.ToolBox;

@SuppressWarnings("unused")
@Injection.Singleton
public class MainToolBox implements ToolBox {

    private final List<Supplier<Tool>> allTools;
    private final List<Supplier<Hammer>> allHammers;
    private final Supplier<Hammer> bigHammer;
    private final Screwdriver screwdriver;
    public int postConstructCallCount;
    public int preDestroyCallCount;
    public int setterCallCount;
    @Injection.Inject
    @Preferred
    Supplier<Hammer> preferredHammer;
    private Supplier<Hammer> setPreferredHammer;

    @Injection.Inject
    MainToolBox(List<Supplier<Tool>> allTools,
                Screwdriver screwdriver,
                @Injection.Named("big") Supplier<Hammer> bigHammer,
                List<Supplier<Hammer>> allHammers) {
        this.allTools = Objects.requireNonNull(allTools);
        this.screwdriver = Objects.requireNonNull(screwdriver);
        this.bigHammer = bigHammer;
        this.allHammers = allHammers;
    }

    @Override
    public List<Supplier<Tool>> toolsInBox() {
        return allTools;
    }

    @Override
    public Supplier<Hammer> preferredHammer() {
        return preferredHammer;
    }

    public List<Supplier<Hammer>> allHammers() {
        return allHammers;
    }

    public Supplier<Hammer> bigHammer() {
        return bigHammer;
    }

    public Screwdriver screwdriver() {
        return screwdriver;
    }

    @Injection.Inject
    void setScrewdriver(Screwdriver screwdriver) {
        assert (this.screwdriver == screwdriver);
        setterCallCount++;
    }

    @Injection.Inject
    void setPreferredHammer(@Preferred Supplier<Hammer> hammer) {
        this.setPreferredHammer = hammer;
    }

    @Service.PostConstruct
    void postConstruct() {
        postConstructCallCount++;
    }

    @Service.PreDestroy
    void preDestroy() {
        preDestroyCallCount++;
    }

}
