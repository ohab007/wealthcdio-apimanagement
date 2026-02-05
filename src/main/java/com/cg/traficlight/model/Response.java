package com.cg.traficlight.model;

import java.util.EnumMap;

public class Response {

    private Directions activeDirection;

    private EnumMap <Directions,Colors> inactiveState;

    private Colors activeColor;

    private boolean paused;

    public Response() {
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

    public EnumMap<Directions, Colors> getInactiveState() {
        return inactiveState;
    }

    public void setInactiveState(EnumMap<Directions, Colors> inactiveState) {
        this.inactiveState = inactiveState;
    }
}
