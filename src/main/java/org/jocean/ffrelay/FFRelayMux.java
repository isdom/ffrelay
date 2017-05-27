package org.jocean.ffrelay;

import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import javax.inject.Inject;

import org.jocean.idiom.BeanHolder;
import org.jocean.idiom.BeanHolderAware;
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

public class FFRelayMux implements BeanHolderAware {
//    private final static Logger LOG = 
//            LoggerFactory.getLogger(FFRelayMux.class);
    
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

	public FFRelayMux(final String name, final String sources, final String dest) {
	    this._name = name;
	    this._sources = sources.split(",");
	    this._dest = dest;
	    this.OUT = LoggerFactory.getLogger(name);
    }
	
	public synchronized void start() {
	    if (this._running) {
	        OUT.warn("ffrelay has already started");
	        return;
	    }
	    
	    this._beginTimestamp = System.currentTimeMillis();
	    this._running = true;
	    this._runner.submit(new Runnable() {
            @Override
            public void run() {
                doRelay();
            }});
	}
	
	private void doRelay() {
        while (this._running) {
            final String relayname = this._sources[this._currentSrcIdx];
            final Relay relay = this._beanHolder.getBean("relay" + relayname, Relay.class);
            if (null == relay) {
                OUT.warn("can not found relay named {}, and try next", relayname);
                stepSrcIdx();
                continue;
            }
            if (!relay.isValid()) {
                OUT.warn("relay named {} not valid, and try next", relayname);
                stepSrcIdx();
                continue;
            }
            OUT.info("relay from {} --> to {}", relay.getDestPullUri(), this._dest);
            try {
                final FFmpegBuilder builder =  new FFmpegBuilder()
                    .setVerbosity(Verbosity.INFO)
                    .setInput(relay.getDestPullUri())
                    .addOutput(this._dest)
                        .setFormat("flv")
                        .setAudioCodec("copy")
                        .setVideoCodec("copy")
                    .done();
                    
                final FFmpegExecutor executor = new FFmpegExecutor(this._ffmpeg);
                final FFmpegJob job = executor.createJob(builder, new ProgressListener() {
                    @Override
                    public void progress(final Progress progress) {
                        final long ts = System.currentTimeMillis();
                        _currentBeginTimestamp.compareAndSet(0, ts);
                        _currentWorkMs = ts - _currentBeginTimestamp.get();
                    }
                });
                
                job.run(new ProcessMonitor() {
                    @Override
                    public void setProcess(final Process p) {
                        _currentProcess = p;
                        OUT.info("current process has been set: {}", _currentProcess);
                        _timer.schedule(new TimerTask() {
                            @Override
                            public void run() {
                                p.destroyForcibly();
                            }}, 1000 * _switchInterval);
                    }

                    @Override
                    public void onOutput(final String line) {
                        _lastOutputTime = System.currentTimeMillis();
                        _lastOutput = line;
                        OUT.info(line);
                        if (null!=_status) {
                            _status.put(_name, line);
                        }
                        if (line.indexOf("invalid dropping") >= 0) {
                            OUT.warn("meet 'invalid dropping' output, so try re-start ffmpeg");
                            _currentProcess.destroyForcibly();
                            return;
                        }
                        if (line.indexOf("Non-monotonous DTS") >= 0) {
                            OUT.warn("meet 'Non-monotonous DTS' output, so try re-start ffmpeg");
                            _currentProcess.destroyForcibly();
                            return;
                        }
                        
                    }});
            } catch (Exception e) {
                OUT.warn("relay stopped bcs of {}", ExceptionUtils.exception2detail(e));
                if (this._running) {
                    OUT.info("restart relaying...");
                }
            } finally {
                _totalWorkMs += _currentWorkMs;
                _currentWorkMs = 0;
                _currentBeginTimestamp.set(0);
                stepSrcIdx();
                try {
                    // wait for 3s
                    Thread.sleep(1000 * 3);
                } catch (InterruptedException e) {
                }
            }
        }
    }

    private void stepSrcIdx() {
        this._currentSrcIdx++;
        if (this._currentSrcIdx >= this._sources.length) {
            this._currentSrcIdx = 0;
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
	        this._timer.cancel();
	    } else {
            OUT.warn("FFRelay not running.");
	    }
	}

    public String getName() {
        return this._name;
    }
    
    public String getNonworkDuration() {
        final Period period = new Period(System.currentTimeMillis() - this._beginTimestamp - 
                (_totalWorkMs + _currentWorkMs));
        return PERIODFMT.print(period.normalizedStandard()) + " nonwork";
//        return Long.toString(System.currentTimeMillis() - this._beginTimestamp) + "/"
//            + Long.toString(_totalWorkMs + _currentWorkMs);
        
    }
    
    public String getLastOutputTime() {
        final Period period = new Period(System.currentTimeMillis() - _lastOutputTime);
        return PERIODFMT.print(period.normalizedStandard()) + " before";
    }
    
    public String getLastOutput() {
        return this._lastOutput;
    }
    
    public void setInterval(final long interval) {
        this._switchInterval = interval;
    }
    
    public void setStatus(final Map<Object, String> status) {
        this._status = status;
    }
    
    @Override
    public void setBeanHolder(final BeanHolder beanHolder) {
        this._beanHolder = beanHolder;
    }
    
    @Inject
    private FFmpeg _ffmpeg;
    
    private BeanHolder _beanHolder;
    private volatile long _switchInterval = 30;
    private long _beginTimestamp;
    private volatile long _totalWorkMs = 0;
    private final AtomicLong _currentBeginTimestamp = new AtomicLong(0);
    private volatile long _currentWorkMs = 0;
    private volatile long _lastOutputTime = 0;
    private volatile String _lastOutput;
    
    private final String _name;
	private final String[] _sources;
	private final String _dest;
	private int _currentSrcIdx = 0;
	private final ExecutorService _runner = 
	        Executors.newSingleThreadExecutor();
	private final Timer _timer = new Timer();
	
	private Map<Object, String> _status;
	
	private volatile boolean _running = false; 
	private volatile Process _currentProcess = null;
}
