package org.infinispan.osgi;

import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.test.SingleCacheManagerTest;
import org.junit.Test;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.testng.listener.PaxExam;
import org.ops4j.pax.exam.karaf.options.KarafDistributionOption;
import org.ops4j.pax.exam.options.RawUrlReference;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;
import org.testng.annotations.Listeners;

import java.net.URL;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.ops4j.pax.exam.CoreOptions.maven;

/**
 * @author mgencur
 */
@Listeners(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
@org.testng.annotations.Test(testName = "osgi.KarafTest", groups = "osgi")
public class KarafTest extends SingleCacheManagerTest {

   private final String KARAF_VERSION = System.getProperty("version.karaf", "2.3.3");
   private final String RESOURCES_DIR = System.getProperty("resources.dir", System.getProperty("java.io.tmpdir"));

   @Configuration
   public Option[] config() throws Exception {
      return new Option[]{
            KarafDistributionOption.karafDistributionConfiguration()
                  .frameworkUrl(maven().groupId("org.apache.karaf").artifactId("apache-karaf").type("zip").version(KARAF_VERSION))
                  .karafVersion(KARAF_VERSION)
                  .name("Apache Karaf"),

            KarafDistributionOption.features(maven().groupId("org.infinispan")
                                                   .artifactId("infinispan-core").type("xml").classifier("features")
                                                   .versionAsInProject(), "infinispan-core"),
            KarafDistributionOption.editConfigurationFileExtend("etc/jre.properties", "jre-1.7", "sun.misc"),
            KarafDistributionOption.editConfigurationFileExtend("etc/jre.properties", "jre-1.6", "sun.misc"),
            KarafDistributionOption.keepRuntimeFolder(),
      };
   }

   @Test
   public void testPutGet() throws Exception {
      System.out.println("========== Test executed ==========");
      cache.put("k", "v");
      assertEquals("v", cache.get("k"));
   }

   @Override
   protected EmbeddedCacheManager createCacheManager() throws Exception {
      return new DefaultCacheManager();
   }
}