/*
 * JBoss, Home of Professional Open Source
 * Copyright 2009 Red Hat Inc. and/or its affiliates and other
 * contributors as indicated by the @author tags. All rights reserved.
 * See the copyright.txt in the distribution for a full listing of
 * individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.infinispan.manager;

import net.jcip.annotations.GuardedBy;
import org.infinispan.Cache;
import org.infinispan.CacheException;
import org.infinispan.Version;
import org.infinispan.commands.RemoveCacheCommand;
import org.infinispan.config.Configuration;
import org.infinispan.config.ConfigurationException;
import org.infinispan.config.ConfigurationValidatingVisitor;
import org.infinispan.config.GlobalConfiguration;
import org.infinispan.config.InfinispanConfiguration;
import org.infinispan.factories.GlobalComponentRegistry;
import org.infinispan.factories.InternalCacheFactory;
import org.infinispan.factories.annotations.SurvivesRestarts;
import org.infinispan.factories.scopes.Scope;
import org.infinispan.factories.scopes.Scopes;
import org.infinispan.jmx.CacheJmxRegistration;
import org.infinispan.jmx.CacheManagerJmxRegistration;
import org.infinispan.jmx.annotations.MBean;
import org.infinispan.jmx.annotations.ManagedAttribute;
import org.infinispan.jmx.annotations.ManagedOperation;
import org.infinispan.lifecycle.ComponentStatus;
import org.infinispan.lifecycle.Lifecycle;
import org.infinispan.notifications.cachemanagerlistener.CacheManagerNotifier;
import org.infinispan.remoting.rpc.ResponseMode;
import org.infinispan.remoting.transport.Address;
import org.infinispan.remoting.transport.Transport;
import org.infinispan.util.Immutables;
import org.infinispan.util.ReflectionUtil;
import org.infinispan.util.Util;
import org.infinispan.util.logging.Log;
import org.infinispan.util.logging.LogFactory;
import org.rhq.helpers.pluginAnnotations.agent.DataType;
import org.rhq.helpers.pluginAnnotations.agent.DisplayType;
import org.rhq.helpers.pluginAnnotations.agent.Metric;
import org.rhq.helpers.pluginAnnotations.agent.Operation;
import org.rhq.helpers.pluginAnnotations.agent.Parameter;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * A <tt>CacheManager</tt> is the primary mechanism for retrieving a {@link Cache} instance, and is often used as a
 * starting point to using the {@link Cache}.
 * <p/>
 * <tt>CacheManager</tt>s are heavyweight objects, and we foresee no more than one <tt>CacheManager</tt> being used per
 * JVM (unless specific configuration requirements require more than one; but either way, this would be a minimal and
 * finite number of instances).
 * <p/>
 * Constructing a <tt>CacheManager</tt> is done via one of its constructors, which optionally take in a {@link
 * org.infinispan.config.Configuration} or a path or URL to a configuration XML file.
 * <p/>
 * Lifecycle - <tt>CacheManager</tt>s have a lifecycle (it implements {@link Lifecycle}) and the default constructors
 * also call {@link #start()}. Overloaded versions of the constructors are available, that do not start the
 * <tt>CacheManager</tt>, although it must be kept in mind that <tt>CacheManager</tt>s need to be started before they
 * can be used to create <tt>Cache</tt> instances.
 * <p/>
 * Once constructed, <tt>CacheManager</tt>s should be made available to any component that requires a <tt>Cache</tt>,
 * via JNDI or via some other mechanism such as an IoC container.
 * <p/>
 * You obtain <tt>Cache</tt> instances from the <tt>CacheManager</tt> by using one of the overloaded
 * <tt>getCache()</tt>, methods. Note that with <tt>getCache()</tt>, there is no guarantee that the instance you get is
 * brand-new and empty, since caches are named and shared. Because of this, the <tt>CacheManager</tt> also acts as a
 * repository of <tt>Cache</tt>s, and is an effective mechanism of looking up or creating <tt>Cache</tt>s on demand.
 * <p/>
 * When the system shuts down, it should call {@link #stop()} on the <tt>CacheManager</tt>. This will ensure all caches
 * within its scope are properly stopped as well.
 * <p/>
 * Sample usage: <code> CacheManager manager = CacheManager.getInstance("my-config-file.xml"); Cache entityCache =
 * manager.getCache("myEntityCache"); entityCache.put("aPerson", new Person());
 * <p/>
 * Configuration myNewConfiguration = new Configuration(); myNewConfiguration.setCacheMode(Configuration.CacheMode.LOCAL);
 * manager.defineConfiguration("myLocalCache", myNewConfiguration); Cache localCache = manager.getCache("myLocalCache");
 * </code>
 *
 * @author Manik Surtani (<a href="mailto:manik@jboss.org">manik@jboss.org</a>)
 * @author Galder Zamarreño
 * @since 4.0
 */
