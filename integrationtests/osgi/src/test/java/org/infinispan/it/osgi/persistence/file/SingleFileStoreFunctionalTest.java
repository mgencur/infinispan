package org.infinispan.it.osgi.persistence.file;

import org.infinispan.Cache;
import org.infinispan.atomic.AtomicMap;
import org.infinispan.atomic.AtomicMapLookup;
import org.infinispan.commons.equivalence.ByteArrayEquivalence;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.configuration.cache.PersistenceConfigurationBuilder;
import org.infinispan.container.DataContainer;
import org.infinispan.container.entries.InternalCacheEntry;
import org.infinispan.it.osgi.BaseInfinispanCoreOSGiTest;
import org.infinispan.it.osgi.Osgi;
import org.infinispan.it.osgi.persistence.BaseStoreFunctionalTestOSGi;
import org.infinispan.manager.CacheContainer;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.persistence.file.SingleFileStore;
import org.infinispan.persistence.BaseStoreFunctionalTest;
import org.infinispan.persistence.spi.PersistenceException;
import org.infinispan.test.CacheManagerCallable;
import org.infinispan.test.TestingUtil;
import org.infinispan.test.fwk.TestCacheManagerFactory;
import org.infinispan.transaction.TransactionMode;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.Test;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.options.RawUrlReference;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;

import javax.transaction.TransactionManager;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static junit.framework.Assert.assertNull;
import static org.infinispan.test.TestingUtil.INFINISPAN_END_TAG;
import static org.infinispan.test.TestingUtil.INFINISPAN_START_TAG_NO_SCHEMA;
import static org.infinispan.test.TestingUtil.withCacheManager;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.ops4j.pax.exam.CoreOptions.junitBundles;
import static org.ops4j.pax.exam.CoreOptions.maven;
import static org.ops4j.pax.exam.CoreOptions.options;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.features;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.karafDistributionConfiguration;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.keepRuntimeFolder;

/**
 * Test cloned from {@link org.infinispan.persistence.file.SingleFileStoreFunctionalTest} and
 * {@link org.infinispan.persistence.BaseStoreFunctionalTest} and modified for running in Karaf with JUnit.
 *
 * TODO: Remove code duplication. We could extend BaseStoreFunctionalTest from org.infinispan.persistence which
 * is made available to PAX EXAM from infinispan-core test-jar. However, there's a package split - the package is also
 * available in infinispan-core and classes from the test-jar's packages cannot be found.
 *
 * @author galderz
 * @author mgencur
 */
@RunWith(PaxExam.class)
@Category(Osgi.class)
@ExamReactorStrategy(PerClass.class)
public class SingleFileStoreFunctionalTest extends BaseStoreFunctionalTestOSGi {

   private static String tmpDirectory;

   @BeforeClass
   public static void setUpTempDir() {
      tmpDirectory = TestingUtil.tmpDirectory(SingleFileStoreFunctionalTest.class);
   }

   @AfterClass
   public static void clearTempDir() {
      TestingUtil.recursiveFileRemove(tmpDirectory);
      new File(tmpDirectory).mkdirs();
   }

   @Override
   protected PersistenceConfigurationBuilder createCacheStoreConfig(PersistenceConfigurationBuilder persistence, boolean preload) {
      persistence
            .addSingleFileStore()
            .location(tmpDirectory)
            .preload(preload);
      return persistence;
   }

   @Test
   public void testParsingEmptyElement() throws Exception {
      String config = INFINISPAN_START_TAG_NO_SCHEMA +
            "<cache-container default-cache=\"default\">" +
            "   <local-cache name=\"default\">\n" +
            "      <persistence passivation=\"false\"> \n" +
            "         <file-store shared=\"false\" preload=\"true\"/> \n" +
            "      </persistence>\n" +
            "   </local-cache>\n" +
            "</cache-container>" +
            INFINISPAN_END_TAG;
      InputStream is = new ByteArrayInputStream(config.getBytes());
      withCacheManager(new CacheManagerCallable(TestCacheManagerFactory.fromStream(is)) {
         @Override
         public void call() {
            Cache<Object, Object> cache = cm.getCache();
            cache.put(1, "v1");
            assertEquals("v1", cache.get(1));
            SingleFileStore cacheLoader = (SingleFileStore) TestingUtil.getFirstLoader(cache);
            assertEquals("Infinispan-SingleFileStore", cacheLoader.getConfiguration().location());
            assertEquals(-1, cacheLoader.getConfiguration().maxEntries());
         }
      });
      TestingUtil.recursiveFileRemove("Infinispan-SingleFileStore");
   }

   @Test
   public void testParsingElement() throws Exception {
      String config = INFINISPAN_START_TAG_NO_SCHEMA +
            "<cache-container default-cache=\"default\">" +
            "   <local-cache name=\"default\">\n" +
            "      <persistence passivation=\"false\"> \n" +
            "         <file-store path=\"other-location\" max-entries=\"100\" shared=\"false\" preload=\"true\"/> \n" +
            "      </persistence>\n" +
            "   </local-cache>\n" +
            "</cache-container>" +
            INFINISPAN_END_TAG;
      InputStream is = new ByteArrayInputStream(config.getBytes());
      withCacheManager(new CacheManagerCallable(TestCacheManagerFactory.fromStream(is)) {
         @Override
         public void call() {
            Cache<Object, Object> cache = cm.getCache();
            cache.put(1, "v1");
            assertEquals("v1", cache.get(1));
            SingleFileStore store = (SingleFileStore) TestingUtil.getFirstLoader(cache);
            assertEquals("other-location", store.getConfiguration().location());
            assertEquals(100, store.getConfiguration().maxEntries());
         }
      });
      TestingUtil.recursiveFileRemove("other-location");
   }

}
