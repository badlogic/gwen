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
		val osName = System.getProperty("os.name").toLowerCase();

		when {
			osName.contains("mac") -> System.load(File(appPath.absolutePath + "/jni/libsnowboy-detect-java.dylib").absolutePath);
			else -> System.load(File(appPath.absolutePath + "/jni/libsnowboy-detect-java.so").absolutePath);
		}
		Log.debug("Loading Snowboy model ${model.absolutePath}");
		this.detector = SnowboyDetect(appPath.absolutePath + "/assets/snowboy/common.res", model.absolutePath);
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
