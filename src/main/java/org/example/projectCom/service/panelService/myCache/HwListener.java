package org.example.projectCom.service.panelService.myCache;

public interface HwListener<K, V> {
    void notify(K key, V value, String action);
}
