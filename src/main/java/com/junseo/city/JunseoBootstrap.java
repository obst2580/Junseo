package com.junseo.city;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;

import java.net.URI;
import java.net.URL;

/**
 * 서버가 월드를 불러오기 전에 실행됩니다.
 * 플러그인 안에 들어 있는 데이터팩(datapack/)을 서버에 등록해서,
 * 바닐라 「빠른 동작」 키(기본 G)를 누르면 스마트폰 화면이 열리게 합니다.
 */
public final class JunseoBootstrap implements PluginBootstrap {

    @Override
    public void bootstrap(BootstrapContext context) {
        context.getLifecycleManager().registerEventHandler(LifecycleEvents.DATAPACK_DISCOVERY.newHandler(event -> {
            try {
                URL url = getClass().getResource("/datapack");
                if (url == null) {
                    context.getLogger().error("플러그인 안에 datapack 폴더가 없어요.");
                    return;
                }
                URI uri = url.toURI();
                event.registrar().discoverPack(uri, "phone", configurer -> configurer
                        .title(Component.text("JunseoCity 스마트폰"))
                        .autoEnableOnServerStart(true));
            } catch (Exception e) {
                context.getLogger().error("스마트폰 데이터팩 등록 실패", e);
            }
        }));
    }
}
