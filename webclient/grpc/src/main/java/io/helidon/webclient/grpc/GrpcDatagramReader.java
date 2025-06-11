package io.helidon.webclient.grpc;

import io.helidon.common.buffers.BufferData;

import java.nio.BufferOverflowException;

/**
 * An abstraction responsible for building complete GRPC datagrams out of individual data frames.
 * <p>
 * Each datagram has a prefix consisting of a compression flag
 * and a size of the datagram. Note that this class doesn't support compression currently.
 * However, due to the HTTP2 flow control, a single GRPC datagram may be split accross multiple
 * individual data frames, and the GRPC client must use the size of the datagram in order to reconstruct
 * the entire datagram so that its content can actually be parsed by a reply type marshaler.
 * This is exactly what this class is doing, along with some required buffering because
 * the last data frame may in fact contain a beginning of a new GRPC datagram which we'll
 * need to fully receive and process as well in the future.
 * <p>
 * Typically, the client would call the GrpcDatagramReader.add(BufferData) method as it receives data
 * from the network, and then call the GrpcDatagramReader.extractNextDatagram() to check if a complete
 * datagram is available.
 * <p>
 * This class is not thread-safe. The client is responsible for synchronizing access to its APIs.
 */
class GrpcDatagramReader {

    // These are arbitrary limits, but they seem sane and support the immediate use-case.
    // It would be nice to make them configurable. However, note that this is a Helidon-internal class.
    private static final int INITIAL_BUFFER_SIZE = 1024;
    private static final int MAX_BUFFER_SIZE = 10 * 1024 * 1024;

    /**
     * A buffer for incoming data. We copy the incoming BufferData objects into this buffer
     * on purpose because existing BufferData implementations aren't immutable. Further, the code
     * that creates the BufferData objects may reuse the underlying byte arrays now or in the future.
     * So we cannot just maintain a list of BufferData objects added to the reader, although that would be nice.
     * <p>
     * The io.helidon.common.buffers.GrowingBufferData is nice, too. However, it seems to create interim
     * byte arrays when adding data to the buffer. Also, it doesn't support a capacity limit which
     * may be important to prevent OOM. For this reason, we use a low-level circular byte array here.
     */
    private byte[] buffer = new byte[INITIAL_BUFFER_SIZE];

    /** Where we read data from. */
    private int readPosition = 0;

    /** Where we write new data to. */
    private int writePosition = 0;

    /** The length of the actual data added to the reader. Note that the buffer is circular. */
    private int length = 0;

    /**
     * Add a new piece of data to this reader. It may be a complete GRPC datagram, or a piece of it,
     * maybe even a piece containing a tail of one datagram and a head of another.
     * The client should call the extractNextDatagram() method to see if there's a complete datagram yet.
     * @param bufferData a piece of data to add to the reader
     */
    void add(final BufferData bufferData) {
        ensureCapacity(bufferData.available());

        // Avoid creating interim arrays. Fill up the tail of the buffer first...
        final int firstPartMaxLengthForWriting = buffer.length - writePosition;
        int read = bufferData.read(buffer, writePosition, Math.min(bufferData.available(), firstPartMaxLengthForWriting));

        // ...and flip to the head if necessary
        if (bufferData.available() > 0) {
            read += bufferData.read(buffer, 0, bufferData.available());
        }

        // Adjust the length and the writePosition
        length += read;
        writePosition += read;
        writePosition %= buffer.length;
    }

    /** Return the size of the next complete datagram, or -1 if it's incomplete yet. */
    private int getNextSize() {
        // Check if we have a complete datagram
        if (length < 5) {
            // We don't even have a complete GRPC header yet
            return -1;
        }

        // We only remove complete datagrams from the buffer, so the readPosition is guaranteed to point
        // to the beginning of a new datagram.

        // Read big endian (unsigned, but oh well...) int32 size from the GRPC header first
        // (ignoring the first byte which is a compression flag)
        final int size = buffer[(readPosition + 1) % buffer.length] << 24
                | (buffer[(readPosition + 2) % buffer.length] << 16)
                | (buffer[(readPosition + 3) % buffer.length] << 8)
                | (buffer[(readPosition + 4) % buffer.length]);

        if (length < 5 + size) {
            // We don't have enough data yet. More data needs to be added to the reader to complete this datagram.
            return -1;
        }

        return size;
    }

    /**
     * Read the next complete GRPC datagram and return a BufferData with its data payload,
     * or return null if the datagram is incomplete yet and more data needs to be added to this reader.
     * @return the GRPC datagram data payload, or null if not ready yet
     */
    BufferData extractNextDatagram() {
        final int size = getNextSize();
        if (size == -1) {
            return null;
        }

        // We have a complete datagram (and perhaps also a start of the next datagram) in the buffer.
        // Let's extract the complete one. Note that we only return the data bytes because higher level code
        // shouldn't be concerned with the details of the GRPC header.
        // So if we want to support compression in the future, it has to be implemented here somewhere.
        final BufferData data = BufferData.create(size);

        // Skip the header because we've already read the size, and we ignore the compression:
        readPosition += 5;
        readPosition %= buffer.length;

        final int firstPartMaxLengthForReading = buffer.length - readPosition;
        data.write(buffer, readPosition, Math.min(firstPartMaxLengthForReading, size));
        // and read the head of the buffer if needed:
        if (size > firstPartMaxLengthForReading) {
            data.write(buffer, 0, size - firstPartMaxLengthForReading);
        }

        // Advance the readPosition
        readPosition += size;
        readPosition %= buffer.length;
        // Adjust the length
        length -= 5 + size;

        return data;
    }

    /** Ensure there's enough capacity to write more data, or throw BufferOverflowException. */
    private void ensureCapacity(final int minCapacity) {
        final int currentCapacity = buffer.length - length;
        if (currentCapacity >= minCapacity) {
            return;
        }

        final int newLength = buffer.length + (minCapacity - currentCapacity);
        if (newLength > MAX_BUFFER_SIZE) {
            throw new BufferOverflowException();
        }

        // Prefer to double the size each time. But resort to the newLength if it's greater, and respect the max limit.
        final int actualNewLength = Math.min(Math.max(buffer.length * 2, newLength), MAX_BUFFER_SIZE);
        final byte[] newBuffer = new byte[actualNewLength];

        if (length > 0) {
            final int firstPartMaxLength = buffer.length - readPosition;
            System.arraycopy(buffer, readPosition, newBuffer, 0, Math.min(length, firstPartMaxLength));
            if (firstPartMaxLength < length) {
                // The data has the second part in the head of our circular buffer:
                System.arraycopy(buffer, 0, newBuffer, firstPartMaxLength, length - firstPartMaxLength);
            }
        }

        buffer = newBuffer;
        readPosition = 0;
        writePosition = length;
    }
}
