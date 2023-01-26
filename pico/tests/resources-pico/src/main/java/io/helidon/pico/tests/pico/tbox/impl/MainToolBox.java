/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.pico.tests.pico.tbox.impl;

import java.util.List;
import java.util.Objects;

import io.helidon.pico.tests.pico.tbox.Hammer;
import io.helidon.pico.tests.pico.tbox.Preferred;
import io.helidon.pico.tests.pico.tbox.Tool;
import io.helidon.pico.tests.pico.tbox.ToolBox;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

@Singleton
public class MainToolBox implements ToolBox {

    private final List<Provider<Tool>> allTools;
    private final List<Provider<Hammer>> allHammers;
    private final Provider<Hammer> bigHammer;

    @Inject
    @Preferred
    Provider<Hammer> preferredHammer;

    private Screwdriver screwdriver;

    public int postConstructCallCount;
    public int preDestroyCallCount;
    public int setterCallCount;

    @Inject
    MainToolBox(
            List<Provider<Tool>> allTools,
            Screwdriver screwdriver,
            @Named("big") Provider<Hammer> bigHammer,
            List<Provider<Hammer>> allHammers) {
        this.allTools = Objects.requireNonNull(allTools);
        this.screwdriver = Objects.requireNonNull(screwdriver);
        this.bigHammer = bigHammer;
        this.allHammers = allHammers;
    }

    @Inject
    void setScrewdriver(Screwdriver screwdriver) {
        assert(this.screwdriver == screwdriver);
        setterCallCount++;
    }

    @Override
    public List<Provider<Tool>> toolsInBox() {
        return allTools;
    }

    @Override
    public Provider<Hammer> preferredHammer() {
        return preferredHammer;
    }

    public List<Provider<Hammer>> allHammers() {
        return allHammers;
    }

    public Provider<Hammer> bigHammer() {
        return bigHammer;
    }

    public Screwdriver screwdriver() {
        return screwdriver;
    }

    @PostConstruct
    void postConstruct() {
        postConstructCallCount++;
    }

    @PreDestroy
    void preDestroy() {
        preDestroyCallCount++;
    }

}
