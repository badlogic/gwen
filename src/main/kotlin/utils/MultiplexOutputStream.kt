package utils

import java.io.OutputStream

class MultiplexOutputStream : OutputStream {
	private val streams: Array<out OutputStream>;

	constructor (vararg streams: OutputStream) {
		this.streams = streams;
	}

	override fun write(b: Int) {
		for (stream in streams) {
			synchronized(stream) {
				stream.write(b);
			}
		}
	}

	override fun write(b: ByteArray?) {
		for (stream in streams) {
			synchronized(stream) {
				stream.write(b);
			}
		}
	}

	override fun write(b: ByteArray?, off: Int, len: Int) {
		for (stream in streams) {
			synchronized(stream) {
				stream.write(b, off, len);
			}
		}
	}
}
