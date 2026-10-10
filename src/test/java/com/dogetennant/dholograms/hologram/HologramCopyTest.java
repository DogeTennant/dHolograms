package com.dogetennant.dholograms.hologram;

import com.dogetennant.dholograms.hologram.line.AnimationType;
import com.dogetennant.dholograms.hologram.line.EntityLine;
import com.dogetennant.dholograms.hologram.line.HologramLine;
import com.dogetennant.dholograms.hologram.line.ItemLine;
import com.dogetennant.dholograms.hologram.line.TextLine;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** /dh clone: the copy has every setting and every line, each as its own type. */
class HologramCopyTest {

    private final World world = mock(World.class);

    private Hologram arena() {
        Hologram arena = new Hologram("arena", new Location(world, 1, 64, 2));
        arena.getDisplaySettings().setBackgroundColor(Backgrounds.GAME);
        arena.getDisplaySettings().setRange(20);
        arena.setRefreshTicks(40);
        arena.setViewPermission("vip.see");
        arena.setTag("spawn");
        arena.setDisabled(true);
        arena.addFlag(HologramFlag.DISABLE_ANIMATIONS);
        arena.setClickAction("RIGHT", "COMMAND:warp arena");

        TextLine title = new TextLine(arena, 0, "<gold>Arena", 0.1);
        title.setAnimationType(AnimationType.WAVE);
        title.setAnimColor1(0x112233);
        title.setAnimColor2(0x445566);
        title.setAnimSpeed(5);
        title.setViewPermission("staff.only");
        title.setXOffset(0.5);
        title.setZOffset(-0.25);
        arena.addLineInternal(0, title);

        ItemLine sword = new ItemLine(arena, 1, "diamond_sword", 0);
        sword.setRotationSpeed(3.0f);
        sword.setBobAmplitude(0.08f);
        arena.addLineInternal(0, sword);

        arena.addPageInternal();                        // page 2, empty
        EntityLine block = new EntityLine(arena, 0, "gold_block", -0.2);
        block.setRotationSpeed(2.0f);
        arena.addLineInternal(2, block);                // page 3
        return arena;
    }

    @Test
    void theCloneHasEverySetting() {
        Hologram copy = arena().copyAs("arena2");

        assertThat(copy.getName()).isEqualTo("arena2");
        assertThat(copy.getLocation()).isEqualTo(new Location(world, 1, 64, 2));
        assertThat(copy.getDisplaySettings().getBackgroundColor()).isEqualTo(Backgrounds.GAME);
        assertThat(copy.getDisplaySettings().getRange()).isEqualTo(20);
        assertThat(copy.getRefreshTicks()).isEqualTo(40);
        assertThat(copy.getViewPermission()).isEqualTo("vip.see");
        assertThat(copy.getTag()).isEqualTo("spawn");
        assertThat(copy.isDisabled()).isTrue();
        assertThat(copy.getFlags()).containsExactly(HologramFlag.DISABLE_ANIMATIONS);
        assertThat(copy.getClickAction("RIGHT")).isEqualTo("COMMAND:warp arena");
    }

    @Test
    void theCloneHasEveryLineAsItsOwnType() {
        Hologram copy = arena().copyAs("arena2");

        assertThat(copy.getPageCount()).isEqualTo(3);
        assertThat(copy.getPage(1)).isEmpty();

        TextLine title = (TextLine) copy.getPage(0).get(0);
        assertThat(title.getRawContent()).isEqualTo("<gold>Arena");
        assertThat(title.getAnimationType()).isEqualTo(AnimationType.WAVE);
        assertThat(title.getAnimColor1()).isEqualTo(0x112233);
        assertThat(title.getAnimColor2()).isEqualTo(0x445566);
        assertThat(title.getAnimSpeed()).isEqualTo(5);
        assertThat(title.getViewPermission()).isEqualTo("staff.only");
        assertThat(title.getHeightOffset()).isEqualTo(0.1);
        assertThat(title.getXOffset()).isEqualTo(0.5);
        assertThat(title.getZOffset()).isEqualTo(-0.25);

        // an item line used to come back as a text line reading "diamond_sword"
        assertThat(copy.getPage(0).get(1)).isInstanceOf(ItemLine.class);
        ItemLine sword = (ItemLine) copy.getPage(0).get(1);
        assertThat(sword.getRawContent()).isEqualTo("diamond_sword");
        assertThat(sword.getRotationSpeed()).isEqualTo(3.0f);
        assertThat(sword.getBobAmplitude()).isEqualTo(0.08f);

        assertThat(copy.getPage(2)).singleElement().isInstanceOf(EntityLine.class);
        EntityLine block = (EntityLine) copy.getPage(2).get(0);
        assertThat(block.getRawContent()).isEqualTo("gold_block");
        assertThat(block.getRotationSpeed()).isEqualTo(2.0f);
        assertThat(block.getHeightOffset()).isEqualTo(-0.2);
    }

    @Test
    void theCloneIsItsOwn() {
        Hologram arena = arena();
        Hologram copy = arena.copyAs("arena2");

        assertThat(copy.getAllLines()).extracting(HologramLine::getHologram).containsOnly(copy);
        assertThat(copy.getDisplaySettings()).isNotSameAs(arena.getDisplaySettings());
        copy.setClickAction("RIGHT", "COMMAND:spawn");
        copy.getDisplaySettings().setRange(5);
        assertThat(arena.getClickAction("RIGHT")).isEqualTo("COMMAND:warp arena");
        assertThat(arena.getDisplaySettings().getRange()).isEqualTo(20);
    }
}
