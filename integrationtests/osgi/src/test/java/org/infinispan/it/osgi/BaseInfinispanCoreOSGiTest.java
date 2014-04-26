package org.infinispan.it.osgi;

import org.infinispan.test.MultipleCacheManagersTest;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.options.RawUrlReference;

import static org.ops4j.pax.exam.CoreOptions.junitBundles;
import static org.ops4j.pax.exam.CoreOptions.maven;
import static org.ops4j.pax.exam.CoreOptions.options;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.features;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.karafDistributionConfiguration;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.keepRuntimeFolder;

public abstract class BaseInfinispanCoreOSGiTest extends MultipleCacheManagersTest {

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
            //install the infinispan-test-jar through a feature file as PAX-EXAM fails to deploy any jars that are not bundles
            features(new RawUrlReference(TEST_UTILS_FEATURE_FILE), "infinispan-core-tests"),
            junitBundles(),
            keepRuntimeFolder()
      );
   }
}
