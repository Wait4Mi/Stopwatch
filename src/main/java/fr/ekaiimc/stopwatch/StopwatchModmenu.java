package fr.ekaiimc.stopwatch;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import fr.ekaiimc.stopwatch.StopwatchConfig; // Assurez-vous que cet import est bon
import me.shedaniel.autoconfig.AutoConfig;

public class StopwatchModmenu implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        // Cette ligne fait le lien magique entre ModMenu et Cloth Config.
        // "parent" est l'écran précédent (la liste des mods), pour pouvoir y revenir avec "Echap".
        return parent -> AutoConfig.getConfigScreen(StopwatchConfig.class, parent).get();
    }
}