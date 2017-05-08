package org.jocean.ffrelay;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.jocean.idiom.ExceptionUtils;
import org.joda.time.Period;
import org.joda.time.format.PeriodFormatter;
import org.joda.time.format.PeriodFormatterBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.bramp.ffmpeg.FFmpeg;
import net.bramp.ffmpeg.FFmpegExecutor;
import net.bramp.ffmpeg.ProcessMonitor;
import net.bramp.ffmpeg.builder.FFmpegBuilder;
import net.bramp.ffmpeg.builder.FFmpegBuilder.Verbosity;
import net.bramp.ffmpeg.job.FFmpegJob;
import net.bramp.ffmpeg.progress.Progress;
import net.bramp.ffmpeg.progress.ProgressListener;

public class FFRelay {
    private final Logger OUT;
    private static final PeriodFormatter PERIODFMT = new PeriodFormatterBuilder()
            .appendYears()
            .appendSuffix(" year ")
            .appendMonths()
            .appendSuffix(" month ")
            .appendWeeks()
            .appendSuffix(" week ")
            .appendDays()
            .appendSuffix(" day ")
            .appendHours()
            .appendSuffix(" hour ")
            .appendMinutes()
            .appendSuffix(" minute ")
            .appendSeconds()
            .appendSuffix(" s")
            .toFormatter();

	public FFRelay(final FFmpeg ffmpeg, final String name, final String source, final String dest) {
	    this._ffmpeg = ffmpeg;
	    this._name = name;
	    this._source = source;
	    this._dest = dest;
	    this.OUT = LoggerFactory.getLogger(name);
    }
	
	public synchronized void start() {
	    if (this._running) {
	        OUT.warn("ffrelay has already started");
	        return;
	    }
	    
	    this._running = true;
	    this._runner.submit(new Runnable() {
            @Override
            public void run() {
                doRelay();
            }});
	}
	
	private void doRelay() {
        while (this._running) {
            OUT.info("relay from {} --> to {}", this._source, this._dest);
            try {
                final FFmpegBuilder builder =  new FFmpegBuilder()
                    .setVerbosity(Verbosity.INFO)
                    .setInput(this._source)
                    .addOutput(this._dest)
                        .setFormat("flv")
                        .setAudioCodec("copy")
                        .setVideoCodec("copy")
                    .done();
                    
                final FFmpegExecutor executor = new FFmpegExecutor(this._ffmpeg);
                final FFmpegJob job = executor.createJob(builder, new ProgressListener() {
                    @Override
                    public void progress(final Progress progress) {
                    }
                });
                
                job.run(new ProcessMonitor() {
                    @Override
                    public void setProcess(final Process p) {
                        _currentProcess = p;
                        OUT.info("current process has been set: {}", _currentProcess);
                    }

                    @Override
                    public void onOutput(final String line) {
                        _lastOutputTime = System.currentTimeMillis();
                        _lastOutput = line;
                        OUT.info(line);
                    }});
            } catch (Exception e) {
                OUT.warn("relay stopped bcs of {}", ExceptionUtils.exception2detail(e));
                if (this._running) {
                    OUT.info("restart relaying...");
                }
            }
        }
    }

    public synchronized void stop() {
	    if (this._running) {
	        final Process p = this._currentProcess;
	        if (null != p) {
	            this._running = false;
	            p.destroyForcibly();
	        } else {
	            OUT.warn("current process is null");
	        }
	        this._runner.shutdownNow();
	    } else {
            OUT.warn("FFRelay not running.");
	    }
	}

    public String getName() {
        return this._name;
    }
    
    public String getLastOutputTime() {
        final Period period = new Period(System.currentTimeMillis() - _lastOutputTime);
        return PERIODFMT.print(period.normalizedStandard()) + " before";
    }
    
    public String getLastOutput() {
        return this._lastOutput;
    }
    
    private volatile long _lastOutputTime = 0;
    private volatile String _lastOutput;
    
    private final String _name;
	private final FFmpeg _ffmpeg;
	private final String _source;
	private final String _dest;
	private final ExecutorService _runner = 
	        Executors.newSingleThreadExecutor();
	
	private volatile boolean _running = false; 
	private volatile Process _currentProcess = null;
}
