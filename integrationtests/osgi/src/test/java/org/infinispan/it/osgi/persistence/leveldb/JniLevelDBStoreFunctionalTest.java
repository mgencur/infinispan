package org.infinispan.it.osgi.persistence.leveldb;

import org.infinispan.configuration.cache.PersistenceConfigurationBuilder;
import org.infinispan.it.osgi.Osgi;
import org.infinispan.it.osgi.persistence.BaseStoreFunctionalTestOSGi;
import org.infinispan.persistence.leveldb.configuration.LevelDBStoreConfiguration;
import org.infinispan.persistence.leveldb.configuration.LevelDBStoreConfigurationBuilder;
import org.infinispan.test.TestingUtil;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.options.RawUrlReference;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;

import java.io.File;

import static org.ops4j.pax.exam.CoreOptions.junitBundles;
import static org.ops4j.pax.exam.CoreOptions.maven;
import static org.ops4j.pax.exam.CoreOptions.options;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.features;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.karafDistributionConfiguration;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.keepRuntimeFolder;

/**
 * Test cloned from {@link org.infinispan.persistence.leveldb.JniLevelDBStoreFunctionalTest} and
 * {@link org.infinispan.persistence.BaseStoreFunctionalTest} and modified for running in Karaf with JUnit.
 *
 * @author mgencur
 */
@RunWith(PaxExam.class)
@Category(Osgi.class)
@ExamReactorStrategy(PerClass.class)
public class JniLevelDBStoreFunctionalTest extends BaseStoreFunctionalTestOSGi {

   private static String tmpDirectory;

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
                           .artifactId("infinispan-cachestore-leveldb")
                           .type("xml")
                           .classifier("features")
                           .versionAsInProject(), "infinispan-cachestore-leveldb-jni"),
            //install the infinispan-test-jar through a feature file as PAX-EXAM fails to deploy any jars that are not bundles
            features(new RawUrlReference(TEST_UTILS_FEATURE_FILE), "infinispan-core-tests"),
            junitBundles(),
            keepRuntimeFolder()
      );
   }

   @BeforeClass
   public static void setUpTempDir() {
      tmpDirectory = TestingUtil.tmpDirectory(JniLevelDBStoreFunctionalTest.class);
   }

   @AfterClass
   public static void clearTempDir() {
      TestingUtil.recursiveFileRemove(tmpDirectory);
      new File(tmpDirectory).mkdirs();
   }

   @Override
   protected PersistenceConfigurationBuilder createCacheStoreConfig(PersistenceConfigurationBuilder p, boolean preload) {
      createStoreBuilder(p)
            .preload(preload)
            .implementationType(LevelDBStoreConfiguration.ImplementationType.JNI);
      return p;
   }

   LevelDBStoreConfigurationBuilder createStoreBuilder(PersistenceConfigurationBuilder loaders) {
      return loaders.addStore(LevelDBStoreConfigurationBuilder.class).location(tmpDirectory + "/data").expiredLocation(tmpDirectory + "/expiry").clearThreshold(2);
   }

}
