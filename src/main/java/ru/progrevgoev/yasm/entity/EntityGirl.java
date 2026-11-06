package ru.progrevgoev.yasm.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import ru.progrevgoev.yasm.ai.BehaviorMode;
import ru.progrevgoev.yasm.ai.goals.GoalFollowPlayer;
import ru.progrevgoev.yasm.ai.goals.GoalFreeRoam;
import ru.progrevgoev.yasm.ai.goals.GoalStay;
import ru.progrevgoev.yasm.scene.SceneManager;

public abstract class EntityGirl extends PathfinderMob {
    private final SceneManager sceneManager = new SceneManager(this);
    private BehaviorMode behaviorMode = BehaviorMode.FOLLOW;

    protected EntityGirl(EntityType<? extends EntityGirl> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FOLLOW_RANGE, 24.0D);
    }

    @Override
    protected void registerGoals() {
        // Lower number = higher priority. Scene-controlled movement will be added here later.
        this.goalSelector.addGoal(1, new GoalStay(this));
        this.goalSelector.addGoal(2, new GoalFollowPlayer(this, 1.0D, 2.0F, 12.0F));
        this.goalSelector.addGoal(5, new GoalFreeRoam(this, 1.0D));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 8.0F));
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide) {
            sceneManager.tick();
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("YasmBehavior", behaviorMode.name());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("YasmBehavior")) {
            behaviorMode = BehaviorMode.fromId(tag.getString("YasmBehavior"));
        }
    }

    public SceneManager getSceneManager() {
        return sceneManager;
    }

    public BehaviorMode getBehaviorMode() {
        return behaviorMode;
    }

    public void setBehaviorMode(BehaviorMode behaviorMode) {
        this.behaviorMode = behaviorMode;
    }

    public abstract ResourceLocation getModelId();
}
