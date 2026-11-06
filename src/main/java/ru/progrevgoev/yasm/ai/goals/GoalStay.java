package ru.progrevgoev.yasm.ai.goals;

import net.minecraft.world.entity.ai.goal.Goal;
import ru.progrevgoev.yasm.entity.EntityGirl;

import java.util.EnumSet;

/** Stub for the STAY behavior. */
public final class GoalStay extends Goal {
    private final EntityGirl girl;

    public GoalStay(EntityGirl girl) {
        this.girl = girl;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return girl.getBehaviorMode() == ru.progrevgoev.yasm.ai.BehaviorMode.STAY;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void tick() {
        girl.getNavigation().stop();
    }
}
