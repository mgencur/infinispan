package org.infinispan.it.osgi.persistence.remote;

import org.infinispan.configuration.cache.PersistenceConfigurationBuilder;
import org.infinispan.it.osgi.Osgi;
import org.infinispan.it.osgi.persistence.BaseStoreFunctionalTestOSGi;
import org.infinispan.persistence.remote.configuration.RemoteStoreConfigurationBuilder;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.options.RawUrlReference;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;

import static org.ops4j.pax.exam.CoreOptions.*;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.*;

/**
 * Test cloned from {@link org.infinispan.persistence.remote.RemoteStoreFunctionalTest} and
 * {@link org.infinispan.persistence.BaseStoreFunctionalTest} and modified for running in Karaf with JUnit.
 *
 * As opposed to the original RemoteStoreFunctionalTest which starts an embedded HotRod server,
 * the current test requires a remote Infinispan server to be running on localhost with
 * cache "notindexed" and HotRod listening on 11222 port. Running an embedded HotRod server inside Karaf
 * does not work.
 *
 * TODO: Automate starting and stopping remote Infinispan server.
 *
 * @author mgencur
 */
@RunWith(PaxExam.class)
@Category(Osgi.class)
@ExamReactorStrategy(PerClass.class)
public class RemoteStoreFunctionalTest extends BaseStoreFunctionalTestOSGi {

   private final String CACHE_NAME = "notindexed";

   @Configuration
   public Option[] config() throws Exception {
      final String KARAF_VERSION = System.getProperty("version.karaf", "2.3.3");
      final String TEST_UTILS_FEATURE_FILE = "file:///" + System.getProperty("basedir").replace("\\", "/") + "/target/test-classes/test-features.xml";

      return options(
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
            features(maven().groupId("org.infinispan")
                           .artifactId("infinispan-cachestore-remote")
                           .type("xml")
                           .classifier("features")
                           .versionAsInProject(), "infinispan-cachestore-remote"),
            //install the infinispan-test-jar through a feature file as PAX-EXAM fails to deploy any jars that are not bundles
            features(new RawUrlReference(TEST_UTILS_FEATURE_FILE), "infinispan-core-tests"),
            junitBundles(),
            keepRuntimeFolder()
      );
   }

   @Override
   protected PersistenceConfigurationBuilder createCacheStoreConfig(PersistenceConfigurationBuilder persistence, boolean preload) {
      persistence
            .addStore(RemoteStoreConfigurationBuilder.class)
            .remoteCacheName(CACHE_NAME)
            .preload(preload)
            .addServer()
            .host("localhost")
            .port(11222);
      return persistence;
   }

   @Override
   public void testPreloadAndExpiry() {
      // No-op, since remote cache store does not support preload
   }

   @Override
   public void testPreloadStoredAsBinary() {
      // No-op, remote cache store does not support store as binary
      // since Hot Rod already stores them as binary
   }

   @Override
   public void testTwoCachesSameCacheStore() {
      //not applicable
   }
}
