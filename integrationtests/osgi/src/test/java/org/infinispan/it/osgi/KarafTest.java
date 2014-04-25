package org.infinispan.it.osgi;

import org.infinispan.Cache;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.test.fwk.TestCacheManagerFactory;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.options.RawUrlReference;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;

import static org.junit.Assert.assertEquals;
import static org.ops4j.pax.exam.CoreOptions.*;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.*;

/**
 * @author mgencur
 */
@RunWith(PaxExam.class)
@Category(Osgi.class)
@ExamReactorStrategy(PerClass.class)
public class KarafTest {

   private final String KARAF_VERSION = System.getProperty("version.karaf", "2.3.3");
   private final String BASEDIR = System.getProperty("basedir");
   private final String TEST_UTILS_FEATURE_FILE = "file:///" + BASEDIR.replace("\\", "/") + "/target/test-classes/test-features.xml";

   @Configuration
   public Option[] config() throws Exception {

      return options(
            // systemProperty("org.ops4j.pax.logging.DefaultServiceLog.level").value("DEBUG"),
            karafDistributionConfiguration()
                  .frameworkUrl(
                        maven()
                              .groupId("org.apache.karaf")
                              .artifactId("apache-karaf")
                              .type("zip")
                              .version(KARAF_VERSION))
                  .karafVersion(KARAF_VERSION),
            features(maven().groupId("org.infinispan")
                           .artifactId("infinispan-core")
                           .type("xml")
                           .classifier("features")
                           .versionAsInProject(), "infinispan-core"),
            features(new RawUrlReference(TEST_UTILS_FEATURE_FILE), "infinispan-core-tests"),
            junitBundles(),
            keepRuntimeFolder()
            );
   }

   @Test
   public void testPutGet() throws Exception {
      ConfigurationBuilder cb = TestCacheManagerFactory.getDefaultCacheConfiguration(true);
      EmbeddedCacheManager cm = TestCacheManagerFactory.createCacheManager(false);
      cm.defineConfiguration("test", cb.build());
      Cache cache = cm.getCache("test");
      cache.put("k", "v");
      assertEquals("v", cache.get("k"));
   }
}