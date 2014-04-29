package org.infinispan.it.osgi;

import org.infinispan.test.MultipleCacheManagersTest;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;

import static org.infinispan.it.osgi.util.IspnKarafOptions.*;
import static org.ops4j.pax.exam.CoreOptions.junitBundles;
import static org.ops4j.pax.exam.CoreOptions.options;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.keepRuntimeFolder;

/**
 * @author mgencur
 *
 *  TODO: Make infinispan-core:tests a bundle. Current approach fails in Karaf 3.0.0
 */
public abstract class BaseInfinispanCoreOSGiTest extends MultipleCacheManagersTest {

   @Configuration
   public Option[] config() throws Exception {
      return options(
            karafContainer(),
            featureIspnCoreDependencies(),
            ispnCorePlusTests(),
            junitBundles(),
            keepRuntimeFolder()
      );
   }
}
