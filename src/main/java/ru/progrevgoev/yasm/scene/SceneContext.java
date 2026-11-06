package ru.progrevgoev.yasm.scene;

import net.minecraft.world.entity.player.Player;
import ru.progrevgoev.yasm.entity.EntityGirl;

import java.util.Optional;

public record SceneContext(EntityGirl girl, Optional<Player> initiator) {}
