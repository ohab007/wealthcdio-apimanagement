package com.cg.traficlight.model;

public class State {

    private Directions activeDirection;

    private Colors activeColor;

    private boolean paused;

    public State() {
    }

    public Directions getActiveDirection() {
        return activeDirection;
    }

    public void setActiveDirection(Directions activeDirection) {
        this.activeDirection = activeDirection;
    }

    public Colors getActiveColor() {
        return activeColor;
    }

    public void setActiveColor(Colors activeColor) {
        this.activeColor = activeColor;
    }

    public boolean isPaused() {
        return paused;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
    }
}
