package ru.progrevgoev.yasm.render.player;

import ru.progrevgoev.yasm.entity.EntityGirl;

public class PlayerModel {
    public boolean isVisible; //Is player model visible?
    private boolean isThirdPerson;

    public PlayerModelPartsVisibilityState state;

    //Gets visibility state.
    public PlayerModelPartsVisibilityState getState(){
        boolean isSexMode = EntityGirl.hasSexWithPlayer;
        return isThirdPerson
                ? isSexMode ? PlayerModelPartsVisibilityState.TPP_SEX : PlayerModelPartsVisibilityState.TPP_NORMAL
                : isSexMode ? PlayerModelPartsVisibilityState.FPP_SEX : PlayerModelPartsVisibilityState.FPP_NORMAL;
        //пиздец это я сделал без вайкодинга
    }
}
