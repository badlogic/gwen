package com.badlogicgames.gwen

import com.esotericsoftware.minlog.Log
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.sound.sampled.*

interface AudioRecorder: Closeable {
    fun getSamplingRate(): Int;
    fun read();
    fun getByteData(): ByteArray;
    fun getShortData(): ShortArray;
}

class LocalAudioRecorder: AudioRecorder {
    private val line: TargetDataLine;
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

        byteData = ByteArray(samplesPerChunk * (if (stereo) 4 else 2));
        shortData = ShortArray(samplesPerChunk);
        Log.info("Started local audio recorder, ${samplingRate}Hz, stereo: $_stereo, ${samplesPerChunk} samples per chunk");
    }

    override fun getSamplingRate(): Int {
        return _samplingRate;
    }

    override fun read() {
        var numRead: Int = 0;
        while (numRead != byteData.size) {
            val result = line.read(byteData, numRead, byteData.size - numRead);
            if (result == -1) throw Exception("Error reading byteData from microphone");
            numRead += result;
        }
        if (_stereo) {
            var ii = 4;
            for (i in 2..byteData.size / 2 - 1 step 2) {
                byteData[i] = byteData[ii];
                byteData[i + 1] = byteData[ii + 1];
                ii += 4;
            }
            numRead.shr(1);
        }
        ByteBuffer.wrap(byteData, 0, numRead).order(ByteOrder.nativeOrder()).asShortBuffer().get(shortData);
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

interface AudioPlayer: Closeable {
    fun play(audio: ByteArray, offset: Int, length: Int);
    fun  getSamplingRate(): Int;
}

class LocalAudioPlayer: AudioPlayer {
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
    while (System.nanoTime() - start < 5000000000L) {
        recorder.read();
        bytes.write(recorder.getByteData());
    }

    println("Playback");
    val player = LocalAudioPlayer(16000);
    val audio = bytes.toByteArray();
    player.play(audio, 0, audio.size);
}