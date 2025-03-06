package org.unsynchronized;

import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * <p>
 * An InputStream designed to record data passing through the stream after a call to
 * record() is made.  After record() is called, the results from every read will
 * be stored in an * internal buffer.  The contents of the buffer can be
 * retrieved by getRecordedData(); to stop recording and clear the internal
 * buffer, call stopRecording().
 * </p>
 *
 * <p>
 * <b>Note</b>: calls to mark() and reset() are merely passed through to the inner stream; if
 * recording is active, the buffer won't be backtracked by reset().
 * </p>
 */
public class LoggerInputStream extends InputStream {

    private final InputStream origin;

    private ByteArrayOutputStream out = null;
    private boolean recording = false;

    public LoggerInputStream(InputStream origin) {
        this.origin = origin;
    }

    @Override
    public synchronized int read() throws IOException {
        int i = origin.read();
        if (recording && i != -1) {
            out.write((byte) i);
        }
        return i;
    }

    @Override
    public synchronized int read(byte @NotNull [] b) throws IOException {
        return this.read(b, 0, b.length);
    }

    @Override
    public synchronized int read(byte @NotNull [] b, int off, int len) throws IOException {
        int retVal = origin.read(b, off, len);
        if (recording && retVal > 0) {
            if (retVal > len) {
                throw new IOException("inner stream read(byte[], int, int) violating contract; return value > len: " + retVal);
            }
            out.write(b, off, retVal);
        }
        return retVal;
    }

    @Override
    public synchronized long skip(long n) throws IOException {
        if (n < 0) {
            throw new IOException("can't skip negative number of bytes");
        }
        if (!recording) {
            return origin.skip(n);
        }
        long nSkipped = 0;
        while (n > Integer.MAX_VALUE) {
            long ret = skip(Integer.MAX_VALUE);
            nSkipped += ret;
            if (ret < Integer.MAX_VALUE) {
                return nSkipped;
            }
            n -= ret;
        }
        int toRead = (int) n;
        int actuallyRead = 0;
        byte[] buf = new byte[10240];
        while (toRead > 0) {
            int r = Math.min(toRead, buf.length);
            int rRet = this.read(buf, 0, r);
            actuallyRead += rRet;
            toRead -= rRet;
            if (rRet < r) {
                break;
            }
        }
        return actuallyRead;
    }

    @Override
    public synchronized int available() throws IOException {
        return origin.available();
    }

    @Override
    public synchronized void close() throws IOException {
        origin.close();
    }

    @Override
    public synchronized void mark(int readLimit) {
        origin.mark(readLimit);
    }

    @Override
    public synchronized void reset() throws IOException {
        origin.reset();
    }

    @Override
    public boolean markSupported() {
        return origin.markSupported();
    }

    /**
     * If not currently recording, start recording.  If the stream is currently recording,
     * the current buffer is cleared.
     */
    public synchronized void startRecording() {
        recording = true;
        out = new ByteArrayOutputStream();
    }

    /**
     * Stops recording and clears the internal buffer.  If recording is not active, an
     * IOException is thrown.
     *
     * @throws IOException if recording is not currently active
     */
    public synchronized void stopRecording() throws IOException {
        if (!recording) {
            throw new IOException("recording not active");
        }
        try {
            out.close();
        } catch (IOException ignore) {
            //
        }
        out = null;
        recording = false;
    }

    /**
     * Returns the data recorded so far; if recording is not active, an empty buffer
     * is returned.
     */
    public synchronized byte[] getRecordedData() {
        if (!recording) {
            return new byte[0];
        }
        return out.toByteArray();
    }
}
