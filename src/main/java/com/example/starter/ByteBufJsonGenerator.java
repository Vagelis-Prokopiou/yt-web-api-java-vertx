package com.example.starter;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.CharBuffer;
import java.util.Arrays;

import com.fasterxml.jackson.core.Base64Variant;
import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.core.io.ContentReference;
import com.fasterxml.jackson.core.io.IOContext;
import com.fasterxml.jackson.core.json.JsonGeneratorImpl;
import com.fasterxml.jackson.core.json.JsonWriteContext;

import io.netty.buffer.ByteBuf;
import io.netty.util.CharsetUtil;

public final class ByteBufJsonGenerator extends JsonGeneratorImpl {

	static final int NULL = Bits.getInt(new byte[]{ 'n', 'u', 'l', 'l' }, 0);

	static final int TRUE = Bits.getInt(new byte[]{ 't', 'r', 'u', 'e' }, 0);

	static final int FALSE_P = Bits.getInt(new byte[]{ 'f', 'a', 'l', 's' }, 0);

	static final byte QUOTE = '"';

	private static final byte BYTE_COMMA = (byte) ',';

	private static final byte BYTE_COLON = (byte) ':';

	private static final byte BYTE_LCURLY = (byte) '{';

	private static final byte BYTE_RCURLY = (byte) '}';

	private static final byte BYTE_LBRACKET = (byte) '[';

	private static final byte BYTE_RBRACKET = (byte) ']';

	public static ByteBufJsonGenerator allocate(ByteBuf cached, int features) {
		var ctxt = new IOContext(null, ContentReference.unknown(), false);
		ctxt.setEncoding(JsonEncoding.UTF8);

		return new ByteBufJsonGenerator(ctxt, features, null, cached);
	}

	final ByteBuf buf;

	public ByteBufJsonGenerator(IOContext ctxt, int features, ObjectCodec codec, ByteBuf buf) {
		super(ctxt, features, codec);
		this.buf = buf;
	}

	@SuppressWarnings({ "all", "java:S1186" })
	@Override
	protected void _releaseBuffers() {

	}

	@SuppressWarnings({ "all", "java:S4524" })
	@Override
	protected final void _verifyValueWrite(String typeMsg) throws IOException {
		final int status = _writeContext.writeValue();
		if (_cfgPrettyPrinter != null) {
			_verifyPrettyValueWrite(typeMsg, status);
			return;
		}
		byte b;
		switch (status) {
		case JsonWriteContext.STATUS_OK_AS_IS:
		default:
			return;
		case JsonWriteContext.STATUS_OK_AFTER_COMMA:
			b = BYTE_COMMA;
			break;
		case JsonWriteContext.STATUS_OK_AFTER_COLON:
			b = BYTE_COLON;
			break;
		case JsonWriteContext.STATUS_OK_AFTER_SPACE: // root-value separator
			if (_rootValueSeparator != null) {
				byte[] raw = _rootValueSeparator.asUnquotedUTF8();
				if (raw.length > 0) {
					writeBytes(raw);
				}
			}
			return;
		case JsonWriteContext.STATUS_EXPECT_NAME:
			_reportCantWriteValueExpectName(typeMsg);
			return;
		}

		buf.writeByte(b);
	}

	private void doWriteBynary(Base64Variant bv, byte[] data, int offset, int len) {
		if (offset > 0 || len != data.length) {
			data = Arrays.copyOfRange(data, offset, len);
		}
		writeRaw(bv.encode(data, false));
	}

	@Override
	public void flush() {
		// NOOP
	}

	private final int outputRawMultiByteChar(int ch, char[] cbuf, int inputOffset, int inputEnd)
			throws IOException {
		// Let's handle surrogates gracefully (as 4 byte output):
		if (ch >= SURR1_FIRST && ch <= SURR2_LAST) {
			// Do we have second part?
			if (inputOffset >= inputEnd || cbuf == null) { // nope... have to note down
				_reportError(String.format(
						"Split surrogate on writeRaw() input (last character): first character 0x%4x", ch));
			} else {
				outputSurrogates(ch, cbuf[inputOffset]);
			}
			return inputOffset + 1;
		}
		var bbuf = this.buf;
		bbuf.writeByte((byte) (0xe0 | (ch >> 12)));
		bbuf.writeByte((byte) (0x80 | ((ch >> 6) & 0x3f)));
		bbuf.writeByte((byte) (0x80 | (ch & 0x3f)));
		return inputOffset;
	}

