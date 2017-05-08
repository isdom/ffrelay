package net.bramp.ffmpeg;

public interface ProcessMonitor {
    public void setProcess(final Process p);
    public void onOutput(final String line);
}
