package com.badlogicgames.gwen;

import java.io.File
import java.io.FileOutputStream

val tmpDir by lazy {
	val dir = File.createTempFile("temp", "gwen");
	dir.delete();
	dir.mkdirs();
	dir;
}

fun extractFromClasspath(name: String): ByteArray {
	return GwenEngine::class.java.classLoader.getResourceAsStream(if (name.startsWith("/")) name.substring(1) else name).readBytes();
}

fun extractFromClasspathToFile(name: String): File {
	val nameOnly = File(name).name;
	val file = File(tmpDir, nameOnly);
	FileOutputStream(file).use {
		it.write(GwenEngine::class.java.classLoader.getResourceAsStream(if (name.startsWith("/")) name.substring(1) else name).readBytes());
	}
	return file;
}