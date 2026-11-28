package org.example.projectCom.service.panelService.myCache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class MyCache<K, V> implements HwCache<K, V> {
    private static final Logger logger = LoggerFactory.getLogger(MyCache.class);

    // Collections.synchronizedMap делает все операции потокобезопасными
    private final Map<K, V> cache = Collections.synchronizedMap(new HashMap<>());
    private final List<HwListener<K, V>> listeners = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void put(K key, V value) {
        cache.put(key, value);
        notifyListeners(key, value, "put");
    }

    @Override
    public void remove(K key) {
        V value = cache.remove(key);
        notifyListeners(key, value, "remove");
    }

    @Override
    public V get(K key) {
        V value = cache.get(key);
        if (value != null) {
            notifyListeners(key, value, "get");
        }
        return value;
    }

    @Override
    public void addListener(HwListener<K, V> listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    @Override
    public void removeListener(HwListener<K, V> listener) {
        listeners.remove(listener);
    }

    private void notifyListeners(K key, V value, String action) {
        // Теперь можно безопасно копировать список, не блокируя дополнительно
        List<HwListener<K, V>> copy;
        synchronized (listeners) {
            copy = new ArrayList<>(listeners);
        }

        for (HwListener<K, V> listener : copy) {
            try {
                listener.notify(key, value, action);
            } catch (Exception e) {
                logger.error("Listener error", e);
            }
        }
    }
    public List<V> getAll() {
        synchronized (cache) {
            return new ArrayList<>(cache.values());
        }
    }
}