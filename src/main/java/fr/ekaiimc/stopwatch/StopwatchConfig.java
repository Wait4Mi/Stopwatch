package fr.ekaiimc.stopwatch;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

@Config(name = "stopwatch")
public class StopwatchConfig implements ConfigData { // <--- IMPORTANT : implements ConfigData

    @ConfigEntry.Gui.Tooltip
    public boolean enabled = true; // <--- IMPORTANT : public

    //@ConfigEntry.Category("display")
    @ConfigEntry.Gui.Tooltip
    public int x = 10; // <--- IMPORTANT : public

    //@ConfigEntry.Category("display")
    @ConfigEntry.Gui.Tooltip
    public int y = 10; // <--- IMPORTANT : public

    //@ConfigEntry.Category("display")
    @ConfigEntry.BoundedDiscrete(min = 50, max = 500)
    public int scalePercent = 100; // <--- IMPORTANT : public
}