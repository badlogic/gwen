package com.badlogicgames.gwen

import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.TargetDataLine

class AudioRecorder {
    val line: TargetDataLine;
    val data: ByteArray;
    val shortData: ShortArray;

    constructor(samplingRate: Float) {
        val format = AudioFormat(samplingRate, 16, 1, true, false);
        val targetInfo = DataLine.Info(TargetDataLine::class.java, format);
        line = AudioSystem.getLine(targetInfo) as TargetDataLine;
        line.open(format);
        line.start();

        data = ByteArray(samplingRate.toInt() / 10 * 2);
        shortData = ShortArray(data.size / 2);
    }

    fun read() : ShortArray {
        val numRead = line.read(data, 0, data.size);
        if (numRead == -1) throw Exception("Error reading data from microphone");
        ByteBuffer.wrap(data, 0, numRead).order(ByteOrder.nativeOrder()).asShortBuffer().get(shortData);
        return shortData;
    }

    fun close() {
        line.close();
    }
}

class AudioPlayer {

}
