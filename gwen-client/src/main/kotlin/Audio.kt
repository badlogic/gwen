package com.badlogicgames.gwen

import com.esotericsoftware.minlog.Log
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.sound.sampled.*

interface AudioRecorder : Closeable {
	fun getSamplingRate(): Int;
	fun read();
	fun getByteData(): ByteArray;
	fun getShortData(): ShortArray;
}

class LocalAudioRecorder : AudioRecorder {
	private val line: TargetDataLine;
	private val buffer: ByteArray;
	private val byteData: ByteArray;
	private val shortData: ShortArray;
	private val _samplingRate: Int;
	private val _stereo: Boolean;

	constructor(samplingRate: Int, samplesPerChunk: Int, stereo: Boolean) {
		this._samplingRate = samplingRate;
		this._stereo = stereo;
		val format = AudioFormat(samplingRate.toFloat(), 16, if (stereo) 2 else 1, true, false);
		val targetInfo = DataLine.Info(TargetDataLine::class.java, format);
		line = AudioSystem.getLine(targetInfo) as TargetDataLine;
		line.open(format);
		line.start();

		buffer = ByteArray(samplesPerChunk * (if (stereo) 4 else 2));
		byteData = ByteArray(samplesPerChunk * 2);
		shortData = ShortArray(samplesPerChunk);
		Log.info("Started local audio recorder: " + line.getFormat().toString());
	}

	override fun getSamplingRate(): Int {
		return _samplingRate;
	}

	override fun read() {
		var numRead: Int = 0;
		while (numRead != buffer.size) {
			val result = line.read(buffer, numRead, buffer.size - numRead);
			if (result == -1) throw Exception("Error reading from microphone");
			numRead += result;
		}
		if (_stereo) {
			var byteIndex = 0;
			var shortIndex = 0;
			for (i in 0..buffer.size - 1 step 4) {
				var left1 = buffer[i].toInt().and(0xFF);
				var left2 = buffer[i + 1].toInt().and(0xFF);
				var right1 = buffer[i + 2].toInt().and(0xFF);
				var right2 = buffer[i + 3].toInt().and(0xFF);

				var left = (left1 or (left2 shl 8)).toShort();
				var right = (right1 or (right2 shl 8)).toShort();
				var mono = (left + right) / 2;

				byteData[byteIndex] = mono.toByte();
				byteData[byteIndex + 1] = (mono ushr 8).toByte();
				byteIndex += 2;

				shortData[shortIndex++] = mono.toShort();
			}
		} else {
			var shortIndex = 0;
			for (i in 0..buffer.size - 1 step 2) {
				var mono1 = buffer[i].toInt().and(0xFF);
				var mono2 = buffer[i + 1].toInt().and(0xFF);
				shortData[shortIndex++] = (mono1 or (mono2 shl 8)).toShort();
			}
			System.arraycopy(buffer, 0, byteData, 0, byteData.size);
		}
	}

	override fun getByteData(): ByteArray {
		return byteData;
	}

	override fun getShortData(): ShortArray {
		return shortData;
	}

	override fun close() {
		line.close();
	}
}

interface AudioPlayer : Closeable {
	fun play(audio: ByteArray, offset: Int, length: Int);
	fun getSamplingRate(): Int;
}

class LocalAudioPlayer : AudioPlayer {
	private val line: SourceDataLine;
	private val _samplingRate: Int;

	constructor(samplingRate: Int) {
		this._samplingRate = samplingRate;
		val format = AudioFormat(samplingRate.toFloat(), 16, 1, true, false);
		val lineInfo = DataLine.Info(SourceDataLine::class.java, format);
		this.line = AudioSystem.getLine(lineInfo) as SourceDataLine;
		this.line.open(format);
		this.line.start();
		Log.info("Started local audio player, ${samplingRate}Hz");
	}

	override fun getSamplingRate(): Int {
		return _samplingRate;
	}

	override fun play(audio: ByteArray, offset: Int, length: Int) {
		var numWritten = 0;
		while (numWritten < length) {
			val result = line.write(audio, offset + numWritten, length - numWritten);
			if (result == -1) throw Exception("Error writting data to audio card");
			numWritten += result;
		}
	}

	override fun close() {
		line.close();
	}
}

class NullAudioPlayer : AudioPlayer {
	constructor() {
		Log.info("Started null audio player");
	}

	override fun play(audio: ByteArray, offset: Int, length: Int) {
	}

	override fun getSamplingRate(): Int {
		return 16000;
	}

	override fun close() {
	}
}

fun main(args: Array<String>) {
	val recorder = LocalAudioRecorder(16000, 1600, true);
	var start = System.nanoTime();
	val bytes = ByteArrayOutputStream();
	println("Recording");
	while (System.nanoTime() - start < 3000000000L) {
		recorder.read();
		bytes.write(recorder.getByteData());
	}

	println("Playback");
	val player = LocalAudioPlayer(16000);
	val audio = bytes.toByteArray();
	player.play(audio, 0, audio.size);
}