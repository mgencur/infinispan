package org.infinispan.it.osgi;

import org.infinispan.Cache;
import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.test.AbstractCacheTest;
import org.infinispan.test.MultipleCacheManagersTest;
import org.infinispan.test.TestingUtil;
import org.infinispan.test.fwk.TestCacheManagerFactory;
import org.infinispan.test.fwk.TransportFlags;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.options.RawUrlReference;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.ops4j.pax.exam.CoreOptions.*;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.*;

/**
 * @author mgencur
 */
@RunWith(PaxExam.class)
@Category(Osgi.class)
@ExamReactorStrategy(PerClass.class)
public class InfinispanCoreOSGiTest extends BaseInfinispanCoreOSGiTest {

   @Test
   public void testLoadConfigFile() throws IOException {
      URL configURL = InfinispanCoreOSGiTest.class.getClassLoader().getResource("infinispan.xml");
      EmbeddedCacheManager cacheManager = new DefaultCacheManager(configURL.openStream());
      try {
         Cache<String, String> cache = cacheManager.getCache();
         cache.put("k1", "v1");
         assertEquals("v1", cache.get("k1"));
      } finally {
         TestingUtil.killCacheManagers(cacheManager);
      }
   }

   @Test
   public void testReplModeBasicTestTx() {
      createCluster(getDefaultClusteredCacheConfig(CacheMode.REPL_SYNC, true), 2);
      waitForClusterToForm();
      try {
         cache(0).put("k1", "v1");
         assertEquals("v1", cache(1).get("k1"));
      } finally {
         TestingUtil.killCacheManagers(cacheManagers);
      }
   }

   @Test
   public void testDistModeBasicTestTx() {
      createCluster(getDefaultClusteredCacheConfig(CacheMode.DIST_SYNC, true), 2);
      waitForClusterToForm();
      try {
         cache(0).put("k1", "v1");
         assertEquals("v1", cache(1).get("k1"));
      } finally {
         TestingUtil.killCacheManagers(cacheManagers);
      }
   }

   @Override
   protected void createCacheManagers() throws Throwable {
   }

}