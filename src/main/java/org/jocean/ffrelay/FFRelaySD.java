package org.jocean.ffrelay;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import javax.inject.Inject;
import javax.net.ssl.SSLException;

import org.jocean.http.Feature;
import org.jocean.http.rosa.SignalClient;
import org.jocean.idiom.ExceptionUtils;
import org.joda.time.Period;
import org.joda.time.format.PeriodFormatter;
import org.joda.time.format.PeriodFormatterBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import net.bramp.ffmpeg.FFmpeg;
import net.bramp.ffmpeg.FFmpegExecutor;
import net.bramp.ffmpeg.ProcessMonitor;
import net.bramp.ffmpeg.builder.FFmpegBuilder;
import net.bramp.ffmpeg.builder.FFmpegBuilder.Verbosity;
import net.bramp.ffmpeg.job.FFmpegJob;
import net.bramp.ffmpeg.progress.Progress;
import net.bramp.ffmpeg.progress.ProgressListener;

public class FFRelaySD implements Relay {
    private static SslContext initSSLCtx() {
        try {
            return SslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE).build();
        } catch (SSLException e) {
            return null;
        }
    }

    private static final SslContext SSLCTX = initSSLCtx();
    
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

	public FFRelaySD(final String name, final String sn, final String dest) {
	    this._name = name;
	    this._sn = sn;
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
            try {
                OUT.info("get info & play for sn:{}", this._sn);
                final GetInfoAndPlayV2.Resp resp = this._client.interaction()
                    .feature(new Feature.ENABLE_SSL(SSLCTX))
                    .feature(new SignalClient.UsingUri(_apiUri))
                    .feature(new SignalClient.UsingPath(_apiPath))
                    .feature(new SignalClient.DecodeResponseBodyAs(GetInfoAndPlayV2.Resp.class))
                    .request(new GetInfoAndPlayV2.Req(this._sn))
                    .<GetInfoAndPlayV2.Resp>build()
                    .timeout(10, TimeUnit.SECONDS)
                    .toBlocking()
                    .single();
                if ( 0 != resp.getErrorCode() 
                   || null == resp.getPlayInfo()
                   || null == resp.getPlayInfo().getRtmp()) {
                    OUT.warn("get info & play for sn({}) failed, resp: {}", this._sn, resp);
                    try {
                        // wait for 1s
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                    }
                    OUT.info("re-try get info & play...");
                    continue;
                }
                OUT.info("get info & play for sn({}) success, resp: {}", this._sn, resp);
                final String rtmp = resp.getPlayInfo().getRtmp();
                OUT.info("relay from {} --> to {}", rtmp, this._dest);
                final FFmpegBuilder builder =  new FFmpegBuilder()
                    .setVerbosity(Verbosity.INFO)
                    .setInput(rtmp)
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
                        _valid = true;
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
                _valid = false;
                _totalWorkMs += _currentWorkMs;
                _currentWorkMs = 0;
                _currentBeginTimestamp.set(0);
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
    
    public String getNonworkDuration() {
        final Period period = new Period(System.currentTimeMillis() - this._beginTimestamp - 
                (_totalWorkMs + _currentWorkMs));
        return PERIODFMT.print(period.normalizedStandard()) + " nonwork";
    }
    
    public String getLastOutputTime() {
        final Period period = new Period(System.currentTimeMillis() - _lastOutputTime);
        return PERIODFMT.print(period.normalizedStandard()) + " before";
    }
    
    public String getLastOutput() {
        return this._lastOutput;
    }
    
    public void setStatus(final Map<Object, String> status) {
        this._status = status;
    }
    
    public void setDestPullUri(final String uri) {
        this._destPullUri = uri;
    }
    
    @Override
    public String getDestPullUri() {
        return this._destPullUri;
    }
    
    @Override
    public boolean isValid() {
        return this._valid;
    }
    
    public void setApiPath(final String path) {
        this._apiPath = path;
    }
    
    @Inject
    private FFmpeg _ffmpeg;
    
    @Inject
    private SignalClient _client;
    
    @Inject
    private URI _apiUri;
    
    private String _apiPath;
    
    private volatile boolean _valid = false;
    private long _beginTimestamp;
    private volatile long _totalWorkMs = 0;
    private final AtomicLong _currentBeginTimestamp = new AtomicLong(0);
    private volatile long _currentWorkMs = 0;
    private volatile long _lastOutputTime = 0;
    private volatile String _lastOutput;
    
    private final String _name;
	private final String _sn;
	private final String _dest;
	private String _destPullUri;
	private final ExecutorService _runner = 
	        Executors.newSingleThreadExecutor();
	
	private Map<Object, String> _status;
	
	private volatile boolean _running = false; 
	private volatile Process _currentProcess = null;
}
