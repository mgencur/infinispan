package org.infinispan.server.test.client.rest;

import org.infinispan.arquillian.core.InfinispanResource;
import org.infinispan.arquillian.core.RemoteInfinispanServer;
import org.infinispan.server.test.category.RESTSingleNode;
import org.infinispan.server.test.category.SingleNode;
import org.infinispan.server.test.util.ManagementClient;
import org.jboss.arquillian.junit.Arquillian;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

/**
 * Test a custom REST client connected to a single Infinispan server.
 * The server is running in standalone mode.
 *
 * @author mgencur
 */
@RunWith(Arquillian.class)
@Category({SingleNode.class})
public class RESTClientIT extends AbstractRESTClientIT {

    private static final String CACHE_TEMPLATE = "localCacheConfiguration";
    private static final String CACHE_CONTAINER = "local";
    private static final String REST_ENDPOINT = "rest-connector";

    @InfinispanResource("container1")
    RemoteInfinispanServer server1;

    @BeforeClass
    public static void beforeClass() throws Exception {
        ManagementClient client = ManagementClient.getStandaloneInstance();
        client.addCacheConfiguration(CACHE_TEMPLATE, CACHE_CONTAINER, ManagementClient.CacheTemplate.LOCAL);
        client.addCache(REST_NAMED_CACHE, CACHE_CONTAINER, CACHE_TEMPLATE, ManagementClient.CacheType.LOCAL);
        client.removeRestEndpoint(REST_ENDPOINT);
        client.addRestEndpoint(REST_ENDPOINT, CACHE_CONTAINER, REST_NAMED_CACHE, "rest");
        client.reloadIfRequired();
    }

    @AfterClass
    public static void afterClass() throws Exception {
        ManagementClient client = ManagementClient.getStandaloneInstance();
        client.removeRestEndpoint(REST_ENDPOINT);
        client.removeCache(REST_NAMED_CACHE, CACHE_CONTAINER, ManagementClient.CacheType.LOCAL);
        client.removeCacheConfiguration(CACHE_TEMPLATE, CACHE_CONTAINER, ManagementClient.CacheTemplate.LOCAL);
    }

    @Override
    protected void addRestServer() {
        server1.reconnect();
        rest.addServer(server1.getRESTEndpoint().getInetAddress().getHostName(), server1.getRESTEndpoint().getContextPath());
    }
}