	protected final void outputSurrogates(int surr1, int surr2) throws IOException {
		int c = _decodeSurrogate(surr1, surr2);

		var bb = this.buf;
		bb.writeByte((byte) (0xf0 | (c >> 18)));
		bb.writeByte((byte) (0x80 | ((c >> 12) & 0x3f)));
		bb.writeByte((byte) (0x80 | ((c >> 6) & 0x3f)));
		bb.writeByte((byte) (0x80 | (c & 0x3f)));
	}

	@Override
	public void writeBinary(Base64Variant bv, byte[] data, int offset, int len) throws IOException {
		_verifyValueWrite(WRITE_BINARY);
		buf.writeByte(QUOTE);
		doWriteBynary(bv, data, offset, offset + len);
		buf.writeByte(QUOTE);
	}

	@Override
	public void writeBoolean(boolean state) throws IOException {
		_verifyValueWrite(WRITE_BOOLEAN);
		if (state) {
			buf.writeInt(TRUE);
		} else {
			buf.writeInt(FALSE_P);
			buf.writeByte('e');
		}

	}

	private void writeBytes(byte[] raw) {
		buf.writeBytes(raw);
	}

	@Override
	public void writeEndArray() throws IOException {
		if (!_writeContext.inArray()) {
			_reportError("Current context not Array but " + _writeContext.typeDesc());
		}
		if (_cfgPrettyPrinter != null) {
			_cfgPrettyPrinter.writeEndArray(this, _writeContext.getEntryCount());
		} else {
			buf.writeByte(BYTE_RBRACKET);
		}
		_writeContext = _writeContext.clearAndGetParent();
	}

	@Override
	public void writeEndObject() throws IOException {
		if (!_writeContext.inObject()) {
			_reportError("Current context not Object but " + _writeContext.typeDesc());
		}
		if (_cfgPrettyPrinter != null) {
			_cfgPrettyPrinter.writeEndObject(this, _writeContext.getEntryCount());
		} else {
			buf.writeByte(BYTE_RCURLY);
		}
		_writeContext = _writeContext.clearAndGetParent();
	}

	@Override
	public void writeFieldName(String name) throws IOException {
		if (_cfgPrettyPrinter != null) {
			writePPFieldName(name);
			return;
		}
		final int status = _writeContext.writeFieldName(name);
		if (status == JsonWriteContext.STATUS_EXPECT_VALUE) {
			_reportError("Can not write a field name, expecting a value");
		}
		if (status == JsonWriteContext.STATUS_OK_AFTER_COMMA) { // need comma
			buf.writeByte(BYTE_COMMA);
		}
		/*
		 * To support [JACKSON-46], we'll do this: (Question: should quoting of spaces (etc) still
		 * be enabled?)
		 */
		if (_cfgUnqNames) {
			writeRaw(name);
			return;
		}
		writeQuotedRaw(name);
	}

	@Override
	public void writeNull() {
		buf.writeInt(NULL);
	}

	@Override
	public void writeNumber(BigDecimal value) throws IOException {
		_verifyValueWrite(WRITE_NUMBER);
		if (value == null) {
			writeNull();
		} else if (_cfgNumbersAsStrings) {
			writeQuotedRaw(_asString(value));
		} else {
			writeRaw(_asString(value));
		}
	}

	@Override
	public void writeNumber(BigInteger value) throws IOException {
		_verifyValueWrite(WRITE_NUMBER);
		if (value == null) {
			writeNull();
		} else if (_cfgNumbersAsStrings) {
			writeQuotedRaw(value.toString());
		} else {
			writeRaw(value.toString());
		}

	}

	@SuppressWarnings("deprecation")
	@Override
	public void writeNumber(double d) throws IOException {
		if (_cfgNumbersAsStrings ||
				(Bits.notFinite(d)
						&& Feature.QUOTE_NON_NUMERIC_NUMBERS.enabledIn(_features))) {
			writeString(String.valueOf(d));
			return;
		}
		// What is the max length for doubles? 40 chars?
		_verifyValueWrite(WRITE_NUMBER);
		writeRaw(String.valueOf(d));
	}

	@SuppressWarnings("deprecation")
	@Override
	public void writeNumber(float f) throws IOException {
		if (_cfgNumbersAsStrings ||
				(Bits.notFinite(f)
						&& Feature.QUOTE_NON_NUMERIC_NUMBERS.enabledIn(_features))) {
			writeString(String.valueOf(f));
			return;
		}
		// What is the max length for floats?
		_verifyValueWrite(WRITE_NUMBER);
		writeRaw(String.valueOf(f));
	}

	@Override
	public void writeNumber(int v) throws IOException {
		_verifyValueWrite(WRITE_NUMBER);

		if (_cfgNumbersAsStrings) {
			buf.writeByte(QUOTE);
		}

		Bits.outputInt(v, buf);

		if (_cfgNumbersAsStrings) {
			buf.writeByte(QUOTE);
		}

	}

