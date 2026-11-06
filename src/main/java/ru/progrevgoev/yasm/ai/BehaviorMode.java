package ru.progrevgoev.yasm.ai;

public enum BehaviorMode {
    FOLLOW,
    STAY,
    AUTONOMOUS;

    public static BehaviorMode fromId(String id) {
        try {
            return valueOf(id);
        } catch (IllegalArgumentException exception) {
            return FOLLOW;
        }
    }
}
