package io.smallrye.reactive.messaging.tck;

import javax.enterprise.inject.spi.Extension;

import io.helidon.microprofile.messaging.MessagingCdiExtension;
import org.eclipse.microprofile.reactive.messaging.tck.ArchiveExtender;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;


public class HelidonMessagingExtender implements ArchiveExtender {
    @Override
    public void extend(JavaArchive archive) {
        archive
                .addPackages(true, MessagingCdiExtension.class.getPackage())
                .addAsServiceProvider(Extension.class, MessagingCdiExtension.class)
                .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml");
    }
}
