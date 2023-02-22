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

package io.helidon.examples.pico.book;

import java.util.List;
import java.util.Optional;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import static io.helidon.pico.services.Utils.toDescription;
import static io.helidon.pico.services.Utils.toDescriptions;

/**
 * Demonstrates setter type injection points w/ qualifiers & optionals.
 */
@org.jvnet.hk2.annotations.Service
public class ColorWheel {
    Optional<?> preferredOptionalRedThing;
    Optional<?> preferredOptionalGreenThing;
    Optional<?> preferredOptionalBlueThing;

    Provider<?> preferredProviderRedThing;
    Provider<?> preferredProviderGreenThing;
    Provider<?> preferredProviderBlueThing;

    @Inject
    void setPreferredOptionalRedThing(@org.jvnet.hk2.annotations.Optional @Red Optional<Color> thing) {
        System.out.println("setting optional color wheel red to " + thing);
        preferredOptionalRedThing = thing;
    }

    @Inject
    void setPreferredOptionalGreenThing(@org.jvnet.hk2.annotations.Optional @Green Optional<Color> thing) {
        System.out.println("setting optional color wheel green to " + thing);
        preferredOptionalGreenThing = thing;
    }

    @Inject
    void setPreferredOptionalBlueThing(@org.jvnet.hk2.annotations.Optional @Blue Optional<Color> thing) {
        System.out.println("setting optional color wheel blue to " + thing);
        preferredOptionalBlueThing = thing;
    }

    @Inject
    void setPreferredProviderRedThing(@org.jvnet.hk2.annotations.Optional @Red Provider<Color> thing) {
        System.out.println("setting provider color wheel red to " + toDescription(thing));
        preferredProviderRedThing = thing;
    }

    @Inject
    void setPreferredProviderGreenThing(@org.jvnet.hk2.annotations.Optional @Green Provider<Color> thing) {
        System.out.println("setting provider wheel green to " + toDescription(thing));
        preferredProviderGreenThing = thing;
    }

    @Inject
    void setPreferredBlueThing(@org.jvnet.hk2.annotations.Optional @Blue Provider<Color> thing) {
        System.out.println("setting provider wheel blue to " + toDescription(thing));
        preferredProviderBlueThing = thing;
    }

    @Inject
    void setListProviderRedThings(@org.jvnet.hk2.annotations.Optional @Red List<Provider<Color>> things) {
        System.out.println("setting providerList color wheel red to " + (things == null ? "null" : toDescriptions(things)));
    }

    @Inject
    void setListProviderGreenThings(@org.jvnet.hk2.annotations.Optional @Green List<Provider<Color>> things) {
        System.out.println("setting providerList wheel green to " + (things == null ? "null" : toDescriptions(things)));
    }

    @Inject
    void setListProviderBlueThings(@org.jvnet.hk2.annotations.Optional @Blue List<Provider<Color>> things) {
        System.out.println("setting providerList wheel blue to " + (things == null ? "null" : toDescriptions(things)));
    }

    // not supported by Pico...
//    @Inject
//    void setIterableProviderRedThings(@org.jvnet.hk2.annotations.Optional @Red IterableProvider<Color> things) {
//        System.out.println("setting iterableList color wheel red to " + things);
//    }
//
//    @Inject
//    void setIterableProviderGreenThings(@org.jvnet.hk2.annotations.Optional @Green IterableProvider<Color> things) {
//        System.out.println("setting iterableList wheel green to " + things);
//    }
//
//    @Inject
//    void setIterableProviderBlueThings(@org.jvnet.hk2.annotations.Optional @Blue IterableProvider<Color> things) {
//        System.out.println("setting iterableList wheel blue to " + things);
//    }

}
