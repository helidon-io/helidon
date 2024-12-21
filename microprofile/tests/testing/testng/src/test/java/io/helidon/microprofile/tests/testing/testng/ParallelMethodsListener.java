/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.stream.Stream;

import io.helidon.microprofile.testing.HelidonTestInfo;
import io.helidon.microprofile.testing.testng.HelidonTest;

import org.testng.IAlterSuiteListener;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.testng.xml.XmlClass;
import org.testng.xml.XmlSuite;
import org.testng.xml.XmlTest;

/**
 * An {@link IAlterSuiteListener} implementation that supports {@link ParallelMethods}.
 */
public class ParallelMethodsListener implements IAlterSuiteListener {

    @Override
    public void alter(List<XmlSuite> suites) {
        Map<String, Integer> suiteNames = new HashMap<>();
        ListIterator<XmlSuite> suitesIt = suites.listIterator();
        while (suitesIt.hasNext()) {
            XmlSuite suite = suitesIt.next();
            suite.setParallel(XmlSuite.ParallelMode.METHODS);
            for (XmlTest test : suite.getTests()) {
                long methodsCount = test.getXmlClasses().stream()
                        .map(XmlClass::getSupportClass)
                        .filter(c -> c.isAnnotationPresent(HelidonTest.class))
                        .flatMap(c -> Stream.of(c.getDeclaredMethods()))
                        .filter(m -> m.isAnnotationPresent(Test.class)
                            || m.isAnnotationPresent(BeforeClass.class)
                            || m.isAnnotationPresent(AfterClass.class))
                        .count();

                // TODO debug why tests are stuck even with 1 thread per method
                                test.setThreadCount((int) methodsCount);
            }
            //            ListIterator<XmlTest> testsIt = suite.getTests().listIterator();
            //            while (testsIt.hasNext()) {
            //                XmlTest test = testsIt.next();
            //                ListIterator<XmlClass> classesIt = test.getXmlClasses().listIterator();
            //                while (classesIt.hasNext()) {
            //                    XmlClass xmlClass = classesIt.next();
            //                    Class<?> testClass = xmlClass.getSupportClass();
            //                    if (testClass.isAnnotationPresent(ParallelMethods.class)) {
            //                        classesIt.remove();
            //                        if (!classesIt.hasPrevious() && !classesIt.hasNext()) {
            //                            testsIt.remove();
            //                        }
            //                        if (!testsIt.hasPrevious() && !testsIt.hasNext()) {
            //                            suitesIt.remove();
            //                        }
            //                        XmlTest parallelTest = new XmlTest();
            //                        parallelTest.setName(testClass.getSimpleName());
            //                        parallelTest.getXmlClasses().add(xmlClass);
            //                        XmlSuite parallelSuite = new XmlSuite();
            //                        String suiteName = parallelSuite.getName();
            //                        parallelSuite.setName(String.format("%s(%d)", suiteName,
            //                                suiteNames.compute(suiteName, (k, v) -> v == null ? 1 : v + 1)));
            //                        parallelSuite.setParallel(XmlSuite.ParallelMode.METHODS);
            //                        parallelSuite.addTest(parallelTest);
            //                        parallelTest.setSuite(parallelSuite);
            //                        suitesIt.add(parallelSuite);
            //                    }
            //                }
            //            }
        }
    }
}
