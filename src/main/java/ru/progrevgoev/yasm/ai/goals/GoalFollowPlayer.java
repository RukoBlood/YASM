package ru.progrevgoev.yasm.ai.goals;

import net.minecraft.world.entity.ai.goal.Goal;
import ru.progrevgoev.yasm.entity.EntityGirl;

import java.util.EnumSet;

/** TODO: implement owner/target selection and path navigation. */
public final class GoalFollowPlayer extends Goal {
    private final EntityGirl girl;
    private final double speed;
    private final float startDistance;
    private final float stopDistance;

    public GoalFollowPlayer(EntityGirl girl, double speed, float startDistance, float stopDistance) {
        this.girl = girl;
        this.speed = speed;
        this.startDistance = startDistance;
        this.stopDistance = stopDistance;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return false; // Stub: intentionally disabled until owner semantics are implemented.
    }

    public double getSpeed() { return speed; }
    public float getStartDistance() { return startDistance; }
    public float getStopDistance() { return stopDistance; }
}