@Scope(Scopes.GLOBAL)
@SurvivesRestarts
@MBean(objectName = DefaultCacheManager.OBJECT_NAME, description = "Component that acts as a manager, factory and container for caches in the system.")
@SuppressWarnings("deprecated")
public class DefaultCacheManager implements EmbeddedCacheManager, CacheManager {
   public static final String OBJECT_NAME = "CacheManager";
   private static final Log log = LogFactory.getLog(DefaultCacheManager.class);
   protected final GlobalConfiguration globalConfiguration;
   protected final Configuration defaultConfiguration;
   private final ConcurrentMap<String, CacheWrapper> caches = new ConcurrentHashMap<String, CacheWrapper>();
   private final ConcurrentMap<String, Configuration> configurationOverrides = new ConcurrentHashMap<String, Configuration>();
   private final GlobalComponentRegistry globalComponentRegistry;
   private final ReentrantLock cacheCreateLock;
   private final ReflectionCache reflectionCache = new ReflectionCache();
   private volatile boolean stopping;

   /**
    * Constructs and starts a default instance of the CacheManager, using configuration defaults.  See {@link
    * Configuration} and {@link GlobalConfiguration} for details of these defaults.
    */
   public DefaultCacheManager() {
      this(null, null, true);
   }

   /**
    * Constructs a default instance of the CacheManager, using configuration defaults.  See {@link Configuration} and
    * {@link GlobalConfiguration} for details of these defaults.
    *
    * @param start if true, the cache manager is started
    */
   public DefaultCacheManager(boolean start) {
      this(null, null, start);
   }

   /**
    * Constructs and starts a new instance of the CacheManager, using the default configuration passed in. Uses defaults
    * for a {@link GlobalConfiguration}.  See {@link GlobalConfiguration} for details of these defaults.
    *
    * @param defaultConfiguration configuration to use as a template for all caches created
    */
   public DefaultCacheManager(Configuration defaultConfiguration) {
      this(null, defaultConfiguration, true);
   }

   /**
    * Constructs a new instance of the CacheManager, using the default configuration passed in. Uses defaults for a
    * {@link org.infinispan.config.GlobalConfiguration}.  See {@link GlobalConfiguration} for details of these
    * defaults.
    *
    * @param defaultConfiguration configuration file to use as a template for all caches created
    * @param start                if true, the cache manager is started
    */
   public DefaultCacheManager(Configuration defaultConfiguration, boolean start) {
      this(null, defaultConfiguration, start);
   }

   /**
    * Constructs and starts a new instance of the CacheManager, using the global configuration passed in, and system
    * defaults for the default named cache configuration.  See {@link Configuration} for details of these defaults.
    *
    * @param globalConfiguration GlobalConfiguration to use for all caches created
    */
   public DefaultCacheManager(GlobalConfiguration globalConfiguration) {
      this(globalConfiguration, null, true);
   }

