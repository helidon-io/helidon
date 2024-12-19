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

package io.helidon.service.tests.toolbox.impl;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import io.helidon.service.registry.Service;
import io.helidon.service.tests.toolbox.Hammer;
import io.helidon.service.tests.toolbox.Preferred;
import io.helidon.service.tests.toolbox.Tool;
import io.helidon.service.tests.toolbox.ToolBox;

@SuppressWarnings("unused")
@Service.Singleton
public class MainToolBox implements ToolBox {

    private final Supplier<List<Tool>> allTools;
    private final Supplier<List<Hammer>> allHammers;
    private final Supplier<Hammer> bigHammer;
    private final Screwdriver screwdriver;
    public int postConstructCallCount;
    public int preDestroyCallCount;
    public int setterCallCount;
    @Service.Inject
    @Preferred
    Supplier<Hammer> preferredHammer;
    private Supplier<Hammer> setPreferredHammer;

    @Service.Inject
    MainToolBox(Supplier<List<Tool>> allTools,
                Screwdriver screwdriver,
                @Service.Named("big") Supplier<Hammer> bigHammer,
                Supplier<List<Hammer>> allHammers) {
        this.allTools = Objects.requireNonNull(allTools);
        this.screwdriver = Objects.requireNonNull(screwdriver);
        this.bigHammer = bigHammer;
        this.allHammers = allHammers;
    }

    @Override
    public Supplier<List<Tool>> toolsInBox() {
        return allTools;
    }

    @Override
    public Supplier<Hammer> preferredHammer() {
        return preferredHammer;
    }

    public Supplier<List<Hammer>> allHammers() {
        return allHammers;
    }

    public Supplier<Hammer> bigHammer() {
        return bigHammer;
    }

    public Screwdriver screwdriver() {
        return screwdriver;
    }

    @Service.Inject
    void setScrewdriver(Screwdriver screwdriver) {
        assert (this.screwdriver == screwdriver);
        setterCallCount++;
    }

    @Service.Inject
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
