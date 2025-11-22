package fr.ekaiimc.stopwatch;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import fr.ekaiimc.stopwatch.StopwatchCheckpoint;
import fr.ekaiimc.stopwatch.StopwatchTrackRecords;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StopwatchCheckpointManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CP_FILE = FabricLoader.getInstance().getConfigDir().resolve("stopwatch_tracks.json").toFile();
    private static final File RECORD_FILE = FabricLoader.getInstance().getConfigDir().resolve("stopwatch_records_multi.json").toFile();

    // Map : Nom du circuit -> Liste des checkpoints
    private Map<String, List<StopwatchCheckpoint>> tracks = new HashMap<>();
    // Map : Nom du circuit -> Records
    private Map<String, StopwatchTrackRecords> allRecords = new HashMap<>();

    private String currentTrackName = null; // Le circuit actif

    private int nextCheckpointIndex = 0;

    // Création
    private boolean creating = false;
    private double tempX, tempZ;
    private String tempName;

    // Position
    private boolean firstTick = true;
    private double lastX, lastZ;

    public StopwatchCheckpointManager() {
        loadTracks();
        loadRecords();

        // Sélection par défaut du premier circuit trouvé si dispo
        if (!tracks.isEmpty()) {
            currentTrackName = tracks.keySet().iterator().next();
        }
    }

    // --- GESTION DES TRACKS ---

    public void createTrack(String name) {
        if (tracks.containsKey(name)) return;
        tracks.put(name, new ArrayList<>());
        allRecords.put(name, new StopwatchTrackRecords());
        selectTrack(name);
        saveTracks();
        saveRecords();
    }

    public boolean selectTrack(String name) {
        if (!tracks.containsKey(name)) return false;
        currentTrackName = name;
        nextCheckpointIndex = 0;
        System.out.println("DEBUG: Track selected: " + name);
        return true;
    }

    public String getCurrentTrackName() {
        return currentTrackName;
    }

    public List<String> getTrackList() {
        return new ArrayList<>(tracks.keySet());
    }

    // --- LOGIQUE JEU ---

    public void reset() {
        nextCheckpointIndex = 0;
    }

    public boolean update(MinecraftClient client) {
        if (client.player == null || currentTrackName == null) return false;

        // On récupère la liste du circuit ACTIF
        List<StopwatchCheckpoint> currentCheckpoints = tracks.get(currentTrackName);
        if (currentCheckpoints == null || currentCheckpoints.isEmpty()) return false;

        double currX = client.player.getX();
        double currZ = client.player.getZ();

        if (firstTick) { lastX = currX; lastZ = currZ; firstTick = false; return false; }

        // Particules sur le circuit actif seulement
        if (client.world.getTime() % 20 == 0) {
            for (StopwatchCheckpoint cp : currentCheckpoints) {
                client.world.addParticleClient(ParticleTypes.HAPPY_VILLAGER, cp.x1, client.player.getY()+1, cp.z1, 0,0,0);
                client.world.addParticleClient(ParticleTypes.HAPPY_VILLAGER, cp.x2, client.player.getY()+1, cp.z2, 0,0,0);
            }
        }

        boolean triggered = false;
        if (nextCheckpointIndex < currentCheckpoints.size()) {
            StopwatchCheckpoint target = currentCheckpoints.get(nextCheckpointIndex);

            if (linesIntersect(lastX, lastZ, currX, currZ, target.x1, target.z1, target.x2, target.z2)) {
                // Petit message discret
                client.player.sendMessage(Text.literal("[" + currentTrackName + "] " + target.name).formatted(Formatting.GREEN), true);

                nextCheckpointIndex++;
                if (nextCheckpointIndex >= currentCheckpoints.size()) nextCheckpointIndex = 0;
                triggered = true;
            }
        }
        lastX = currX; lastZ = currZ;
        return triggered;
    }

    // --- GESTION DES RECORDS ---

    public boolean checkSectorRecord(int sectorIndex, long duration) {
        if (currentTrackName == null) return false;

        StopwatchTrackRecords records = allRecords.computeIfAbsent(currentTrackName, k -> new StopwatchTrackRecords());

        while (records.bestSectorTimes.size() <= sectorIndex) {
            records.bestSectorTimes.add(-1L);
        }

        long currentBest = records.bestSectorTimes.get(sectorIndex);
        if (currentBest == -1 || duration < currentBest) {
            records.bestSectorTimes.set(sectorIndex, duration);
            saveRecords();
            return true;
        }
        return false;
    }

    public boolean checkLapRecord(long duration) {
        if (currentTrackName == null) return false;
        StopwatchTrackRecords records = allRecords.computeIfAbsent(currentTrackName, k -> new StopwatchTrackRecords());

        if (records.bestLapTime == -1 || duration < records.bestLapTime) {
            records.bestLapTime = duration;
            saveRecords();
            return true;
        }
        return false;
    }

    // --- CRÉATION DE POINTS ---

    public void startCreation(String name, double x, double z) {
        this.tempName = name; this.tempX = x; this.tempZ = z; this.creating = true;
    }

    public void finishCreation(double x, double z) {
        if (!creating || currentTrackName == null) return;

        StopwatchCheckpoint newCp = new StopwatchCheckpoint(tempName, tempX, tempZ, x, z);
        tracks.get(currentTrackName).add(newCp);

        creating = false;
        saveTracks();
        // On reset les records de CE circuit car il a changé
        allRecords.put(currentTrackName, new StopwatchTrackRecords());
        saveRecords();
    }

    public void clearCheckpoints() {
        if (currentTrackName == null) return;
        tracks.get(currentTrackName).clear();
        nextCheckpointIndex = 0;
        saveTracks();
        allRecords.put(currentTrackName, new StopwatchTrackRecords());
        saveRecords();
    }

    // --- ACCESSEURS ---
    public int getCheckpointCount() {
        if (currentTrackName == null || !tracks.containsKey(currentTrackName)) return 0;
        return tracks.get(currentTrackName).size();
    }

    public String getCheckpointName(int index) {
        if (currentTrackName == null) return "???";
        List<StopwatchCheckpoint> cps = tracks.get(currentTrackName);
        if (cps == null || cps.isEmpty()) return "???";
        return cps.get(index % cps.size()).name;
    }

    public List<StopwatchCheckpoint> getCurrentList() {
        if (currentTrackName == null) return new ArrayList<>();
        return tracks.get(currentTrackName);
    }

    public int getNextCheckpointIndex() {
        return nextCheckpointIndex;
    }

    public boolean isCreating() { return creating; }

    // Récupérer les records pour un circuit donné
    public StopwatchTrackRecords getRecords(String trackName) {
        return allRecords.get(trackName);
    }

    // Vérifier si un circuit existe
    public boolean trackExists(String trackName) {
        return tracks.containsKey(trackName);
    }

    // Supprimer les records d'un circuit (mais garder le circuit)
    public boolean resetTrackRecords(String trackName) {
        if (!allRecords.containsKey(trackName)) return false;

        // On remplace par un objet vide
        allRecords.put(trackName, new StopwatchTrackRecords());
        saveRecords();
        return true;
    }

    // Supprimer entièrement un circuit
    public boolean deleteTrack(String trackName) {
        if (!tracks.containsKey(trackName)) return false;

        // Suppression des données
        tracks.remove(trackName);
        allRecords.remove(trackName);

        // Si on a supprimé le circuit sur lequel on est actuellement
        if (trackName.equals(currentTrackName)) {
            currentTrackName = null;
            nextCheckpointIndex = 0;

            // On essaie de basculer sur un autre circuit s'il en reste
            if (!tracks.isEmpty()) {
                currentTrackName = tracks.keySet().iterator().next();
            }
        }

        saveTracks();
        saveRecords();
        return true;
    }
    // --- JSON I/O (Maps) ---

    private void saveTracks() {
        try (FileWriter writer = new FileWriter(CP_FILE)) {
            GSON.toJson(tracks, writer);
        } catch (IOException e) { e.printStackTrace(); }
    }

    private void loadTracks() {
        if (!CP_FILE.exists()) return;
        try (FileReader reader = new FileReader(CP_FILE)) {
            Type type = new TypeToken<HashMap<String, List<StopwatchCheckpoint>>>(){}.getType();
            Map<String, List<StopwatchCheckpoint>> loaded = GSON.fromJson(reader, type);
            if (loaded != null) tracks = loaded;
        } catch (IOException e) { e.printStackTrace(); }
    }

    private void saveRecords() {
        try (FileWriter writer = new FileWriter(RECORD_FILE)) {
            GSON.toJson(allRecords, writer);
        } catch (IOException e) { e.printStackTrace(); }
    }

    private void loadRecords() {
        if (!RECORD_FILE.exists()) return;
        try (FileReader reader = new FileReader(RECORD_FILE)) {
            Type type = new TypeToken<HashMap<String, StopwatchTrackRecords>>(){}.getType();
            Map<String, StopwatchTrackRecords> loaded = GSON.fromJson(reader, type);
            if (loaded != null) allRecords = loaded;
        } catch (IOException e) { e.printStackTrace(); }
    }

    private static boolean linesIntersect(double p1x, double p1z, double p2x, double p2z, double p3x, double p3z, double p4x, double p4z) {
        double s1_x = p2x - p1x; double s1_z = p2z - p1z;
        double s2_x = p4x - p3x; double s2_z = p4z - p3z;
        double s = (-s1_z * (p1x - p3x) + s1_x * (p1z - p3z)) / (-s2_x * s1_z + s1_x * s2_z);
        double t = ( s2_x * (p1z - p3z) - s2_z * (p1x - p3x)) / (-s2_x * s1_z + s1_x * s2_z);
        return (s >= 0 && s <= 1 && t >= 0 && t <= 1);
    }
}