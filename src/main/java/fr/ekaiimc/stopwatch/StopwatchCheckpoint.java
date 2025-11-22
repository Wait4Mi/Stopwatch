package fr.ekaiimc.stopwatch;

public class StopwatchCheckpoint {
    public String name;
    public double x1, z1;
    public double x2, z2;

    public StopwatchCheckpoint(String name, double x1, double z1, double x2, double z2) {
        this.name = name;
        this.x1 = x1;
        this.z1 = z1;
        this.x2 = x2;
        this.z2 = z2;
    }
}