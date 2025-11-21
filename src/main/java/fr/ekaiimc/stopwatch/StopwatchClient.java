package fr.ekaiimc.stopwatch;

import fr.ekaiimc.stopwatch.StopwatchConfig;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StopwatchClient implements ClientModInitializer {

    public static final String MOD_ID = "stopwatch";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static KeyBinding toggleKey;
    private static KeyBinding resetKey;

    // Logique du chrono
    private boolean isRunning = false;
    private long startTime = 0;
    private long storedTime = 0;

    @Override
    public void onInitializeClient() {
        LOGGER.info("Initialisation de StopwatchClient...");

        // 1. Config
        AutoConfig.register(StopwatchConfig.class, GsonConfigSerializer::new);

        // 2. Touches
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.stopwatch.toggle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_H,
                "category.stopwatch.main"
        ));

        resetKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.stopwatch.reset",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_J,
                "category.stopwatch.main"
        ));

        // 3. Events Ticks
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleKey.wasPressed()) {
                toggleTimer();
            }
            while (resetKey.wasPressed()) {
                resetTimer();
            }
        });

        // 4. Rendu
        HudRenderCallback.EVENT.register(this::renderStopwatch);

        LOGGER.info("StopwatchClient initialisé avec succès !");
    }

    private void toggleTimer() {
        if (isRunning) {
            storedTime += System.currentTimeMillis() - startTime;
            isRunning = false;
            LOGGER.info("Chrono mis en pause.");
        } else {
            startTime = System.currentTimeMillis();
            isRunning = true;
            LOGGER.info("Chrono démarré.");
        }
    }

    private void resetTimer() {
        isRunning = false;
        storedTime = 0;
        LOGGER.info("Chrono réinitialisé.");
    }

    private void renderStopwatch(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();

        // Sécurités de base
        if (client == null || client.player == null || client.options.hudHidden) return;

        // Récupération de la config
        StopwatchConfig config = AutoConfig.getConfigHolder(StopwatchConfig.class).getConfig();
        if (!config.enabled) return;

        // Calcul du temps
        long currentTime = isRunning ? (System.currentTimeMillis() - startTime) + storedTime : storedTime;
        String timeString = formatTime(currentTime);
        TextRenderer textRenderer = client.textRenderer;

        // Variables de position et scale
        float scale = config.scalePercent / 100f;
        int x = config.x;
        int y = config.y;

        // --- DESSIN ---
        context.getMatrices().pushMatrix();

        // 1. Translation avec Z-Index positif (50.0f) pour être sûr d'être DEVANT
        context.getMatrices().translate(x, y);

        // 2. Scale
        context.getMatrices().scale(scale, scale);

        // 3. Dessin (Couleur blanche 0xFFFFFFFF)
        context.drawTextWithShadow(textRenderer, timeString, 0, 0, 0xFFFFFFFF);

        context.getMatrices().popMatrix();
    }

    private String formatTime(long millis) {
        long ms = millis % 1000;
        long seconds = (millis / 1000) % 60;
        long minutes = (millis / (1000 * 60)) % 60;
        long hours = (millis / (1000 * 60 * 60));
        return String.format("%02d:%02d:%02d:%03d", hours, minutes, seconds, ms);
    }
}
