package org.steve.sonicpulse.client.gui;

import net.minecraft.util.Identifier;

public enum SonicPulseSkin {
    MODERN("Modern", Identifier.of("sonicpulse", "textures/gui/skin_modern.png"), 0xFF222222, 0xFFFFFFFF),
    RETRO("Retro", Identifier.of("sonicpulse", "textures/gui/skin_retro.png"), 0xFF553311, 0xFFCCAA88),
    TRANSPARENT("Transparent", Identifier.of("sonicpulse", "textures/gui/skin_transparent.png"), 0x80000000, 0x80FFFFFF),
    NETHER("Nether", Identifier.of("sonicpulse", "textures/gui/skin_nether.png"), 0xFF330000, 0xFFFF4400),
    OCEAN("Ocean", Identifier.of("sonicpulse", "textures/gui/skin_ocean.png"), 0xFF001133, 0xFF00AAFF),
    ENDER("Ender", Identifier.of("sonicpulse", "textures/gui/skin_ender.png"), 0xFF110022, 0xFFCC00FF),
    GOLD("Gold", Identifier.of("sonicpulse", "textures/gui/skin_gold.png"), 0xFF332200, 0xFFFFCC00);

    private final String name;
    private final Identifier texture;
    private final int bgColor;
    private final int borderColor;

    SonicPulseSkin(String name, Identifier texture, int bgColor, int borderColor) {
        this.name = name;
        this.texture = texture;
        this.bgColor = bgColor;
        this.borderColor = borderColor;
    }

    public String getName() { return name; }
    public Identifier getTexture() { return texture; }
    public int getBgColor() { return bgColor; }
    public int getBorderColor() { return borderColor; }
}
