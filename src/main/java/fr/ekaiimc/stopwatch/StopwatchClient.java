package fr.ekaiimc.stopwatch;

import com.mojang.brigadier.arguments.StringArgumentType;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class StopwatchClient implements ClientModInitializer {

    public static final String MOD_ID = "stopwatch";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static KeyBinding toggleKey;
    private static KeyBinding resetKey;
    private static KeyBinding lapKey;

    private boolean isRunning = false;
    private long startTime = 0;
    private long storedTime = 0;

    // Données
    private final List<Long> laps = new ArrayList<>();
    private final List<StopwatchLapDisplay> cachedLaps = new ArrayList<>();

    // Managers
    private final StopwatchCheckpointManager checkpointManager = new StopwatchCheckpointManager();

    // Optimisation
    private final StringBuilder sb = new StringBuilder();

    @Override
    public void onInitializeClient() {
        LOGGER.info("Initialisation de StopwatchClient...");

        AutoConfig.register(StopwatchConfig.class, GsonConfigSerializer::new);
        registerKeys();
        registerEvents();
        registerCommands();

        HudRenderCallback.EVENT.register(this::renderStopwatch);
    }

    private void registerKeys() {
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.stopwatch.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_H, "category.stopwatch.main"));
        resetKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.stopwatch.reset", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_J, "category.stopwatch.main"));
        lapKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.stopwatch.lap", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, "category.stopwatch.main"));
    }

    private void registerEvents() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            StopwatchConfig config = AutoConfig.getConfigHolder(StopwatchConfig.class).getConfig();

            while (toggleKey.wasPressed()) toggleTimer();

            while (resetKey.wasPressed()) {
                resetTimer();
                checkpointManager.reset();
            }

            while (lapKey.wasPressed()) recordLap();

            // --- DÉTECTION AUTOMATIQUE ---
            if (checkpointManager.update(client)) {

                // CAS AUTO-START
                if (!isRunning && config.autoStart && checkpointManager.getNextCheckpointIndex() == 1) {

                    startTimer(); // 1. On lance le chrono
                    recordLap();  // 2. CORRECTION : On valide immédiatement le CP 0 (Départ) dans la liste !

                    client.player.sendMessage(Text.literal("GO ! Chrono démarré !").formatted(Formatting.GREEN), true);
                }
                // CAS NORMAL (En course)
                else if (isRunning) {
                    recordLap();
                }
            }
        });
    }

    private void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("sw")

                    // =========================================
                    // 1. GESTION DES CIRCUITS (TRACKS)
                    // =========================================
                    .then(ClientCommandManager.literal("track")

                            // /sw track create <Nom>
                            .then(ClientCommandManager.literal("create")
                                    .then(ClientCommandManager.argument("name", StringArgumentType.word())
                                            .executes(context -> {
                                                String name = StringArgumentType.getString(context, "name");
                                                checkpointManager.createTrack(name);
                                                context.getSource().sendFeedback(Text.literal("Circuit '" + name + "' créé et sélectionné !").formatted(Formatting.GREEN));
                                                resetTimer();
                                                return 1;
                                            })
                                    )
                            )

                            // /sw track select <Nom>
                            .then(ClientCommandManager.literal("select")
                                    .then(ClientCommandManager.argument("name", StringArgumentType.word())
                                            .executes(context -> {
                                                String name = StringArgumentType.getString(context, "name");
                                                if (checkpointManager.selectTrack(name)) {
                                                    context.getSource().sendFeedback(Text.literal("Circuit '" + name + "' sélectionné.").formatted(Formatting.GREEN));
                                                    resetTimer();
                                                } else {
                                                    context.getSource().sendFeedback(Text.literal("Circuit inconnu.").formatted(Formatting.RED));
                                                }
                                                return 1;
                                            })
                                    )
                            )

                            // /sw track list
                            .then(ClientCommandManager.literal("list")
                                    .executes(context -> {
                                        List<String> tracks = checkpointManager.getTrackList();
                                        String current = checkpointManager.getCurrentTrackName();
                                        context.getSource().sendFeedback(Text.literal("--- Circuits Disponibles ---").formatted(Formatting.GOLD));

                                        if (tracks.isEmpty()) {
                                            context.getSource().sendFeedback(Text.literal("Aucun circuit. Faites /sw track create <nom>").formatted(Formatting.GRAY));
                                        } else {
                                            for (String t : tracks) {
                                                String prefix = t.equals(current) ? ">> " : "   ";
                                                int color = t.equals(current) ? Formatting.GREEN.getColorValue() : Formatting.WHITE.getColorValue();
                                                context.getSource().sendFeedback(Text.literal(prefix + t).styled(s -> s.withColor(color)));
                                            }
                                        }
                                        return 1;
                                    })
                            )

                            // /sw track reset_records <Nom> (NOUVEAU)
                            .then(ClientCommandManager.literal("reset_records")
                                    .then(ClientCommandManager.argument("name", StringArgumentType.word())
                                            .executes(context -> {
                                                String name = StringArgumentType.getString(context, "name");
                                                if (checkpointManager.resetTrackRecords(name)) {
                                                    context.getSource().sendFeedback(Text.literal("Records du circuit '" + name + "' effacés.").formatted(Formatting.YELLOW));
                                                } else {
                                                    context.getSource().sendError(Text.literal("Circuit introuvable."));
                                                }
                                                return 1;
                                            })
                                    )
                            )

                            // /sw track delete <Nom> (NOUVEAU)
                            .then(ClientCommandManager.literal("delete")
                                    .then(ClientCommandManager.argument("name", StringArgumentType.word())
                                            .executes(context -> {
                                                String name = StringArgumentType.getString(context, "name");
                                                // On vérifie si on supprime le circuit actuel pour reset le HUD
                                                boolean wasCurrent = name.equals(checkpointManager.getCurrentTrackName());

                                                if (checkpointManager.deleteTrack(name)) {
                                                    context.getSource().sendFeedback(Text.literal("Circuit '" + name + "' définitivement supprimé.").formatted(Formatting.RED));
                                                    if (wasCurrent) {
                                                        resetTimer(); // On vide l'écran
                                                        context.getSource().sendFeedback(Text.literal("Le circuit actif a été supprimé.").formatted(Formatting.GRAY));
                                                    }
                                                } else {
                                                    context.getSource().sendError(Text.literal("Circuit introuvable."));
                                                }
                                                return 1;
                                            })
                                    )
                            )
                    )

                    // =========================================
                    // 2. CRÉATION DES POINTS (Checkpoints)
                    // =========================================
                    .then(ClientCommandManager.literal("start")
                            .then(ClientCommandManager.argument("name", StringArgumentType.greedyString())
                                    .executes(context -> {
                                        if (checkpointManager.getCurrentTrackName() == null) {
                                            context.getSource().sendError(Text.literal("Aucun circuit sélectionné ! Faites d'abord /sw track create <nom>"));
                                            return 0;
                                        }
                                        var source = context.getSource();
                                        String name = StringArgumentType.getString(context, "name");
                                        checkpointManager.startCreation(name, source.getPosition().x, source.getPosition().z);
                                        source.sendFeedback(Text.literal("Point A défini. Allez au point B -> /sw end").formatted(Formatting.YELLOW));
                                        return 1;
                                    })
                            )
                    )

                    .then(ClientCommandManager.literal("end")
                            .executes(context -> {
                                var source = context.getSource();
                                if (!checkpointManager.isCreating()) {
                                    source.sendError(Text.literal("Faites d'abord /sw start <nom>"));
                                    return 0;
                                }
                                checkpointManager.finishCreation(source.getPosition().x, source.getPosition().z);
                                source.sendFeedback(Text.literal("Checkpoint ajouté au circuit " + checkpointManager.getCurrentTrackName()).formatted(Formatting.GREEN));
                                return 1;
                            })
                    )

                    // =========================================
                    // 3. UTILITAIRES (Circuit Actif)
                    // =========================================
                    .then(ClientCommandManager.literal("list")
                            .executes(context -> {
                                String track = checkpointManager.getCurrentTrackName();
                                if (track == null) {
                                    context.getSource().sendError(Text.literal("Aucun circuit sélectionné."));
                                    return 0;
                                }
                                var list = checkpointManager.getCurrentList();
                                context.getSource().sendFeedback(Text.literal("--- Checkpoints de " + track + " ---").formatted(Formatting.GOLD));

                                if (list.isEmpty()) {
                                    context.getSource().sendFeedback(Text.literal("Aucun point.").formatted(Formatting.RED));
                                } else {
                                    for (int i = 0; i < list.size(); i++) {
                                        String cpName = list.get(i).name;
                                        // Petit bonus visuel : Index 0 = Départ
                                        String prefix = (i == 0) ? "[DÉPART] " : (i + ". ");
                                        context.getSource().sendFeedback(Text.literal(prefix + cpName).formatted(Formatting.WHITE));
                                    }
                                }
                                return 1;
                            })
                    )

                    .then(ClientCommandManager.literal("clear")
                            .executes(context -> {
                                if (checkpointManager.getCurrentTrackName() == null) return 0;
                                checkpointManager.clearCheckpoints();
                                context.getSource().sendFeedback(Text.literal("Circuit vidé (Checkpoints supprimés).").formatted(Formatting.RED));
                                return 1;
                            })
                    )

                    // =========================================
                    // 4. CONSULTATION DES RECORDS
                    // =========================================
                    .then(ClientCommandManager.literal("records")
                            // Cas 1 : Records du circuit actuel
                            .executes(context -> {
                                String current = checkpointManager.getCurrentTrackName();
                                if (current == null) {
                                    context.getSource().sendError(Text.literal("Aucun circuit sélectionné."));
                                    return 0;
                                }
                                printRecords(context.getSource(), current);
                                return 1;
                            })
                            // Cas 2 : Records d'un circuit spécifique
                            .then(ClientCommandManager.argument("name", StringArgumentType.word())
                                    .executes(context -> {
                                        String name = StringArgumentType.getString(context, "name");
                                        if (!checkpointManager.trackExists(name)) {
                                            context.getSource().sendError(Text.literal("Le circuit '" + name + "' n'existe pas."));
                                            return 0;
                                        }
                                        printRecords(context.getSource(), name);
                                        return 1;
                                    })
                            )
                    )
            );
        });
    }

    // --- LOGIQUE INTERNE ---

    private void toggleTimer() {
        if (isRunning) {
            storedTime += System.currentTimeMillis() - startTime;
            isRunning = false;
        } else {
            startTimer();
        }
    }

    private void startTimer() {
        startTime = System.currentTimeMillis();
        isRunning = true;
    }

    private void resetTimer() {
        isRunning = false;
        storedTime = 0;
        laps.clear();
        cachedLaps.clear();
    }

    private void recordLap() {
        if (!isRunning) return;
        long currentTime = (System.currentTimeMillis() - startTime) + storedTime;
        laps.add(currentTime);

        StopwatchConfig config = AutoConfig.getConfigHolder(StopwatchConfig.class).getConfig();
        int currentIndex = laps.size() - 1;

        int totalCP = checkpointManager.getCheckpointCount();
        if (totalCP == 0) totalCP = 1;

        int hitCheckpointIndex = currentIndex % totalCP; // 0 = Ligne, 1 = S1...

        // 1. DÉTECTION FIN DE TOUR
        boolean isLapFinish = (hitCheckpointIndex == 0 && currentIndex > 0);

        // 2. CALCUL TEMPS SECTEUR
        long currentLapEndTime = laps.get(currentIndex);
        long previousEventTime = (currentIndex == 0) ? 0 : laps.get(currentIndex - 1);
        long currentSegmentDuration = currentLapEndTime - previousEventTime;

        // 3. CALCUL TEMPS TOTAL DU TOUR (Running Time)
        int offsetToStart = isLapFinish ? totalCP : hitCheckpointIndex;
        int indexStartOfThisLap = currentIndex - offsetToStart;
        if (indexStartOfThisLap < 0) indexStartOfThisLap = 0;

        long startOfThisLapTime = laps.get(indexStartOfThisLap);
        if (currentIndex < totalCP) startOfThisLapTime = 0;

        long currentLapRunningTime = currentLapEndTime - startOfThisLapTime;

        // --- 4. GESTION DES RECORDS (CORRIGÉ) ---
        boolean isBestSector = false;
        boolean isBestLap = false;

        // FIX : On ne vérifie les records QUE si ce n'est pas le tout premier point (Départ)
        if (currentIndex > 0) {
            isBestSector = checkpointManager.checkSectorRecord(hitCheckpointIndex, currentSegmentDuration);

            if (isLapFinish) {
                isBestLap = checkpointManager.checkLapRecord(currentLapRunningTime);
            }
        }

        // --- 5. FORMATAGE ---
        int currentLapNum = (currentIndex / totalCP) + 1;
        if (isLapFinish) currentLapNum -= 1;
        if (currentLapNum < 1) currentLapNum = 1;

        String label = (hitCheckpointIndex == 0) ? "Fin Tour" : "Secteur " + hitCheckpointIndex;
        String lapNumStr = currentLapNum + ". " + label;

        String sectorTime = formatTime(currentSegmentDuration);
        if (isBestSector) sectorTime = "§d" + sectorTime;

        String totalTimeStr = formatTime(currentLapRunningTime);
        if (isBestLap) totalTimeStr = "§d" + totalTimeStr;
        else if (isLapFinish) totalTimeStr = "§e" + totalTimeStr;
        else totalTimeStr = "§7" + totalTimeStr;

        // 6. DELTA
        String deltaText = "";
        int deltaColor = 0xFFFFFFFF;

        if (config.useSpeedrunMode && currentIndex >= totalCP) {
            int prevIndex = currentIndex - totalCP;
            long prevSegmentEnd = laps.get(prevIndex);
            long prevSegmentStart = (prevIndex == 0) ? 0 : laps.get(prevIndex - 1);
            long prevSegmentDuration = prevSegmentEnd - prevSegmentStart;

            long delta = currentSegmentDuration - prevSegmentDuration;
            double val = Math.round((delta / 1000.0) * 100.0) / 100.0;

            deltaText = (delta > 0 ? "+" : "") + val + "s";
            deltaColor = (delta < 0) ? 0xFF55FF55 : 0xFFFF5555;
        }

        cachedLaps.add(new StopwatchLapDisplay(lapNumStr, sectorTime, deltaText, deltaColor, totalTimeStr));
    }

    // --- AFFICHAGE HUD DYNAMIQUE ---
    private void renderStopwatch(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.options.hudHidden) return;

        StopwatchConfig config = AutoConfig.getConfigHolder(StopwatchConfig.class).getConfig();
        if (!config.enabled) return;

        // --- 1. CALCUL DU TEMPS À AFFICHER ---
        long sessionTime = isRunning ? (System.currentTimeMillis() - startTime) + storedTime : storedTime;
        long displayTime = sessionTime;

        // Si mode Speedrun activé : On affiche le temps du TOUR ACTUEL, pas le temps total
        if (config.useSpeedrunMode && !laps.isEmpty()) {
            int totalCP = checkpointManager.getCheckpointCount();
            if (totalCP == 0) totalCP = 1;

            // On cherche le dernier passage sur la ligne de départ (Checkpoint 0)
            // On parcourt la liste à l'envers pour trouver le plus récent
            long lastFinishLineTime = 0;
            for (int i = laps.size() - 1; i >= 0; i--) {
                // L'index 0 (et ses multiples) correspond à la ligne de départ
                if (i % totalCP == 0) {
                    lastFinishLineTime = laps.get(i);
                    break; // On a trouvé le dernier départ en date, on arrête
                }
            }

            // Temps Affiché = Temps Total - Temps au dernier passage de la ligne
            displayTime = sessionTime - lastFinishLineTime;
        }

        String mainTimeString = formatTime(displayTime);

        TextRenderer textRenderer = client.textRenderer;
        float scale = config.scalePercent / 100f;
        int x = config.x;
        int y = config.y;

        // Isolation de la matrice principale
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(x, y);
        context.getMatrices().scale(scale, scale);

        // Dessin du temps
        context.drawTextWithShadow(textRenderer, mainTimeString, 0, 0, 0xFFFFFFFF);

        // --- 2. TABLEAU DES TEMPS ---
        if (cachedLaps.size() > 1) { // On affiche s'il y a au moins 1 secteur enregistré (hors départ)
            context.getMatrices().pushMatrix();
            context.getMatrices().translate(0, 14);
            context.getMatrices().scale(0.8f, 0.8f);

            int lineHeight = 12;
            int totalCP = checkpointManager.getCheckpointCount();
            if (totalCP == 0) totalCP = 1;

            int maxVisible = 8;
            // On commence à 1 pour cacher le "Départ" (Index 0)
            int startIndex = Math.max(1, cachedLaps.size() - maxVisible);
            int visibleCount = cachedLaps.size() - startIndex;

            // Calcul dynamique largeur
            int minNameWidth = textRenderer.getWidth("Nom") + 10;
            int maxDetectedWidth = minNameWidth;
            for (int k = startIndex; k < cachedLaps.size(); k++) {
                int w = textRenderer.getWidth(cachedLaps.get(k).lapNumber);
                if (w > maxDetectedWidth) maxDetectedWidth = w;
            }
            int col1Width = maxDetectedWidth + 10;

            int col1_X = 0;
            int col2_X = col1Width;
            int col3_X = col2_X + 60;
            int col4_X = col3_X + 45;
            int totalBoxWidth = col4_X + 60;

            // Calcul dynamique hauteur
            int boxHeight = (visibleCount + 1) * lineHeight + 4;
            for (int k = startIndex; k < cachedLaps.size(); k++) {
                if (k % totalCP == 0 && k != cachedLaps.size() - 1) boxHeight += 5;
            }

            // Fond
            context.fill(-4, -2, totalBoxWidth, boxHeight, 0x90000000);

            // En-têtes
            context.drawText(textRenderer, "Nom", col1_X, 0, 0xFFFFAA00, false);
            context.drawText(textRenderer, "Secteur", col2_X, 0, 0xFFFFAA00, false);
            context.drawText(textRenderer, "Gap", col3_X, 0, 0xFFFFAA00, false);
            context.drawText(textRenderer, "Total", col4_X, 0, 0xFFFFAA00, false);

            int currentY = lineHeight;

            for (int i = startIndex; i < cachedLaps.size(); i++) {
                StopwatchLapDisplay lap = cachedLaps.get(i);

                context.drawText(textRenderer, lap.lapNumber, col1_X, currentY, 0xFFAAAAAA, false);
                context.drawText(textRenderer, lap.sectorTime, col2_X, currentY, 0xFFFFFFFF, false);
                context.drawText(textRenderer, lap.deltaText, col3_X, currentY, lap.deltaColor, false);
                context.drawText(textRenderer, lap.totalTime, col4_X, currentY, 0xFFCCCCCC, false);

                currentY += lineHeight;

                // Séparateur fin de tour
                if (i % totalCP == 0 && i != cachedLaps.size() - 1) {
                    context.fill(0, currentY - 2, totalBoxWidth - 5, currentY - 1, 0x40FFFFFF);
                    currentY += 5;
                }
            }
            context.getMatrices().popMatrix();
        }

        context.getMatrices().popMatrix();
    }

    // --- AFFICHAGE RECORDS CHAT ---
    private void printRecords(net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource source, String trackName) {
        StopwatchTrackRecords rec = checkpointManager.getRecords(trackName);

        source.sendFeedback(Text.literal("=== Records : " + trackName + " ===").formatted(Formatting.GOLD, Formatting.BOLD));

        if (rec == null || (rec.bestLapTime == -1 && rec.bestSectorTimes.isEmpty())) {
            source.sendFeedback(Text.literal("Aucun record.").formatted(Formatting.GRAY));
            return;
        }

        // Meilleur Tour
        String lapTimeStr = (rec.bestLapTime == -1) ? "--:--:--" : formatTime(rec.bestLapTime);
        source.sendFeedback(Text.literal("🏆 Meilleur Tour : ").formatted(Formatting.LIGHT_PURPLE)
                .append(Text.literal(lapTimeStr).formatted(Formatting.WHITE)));

        // Meilleurs Secteurs & Tour Idéal
        long idealLap = 0;
        boolean idealComplete = true;
        int totalSectors = rec.bestSectorTimes.size();

        if (totalSectors > 0) {
            source.sendFeedback(Text.literal("--- Meilleurs Secteurs ---").formatted(Formatting.YELLOW));

            // Ordre : 1 -> N-1 -> 0 (Fin)
            List<Integer> order = new ArrayList<>();
            for (int i = 1; i < totalSectors; i++) order.add(i);
            if (totalSectors > 0) order.add(0);

            for (int i : order) {
                long time = rec.bestSectorTimes.get(i);
                if (time == -1) { idealComplete = false; continue; }
                idealLap += time;
                String name = (i == 0) ? "Secteur Final" : "Secteur " + i;
                source.sendFeedback(Text.literal(name + " : ").formatted(Formatting.GOLD)
                        .append(Text.literal(formatTime(time)).formatted(Formatting.GREEN)));
            }
        }

        // Tour Idéal
        if (idealComplete && idealLap > 0) {
            source.sendFeedback(Text.literal("⚡ Tour Idéal : ").formatted(Formatting.AQUA)
                    .append(Text.literal(formatTime(idealLap)).formatted(Formatting.WHITE)));
        }
    }

    // Optimisation StringBuilder
    private String formatTime(long millis) {
        long ms = millis % 1000;
        long seconds = (millis / 1000) % 60;
        long minutes = (millis / (1000 * 60)) % 60;
        long hours = (millis / (1000 * 60 * 60));

        sb.setLength(0);
        if (hours < 10) sb.append('0'); sb.append(hours).append(':');
        if (minutes < 10) sb.append('0'); sb.append(minutes).append(':');
        if (seconds < 10) sb.append('0'); sb.append(seconds).append(':');
        if (ms < 10) sb.append("00"); else if (ms < 100) sb.append('0'); sb.append(ms);
        return sb.toString();
    }
}