   /**
    * Constructs a new instance of the CacheManager, using the global configuration passed in, and system defaults for
    * the default named cache configuration.  See {@link Configuration} for details of these defaults.
    *
    * @param globalConfiguration GlobalConfiguration to use for all caches created
    * @param start               if true, the cache manager is started.
    */
   public DefaultCacheManager(GlobalConfiguration globalConfiguration, boolean start) {
      this(globalConfiguration, null, start);
   }

   /**
    * Constructs and starts a new instance of the CacheManager, using the global and default configurations passed in.
    * If either of these are null, system defaults are used.
    *
    * @param globalConfiguration  global configuration to use. If null, a default instance is created.
    * @param defaultConfiguration default configuration to use. If null, a default instance is created.
    */
   public DefaultCacheManager(GlobalConfiguration globalConfiguration, Configuration defaultConfiguration) {
      this(globalConfiguration, defaultConfiguration, true);
   }

   /**
    * Constructs a new instance of the CacheManager, using the global and default configurations passed in. If either of
    * these are null, system defaults are used.
    *
    * @param globalConfiguration  global configuration to use. If null, a default instance is created.
    * @param defaultConfiguration default configuration to use. If null, a default instance is created.
    * @param start                if true, the cache manager is started
    */
   public DefaultCacheManager(GlobalConfiguration globalConfiguration, Configuration defaultConfiguration,
                              boolean start) {
      this.globalConfiguration = globalConfiguration == null ? new GlobalConfiguration() : globalConfiguration
              .clone();
      this.globalConfiguration.accept(new ConfigurationValidatingVisitor());
      this.defaultConfiguration = defaultConfiguration == null ? new Configuration() : defaultConfiguration.clone();
      this.defaultConfiguration.accept(new ConfigurationValidatingVisitor());
      this.globalComponentRegistry = new GlobalComponentRegistry(this.globalConfiguration, this, reflectionCache, caches.keySet());
      this.cacheCreateLock = new ReentrantLock();
      if (start)
         start();
   }

   /**
    * Constructs and starts a new instance of the CacheManager, using the configuration file name passed in. This
    * constructor first searches for the named file on the classpath, and failing that, treats the file name as an
    * absolute path.
    *
    * @param configurationFile name of configuration file to use as a template for all caches created
    *
    * @throws java.io.IOException if there is a problem with the configuration file.
    */
   public DefaultCacheManager(String configurationFile) throws IOException {
      this(configurationFile, true);
   }

   /**
    * Constructs a new instance of the CacheManager, using the configuration file name passed in. This constructor first
    * searches for the named file on the classpath, and failing that, treats the file name as an absolute path.
    *
    * @param configurationFile name of configuration file to use as a template for all caches created
    * @param start             if true, the cache manager is started
    *
    * @throws java.io.IOException if there is a problem with the configuration file.
    */
   public DefaultCacheManager(String configurationFile, boolean start) throws IOException {
      try {
         InfinispanConfiguration configuration = InfinispanConfiguration.newInfinispanConfiguration(
                 configurationFile, InfinispanConfiguration.resolveSchemaPath(),
                 new ConfigurationValidatingVisitor());

         globalConfiguration = configuration.parseGlobalConfiguration();
         defaultConfiguration = configuration.parseDefaultConfiguration();
         for (Map.Entry<String, Configuration> entry : configuration.parseNamedConfigurations().entrySet()) {
            Configuration c = defaultConfiguration.clone();
            c.applyOverrides(entry.getValue());
            configurationOverrides.put(entry.getKey(), c);
         }
         globalComponentRegistry = new GlobalComponentRegistry(globalConfiguration, this, reflectionCache, caches.keySet());
         cacheCreateLock = new ReentrantLock();
      } catch (RuntimeException re) {
         throw new ConfigurationException(re);
      }
      if (start)
         start();
   }

