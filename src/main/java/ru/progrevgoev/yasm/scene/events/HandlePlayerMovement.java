package ru.progrevgoev.yasm.scene.events;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import org.lwjgl.glfw.GLFW;

public class HandlePlayerMovement {
    private static boolean notInSex = true; //FLAG: is in sex scene
    public static boolean isFastSex = false;
    public static boolean isCumming = false;
    public static Input input;

    @SubscribeEvent
    public void PreventPlayerFromMoving(MovementInputUpdateEvent event){
        input = event.getInput();
        isFastSex = input.shiftKeyDown;
        isCumming = input.jumping;

        if (!notInSex){
            if (input.shiftKeyDown){
                //STUB: triggers fast sex action
            }
            if (input.jumping){
                //STUB: if cum percentage is less than 100% (1.0F), then force exit scene. Otherwise, play cumming animation.
                //cum percentage grabs from gui.sex.SexUI
            }

            input.up = false;
            input.down = false;
            input.left = false;
            input.right = false;
            input.shiftKeyDown = false;
            input.jumping = false;
            input.forwardImpulse = 0.0f;
            input.leftImpulse = 0.0f;
            //set player velocity to 0.
            Minecraft.getInstance().player.setDeltaMovement(Vec3.ZERO);
        }
    }

    public static boolean isNotInSex(){
        return notInSex;
    }

    public static void setMovementLock(boolean lock){
        notInSex = lock;
        if (!lock) handlePlayerMovementTick();
    }

    @OnlyIn(Dist.CLIENT)
    private static void handlePlayerMovementTick(){
        LocalPlayer localPlayer = Minecraft.getInstance().player;
        //STUB: if player in sex scene and presses buttons, sends message "Jump to stop sex".
    }

    //это чтобы нихуя не понатыкал
    @SubscribeEvent
    public void PreventPlayerFromTakingAction(InputEvent.MouseButton.Pre event){
        if (!notInSex && event.getAction() == GLFW.GLFW_PRESS){
            event.setCanceled(true);
        }
    }
}
