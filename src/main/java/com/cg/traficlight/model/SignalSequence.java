package com.cg.traficlight.model;

public class SignalSequence {

    private long timeGreenNS = 20;

    private long timeYellowNS = 3;

    private long timeGreenEW = 20;

    private long timeYellowEW = 3;

    public long getTimeGreenNS() {
        return timeGreenNS;
    }

    public void setTimeGreenNS(long timeGreenNS) {
        this.timeGreenNS = timeGreenNS;
    }

    public long getTimeYellowNS() {
        return timeYellowNS;
    }

    public void setTimeYellowNS(long timeYellowNS) {
        this.timeYellowNS = timeYellowNS;
    }

    public long getTimeGreenEW() {
        return timeGreenEW;
    }

    public void setTimeGreenEW(long timeGreenEW) {
        this.timeGreenEW = timeGreenEW;
    }

    public long getTimeYellowEW() {
        return timeYellowEW;
    }

    public void setTimeYellowEW(long timeYellowEW) {
        this.timeYellowEW = timeYellowEW;
    }
}
