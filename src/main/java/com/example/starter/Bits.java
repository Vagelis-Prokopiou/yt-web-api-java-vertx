package com.example.starter;

import java.util.Objects;

import io.netty.buffer.ByteBuf;
import jdk.internal.misc.Unsafe;

public final class Bits {

	static final Unsafe U;

	static final long C_OFF;
	static final long V_OFF;
	private static final int MILLION = 1000000;

	private static final int BILLION = 1000000000;
	private static final long BILLION_L = 1000000000L;
	private static final long MIN_INT_AS_LONG = Integer.MIN_VALUE;
	private static final long MAX_INT_AS_LONG = Integer.MAX_VALUE;
	private static final byte[] SMALLEST_INT = String.valueOf(Integer.MIN_VALUE).getBytes();
	private static final byte[] SMALLEST_LONG = String.valueOf(Long.MIN_VALUE).getBytes();
	private static final int[] TRIPLET_TO_CHARS = new int[1000];
	static {
		try {
			var f = Unsafe.class.getDeclaredField("theUnsafe");
			f.setAccessible(true);
			U = (Unsafe) f.get(null);
			C_OFF = U.objectFieldOffset(String.class, "coder");
			V_OFF = U.objectFieldOffset(String.class, "value");
		} catch (Throwable e) {
			throw new ExceptionInInitializerError(e);
		}
	}

	static {
		int fullIx = 0;
		for (int i1 = 0; i1 < 10; ++i1) {
			for (int i2 = 0; i2 < 10; ++i2) {
				for (int i3 = 0; i3 < 10; ++i3) {
					int enc = ((i1 + '0') << 16)
							| ((i2 + '0') << 8)
							| (i3 + '0');
					TRIPLET_TO_CHARS[fullIx++] = enc;
				}
			}
		}
	}

	private static byte coder(String s) {
		return U.getByte(s, C_OFF);
	}

	private static void full3(int t, ByteBuf b) {
		int enc = TRIPLET_TO_CHARS[t];
		b.writeByte((byte) (enc >> 16));
		b.writeByte((byte) (enc >> 8));
		b.writeByte((byte) enc);
	}

	public static int getInt(byte[] b, int off) {
		return (b[off + 3] & 0xFF) +
				((b[off + 2] & 0xFF) << 8) +
				((b[off + 1] & 0xFF) << 16) +
				((b[off]) << 24);
	}

	private static void leading3(int t, ByteBuf b) {
		int enc = TRIPLET_TO_CHARS[t];
		if (t > 9) {
			if (t > 99) {
				b.writeByte((byte) (enc >> 16));
			}
			b.writeByte((byte) (enc >> 8));
		}
		b.writeByte((byte) enc);
	}

	public static boolean notFinite(double value) {
		return Double.isNaN(value) || Double.isInfinite(value);
	}

	public static boolean notFinite(float value) {
		// before Java 8 need separate checks
		return Float.isNaN(value) || Float.isInfinite(value);
	}

	private static void outputFullBillion(int v, ByteBuf b) {
		int thousands = v / 1000;
		int ones = (v - (thousands * 1000)); // == value % 1000
		int millions = thousands / 1000;
		thousands -= (millions * 1000);

		int enc = TRIPLET_TO_CHARS[millions];
		b.writeByte((byte) (enc >> 16));
		b.writeByte((byte) (enc >> 8));
		b.writeByte((byte) enc);

		enc = TRIPLET_TO_CHARS[thousands];
		b.writeByte((byte) (enc >> 16));
		b.writeByte((byte) (enc >> 8));
		b.writeByte((byte) enc);

		enc = TRIPLET_TO_CHARS[ones];
		b.writeByte((byte) (enc >> 16));
		b.writeByte((byte) (enc >> 8));
		b.writeByte((byte) enc);
	}