	@Override
	public void writeNumber(long v) throws IOException {
		_verifyValueWrite(WRITE_NUMBER);

		if (_cfgNumbersAsStrings) {
			buf.writeByte(QUOTE);
		}

		Bits.outputLong(v, buf);

		if (_cfgNumbersAsStrings) {
			buf.writeByte(QUOTE);
		}
	}

	@Override
	public void writeNumber(short s) throws IOException {
		_verifyValueWrite(WRITE_NUMBER);

		if (_cfgNumbersAsStrings) {
			buf.writeByte(QUOTE);
		}

		Bits.outputInt(s, buf);

		if (_cfgNumbersAsStrings) {
			buf.writeByte(QUOTE);
		}
	}

	@Override
	public void writeNumber(String encodedValue) throws IOException {
		_verifyValueWrite(WRITE_NUMBER);
		if (encodedValue == null) {
			writeNull();
		} else if (_cfgNumbersAsStrings) {
			writeQuotedRaw(encodedValue);
		} else {
			writeRaw(encodedValue);
		}
	}

	private void writePPFieldName(String name) throws IOException {
		int status = _writeContext.writeFieldName(name);
		if (status == JsonWriteContext.STATUS_EXPECT_VALUE) {
			_reportError("Can not write a field name, expecting a value");
		}
		if ((status == JsonWriteContext.STATUS_OK_AFTER_COMMA)) {
			_cfgPrettyPrinter.writeObjectEntrySeparator(this);
		} else {
			_cfgPrettyPrinter.beforeObjectEntries(this);
		}
		if (_cfgUnqNames) {
			writeRaw(name);
			return;
		}
		writeQuotedRaw(name);
	}

	private final void writeQuotedRaw(String value) {
		buf.writeByte(QUOTE);
		writeRaw(value);
		buf.writeByte(QUOTE);
	}

	@Override
	public void writeRaw(char ch) throws IOException {
		if (ch <= 0x7F) {
			buf.writeByte((byte) ch);
		} else if (ch < 0x800) { // 2-byte?
			buf.writeByte((byte) (0xc0 | (ch >> 6)));
			buf.writeByte((byte) (0x80 | (ch & 0x3f)));
		} else {
			/* offset = */ outputRawMultiByteChar(ch, null, 0, 0);
		}

	}

	@Override
	public void writeRaw(char[] text, int offset, int len) throws IOException {
		buf.writeCharSequence(CharBuffer.wrap(text, offset, len), CharsetUtil.UTF_8);
	}

	@Override
	public void writeRaw(String text) {
		if (!text.isEmpty()) {
			var ascii = Bits.unwrapIfAscii(text);
			if (ascii == null) {
				buf.writeCharSequence(text, CharsetUtil.UTF_8);
			} else {
				buf.writeBytes(ascii);
			}
		}
	}

	@Override
	public void writeRaw(String text, int offset, int len) throws IOException {
		buf.writeCharSequence(CharBuffer.wrap(text, offset, len), CharsetUtil.UTF_8);
	}

	@Override
	public void writeRawUTF8String(byte[] buffer, int offset, int len) throws IOException {
		buf.writeBytes(buffer, offset, len);
	}

	@Override
	public void writeStartArray() throws IOException {
		_verifyValueWrite("start an array");
		_writeContext = _writeContext.createChildArrayContext();
		if (_cfgPrettyPrinter != null) {
			_cfgPrettyPrinter.writeStartArray(this);
		} else {
			buf.writeByte(BYTE_LBRACKET);
		}
	}

	@Override
	public void writeStartObject() throws IOException {
		_verifyValueWrite("start an object");
		_writeContext = _writeContext.createChildObjectContext();
		if (_cfgPrettyPrinter != null) {
			_cfgPrettyPrinter.writeStartObject(this);
		} else {
			buf.writeByte(BYTE_LCURLY);
		}
	}

	@Override
	public void writeString(char[] buffer, int offset, int len) throws IOException {
		_verifyValueWrite(WRITE_STRING);
		buf.writeCharSequence(CharBuffer.wrap(buffer, offset, len), CharsetUtil.UTF_8);
	}

	@Override
	public void writeString(String text) throws IOException {
		_verifyValueWrite(WRITE_STRING);
		if (text == null) {
			writeNull();
			return;
		}

		buf.writeByte(QUOTE);
		writeRaw(text);
		buf.writeByte(QUOTE);
	}

	@Override
	public void writeUTF8String(byte[] buffer, int offset, int len) throws IOException {
		_verifyValueWrite(WRITE_STRING);
		buf.writeByte(QUOTE);
		buf.writeBytes(buffer, offset, len);
		buf.writeByte(QUOTE);
	}

}