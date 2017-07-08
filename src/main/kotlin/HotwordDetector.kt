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
	var triggered: Boolean = false;

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
		 try {
       	return triggered || detector.RunDetection(audioData, audioData.size) > 0;
	 	} finally {
			 triggered = false;
		 } 
    }

    override fun close() {
        detector.delete();
    }
}

class WebHotwordDetector : HotwordDetector {
    public var triggered: Boolean = false;
	
	override fun trigger() {
		triggered = true;
	}

    override fun detect(audioData: ShortArray): Boolean {
		 try {
       	return triggered;
	 	} finally {
			 triggered = false;
		 } 
    }

    override fun close() {
    }
}
