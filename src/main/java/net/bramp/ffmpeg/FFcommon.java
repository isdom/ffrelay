package net.bramp.ffmpeg;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.io.CharStreams;
import net.bramp.ffmpeg.io.ProcessUtils;
import net.bramp.ffmpeg.progress.Progress;

import javax.annotation.Nonnull;

import org.jocean.ffrelay.FFRelay;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.google.common.base.Preconditions.checkNotNull;

/** Private class to contain common methods for both FFmpeg and FFprobe. */
abstract class FFcommon {
    private static final Logger LOG = LoggerFactory
            .getLogger(FFcommon.class);

  /** Path to the binary (e.g. /usr/bin/ffmpeg) */
  final String path;

  /** Function to run FFmpeg. We define it like this so we can swap it out (during testing) */
  final ProcessFunction runFunc;

  /** Version string */
  String version = null;

  public FFcommon(@Nonnull String path) {
    this(path, new RunProcessFunction());
  }

  protected FFcommon(@Nonnull String path, @Nonnull ProcessFunction runFunction) {
    Preconditions.checkArgument(!Strings.isNullOrEmpty(path));
    this.runFunc = checkNotNull(runFunction);
    this.path = path;
  }

  protected BufferedReader wrapInReader(Process p) {
    return new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));
  }

  protected void throwOnError(Process p) throws IOException {
    try {
      // TODO In java 8 use waitFor(long timeout, TimeUnit unit)
      if (ProcessUtils.waitForWithTimeout(p, 1, TimeUnit.SECONDS) != 0) {
        // TODO Parse the error
        throw new IOException(path + " returned non-zero exit status. Check stdout.");
      }
    } catch (TimeoutException e) {
      throw new IOException("Timed out waiting for " + path + " to finish.");
    }
  }

  /**
   * Returns the version string for this binary.
   *
   * @return the version string.
   * @throws IOException If there is an error capturing output from the binary.
   */
  public synchronized @Nonnull String version() throws IOException {
    if (this.version == null) {
      Process p = runFunc.run(ImmutableList.of(path, "-version"));
      try {
        BufferedReader r = wrapInReader(p);
        this.version = r.readLine();
        CharStreams.copy(r, CharStreams.nullWriter()); // Throw away rest of the output

        throwOnError(p);
      } finally {
        p.destroy();
      }
    }
    return version;
  }

  public String getPath() {
    return path;
  }

  /**
   * Returns the full path to the binary with arguments appended.
   *
   * @param args The arguments to pass to the binary.
   * @return The full path and arguments to execute the binary.
   * @throws IOException If there is an error capturing output from the binary
   */
  public List<String> path(List<String> args) throws IOException {
    return ImmutableList.<String>builder().add(path).addAll(args).build();
  }

  /**
   * Runs the binary (ffmpeg) with the supplied args. Blocking until finished.
   *
   * @param args The arguments to pass to the binary.
   * @throws IOException If there is a problem executing the binary.
   */
  public void run(List<String> args) throws IOException {
    checkNotNull(args);

    final Process p = runFunc.run(path(args));
    try {
      // TODO Move the copy onto a thread, so that FFmpegProgressListener can be on this thread.

      // Now block reading ffmpeg's stdout. We are effectively throwing away the output.
      // CharStreams.copy(wrapInReader(p), System.out); // TODO Should I be outputting to stdout?

      final BufferedReader in = wrapInReader(p);
      String line;
      while ((line = in.readLine()) != null) {
//          LOG.info("{}", line);
      }

      throwOnError(p);

    } finally {
      p.destroy();
    }
  }
  
  /**
   * Runs the binary (ffmpeg) with the supplied args. Blocking until finished.
   *
   * @param args The arguments to pass to the binary.
   * @throws IOException If there is a problem executing the binary.
   */
  public void run(final List<String> args, final ProcessMonitor monitor) throws IOException {
    checkNotNull(args);

    final Process p = runFunc.run(path(args));
    
    try {
        monitor.setProcess(p);
    } catch (Exception e) {
    }
    
    try {
      // TODO Move the copy onto a thread, so that FFmpegProgressListener can be on this thread.

      // Now block reading ffmpeg's stdout. We are effectively throwing away the output.
      // CharStreams.copy(wrapInReader(p), System.out); // TODO Should I be outputting to stdout?

      final BufferedReader in = wrapInReader(p);
      
      String line;
      while ((line = in.readLine()) != null) {
          try {
              monitor.onOutput(line);
          } catch (Exception e) {
          }
      }
      
      throwOnError(p);

    } finally {
      p.destroy();
    }
  }
}
