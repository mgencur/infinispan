package org.infinispan.it.osgi.tx;

import org.infinispan.Cache;
import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.it.osgi.BaseInfinispanCoreOSGiTest;
import org.infinispan.it.osgi.Osgi;
import org.infinispan.manager.CacheContainer;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.remoting.rpc.RpcManagerImpl;
import org.infinispan.test.TestingUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;

import javax.transaction.Transaction;
import javax.transaction.TransactionManager;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * Test cloned from {@link org.infinispan.tx.TransactionsSpanningReplicatedCachesTest} and modified for running in Karaf
 * with JUnit.
 *
 * @author mmarkus
 * @author mgencur
 */
@RunWith(PaxExam.class)
@Category(Osgi.class)
@ExamReactorStrategy(PerClass.class)
public class TransactionsSpanningReplicatedCachesTest extends BaseInfinispanCoreOSGiTest {

   private EmbeddedCacheManager cm1, cm2;

   @Override
   protected void createCacheManagers() throws Throwable {
      //not used
   }

   @Before
   public void setUp() {
      ConfigurationBuilder c = getConfiguration();
      cm1 = addClusterEnabledCacheManager(c);
      cm2 = addClusterEnabledCacheManager(c);

      defineConfigurationOnAllManagers("c1", c);
      defineConfigurationOnAllManagers("c2", c);

      waitForClusterToForm();
   }

   @After
   public void tearDown() {
      TestingUtil.killCacheManagers(cm1);
      TestingUtil.killCacheManagers(cm2);
   }

   private void startAllCaches() {
      startCache("c1");
      startCache("c2");
      startCache("cache1");
      startCache("cache2");
      startCache(CacheContainer.DEFAULT_CACHE_NAME);
   }

   private void startCache(String c1) {
      cm1.getCache(c1);
      cm2.getCache(c1);
      waitForClusterToForm(c1);
   }

   protected ConfigurationBuilder getConfiguration() {
      ConfigurationBuilder c = getDefaultClusteredCacheConfig(CacheMode.REPL_SYNC, true);
      c.jmxStatistics().enable();
      return c;
   }

   @Test
   public void testReadOnlyTransaction() throws Exception {
      Cache<String, String> c1 = cm1.getCache();
      Cache<String, String> c2 = cm2.getCache();
      RpcManagerImpl ri = (RpcManagerImpl) c1.getAdvancedCache().getRpcManager();

      c1.put("k", "v");

      assert "v".equals(c1.get("k"));
      assert "v".equals(c2.get("k"));
      long oldRC = ri.getReplicationCount();
      c1.getAdvancedCache().getTransactionManager().begin();
      assert "v".equals(c1.get("k"));
      c1.getAdvancedCache().getTransactionManager().commit();

      assert ri.getReplicationCount() == oldRC;
   }

   @Test
   public void testCommitSpanningCaches() throws Exception {
      startAllCaches();
      Cache<String, String> c1 = cm1.getCache("c1");
      Cache<String, String> c1Replica = cm2.getCache("c1");
      Cache<String, String> c2 = cm1.getCache("c2");
      Cache<String, String> c2Replica = cm2.getCache("c2");

      assert c1.isEmpty();
      assert c2.isEmpty();
      assert c1Replica.isEmpty();
      assert c2Replica.isEmpty();

      c1.put("c1key", "c1value");
      c2.put("c2key", "c2value");

      assertInitialValues(c1, c1Replica, c2, c2Replica);

      TransactionManager tm = TestingUtil.getTransactionManager(c1);

      tm.begin();
      c1.put("c1key", "c1value_new");
      c2.put("c2key", "c2value_new");

      assert c1.get("c1key").equals("c1value_new");
      assert c1Replica.get("c1key").equals("c1value");
      assert c2.get("c2key").equals("c2value_new");
      assert c2Replica.get("c2key").equals("c2value");

      Transaction tx = tm.suspend();

      assertInitialValues(c1, c1Replica, c2, c2Replica);

      tm.resume(tx);
      log.trace("before commit...");
      tm.commit();


      assert c1.get("c1key").equals("c1value_new");
      assert c1Replica.get("c1key").equals("c1value_new");
      assertEquals(c2.get("c2key"), "c2value_new");
      assert c2Replica.get("c2key").equals("c2value_new");
   }

