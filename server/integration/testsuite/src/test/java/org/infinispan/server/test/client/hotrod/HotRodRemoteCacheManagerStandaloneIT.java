package org.infinispan.server.test.client.hotrod;

import org.infinispan.arquillian.core.InfinispanResource;
import org.infinispan.arquillian.core.RemoteInfinispanServer;
import org.infinispan.server.test.category.HotRodClusteredStandalone;
import org.infinispan.server.test.category.HotRodStandalone;
import org.infinispan.server.test.category.Smoke;
import org.jboss.arquillian.junit.Arquillian;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.infinispan.server.test.util.ITestUtils.isLocalMode;

/**
 * Tests for the HotRod client RemoteCacheManager class in standalone mode.
 *
 * @author mgencur
 */
@RunWith(Arquillian.class)
@Category({ HotRodStandalone.class, HotRodClusteredStandalone.class, Smoke.class })
public class HotRodRemoteCacheManagerStandaloneIT extends AbstractRemoteCacheManagerIT {

    @InfinispanResource("container1")
    RemoteInfinispanServer server1;

    @InfinispanResource("container2")
    RemoteInfinispanServer server2;

    @Override
    protected List<RemoteInfinispanServer> getServers() {
        List<RemoteInfinispanServer> servers = new ArrayList<RemoteInfinispanServer>();
        servers.add(server1);
        if (!isLocalMode()) {
            servers.add(server2);
        }
        return Collections.unmodifiableList(servers);
    }
}
