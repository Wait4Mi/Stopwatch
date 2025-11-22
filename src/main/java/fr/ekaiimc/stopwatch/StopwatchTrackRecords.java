package fr.ekaiimc.stopwatch;

import java.util.ArrayList;
import java.util.List;

public class StopwatchTrackRecords {
    public long bestLapTime = -1; // -1 veut dire "pas encore de record"
    public List<Long> bestSectorTimes = new ArrayList<>(); // Liste des meilleurs temps par secteur
}