   @Test
   public void testRollbackSpanningCaches() throws Exception {
      startAllCaches();
      Cache<String, String> c1 = cm1.getCache("c1");
      Cache<String, String> c1Replica = cm2.getCache("c1");
      Cache<String, String> c2 = cm1.getCache("c2");
      Cache<String, String> c2Replica = cm2.getCache("c2");

      assert c1.isEmpty();
      assert c2.isEmpty();
      assert c1Replica.isEmpty();
      assert c2Replica.isEmpty();

      c1.put("c1key", "c1value");
      c2.put("c2key", "c2value");

      assertInitialValues(c1, c1Replica, c2, c2Replica);

      TransactionManager tm = TestingUtil.getTransactionManager(c1);

      tm.begin();
      c1.put("c1key", "c1value_new");
      c2.put("c2key", "c2value_new");

      assert c1.get("c1key").equals("c1value_new");
      assert c1Replica.get("c1key").equals("c1value");
      assert c2.get("c2key").equals("c2value_new");
      assert c2Replica.get("c2key").equals("c2value");

      Transaction tx = tm.suspend();

      assert c1.get("c1key").equals("c1value");
      assert c1Replica.get("c1key").equals("c1value");
      assert c2.get("c2key").equals("c2value");
      assert c2Replica.get("c2key").equals("c2value");

      tm.resume(tx);
      tm.rollback();

      assert c1.get("c1key").equals("c1value");
      assert c1Replica.get("c1key").equals("c1value");
      assert c2.get("c2key").equals("c2value");
      assert c2Replica.get("c2key").equals("c2value");
   }

   private void assertInitialValues(Cache<String, String> c1, Cache<String, String> c1Replica, Cache<String, String> c2, Cache<String, String> c2Replica) {
      for (Cache<String, String> c : Arrays.asList(c1, c1Replica)) {
         assert !c.isEmpty();
         assert c.size() == 1;
         assert c.get("c1key").equals("c1value");
      }

      for (Cache<String, String> c : Arrays.asList(c2, c2Replica)) {
         assert !c.isEmpty();
         assert c.size() == 1;
         assert c.get("c2key").equals("c2value");
      }
   }

   @Test
   public void testRollbackSpanningCaches2() throws Exception {
      startAllCaches();
      Cache<String, String> c1 = cm1.getCache("c1");

      assert c1.getCacheConfiguration().clustering().cacheMode().isClustered();
      Cache<String, String> c1Replica = cm2.getCache("c1");

      assert c1.isEmpty();
      assert c1Replica.isEmpty();

      c1.put("c1key", "c1value");
      assert c1.get("c1key").equals("c1value");
      assert c1Replica.get("c1key").equals("c1value");
   }

   @Test
   public void testSimpleCommit() throws Exception {
      startAllCaches();
      Cache<String, String> c1 = cm1.getCache("c1");
      Cache<String, String> c1Replica = cm2.getCache("c1");


      assert c1.isEmpty();
      assert c1Replica.isEmpty();

      TransactionManager tm = TestingUtil.getTransactionManager(c1);
      tm.begin();
      c1.put("c1key", "c1value");
      tm.commit();

      assert c1.get("c1key").equals("c1value");
      assert c1Replica.get("c1key").equals("c1value");
   }

   @Test
   public void testPutIfAbsent() throws Exception {
      startAllCaches();
      Cache<String, String> c1 = cm1.getCache("c1");
      Cache<String, String> c1Replica = cm2.getCache("c1");


      assert c1.isEmpty();
      assert c1Replica.isEmpty();

      TransactionManager tm = TestingUtil.getTransactionManager(c1);
      tm.begin();
      c1.put("c1key", "c1value");
      tm.commit();

      assert c1.get("c1key").equals("c1value");
      assert c1Replica.get("c1key").equals("c1value");

      tm.begin();
      c1.putIfAbsent("c1key", "SHOULD_NOT_GET_INSERTED");
      tm.commit();

      assert c1.get("c1key").equals("c1value");
      assert c1Replica.get("c1key").equals("c1value");
   }

   @Test
   public void testTwoNamedCachesSameNode() throws Exception {
      runTest(cache(0, "cache1"), cache(0, "cache2"));
   }

   @Test
   public void testDefaultCacheAndNamedCacheSameNode() throws Exception {
      runTest(cache(0), cache(0, "cache1"));
   }

   @Test
   public void testTwoNamedCachesDifferentNodes() throws Exception {
      runTest(cache(0, "cache1"), cache(1, "cache2"));
   }

   @Test
   public void testDefaultCacheAndNamedCacheDifferentNodes() throws Exception {
      runTest(cache(0), cache(1, "cache1"));
   }

   private void runTest(Cache cache1, Cache cache2) throws Exception {
      startAllCaches();
      assertFalse(cache1.containsKey("a"));
      assertFalse(cache2.containsKey("b"));

      TransactionManager tm = TestingUtil.getTransactionManager(cache1);
      tm.begin();
      cache1.put("a", "value1");
      cache2.put("b", "value2");
      tm.commit();

      assertEquals("value1", cache1.get("a"));
      assertEquals("value2", cache2.get("b"));

      tm.begin();
      cache1.remove("a");
      cache2.remove("b");
      tm.commit();

      assertFalse(cache1.containsKey("a"));
      assertFalse(cache2.containsKey("b"));
   }

}
