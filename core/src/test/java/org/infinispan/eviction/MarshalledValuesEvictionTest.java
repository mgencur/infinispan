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
package org.infinispan.eviction;

import java.io.Externalizable;
import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import org.infinispan.commands.write.EvictCommand;
import org.infinispan.config.Configuration;
import org.infinispan.context.InvocationContext;
import org.infinispan.interceptors.MarshalledValueInterceptor;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.marshall.MarshalledValue;
import org.infinispan.marshall.StreamingMarshaller;
import org.infinispan.test.SingleCacheManagerTest;
import org.infinispan.test.TestingUtil;
import org.infinispan.test.fwk.TestCacheManagerFactory;
import org.jgroups.util.Util;
import org.testng.annotations.Test;

@Test(groups = "functional", testName = "eviction.MarshalledValuesEvictionTest", enabled = false, description = "Is this test even valid?  Evictions don't go thru the marshalled value interceptor when initiated form the data container!")
public class MarshalledValuesEvictionTest extends SingleCacheManagerTest {
   
   private static final int CACHE_SIZE=128;


   @Override
   protected EmbeddedCacheManager createCacheManager() throws Exception {
      Configuration cfg = new Configuration();
      cfg.setEvictionStrategy(EvictionStrategy.FIFO);
      cfg.setEvictionWakeUpInterval(100);
      cfg.setEvictionMaxEntries(CACHE_SIZE); // CACHE_SIZE max entries
      cfg.setUseLockStriping(false); // to minimise chances of deadlock in the unit test
      cfg.setUseLazyDeserialization(true);
      EmbeddedCacheManager cm = TestCacheManagerFactory.createCacheManager(cfg);
      cache = cm.getCache();
      StreamingMarshaller marshaller = TestingUtil.extractComponent(cache, StreamingMarshaller.class);
      MockMarshalledValueInterceptor interceptor = new MockMarshalledValueInterceptor(marshaller);
      assert TestingUtil.replaceInterceptor(cache, interceptor, MarshalledValueInterceptor.class);
      return cm;
   }
   
   public void testEvictCustomKeyValue() {
      for (int i = 0; i<CACHE_SIZE*2;i++) {
         EvictionPojo p1 = new EvictionPojo();
         p1.i = (int)Util.random(2000);
         EvictionPojo p2 = new EvictionPojo();
         p2.i = 24;
         cache.put(p1, p2);         
      }   

      // wait for the cache size to drop to CACHE_SIZE, up to a specified amount of time.
      long giveupTime = System.currentTimeMillis() + (1000 * 10); // 10 sec
      while (cache.getAdvancedCache().getDataContainer().size() > CACHE_SIZE && System.currentTimeMillis() < giveupTime) {
         TestingUtil.sleepThread(100);
      }
      
      assert cache.getAdvancedCache().getDataContainer().size() <= CACHE_SIZE : "Expected 1, was " + cache.size(); 

      //let eviction manager kick in
      Util.sleep(3000);
      MockMarshalledValueInterceptor interceptor = (MockMarshalledValueInterceptor) TestingUtil.findInterceptor(cache, MarshalledValueInterceptor.class);
      assert !interceptor.marshalledValueCreated;
   }

   public void testEvictPrimitiveKeyCustomValue() {
      for (int i = 0; i<CACHE_SIZE*2;i++) {
         EvictionPojo p1 = new EvictionPojo();
         p1.i = (int)Util.random(2000);
         EvictionPojo p2 = new EvictionPojo();
         p2.i = 24;
         cache.put(p1, p2);         
      }

      // wait for the cache size to drop to CACHE_SIZE, up to a specified amount of time.
      long giveupTime = System.currentTimeMillis() + (1000 * 10); // 10 sec
      while (cache.getAdvancedCache().getDataContainer().size() > CACHE_SIZE && System.currentTimeMillis() < giveupTime) {
         TestingUtil.sleepThread(100);
      }
      
      assert cache.getAdvancedCache().getDataContainer().size() <= CACHE_SIZE : "Expected 1, was " + cache.size(); 
      //let eviction manager kick in
      Util.sleep(3000);      
      MockMarshalledValueInterceptor interceptor = (MockMarshalledValueInterceptor) TestingUtil.findInterceptor(cache, MarshalledValueInterceptor.class);
      assert !interceptor.marshalledValueCreated;
   }
   
   static class MockMarshalledValueInterceptor extends MarshalledValueInterceptor {
      boolean marshalledValueCreated;
      
      MockMarshalledValueInterceptor(StreamingMarshaller marshaller) {
         injectMarshaller(marshaller);
      }

      @Override
      protected MarshalledValue createMarshalledValue(Object toWrap, InvocationContext ctx)
               throws NotSerializableException {
         marshalledValueCreated = true;
         return super.createMarshalledValue(toWrap, ctx);
      }

      @Override
      public Object visitEvictCommand(InvocationContext ctx, EvictCommand command) throws Throwable {
         // Reset value so that changes due to invocation can be asserted
         if (marshalledValueCreated) marshalledValueCreated = false;
         return super.visitEvictCommand(ctx, command);
      }
   }

   class EvictionPojo implements Externalizable {
      int i;

      public boolean equals(Object o) {
         if (this == o) return true;
         if (o == null || getClass() != o.getClass()) return false;
         EvictionPojo pojo = (EvictionPojo) o;
         if (i != pojo.i) return false;
         return true;
      }

      public int hashCode() {
         int result;
         result = i;
         return result;
      }

      @Override
      public void writeExternal(ObjectOutput out) throws IOException {
         out.writeInt(i);
      }

      @Override
      public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
         i = in.readInt();
      }

   }
}
