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

import org.glassfish.hk2.api.ServiceHandle;
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.hk2.utilities.ServiceLocatorUtilities;

public class MainHk2 {

    public static void main(String[] args) {
        final long memStart = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        final long start = System.currentTimeMillis();

        ServiceLocator locator = ServiceLocatorUtilities.createAndPopulateServiceLocator();

        try {
            ServiceHandle<Library> librarySh = locator.getServiceHandle(Library.class);
            System.out.println("found a library handle: " + librarySh.getActiveDescriptor());
            Library library = librarySh.getService();
            System.out.println("found a library: " + library);
        } catch (Exception e) {
            // list injection is not supported in Hk2 - must switch to use IterableProvider instead.
            // see https://javaee.github.io/hk2/apidocs/org/glassfish/hk2/api/IterableProvider.html
            // and see https://javaee.github.io/hk2/introduction.html
            System.out.println("error: " + e.getMessage());
        }

        ServiceHandle<ColorWheel> colorWheelSh = locator.getServiceHandle(ColorWheel.class);
        System.out.println("found a color wheel handle: " + colorWheelSh.getActiveDescriptor());
        ColorWheel library = colorWheelSh.getService();
        System.out.println("color wheel: " + library);

        final long finish = System.currentTimeMillis();
        final long memFinish = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        System.out.println("Hk2 Main memory consumption = " + (memFinish - memStart) + " bytes");
        System.out.println("Hk2 Main elapsed time = " + (finish - start) + " ms");
    }

}
