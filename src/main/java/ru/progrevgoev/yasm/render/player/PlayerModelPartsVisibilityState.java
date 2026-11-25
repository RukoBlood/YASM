package ru.progrevgoev.yasm.render.player;

/**States of player body parts visibility. <p>
 * In fpp mode only hands visible. In tpp mode full body is visible. <p>
 * Normal - no wiener, SEX - yes wiener. Enough said.*/
public enum PlayerModelPartsVisibilityState {
    FPP_NORMAL, FPP_SEX, TPP_NORMAL, TPP_SEX
}

//Смотри атомов, ручками написал энум с состояниями. Драгодаст так не умеет.
//Я хочу секретный апи дипсика чтобы он мне говнокодил, а я кайфовал.
//Пока что вынес в отдельный класс, потом будет внутри другого.
