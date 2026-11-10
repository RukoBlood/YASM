package ru.progrevgoev.yasm;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import ru.progrevgoev.yasm.registry.ModEntities;
import ru.progrevgoev.yasm.network.NetworkBootstrap;

/**
 * А насчёт вайбкодинга... Я не знаю, почему тебя это так бесит.
 * Потому что, во-первых, в комьюнити программистов вайб-кодинг не считается зашкваром.
 * Наоборот, блядь, его уважают, потому что за ним будущее.
 * Зачем, блядь, тратить десятки тысяч часов, блядь, просто на монотонную скучную работу, если он банально ускоряет работу?
 * Вот. А во-вторых, вайбкодинг — это не значит, что ты взял, написал и тебе, блядь, за 5 минут сделали.
 * Если чё, я сижу часами, блядь, сутками за компом. То есть с утра. До глубокого утра сутки.
 * Вот, чтобы что-то сделать.
 * А касаемо багов, у меня их нету, потому что я их все исправляю.
 * И код у меня идеально написан, потому что я не просто на вайб-кодил, а я сам лично проверяю код, блядь, и убираю лишнее.
 * Вот. Или дополняю его. Так что, бро, стереотипы не надо, зачем?
 * <p>Dragodast, 2026
 * <p>
 * Кстати про слова драгодаста.
 * 1. Вайбкод не считается зашкваром если проект реально качественный или хотя-бы опенсорс.
 * 2. Он лично не проверяет код, он не шарит даже за java. У него в моде 2 мэйн класса, класс девушки состоит из 5000 строк говнокода,
 * А также никакой структуры качественной. Ни сортировки классов, ни конструкторов, короче спагетти от клода (или секретного апи дипсика)*/

@Mod(Main.MOD_ID)
public final class Main {
    public static final String MOD_ID = "yasm";

    public Main(IEventBus modEventBus) {
        ModEntities.ENTITY_TYPES.register(modEventBus);
        modEventBus.addListener(this::onEntityAttributeCreation);
        NetworkBootstrap.register(modEventBus);
    }

    private void onEntityAttributeCreation(EntityAttributeCreationEvent event) {
        event.put(ModEntities.EXAMPLE_GIRL.get(), ru.progrevgoev.yasm.entity.EntityGirl.createAttributes().build());
        event.put(ModEntities.ALICE.get(), ru.progrevgoev.yasm.entity.EntityGirl.createAttributes().build());
    }
}
