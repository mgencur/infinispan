package org.infinispan.server.test.client.rest;

import org.infinispan.arquillian.core.InfinispanResource;
import org.infinispan.arquillian.core.RemoteInfinispanServer;
import org.infinispan.server.test.category.RESTClusteredStandalone;
import org.infinispan.server.test.category.Smoke;
import org.jboss.arquillian.junit.Arquillian;
import org.junit.BeforeClass;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.infinispan.server.test.util.ITestUtils.isLocalMode;
import static org.infinispan.server.test.util.ITestUtils.isReplicatedMode;

/**
 * Tests for the REST client.
 *
 * @author <a href="mailto:jvilkola@redhat.com">Jozef Vilkolak</a>
 * @author <a href="mailto:mlinhard@redhat.com">Michal Linhard</a>
 * @author mgencur
 */
@RunWith(Arquillian.class)
@Category({ RESTClusteredStandalone.class, Smoke.class })
public class RESTReplicationStandaloneIT extends AbstractRESTReplicationIT {

    private static final int REST_PORT1 = 8080;
    private static final int REST_PORT2 = 8180;

    @InfinispanResource("container1")
    RemoteInfinispanServer server1;

    @InfinispanResource("container2")
    RemoteInfinispanServer server2;

    @Override
    protected List<RemoteInfinispanServer> getServers() {
        List<RemoteInfinispanServer> servers = new ArrayList<RemoteInfinispanServer>();
        servers.add(server1);
        servers.add(server2);
        return Collections.unmodifiableList(servers);
    }

    @Override
    protected int getRestPort1() {
        return REST_PORT1;
    }

    @Override
    protected int getRestPort2() {
        return REST_PORT2;
    }
}
