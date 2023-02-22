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

import io.helidon.pico.PicoServices;
import io.helidon.pico.ServiceProvider;
import io.helidon.pico.Services;

public class MainPico {

    public static void main(String[] args) {
        final long memStart = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        final long start = System.currentTimeMillis();

        Services services = PicoServices.picoServices().orElseThrow().services();

        ServiceProvider<Library> librarySp = services.lookupFirst(Library.class);
        System.out.println("found a library provider: " + librarySp.description());
        Library library = librarySp.get();
        System.out.println("library: " + library);

        ServiceProvider<ColorWheel> colorWheelSp = services.lookupFirst(ColorWheel.class);
        System.out.println("found a color wheel provider: " + colorWheelSp.description());
        ColorWheel colorWheel = colorWheelSp.get();
        System.out.println("color wheel: " + colorWheel);

        final long finish = System.currentTimeMillis();
        final long memFinish = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        System.out.println("Pico Main memory consumption = " + (memFinish - memStart) + " bytes");
        System.out.println("Pico Main elapsed time = " + (finish - start) + " ms");
    }

}