   /**
    * Constructs and starts a new instance of the CacheManager, using the input stream passed in to read configuration
    * file contents.
    *
    * @param configurationStream stream containing configuration file contents, to use as a template for all caches
    *                            created
    *
    * @throws java.io.IOException if there is a problem with the configuration stream.
    */
   public DefaultCacheManager(InputStream configurationStream) throws IOException {
      this(configurationStream, true);
   }

   /**
    * Constructs a new instance of the CacheManager, using the input stream passed in to read configuration file
    * contents.
    *
    * @param configurationStream stream containing configuration file contents, to use as a template for all caches
    *                            created
    * @param start               if true, the cache manager is started
    *
    * @throws java.io.IOException if there is a problem reading the configuration stream
    */
   public DefaultCacheManager(InputStream configurationStream, boolean start) throws IOException {
      InputStream schemaInputStream = InfinispanConfiguration.findSchemaInputStream();
      try {
         InfinispanConfiguration configuration = InfinispanConfiguration.newInfinispanConfiguration(
                 configurationStream, schemaInputStream,
                 new ConfigurationValidatingVisitor());
         globalConfiguration = configuration.parseGlobalConfiguration();
         defaultConfiguration = configuration.parseDefaultConfiguration();
         for (Map.Entry<String, Configuration> entry : configuration.parseNamedConfigurations().entrySet()) {
            Configuration c = defaultConfiguration.clone();
            c.applyOverrides(entry.getValue());
            configurationOverrides.put(entry.getKey(), c);
         }
         globalComponentRegistry = new GlobalComponentRegistry(globalConfiguration, this, reflectionCache, caches.keySet());
         cacheCreateLock = new ReentrantLock();
      } catch (ConfigurationException ce) {
         throw ce;
      } catch (RuntimeException re) {
         throw new ConfigurationException(re);
      } finally {
         Util.close(schemaInputStream);
      }
      if (start)
         start();
   }

   /**
    * Constructs a new instance of the CacheManager, using the two configuration file names passed in. The first file
    * contains the GlobalConfiguration configuration The second file contain the Default configuration. The third
    * filename contains the named cache configuration This constructor first searches for the named file on the
    * classpath, and failing that, treats the file name as an absolute path.
    *
    * @param start                    if true, the cache manager is started
    * @param globalConfigurationFile  name of file that contains the global configuration
    * @param defaultConfigurationFile name of file that contains the default configuration
    * @param namedCacheFile           name of file that contains the named cache configuration
    *
    * @throws java.io.IOException if there is a problem with the configuration file.
    */
   public DefaultCacheManager(String globalConfigurationFile, String defaultConfigurationFile, String namedCacheFile,
                              boolean start) throws IOException {
      try {
         InfinispanConfiguration gconfiguration = InfinispanConfiguration.newInfinispanConfiguration(
                 globalConfigurationFile, InfinispanConfiguration.resolveSchemaPath(),
                 new ConfigurationValidatingVisitor());

         globalConfiguration = gconfiguration.parseGlobalConfiguration();

         InfinispanConfiguration dconfiguration = InfinispanConfiguration.newInfinispanConfiguration(
                 defaultConfigurationFile, InfinispanConfiguration.resolveSchemaPath(),
                 new ConfigurationValidatingVisitor());

         defaultConfiguration = dconfiguration.parseDefaultConfiguration();

         if (namedCacheFile != null) {
            InfinispanConfiguration NCconfiguration = InfinispanConfiguration.newInfinispanConfiguration(
                    namedCacheFile, InfinispanConfiguration.resolveSchemaPath(),
                    new ConfigurationValidatingVisitor());

            for (Map.Entry<String, Configuration> entry : NCconfiguration.parseNamedConfigurations().entrySet()) {
               Configuration c = defaultConfiguration.clone();
               c.applyOverrides(entry.getValue());
               configurationOverrides.put(entry.getKey(), c);
            }
         }

         globalComponentRegistry = new GlobalComponentRegistry(this.globalConfiguration, this, reflectionCache, caches.keySet());
         cacheCreateLock = new ReentrantLock();
      } catch (RuntimeException re) {
         throw new ConfigurationException(re);
      }

      if (start)
         start();
   }

