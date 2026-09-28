package com.or.sdvoe.domain;

import java.util.ArrayList;
import java.util.List;

public class SignalCapability {
    private int maxWidth = 3840;
    private int maxHeight = 2160;
    private int maxFps = 60;
    private List<String> colorSpaces = new ArrayList<>(List.of("YUV422", "RGB"));
    private List<String> interfaces = new ArrayList<>(List.of("HDMI", "SDI"));
    private boolean lossless = true;
    private boolean hdcp;

    public int getMaxWidth() {
        return maxWidth;
    }

    public void setMaxWidth(int maxWidth) {
        this.maxWidth = maxWidth;
    }

    public int getMaxHeight() {
        return maxHeight;
    }

    public void setMaxHeight(int maxHeight) {
        this.maxHeight = maxHeight;
    }

    public int getMaxFps() {
        return maxFps;
    }

    public void setMaxFps(int maxFps) {
        this.maxFps = maxFps;
    }

    public List<String> getColorSpaces() {
        return colorSpaces;
    }

    public void setColorSpaces(List<String> colorSpaces) {
        this.colorSpaces = colorSpaces;
    }

    public List<String> getInterfaces() {
        return interfaces;
    }

    public void setInterfaces(List<String> interfaces) {
        this.interfaces = interfaces;
    }

    public boolean isLossless() {
        return lossless;
    }

    public void setLossless(boolean lossless) {
        this.lossless = lossless;
    }

    public boolean isHdcp() {
        return hdcp;
    }

    public void setHdcp(boolean hdcp) {
        this.hdcp = hdcp;
    }
}
