/*
 * Copyright (c) 2019, 2023 Oracle and/or its affiliates.
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

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.context.control.RequestContextController;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.se.SeContainer;
import jakarta.enterprise.inject.se.SeContainerInitializer;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;

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
        assertThat(initializer, notNullValue());
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
        assertThat(controller, notNullValue());
        assertThat(beanManager, notNullValue());
        assertThat(gorpAsParameter, notNullValue());

        final Set<Bean<?>> gorpBeans = beanManager.getBeans(Gorp.class);
        assertThat(gorpBeans, notNullValue());
        assertThat(gorpBeans, hasSize(1));
        @SuppressWarnings("unchecked")
        final Bean<Gorp> gorpBean = (Bean<Gorp>)gorpBeans.iterator().next();
        assertThat(gorpBean, notNullValue());

        final ReferenceCountedContext context = ReferenceCountedContext.getInstanceFrom(beanManager);
        assertThat(context, notNullValue());
        assertThat(context.isActive(), is(true));

        // Gorp is @ReferenceCounted.  It is proxied.  Note that we do
        // not dereference the gorpAsParameter proxy yet, so no
        // underlying instance is created.
        assertThat(context.getReferenceCount(gorpBean), is(0));

        Gorp gorp = null;
        try {
            controller.activate();

            // this.bean is @RequestScoped.  It houses an instance of
            // Gorp.
            assertThat(this.bean, notNullValue());
            assertThat(context.getReferenceCount(gorpBean), is(0));

            // Here, the gorp acquired is a client proxy.  No
            // contextual instance has been created yet.
            gorp = this.bean.getGorp();
            assertThat(gorp, notNullValue());
            assertThat(context.getReferenceCount(gorpBean), is(0));

            // This will cause the underlying instance to be created.
            // It will be the first Gorp instance created anywhere.
            // The reference count is 1.
            assertThat(gorp.getId(), is(1));
            assertThat(context.getReferenceCount(gorpBean), is(1));

        } finally {

            // Now the RequestScope goes away.  So HousingBean is
            // disposed.  Its internal Gorp reference gets its
            // reference count decremented.  This results in a
            // reference count of 0.  This Gorp reference is therefore
            // destroyed.
            controller.deactivate();
            assertThat(context.getReferenceCount(gorpBean), is(0));
        }

        // Now we kick the proxy.  This will cause another instance to
        // be created.  The reference count will bump to 1.
        assertThat(gorpAsParameter.getId(), is(2));
        assertThat(context.getReferenceCount(gorpBean), is(1));

        // Next we refer to the Gorp instance that was referred to by
        // the request-scoped HousingBean.  If Gorp had been
        // Dependent-scoped, then this would result in a new instance
        // being created.  But because Gorp is ReferenceCounted, and
        // because the gorpParameter dereference above caused the
        // reference count to jump, THIS Gorp instance will now be the
        // same one as gorpAsParameter.
        assertThat(gorp.getId(), is(2));
        assertThat(context.getReferenceCount(gorpBean), is(2));

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