   /**
    * {@inheritDoc}
    */
   public Configuration defineConfiguration(String cacheName, Configuration configurationOverride) {
      return defineConfiguration(cacheName, configurationOverride, defaultConfiguration, true);
   }

   /**
    * {@inheritDoc}
    */
   public Configuration defineConfiguration(String cacheName, String templateName, Configuration configurationOverride) {
      if (templateName != null) {
         Configuration c = configurationOverrides.get(templateName);
         if (c != null)
            return defineConfiguration(cacheName, configurationOverride, c, false);
         return defineConfiguration(cacheName, configurationOverride);
      }
      return defineConfiguration(cacheName, configurationOverride);
   }

   private Configuration defineConfiguration(String cacheName, Configuration configOverride,
                                             Configuration defaultConfigIfNotPresent, boolean checkExisting) {
      assertIsNotTerminated();
      if (cacheName == null || configOverride == null)
         throw new NullPointerException("Null arguments not allowed");
      if (cacheName.equals(DEFAULT_CACHE_NAME))
         throw new IllegalArgumentException("Cache name cannot be used as it is a reserved, internal name");
      if (checkExisting) {
         Configuration existing = configurationOverrides.get(cacheName);
         if (existing != null) {
            existing.applyOverrides(configOverride);
            return existing.clone();
         }
      }
      Configuration configuration = defaultConfigIfNotPresent.clone();
      configuration.applyOverrides(configOverride.clone());
      configurationOverrides.put(cacheName, configuration);
      setConfigurationName(cacheName, configuration);
      return configuration;
   }

   /**
    * Retrieves the default cache associated with this cache manager. Note that the default cache does not need to be
    * explicitly created with {@link #createCache(String)} since it is automatically created lazily when first used.
    * <p/>
    * As such, this method is always guaranteed to return the default cache.
    *
    * @return the default cache.
    */
   public <K, V> Cache<K, V> getCache() {
      return getCache(DEFAULT_CACHE_NAME);
   }

   /**
    * Retrieves a named cache from the system. If the cache has been previously created with the same name, the running
    * cache instance is returned. Otherwise, this method attempts to create the cache first.
    * <p/>
    * When creating a new cache, this method will use the configuration passed in to the CacheManager on construction,
    * as a template, and then optionally apply any overrides previously defined for the named cache using the {@link
    * #defineConfiguration(String, Configuration)} or {@link #defineConfiguration(String, String, Configuration)}
    * methods, or declared in the configuration file.
    *
    * @param cacheName name of cache to retrieve
    *
    * @return a cache instance identified by cacheName
    */
   @SuppressWarnings("unchecked")
   public <K, V> Cache<K, V> getCache(String cacheName) {
      assertIsNotTerminated();
      if (cacheName == null)
         throw new NullPointerException("Null arguments not allowed");

      CacheWrapper cw = caches.get(cacheName);
      if (cw != null) {
         return cw.getCache();
      }

      boolean acquired = false;
      try {
         if (cacheCreateLock.tryLock(defaultConfiguration.getLockAcquisitionTimeout(), MILLISECONDS)) {
            acquired = true;
            return createCache(cacheName);
         } else {
            throw new CacheException("Unable to acquire lock on cache with name " + cacheName);
         }
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
         throw new CacheException("Interrupted while trying to get lock on cache with cache name " + cacheName, e);
      } finally {
         if (acquired)
            cacheCreateLock.unlock();
      }
   }

   @Override
   public boolean cacheExists(String cacheName) {
      return caches.containsKey(cacheName);
   }

