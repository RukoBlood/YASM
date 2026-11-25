package ru.progrevgoev.yasm.util;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Bi-Directional Map. Can be useful. Code stolen from jenny mod.*/
public class BiDirectionalMap<K, V> {
    private final HashMap<K, V> forwardMap = new HashMap<>();
    private final HashMap<V, K> reverseMap = new HashMap<>();

    public void put(K k, V v){
        V oldValue = this.forwardMap.put(k, v);
        this.reverseMap.remove(oldValue);
        this.reverseMap.put(v, k);
    }

    public V getByKey(K k){
        return this.forwardMap.get(k);
    }

    public K getByValue(V v){
        return this.reverseMap.get(v);
    }

    public int size(){
        return this.forwardMap.size();
    }

    public void removeByKey(K k){
        V removedValue = this.forwardMap.get(k);
        if (removedValue != null){
            this.forwardMap.remove(k);
            this.reverseMap.remove(removedValue);
        }
    }

    public Set<Map.Entry<K, V>> entrySet(){
        return this.forwardMap.entrySet();
    }

    public Set<K> keySet(){
        return this.forwardMap.keySet();
    }

    public Set<V> valueSet(){
        return this.reverseMap.keySet();
    }

    public void clear(){
        this.reverseMap.clear();
        this.forwardMap.clear();
    }
}
