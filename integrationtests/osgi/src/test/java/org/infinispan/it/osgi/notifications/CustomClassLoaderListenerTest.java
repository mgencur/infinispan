package org.infinispan.it.osgi.notifications;

import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.it.osgi.BaseInfinispanCoreOSGiTest;
import org.infinispan.it.osgi.Osgi;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.notifications.Listener;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryActivated;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryCreated;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryEvicted;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryLoaded;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryModified;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryPassivated;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryRemoved;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryVisited;
import org.infinispan.notifications.cachelistener.event.CacheEntryActivatedEvent;
import org.infinispan.notifications.cachelistener.event.CacheEntryCreatedEvent;
import org.infinispan.notifications.cachelistener.event.CacheEntryEvictedEvent;
import org.infinispan.notifications.cachelistener.event.CacheEntryLoadedEvent;
import org.infinispan.notifications.cachelistener.event.CacheEntryModifiedEvent;
import org.infinispan.notifications.cachelistener.event.CacheEntryPassivatedEvent;
import org.infinispan.notifications.cachelistener.event.CacheEntryRemovedEvent;
import org.infinispan.notifications.cachelistener.event.CacheEntryVisitedEvent;
import org.infinispan.persistence.dummy.DummyInMemoryStoreConfigurationBuilder;
import org.infinispan.test.TestingUtil;
import org.infinispan.test.fwk.TestCacheManagerFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

/**
 * Test cloned from {@link org.infinispan.notifications.cachelistener.CustomClassLoaderListenerTest}
 * and modified for running in Karaf with JUnit.
 *
 * @author ttarrant
 * @author jholusa
 * @author mgencur
 */
@RunWith(PaxExam.class)
@Category(Osgi.class)
@ExamReactorStrategy(PerClass.class)
public class CustomClassLoaderListenerTest extends BaseInfinispanCoreOSGiTest {

   private CustomClassLoader ccl;

   @Before
   public void setUp() {
      ConfigurationBuilder builder = TestCacheManagerFactory.getDefaultCacheConfiguration(false);
      builder.persistence().passivation(true).addStore(DummyInMemoryStoreConfigurationBuilder.class);
      cacheManagers.add(TestCacheManagerFactory.createCacheManager(builder));
   }

   @After
   public void tearDown() {
      TestingUtil.killCacheManagers(manager(0));
   }

   @Test
   public void testCustomClassLoaderListener() throws Exception {
      ccl = new CustomClassLoader(Thread.currentThread().getContextClassLoader());
      ClassLoaderListener listener = new ClassLoaderListener();
      cache(0).getAdvancedCache().with(ccl).addListener(listener);

      cache(0).put("a", "a"); // Created + Modified
      assertEquals(1, listener.createdCounter);
      assertEquals(0, listener.modifiedCounter);
      assertEquals(0, listener.removedCounter);
      assertEquals(0, listener.visitedCounter);
      assertEquals(0, listener.activatedCounter);
      assertEquals(0, listener.passivatedCounter);
      assertEquals(0, listener.evictedCounter);
      assertEquals(0, listener.loadedCounter);
      listener.reset();

      cache(0).replace("a", "b"); // Modified
      assertEquals(0, listener.createdCounter);
      assertEquals(1, listener.modifiedCounter);
      assertEquals(0, listener.removedCounter);
      assertEquals(0, listener.visitedCounter);
      assertEquals(0, listener.activatedCounter);
      assertEquals(0, listener.passivatedCounter);
      assertEquals(0, listener.evictedCounter);
      assertEquals(0, listener.loadedCounter);
      listener.reset();

      cache(0).evict("a"); // Passivated + Evicted
      assertEquals(0, listener.createdCounter);
      assertEquals(0, listener.modifiedCounter);
      assertEquals(0, listener.removedCounter);
      assertEquals(0, listener.visitedCounter);
      assertEquals(0, listener.activatedCounter);
      assertEquals(1, listener.passivatedCounter);
      assertEquals(1, listener.evictedCounter);
      assertEquals(0, listener.loadedCounter);
      listener.reset();

      cache(0).get("a"); // Loaded + Activated + Visited
      assertEquals(0, listener.createdCounter);
      assertEquals(0, listener.modifiedCounter);
      assertEquals(0, listener.removedCounter);
      assertEquals(1, listener.visitedCounter);
      assertEquals(1, listener.activatedCounter);
      assertEquals(0, listener.passivatedCounter);
      assertEquals(0, listener.evictedCounter);
      assertEquals(1, listener.loadedCounter);
      listener.reset();

      cache(0).remove("a"); // Removed
      assertEquals(0, listener.createdCounter);
      assertEquals(0, listener.modifiedCounter);
      assertEquals(1, listener.removedCounter);
      assertEquals(0, listener.visitedCounter);
      assertEquals(0, listener.activatedCounter);
      assertEquals(0, listener.passivatedCounter);
      assertEquals(0, listener.evictedCounter);
      assertEquals(0, listener.loadedCounter);
   }