	public static void outputInt(int v, ByteBuf b) {
		if (v < 0) {
			if (v == Integer.MIN_VALUE) {
				outputSmallestI(b);
				return;
			}
			b.writeByte('-');
			v = -v;
		}

		if (v < MILLION) { // at most 2 triplets...
			if (v < 1000) {
				if (v < 10) {
					b.writeByte((byte) ('0' + v));
					return;
				}
				leading3(v, b);
				return;
			}
			int thousands = v / 1000;
			v -= (thousands * 1000); // == value % 1000
			leading3(thousands, b);
			full3(v, b);
			return;
		}

		// ok, all 3 triplets included
		/*
		 * Let's first hand possible billions separately before handling 3 triplets. This is
		 * possible since we know we can have at most '2' as billion count.
		 */
		if (v >= BILLION) {
			v -= BILLION;
			if (v >= BILLION) {
				v -= BILLION;
				b.writeByte('2');
			} else {
				b.writeByte('1');
			}
			outputFullBillion(v, b);
			return;
		}
		int newValue = v / 1000;
		int ones = (v - (newValue * 1000)); // == value % 1000
		v = newValue;
		newValue /= 1000;
		int thousands = (v - (newValue * 1000));

		leading3(newValue, b);
		full3(thousands, b);
		full3(ones, b);
	}

	public static void outputLong(long v, ByteBuf b) {
		if (v < 0L) {
			if (v > MIN_INT_AS_LONG) {
				outputInt((int) v, b);
				return;
			}
			if (v == Long.MIN_VALUE) {
				outputSmallestL(b);
				return;
			}
			b.writeByte('-');
			v = -v;
		} else {
			if (v <= MAX_INT_AS_LONG) {
				outputInt((int) v, b);
				return;
			}
		}

		// Ok, let's separate last 9 digits (3 x full sets of 3)
		long upper = v / BILLION_L;
		v -= (upper * BILLION_L);

		// two integers?
		if (upper < BILLION_L) {
			outputUptoBillion((int) upper, b);
			return;
		} else {
			// no, two ints and bits; hi may be about 16 or so
			long hi = upper / BILLION_L;
			upper -= (hi * BILLION_L);
			leading3((int) hi, b);
			outputFullBillion((int) upper, b);
		}
		outputFullBillion((int) v, b);
	}

	private static void outputSmallestI(ByteBuf b) {
		b.writeBytes(SMALLEST_INT);
	}

	private static void outputSmallestL(ByteBuf b) {
		b.writeBytes(SMALLEST_LONG);
	}

	private static void outputUptoBillion(int v, ByteBuf b) {
		if (v < MILLION) { // at most 2 triplets...
			if (v < 1000) {
				leading3(v, b);
				return;
			}
			int thousands = v / 1000;
			int ones = v - (thousands * 1000); // == value % 1000
			outputUptoMillion(b, thousands, ones);
			return;
		}
		int thousands = v / 1000;
		int ones = (v - (thousands * 1000)); // == value % 1000
		int millions = thousands / 1000;
		thousands -= (millions * 1000);

		leading3(millions, b);

		int enc = TRIPLET_TO_CHARS[thousands];
		b.writeByte((byte) (enc >> 16));
		b.writeByte((byte) (enc >> 8));
		b.writeByte((byte) enc);

		enc = TRIPLET_TO_CHARS[ones];
		b.writeByte((byte) (enc >> 16));
		b.writeByte((byte) (enc >> 8));
		b.writeByte((byte) enc);
	}

	private static void outputUptoMillion(ByteBuf b, int thousands, int ones) {
		int enc = TRIPLET_TO_CHARS[thousands];
		if (thousands > 9) {
			if (thousands > 99) {
				b.writeByte((byte) (enc >> 16));
			}
			b.writeByte((byte) (enc >> 8));
		}
		b.writeByte((byte) enc);
		// and then full
		enc = TRIPLET_TO_CHARS[ones];
		b.writeByte((byte) (enc >> 16));
		b.writeByte((byte) (enc >> 8));
		b.writeByte((byte) enc);
	}

	public static byte[] unwrap(String s) {
		return (byte[]) U.getReference(s, V_OFF);
	}

	public static byte[] unwrapIfAscii(String s) {
		if (coder(Objects.requireNonNull(s)) == 0) {
			return unwrap(s);
		}
		return null;
	}

	private Bits() {
	}
}
