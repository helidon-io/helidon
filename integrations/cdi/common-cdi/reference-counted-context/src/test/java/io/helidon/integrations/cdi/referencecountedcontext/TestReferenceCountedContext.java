/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates.
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
package io.helidon.integrations.cdi.referencecountedcontext;

import java.util.Set;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Initialized;
import javax.enterprise.context.RequestScoped;
import javax.enterprise.context.control.RequestContextController;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.se.SeContainer;
import javax.enterprise.inject.se.SeContainerInitializer;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.inject.Inject;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ApplicationScoped
final class TestReferenceCountedContext {

    @Inject
    private HousingBean bean;

    private SeContainer container;

    private TestReferenceCountedContext() {
        super();
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    private final void startContainer() {
        stopContainer();
        final SeContainerInitializer initializer = SeContainerInitializer.newInstance();
        assertNotNull(initializer);
        initializer.disableDiscovery();
        initializer.addBeanClasses(this.getClass(), HousingBean.class, Gorp.class);
        initializer.addExtensions(ReferenceCountedExtension.class);
        this.container = initializer.initialize();
    }

    @AfterEach
    private final void stopContainer() {
        if (this.container != null) {
            this.container.close();
            this.container = null;
        }
    }

    private final void onStartup(@Observes @Initialized(ApplicationScoped.class) final Object event,
                                 final RequestContextController controller,
                                 final BeanManager beanManager,
                                 final Gorp gorpAsParameter) {
        assertNotNull(controller);
        assertNotNull(beanManager);
        assertNotNull(gorpAsParameter);

        final Set<Bean<?>> gorpBeans = beanManager.getBeans(Gorp.class);
        assertNotNull(gorpBeans);
        assertEquals(1, gorpBeans.size());
        @SuppressWarnings("unchecked")
        final Bean<Gorp> gorpBean = (Bean<Gorp>)gorpBeans.iterator().next();
        assertNotNull(gorpBean);

        final ReferenceCountedContext context = ReferenceCountedContext.getInstanceFrom(beanManager);
        assertNotNull(context);
        assertTrue(context.isActive());

        // Gorp is @ReferenceCounted.  It is proxied.  Note that we do
        // not dereference the gorpAsParameter proxy yet, so no
        // underlying instance is created.
        assertEquals(0, context.getReferenceCount(gorpBean));

        Gorp gorp = null;
        try {
            controller.activate();

            // this.bean is @RequestScoped.  It houses an instance of
            // Gorp.
            assertNotNull(this.bean);
            assertEquals(0, context.getReferenceCount(gorpBean));

            // Here, the gorp acquired is a client proxy.  No
            // contextual instance has been created yet.
            gorp = this.bean.getGorp();
            assertNotNull(gorp);
            assertEquals(0, context.getReferenceCount(gorpBean));

            // This will cause the underlying instance to be created.
            // It will be the first Gorp instance created anywhere.
            // The reference count is 1.
            assertEquals(1, gorp.getId());
            assertEquals(1, context.getReferenceCount(gorpBean));

        } finally {

            // Now the RequestScope goes away.  So HousingBean is
            // disposed.  Its internal Gorp reference gets its
            // reference count decremented.  This results in a
            // reference count of 0.  This Gorp reference is therefore
            // destroyed.
            controller.deactivate();
            assertEquals(0, context.getReferenceCount(gorpBean));
        }

        // Now we kick the proxy.  This will cause another instance to
        // be created.  The reference count will bump to 1.
        assertEquals(2, gorpAsParameter.getId());
        assertEquals(1, context.getReferenceCount(gorpBean));

        // Next we refer to the Gorp instance that was referred to by
        // the request-scoped HousingBean.  If Gorp had been
        // Dependent-scoped, then this would result in a new instance
        // being created.  But because Gorp is ReferenceCounted, and
        // because the gorpParameter dereference above caused the
        // reference count to jump, THIS Gorp instance will now be the
        // same one as gorpAsParameter.
        assertEquals(2, gorp.getId());
        assertEquals(2, context.getReferenceCount(gorpBean));

    }

    @Test
    final void testStorage() {

    }

    @RequestScoped
    private static class HousingBean {

        @Inject
        private Gorp gorp;

        private HousingBean() {
            super();
        }

        public Gorp getGorp() {
            return this.gorp;
        }

    }

    @ReferenceCounted
    private static class Gorp {

        private static volatile int staticId;

        private final int id;

        private Gorp() {
            super();
            this.id = ++staticId;
        }

        public int getId() {
            return this.id;
        }

    }

}
