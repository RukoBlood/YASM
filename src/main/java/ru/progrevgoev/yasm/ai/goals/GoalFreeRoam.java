package ru.progrevgoev.yasm.ai.goals;

import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import ru.progrevgoev.yasm.entity.EntityGirl;

/** Autonomous roaming placeholder; add only when AUTONOMOUS mode is active. */
public final class GoalFreeRoam extends RandomStrollGoal {
    private final EntityGirl girl;

    public GoalFreeRoam(EntityGirl girl, double speed) {
        super(girl, speed);
        this.girl = girl;
    }

    @Override
    public boolean canUse() {
        return girl.getBehaviorMode() == ru.progrevgoev.yasm.ai.BehaviorMode.AUTONOMOUS && super.canUse();
    }
}