   @Override
   public <K, V> Cache<K, V> getCache(String cacheName, boolean createIfAbsent) {
      boolean cacheExists = cacheExists(cacheName);
      if (!cacheExists && !createIfAbsent)
         return null;
      else
         return getCache(cacheName);
   }

   @Override
   public void removeCache(String cacheName) {
      RemoveCacheCommand cmd = new RemoveCacheCommand(this, globalComponentRegistry);
      cmd.injectComponents(null, globalComponentRegistry.getNamedComponentRegistry(cacheName));
      cmd.setCacheName(cacheName);
      Transport transport = getTransport();
      try {
         if (transport != null) {
            Configuration c = getConfiguration(cacheName);
            // Use sync replication timeout
            transport.invokeRemotely(null, cmd, ResponseMode.SYNCHRONOUS, c.getSyncReplTimeout(), false, null, false);
         }
         // Once sent to the cluster, remove the local cache
         cmd.perform(null);
      } catch (Throwable t) {
         throw new CacheException("Error removing cache", t);
      }
   }

   /**
    * {@inheritDoc}
    */
   public String getClusterName() {
      return globalConfiguration.getClusterName();
   }

   /**
    * {@inheritDoc}
    */
   public List<Address> getMembers() {
      Transport t = getTransport();
      return t == null ? null : t.getMembers();
   }

   /**
    * {@inheritDoc}
    */
   public Address getAddress() {
      Transport t = getTransport();
      return t == null ? null : t.getAddress();
   }

   /**
    * {@inheritDoc}
    */
   public Address getCoordinator() {
      Transport t = getTransport();
      return t == null ? null : t.getCoordinator();
   }

   /**
    * {@inheritDoc}
    */
   public boolean isCoordinator() {
      Transport t = getTransport();
      return t != null && t.isCoordinator();
   }

   @GuardedBy("Cache name lock container keeps a lock per cache name which guards this method")
   private Cache createCache(String cacheName) {
      CacheWrapper existingCache = caches.get(cacheName);
      if (existingCache != null)
         return existingCache.getCache();

      Configuration c = getConfiguration(cacheName);
      setConfigurationName(cacheName, c);

      c.setGlobalConfiguration(globalConfiguration);
      c.assertValid();
      Cache cache = new InternalCacheFactory().createCache(c, globalComponentRegistry, cacheName, reflectionCache);
      CacheWrapper cw = new CacheWrapper(cache);
      try {
         existingCache = caches.putIfAbsent(cacheName, cw);
         if (existingCache != null) {
            throw new IllegalStateException("attempt to initialize the cache twice");
         }
         cache.start();
      } finally {
         cw.latch.countDown();
      }
      return cache;
   }

   private Configuration getConfiguration(String cacheName) {
      Configuration c;
      if (cacheName.equals(DEFAULT_CACHE_NAME) || !configurationOverrides.containsKey(cacheName))
         c = defaultConfiguration.clone();
      else
         c = configurationOverrides.get(cacheName);
      return c;
   }

   public void start() {
      globalComponentRegistry.getComponent(CacheManagerJmxRegistration.class).start();
   }

   public void stop() {
      if (!stopping) {
         synchronized (this) {
            // DCL to make sure that only one thread calls stop at one time,
            // and any other calls by other threads are ignored.
            if (!stopping) {
               stopping = true;
               // make sure we stop the default cache LAST!
               Cache defaultCache = null;
               for (Map.Entry<String, CacheWrapper> entry : caches.entrySet()) {
                  if (entry.getKey().equals(DEFAULT_CACHE_NAME)) {
                     defaultCache = entry.getValue().cache;
                  } else {
                     Cache c = entry.getValue().cache;
                     if (c != null) {
                        unregisterCacheMBean(c);
                        c.stop();
                     }
                  }
               }

               if (defaultCache != null) {
                  unregisterCacheMBean(defaultCache);
                  defaultCache.stop();
               }
               globalComponentRegistry.getComponent(CacheManagerJmxRegistration.class).stop();
               globalComponentRegistry.stop();

               // Clear the reflection cache to avoid leaks
               reflectionCache.stop();
            } else {
               if (log.isTraceEnabled())
                  log.trace("Ignore call to stop as the cache manager is stopping");
            }
         }
      } else {
         if (log.isTraceEnabled())
            log.trace("Ignore call to stop as the cache manager is stopping");
      }
   }

