package net.bramp.ffmpeg;

import rx.functions.Action1;

public interface ProcessFacade {
    public void shutdown();
    public boolean readStdout(final Action1<String> online);
}
