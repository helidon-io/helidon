/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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
package io.helidon.integrations.graal.mp.nativeimage.extension;

import java.lang.reflect.Type;
import java.security.ProtectionDomain;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Inject;
import com.oracle.svm.core.annotate.InjectAccessors;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import org.jboss.classfilewriter.ClassFile;
import org.jboss.weld.event.ObserverNotifier;
import org.jboss.weld.executor.DaemonThreadFactory;
import org.jboss.weld.util.reflection.ParameterizedTypeImpl;

/**
 * Substitutions needed for Weld.
 */
public class WeldSubstitutions {
    @TargetClass(className = "org.jboss.weld.bootstrap.events.ContainerLifecycleEventPreloader")
    static final class ContainerLifecycleEventPreloaderSubstitution {
        @Alias
        private ObserverNotifier notifier;

        @Alias
        @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset, isFinal = true)
        private ExecutorService executor;

        @Inject
        @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)
        private final DaemonThreadFactory dtf = new DaemonThreadFactory(new ThreadGroup("weld-preloaders"),
                                                                        "weld-preloader-");

        @Substitute
        void preloadContainerLifecycleEvent(Class<?> eventRawType, Type... typeParameters) {
            dtf.newThread(new Runnable() {
                @Override
                public void run() {
                    notifier.resolveObserverMethods(new ParameterizedTypeImpl(eventRawType, typeParameters, null));
                }
            }).start();
        }

        @Substitute
        void shutdown() {
        }
    }

    @TargetClass(className = "org.jboss.weld.event.ObserverNotifier")
    static final class ObserverNotifierSubstitution {
        @Alias
        @InjectAccessors(ForkJoinAccessors.class)
        private Executor asyncEventExecutor;
    }

    /**
     * Injected when building native-image.
     */
    public static final class ForkJoinAccessors {
        /**
         * Getter.
         * @param object object
         * @return executor from {@link java.util.concurrent.ForkJoinPool}
         */
        public static Executor get(Object object) {
            return ForkJoinPool.commonPool();
        }

        /**
         * Setter - does nothing.
         * @param object object
         * @param executor executor
         */
        public static void set(Object object, Executor executor) {

        }
    }

    @TargetClass(className = "org.jboss.weld.util.bytecode.ClassFileUtils")
    static final class ClassFileUtils {
        @Substitute
        public static Class<?> toClass(ClassFile ct, ClassLoader loader, ProtectionDomain domain) {
            throw new IllegalStateException("Cannot load " + ct.getName());
        }
    }
}