   private void unregisterCacheMBean(Cache cache) {
      if (cache.getStatus().allowInvocations() && cache.getConfiguration().isExposeJmxStatistics()) {
         cache.getAdvancedCache().getComponentRegistry().getComponent(CacheJmxRegistration.class)
                 .unregisterCacheMBean();
      }
   }

   public void addListener(Object listener) {
      CacheManagerNotifier notifier = globalComponentRegistry.getComponent(CacheManagerNotifier.class);
      notifier.addListener(listener);
   }

   public void removeListener(Object listener) {
      CacheManagerNotifier notifier = globalComponentRegistry.getComponent(CacheManagerNotifier.class);
      notifier.removeListener(listener);
   }

   public Set<Object> getListeners() {
      CacheManagerNotifier notifier = globalComponentRegistry.getComponent(CacheManagerNotifier.class);
      return notifier.getListeners();
   }

   public ComponentStatus getStatus() {
      return globalComponentRegistry.getStatus();
   }

   public GlobalConfiguration getGlobalConfiguration() {
      return globalConfiguration;
   }

   public Configuration getDefaultConfiguration() {
      return defaultConfiguration;
   }

   public Set<String> getCacheNames() {
      // Get the XML/programmatically defined caches
      Set<String> names = new HashSet<String>(configurationOverrides.keySet());
      // Add the caches created dynamically without explicit config
      // Since caches could be modified dynamically, make a safe copy of keys
      names.addAll(Immutables.immutableSetConvert(caches.keySet()));
      names.remove(DEFAULT_CACHE_NAME);
      if (names.isEmpty())
         return Collections.emptySet();
      else
         return Immutables.immutableSetWrap(names);
   }

   public boolean isRunning(String cacheName) {
      CacheWrapper w = caches.get(cacheName);
      try {
         return w != null && w.latch.await(0, TimeUnit.MILLISECONDS);
      } catch (InterruptedException e) {
         return false;
      }
   }

   public boolean isDefaultRunning() {
      return isRunning(DEFAULT_CACHE_NAME);
   }

   @ManagedAttribute(description = "The status of the cache manager instance.")
   @Metric(displayName = "Cache manager status", dataType = DataType.TRAIT, displayType = DisplayType.SUMMARY)
   public String getCacheManagerStatus() {
      return getStatus().toString();
   }

   @ManagedAttribute(description = "The defined cache names and their statuses.  The default cache is not included in this representation.")
   @Metric(displayName = "List of defined caches", dataType = DataType.TRAIT, displayType = DisplayType.SUMMARY)
   public String getDefinedCacheNames() {
      StringBuilder result = new StringBuilder("[");
      for (String cacheName : getCacheNames()) {
         boolean started = caches.containsKey(cacheName);
         result.append(cacheName).append(started ? "(created)" : "(not created)");
      }
      result.append("]");
      return result.toString();
   }

   @ManagedAttribute(description = "The total number of defined caches, excluding the default cache.")
   @Metric(displayName = "Number of caches defined", displayType = DisplayType.SUMMARY)
   public String getDefinedCacheCount() {
      return String.valueOf(this.configurationOverrides.keySet().size());
   }

   @ManagedAttribute(description = "The total number of created caches, including the default cache.")
   @Metric(displayName = "Number of caches created", displayType = DisplayType.SUMMARY)
   public String getCreatedCacheCount() {
      return String.valueOf(this.caches.keySet().size());
   }

