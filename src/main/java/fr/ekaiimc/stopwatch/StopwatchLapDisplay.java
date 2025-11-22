package fr.ekaiimc.stopwatch;

public class StopwatchLapDisplay {
    public String lapNumber;   // "1"
    public String sectorTime;  // "00:12:450"
    public String deltaText;   // "-1.2s"
    public int deltaColor;     // Vert/Rouge
    public String totalTime;   // "00:12:450" (Temps cumulé)

    public StopwatchLapDisplay(String lapNumber, String sectorTime, String deltaText, int deltaColor, String totalTime) {
        this.lapNumber = lapNumber;
        this.sectorTime = sectorTime;
        this.deltaText = deltaText;
        this.deltaColor = deltaColor;
        this.totalTime = totalTime;
    }
}