   @Override
   protected void createCacheManagers() throws Throwable {
      //not used
   }

   public static class CustomClassLoader extends ClassLoader {
      public CustomClassLoader(ClassLoader parent) {
         super(parent);
      }
   }

   @Listener
   public class ClassLoaderListener {
      int createdCounter = 0;
      int removedCounter = 0;
      int modifiedCounter = 0;
      int visitedCounter = 0;
      int evictedCounter = 0;
      int passivatedCounter = 0;
      int loadedCounter = 0;
      int activatedCounter = 0;


      @CacheEntryCreated
      public void handleCreated(CacheEntryCreatedEvent e) {
         assertEquals(ccl, Thread.currentThread().getContextClassLoader());
         if (!e.isPre()) {
            createdCounter++;
         }
      }

      @CacheEntryRemoved
      public void handleRemoved(CacheEntryRemovedEvent e) {
         assertEquals(ccl, Thread.currentThread().getContextClassLoader());
         if (!e.isPre()) {
            removedCounter++;
         }
      }

      @CacheEntryModified
      public void handleModified(CacheEntryModifiedEvent e) {
         assertEquals(ccl, Thread.currentThread().getContextClassLoader());
         if (!e.isPre()) {
            modifiedCounter++;
         }
      }

      @CacheEntryVisited
      public void handleVisited(CacheEntryVisitedEvent e) {
         assertEquals(ccl, Thread.currentThread().getContextClassLoader());
         if (!e.isPre()) {
            visitedCounter++;
         }
      }

      @CacheEntryEvicted
      public void handleEvicted(CacheEntryEvictedEvent e) {
         assertEquals(ccl, Thread.currentThread().getContextClassLoader());
         if (!e.isPre()) {
            evictedCounter++;
         }
      }

      @CacheEntryPassivated
      public void handlePassivated(CacheEntryPassivatedEvent e) {
         assertEquals(ccl, Thread.currentThread().getContextClassLoader());
         if (!e.isPre()) {
            passivatedCounter++;
         }
      }

      @CacheEntryActivated
      public void handleActivated(CacheEntryActivatedEvent e) {
         assertEquals(ccl, Thread.currentThread().getContextClassLoader());
         if (!e.isPre()) {
            activatedCounter++;
         }
      }

      @CacheEntryLoaded
      public void handleLoaded(CacheEntryLoadedEvent e) {
         assertEquals(ccl, Thread.currentThread().getContextClassLoader());
         if (!e.isPre()) {
            loadedCounter++;
         }
      }

      void reset() {
         createdCounter = 0;
         removedCounter = 0;
         modifiedCounter = 0;
         visitedCounter = 0;
         evictedCounter = 0;
         passivatedCounter = 0;
         loadedCounter = 0;
         activatedCounter = 0;
      }
   }
}
