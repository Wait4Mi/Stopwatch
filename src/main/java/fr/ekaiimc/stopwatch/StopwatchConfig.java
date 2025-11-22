package fr.ekaiimc.stopwatch;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

@Config(name = "stopwatch")
public class StopwatchConfig implements ConfigData {

    @ConfigEntry.Gui.Tooltip
    public boolean enabled = true;

    @ConfigEntry.Category("logic")
    @ConfigEntry.Gui.Tooltip
    public boolean useSpeedrunMode = true;

    // --- NOUVELLE OPTION ---
    @ConfigEntry.Category("logic")
    @ConfigEntry.Gui.Tooltip
    public boolean autoStart = false; // Démarrage automatique au passage de la ligne

    @ConfigEntry.Category("display")
    @ConfigEntry.Gui.Tooltip
    public int x = 10;

    @ConfigEntry.Category("display")
    @ConfigEntry.Gui.Tooltip
    public int y = 10;

    @ConfigEntry.Category("display")
    @ConfigEntry.Gui.Tooltip
    public int scalePercent = 100;
}