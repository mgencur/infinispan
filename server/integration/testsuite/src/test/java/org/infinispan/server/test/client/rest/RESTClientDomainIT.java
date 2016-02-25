package org.infinispan.server.test.client.rest;

import org.infinispan.arquillian.core.InfinispanResource;
import org.infinispan.arquillian.core.RemoteInfinispanServer;
import org.infinispan.server.test.category.RESTLocalDomain;
import org.infinispan.server.test.util.ManagementClient;
import org.jboss.arquillian.junit.Arquillian;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

/**
 *
 * @author <a href="mailto:jvilkola@redhat.com">Jozef Vilkolak</a>
 * @author <a href="mailto:mlinhard@redhat.com">Michal Linhard</a>
 * @author mgencur
 *
 */
@RunWith(Arquillian.class)
@Category({ RESTLocalDomain.class })
public class RESTClientDomainIT extends AbstractRESTClientIT {

    private static final int REST_PORT = 8082;

    @InfinispanResource(value = "master:server-one", jmxPort = 4447)
    RemoteInfinispanServer server1;

    @BeforeClass
    public static void beforeClass() throws Exception {
        ManagementClient client = ManagementClient.getInstance();
        client.addSocketBinding("rest-local", "clustered-sockets", REST_PORT);
        client.addLocalCache("restLocalCache", "clustered", "localCacheConfiguration");
        client.addLocalCache(REST_NAMED_CACHE, "clustered", "localCacheConfiguration");
        client.addRestEndpoint("restLocal", "clustered", "restLocalCache", "rest-local");
    }

    @AfterClass
    public static void afterClass() throws Exception {
        ManagementClient client = ManagementClient.getInstance();
        client.removeRestEndpoint("restLocal");
        client.removeLocalCache(REST_NAMED_CACHE, "clustered");
        client.removeLocalCache("restLocalCache", "clustered");
        client.removeLocalCacheConfiguration("localCacheConfiguration", "clustered");
        client.removeSocketBinding("rest-local", "clustered-sockets");
    }

    @Override
    protected void addRestServer() {
        RESTHelper.addServer(server1.getRESTEndpoint().getInetAddress().getHostName(), REST_PORT, server1.getRESTEndpoint().getContextPath());
    }
}
