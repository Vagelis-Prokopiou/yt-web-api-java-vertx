package com.example.starter;

import java.io.IOException;
import java.io.OutputStream;

import io.vertx.core.buffer.impl.BufferImpl;

public final class DataOut extends OutputStream {

	final BufferImpl buf;

	public DataOut(BufferImpl buf) {
		this.buf = buf;
	}

	public DataOut reset() {
		buf.byteBuf().clear();
		return this;
	}

	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		buf.appendBytes(b, off, len);
	}

	@Override
	public void write(int b) throws IOException {
		buf.appendByte((byte) (b & 0xFF));
	}

}