   @ManagedAttribute(description = "The total number of running caches, including the default cache.")
   @Metric(displayName = "Number of running caches", displayType = DisplayType.SUMMARY)
   public String getRunningCacheCount() {
      int running = 0;
      for (CacheWrapper cachew : caches.values()) {
         Cache cache = cachew.cache;
         if (cache != null && cache.getStatus() == ComponentStatus.RUNNING)
            running++;
      }
      return String.valueOf(running);
   }

   @ManagedAttribute(description = "Infinispan version.")
   @Metric(displayName = "Infinispan version", displayType = DisplayType.SUMMARY, dataType = DataType.TRAIT)
   public String getVersion() {
      return Version.printVersion();
   }

   @ManagedAttribute(description = "The name of this cache manager")
   @Metric(displayName = "Cache manager name", displayType = DisplayType.SUMMARY, dataType = DataType.TRAIT)
   public String getName() {
      return globalConfiguration.getCacheManagerName();
   }

   @ManagedOperation(description = "Starts the default cache associated with this cache manager")
   @Operation(displayName = "Starts the default cache")
   public void startCache() {
      getCache();
   }

   @ManagedOperation(description = "Starts a named cache from this cache manager")
   @Operation(name = "startCacheWithCacheName", displayName = "Starts a cache with the given name")
   public void startCache(@Parameter(name = "cacheName", description = "Name of cache to start") String cacheName) {
      getCache(cacheName);
   }

   @ManagedAttribute(description = "The network address associated with this instance")
   @Metric(displayName = "Network address", dataType = DataType.TRAIT, displayType = DisplayType.SUMMARY)
   public String getNodeAddress() {
      return getLogicalAddressString();
   }

   @ManagedAttribute(description = "The physical network addresses associated with this instance")
   @Metric(displayName = "Physical network addresses", dataType = DataType.TRAIT, displayType = DisplayType.SUMMARY)
   public String getPhysicalAddresses() {
      Transport t = getTransport();
      if (t == null) return "local";
      List<Address> address = t.getPhysicalAddresses();
      return address == null ? "local" : address.toString();
   }

   @ManagedAttribute(description = "List of members in the cluster")
   @Metric(displayName = "Cluster members", dataType = DataType.TRAIT, displayType = DisplayType.SUMMARY)
   public String getClusterMembers() {
      Transport t = getTransport();
      if (t == null) return "local";
      List<Address> addressList = t.getMembers();
      return addressList.toString();
   }

   @ManagedAttribute(description = "Size of the cluster in number of nodes")
   @Metric(displayName = "Cluster size", displayType = DisplayType.SUMMARY)
   public int getClusterSize() {
      Transport t = getTransport();
      if (t == null) return 1;
      return t.getMembers().size();
   }

   private String getLogicalAddressString() {
      return getAddress() == null ? "local" : getAddress().toString();
   }

   private void assertIsNotTerminated() {
      if (globalComponentRegistry.getStatus().isTerminated())
         throw new IllegalStateException("Cache container has been stopped and cannot be reused. Recreate the cache container.");
   }

   private Transport getTransport() {
      if (globalComponentRegistry == null) return null;
      return globalComponentRegistry.getComponent(Transport.class);
   }


   @Override
   public String toString() {
      return super.toString() + "@Address:" + getAddress();
   }

   /**
    * Use reflection for this as we don't want to expose setName on Configuration.
    */
   private void setConfigurationName(String cacheName, Configuration configuration) {
      ReflectionUtil.setValue(configuration, "name", cacheName);
   }
}

class CacheWrapper {
   Cache cache;
   CountDownLatch latch = new CountDownLatch(1);

   CacheWrapper(Cache cache) {
      this.cache = cache;
   }

   Cache getCache() {
      try {
         latch.await();
      } catch (InterruptedException ie) {
         Thread.currentThread().interrupt();
      }
      return cache;
   }
}
