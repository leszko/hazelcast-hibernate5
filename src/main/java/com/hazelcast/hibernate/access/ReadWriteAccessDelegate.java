/* 
 * Copyright (c) 2008-2010, Hazel Ltd. All Rights Reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at 
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.hazelcast.hibernate.access;

import com.hazelcast.hibernate.CacheEnvironment;
import com.hazelcast.hibernate.region.HazelcastRegion;
import org.hibernate.cache.CacheException;
import org.hibernate.cache.access.SoftLock;

import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * Makes <b>READ COMMITTED</b> consistency guarantees even in a clustered environment.
 *
 * @author Leo Kim (lkim@limewire.com)
 */
public class ReadWriteAccessDelegate<T extends HazelcastRegion> extends AbstractAccessDelegate<T> {

    private final int lockTimeout;

    public ReadWriteAccessDelegate(T hazelcastRegion, final Properties props) {
        super(hazelcastRegion, props);
        lockTimeout = CacheEnvironment.getLockTimeoutInSeconds(props);
    }

    public boolean afterInsert(final Object key, final Object value, final Object version) throws CacheException {
        getCache().put(key, value);
        return true;
    }

    /**
     * {@inheritDoc}
     * <p/>
     * Called after <code>com.hazelcast.hibernate.access.ReadWriteAccessDelegate.lockItem()</code>
     */
    public boolean afterUpdate(final Object key, final Object value, final Object currentVersion, final Object previousVersion,
                               final SoftLock lock) throws CacheException {
        try {
            getCache().put(key, value);
        } finally {
            unlockItem(key, lock);
        }
        return true;
    }

    public boolean putFromLoad(final Object key, final Object value, final long txTimestamp, final Object version,
                               final boolean minimalPutOverride) throws CacheException {
        getCache().put(key, value);
        return true;
    }

    public SoftLock lockItem(final Object key, final Object version) throws CacheException {
        if (lockTimeout > 0) {
            if (!getCache().tryLock(key, lockTimeout, TimeUnit.SECONDS)) {
                throw new CacheException("Cache lock could not be acquired! Wait-time: " + lockTimeout + " seconds");
            }
        } else {
            getCache().lock(key);
        }
        return new SoftLock() {
        }; // dummy lock
    }

    public void unlockItem(final Object key, final SoftLock lock) throws CacheException {
        getCache().unlock(key);
    }

    public void unlockRegion(SoftLock lock) throws CacheException {
    }
}
