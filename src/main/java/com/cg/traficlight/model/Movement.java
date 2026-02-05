package com.cg.traficlight.model;

public class Movement {
    private Directions direction;
    private Colors colors;
    private long durationMillis;

    public Movement(Directions direction, Colors colors, long durationMillis) {
        this.direction = direction;
        this.colors = colors;
        this.durationMillis = durationMillis;
    }

    public Directions getDirection() {
        return direction;
    }

    public void setDirection(Directions direction) {
        this.direction = direction;
    }

    public Colors getColors() {
        return colors;
    }

    public void setColors(Colors colors) {
        this.colors = colors;
    }

    public long getDurationMillis() {
        return durationMillis;
    }

    public void setDurationMillis(long durationMillis) {
        this.durationMillis = durationMillis;
    }
}
