package com.badlogicgames.gwen;

import ai.kitt.snowboy.SnowboyDetect
import com.esotericsoftware.minlog.Log
import java.io.Closeable
import java.io.File

interface HotwordDetector : Closeable {
	fun trigger();

	fun detect(audioData: ShortArray): Boolean;
}

class SnowboyHotwordDetector : HotwordDetector {
	val detector: SnowboyDetect;
	@Volatile var triggered: Boolean = false;

	constructor(model: File) {
		Log.debug("Loading Snowboy model ${model.absolutePath}");
		this.detector = SnowboyDetect(common.absolutePath, model.absolutePath);
	}

	constructor(model: String) {
		Log.debug("Loading Snowboy model ${model}");
		val file = extractFromClasspathToFile(model);
		this.detector = SnowboyDetect(common.absolutePath, file.absolutePath);
	}

	override fun trigger() {
		triggered = true;
	}

	override fun detect(audioData: ShortArray): Boolean {
		val wasTriggered = triggered;
		if (wasTriggered) triggered = false;
		return wasTriggered || detector.RunDetection(audioData, audioData.size) > 0;
	}

	override fun close() {
		detector.delete();
	}
}

private val common by lazy {
	val osName = System.getProperty("os.name").toLowerCase();
	var libFile: String;
	when {
		osName.contains("mac") -> libFile = "jni/libsnowboy-detect-java.dylib";
		else -> libFile = "jni/libsnowboy-detect-java.so";
	}
	val tempFile = extractFromClasspathToFile(libFile);
	System.load(tempFile.absolutePath);
	extractFromClasspathToFile("assets/snowboy/common.res");
}

class WebHotwordDetector : HotwordDetector {
	@Volatile var triggered: Boolean = false;

	override fun trigger() {
		triggered = true;
	}

	override fun detect(audioData: ShortArray): Boolean {
		val wasTriggered = triggered;
		if (wasTriggered) triggered = false;
		return wasTriggered;
	}

	override fun close() {
	}
}
