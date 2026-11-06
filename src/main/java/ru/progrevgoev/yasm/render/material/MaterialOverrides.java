package ru.progrevgoev.yasm.render.material;

import java.util.HashMap;
import java.util.Map;

/** Client-side visual overrides; server synchronizes logical visibility state. */
public final class MaterialOverrides {
    private final Map<String, Boolean> visibility = new HashMap<>();

    public void setVisible(String materialOrGroup, boolean visible) {
        visibility.put(materialOrGroup, visible);
    }

    public boolean isVisible(String materialOrGroup, boolean defaultValue) {
        return visibility.getOrDefault(materialOrGroup, defaultValue);
    }

    public void clear() {
        visibility.clear();
    }
}
