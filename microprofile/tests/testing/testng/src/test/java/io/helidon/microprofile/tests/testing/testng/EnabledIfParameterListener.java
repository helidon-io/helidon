/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.microprofile.tests.testing.testng;

import java.util.List;
import java.util.ListIterator;

import org.testng.IAlterSuiteListener;
import org.testng.xml.XmlClass;
import org.testng.xml.XmlPackage;
import org.testng.xml.XmlSuite;
import org.testng.xml.XmlTest;

/**
 * An {@link IAlterSuiteListener} implementation that supports {@link EnabledIfParameter}.
 */
public final class EnabledIfParameterListener implements IAlterSuiteListener {

    @Override
    public void alter(List<XmlSuite> suites) {
        for (XmlSuite suite : suites) {
            ListIterator<XmlTest> testsIt = suite.getTests().listIterator();
            while (testsIt.hasNext()) {
                XmlTest xmlTest = testsIt.next();
                ListIterator<XmlClass> classesIt = xmlTest.getXmlClasses().listIterator();
                while (classesIt.hasNext()) {
                    XmlClass xmlClass = classesIt.next();
                    if (isDisabled(xmlClass, xmlTest)) {
                        // remove test class
                        classesIt.remove();
                        removeIfEmpty(classesIt, testsIt);
                    }
                }
                ListIterator<XmlPackage> packagesIt = xmlTest.getXmlPackages().listIterator();
                while (packagesIt.hasNext()) {
                    XmlPackage xmlPackage = packagesIt.next();
                    ListIterator<XmlClass> pkgClassesIt = xmlPackage.getXmlClasses().listIterator();
                    while (pkgClassesIt.hasNext()) {
                        XmlClass xmlClass = pkgClassesIt.next();
                        if (isDisabled(xmlClass, xmlTest)) {
                            // remove test class
                            pkgClassesIt.remove();
                            removeIfEmpty(pkgClassesIt, packagesIt);
                            removeIfEmpty(pkgClassesIt, testsIt);
                        }
                    }
                }
            }
        }
    }

    private static boolean isDisabled(XmlClass xmlClass, XmlTest xmlTest) {
        Class<?> testClass = xmlClass.getSupportClass();
        EnabledIfParameter annotation = testClass.getAnnotation(EnabledIfParameter.class);
        return annotation != null && !annotation.value().equals(xmlTest.getParameter(annotation.key()));
    }

    private static void removeIfEmpty(ListIterator<?> childIt, ListIterator<?> parentIt) {
        if (!childIt.hasPrevious() && !childIt.hasNext()) {
            parentIt.remove();
        }
    }
}
