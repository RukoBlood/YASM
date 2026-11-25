package ru.progrevgoev.yasm.gui.sex;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;

@OnlyIn(Dist.CLIENT)
public class SexGUI {
    public static boolean isVisible = false; //FLAG: is sex gui visible (False by default)
    public static double cumPercentage = 0.0; //VALUE: sex progress

    private static double cumPercentageDrawable = cumPercentage; //Value: renderable amount.
    private static float transitionStep = 0.0f;
    private static float cumStep = 0.0f;
    private static boolean keepSpacePressed = false;
    private static boolean displayState = true;



    public static void showUI() {
        if (!isVisible){
            resetCumPercentage();
            isVisible = true;
            displayState = true;
        }
    }

    public static void setHornyMeterVisible(boolean visible) {
        if (!isVisible) {
            resetCumPercentage();
            isVisible = true;
            displayState = visible;
        }
    }

    public static void hide() {
        resetCumPercentage();
        isVisible = false;
        displayState = true;
    }

    public static boolean isSexUIVisible() {
        return isVisible;
    }

    private static void resetCumPercentage() {
    }

    @SubscribeEvent
    public void onRenderGameOverlay(){} //TODO: Port this piece of code to nf 1.21.11


}
