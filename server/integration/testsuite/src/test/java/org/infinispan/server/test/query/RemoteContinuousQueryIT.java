package org.infinispan.server.test.query;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.infinispan.arquillian.core.InfinispanResource;
import org.infinispan.arquillian.core.RemoteInfinispanServer;
import org.infinispan.client.hotrod.Search;
import org.infinispan.protostream.sampledomain.User;
import org.infinispan.query.api.continuous.ContinuousQuery;
import org.infinispan.query.api.continuous.ContinuousQueryListener;
import org.infinispan.query.dsl.Query;
import org.infinispan.query.dsl.QueryFactory;
import org.infinispan.server.test.category.Queries;
import org.infinispan.server.test.category.SingleNode;
import org.infinispan.server.test.util.ManagementClient;
import org.jboss.arquillian.junit.Arquillian;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

/**
 * Basic tests for continuous query over HotRod.
 *
 * @author vjuranek
 * @since 8.1
 */
@Category({SingleNode.class})
@RunWith(Arquillian.class)
public class RemoteContinuousQueryIT extends RemoteQueryBaseIT {

   private static final String CACHE_TEMPLATE = "localTestCacheConfiguration";
   private static final String CACHE_CONTAINER = "local";
   private static final String TEST_CACHE = "localtestcache";

   @InfinispanResource("container1")
   protected RemoteInfinispanServer server;

   public RemoteContinuousQueryIT() {
      super(CACHE_CONTAINER, TEST_CACHE);
   }

   @Override
   protected RemoteInfinispanServer getServer() {
      return server;
   }

   @BeforeClass
   public static void beforeClass() throws Exception {
      ManagementClient client = ManagementClient.getStandaloneInstance();
      client.addCacheConfiguration(CACHE_TEMPLATE, CACHE_CONTAINER, ManagementClient.CacheTemplate.LOCAL);
      Map<String, String> properties = new HashMap<>();
      properties.put("default.directory_provider", "ram");
      properties.put("lucene_version", "LUCENE_CURRENT");
      client.enableIndexingForConfiguration(CACHE_TEMPLATE, CACHE_CONTAINER, ManagementClient.CacheTemplate.LOCAL, ManagementClient.IndexingType.ALL, properties);
      client.addCache(TEST_CACHE, CACHE_CONTAINER, CACHE_TEMPLATE, ManagementClient.CacheType.LOCAL);
   }

   @AfterClass
   public static void afterClass() throws Exception {
      ManagementClient client = ManagementClient.getStandaloneInstance();
      client.removeCache(TEST_CACHE, CACHE_CONTAINER, ManagementClient.CacheType.LOCAL);
      client.removeCacheConfiguration(CACHE_TEMPLATE, CACHE_CONTAINER, ManagementClient.CacheTemplate.LOCAL);
   }

   @Test
   public void testContinuousQuery() throws Exception {
      remoteCache.put(1, createUser(1, 25));
      remoteCache.put(2, createUser(2, 25));
      remoteCache.put(3, createUser(3, 20));
      assertEquals(3, remoteCache.size());

      QueryFactory qf = Search.getQueryFactory(remoteCache);
      Query query = qf.from(User.class).having("name").eq("user1").and().having("age").gt(20).build();

      final BlockingQueue<Integer> joined = new LinkedBlockingQueue<>();
      final BlockingQueue<Integer> updated = new LinkedBlockingQueue<>();
      final BlockingQueue<Integer> left = new LinkedBlockingQueue<>();

      ContinuousQueryListener<Integer, User> listener = new ContinuousQueryListener<Integer, User>() {
         @Override
         public void resultJoining(Integer key, User value) {
            joined.add(key);
         }

         @Override
         public void resultUpdated(Integer key, User value) {
            updated.add(key);
         }

         @Override
         public void resultLeaving(Integer key) {
            left.add(key);
         }
      };
      ContinuousQuery<Integer, User> continuousQuery = Search.getContinuousQuery(remoteCache);
      continuousQuery.addContinuousQueryListener(query, listener);

      expectElementsInQueue(joined, 1);
      expectElementsInQueue(updated, 0);
      expectElementsInQueue(left, 0);

      User user4 = createUser(4, 30);
      user4.setName("user1");
      remoteCache.put(4, user4);
      expectElementsInQueue(joined, 1);
      expectElementsInQueue(updated, 0);
      expectElementsInQueue(left, 0);

      User user1 = remoteCache.get(1);
      user1.setAge(19);
      remoteCache.put(1, user1);
      expectElementsInQueue(joined, 0);
      expectElementsInQueue(updated, 0);
      expectElementsInQueue(left, 1);

      user4 = remoteCache.get(4);
      user4.setAge(32);
      remoteCache.put(4, user4);
      expectElementsInQueue(joined, 0);
      expectElementsInQueue(updated, 1);
      expectElementsInQueue(left, 0);

      remoteCache.clear();
      expectElementsInQueue(joined, 0);
      expectElementsInQueue(updated, 0);
      expectElementsInQueue(left, 1);

      continuousQuery.removeContinuousQueryListener(listener);
      user1.setAge(25);
      remoteCache.put(1, user1);
      expectElementsInQueue(joined, 0);
      expectElementsInQueue(updated, 0);
      expectElementsInQueue(left, 0);
   }

   private User createUser(int id, int age) {
      User user = new User();
      user.setId(id);
      user.setName("user" + id);
      user.setAge(age);
      user.setSurname("Doesn't matter");
      user.setGender(User.Gender.MALE);
      return user;
   }

   private void expectElementsInQueue(BlockingQueue<?> queue, int numElements) {
      for (int i = 0; i < numElements; i++) {
         try {
            Object e = queue.poll(5, TimeUnit.SECONDS);
            assertNotNull("Queue was empty!", e);
         } catch (InterruptedException e) {
            throw new AssertionError("Interrupted while waiting for condition", e);
         }
      }
      try {
         // no more elements expected here
         Object e = queue.poll(5, TimeUnit.SECONDS);
         assertNull("No more elements expected in queue!", e);
      } catch (InterruptedException e) {
         throw new AssertionError("Interrupted while waiting for condition", e);
      }
   }
}
