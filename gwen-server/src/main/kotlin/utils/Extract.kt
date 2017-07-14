package com.badlogicgames.gwen;

import java.io.File
import java.io.FileOutputStream
import java.io.IOException

val tmpDir by lazy {
	val dir = File.createTempFile("temp", "gwen");
	dir.delete();
	dir.mkdirs();
	dir;
}

fun extractFromClasspath(name: String): ByteArray {
	var input = GwenEngine::class.java.classLoader.getResourceAsStream(if (name.startsWith("/")) name.substring(1) else name)
	if (input == null) throw IOException("File not found: " + name);
	return input.readBytes();
}

fun extractFromClasspathToFile(name: String): File {
	val nameOnly = File(name).name;
	val file = File(tmpDir, nameOnly);
	FileOutputStream(file).use {
		it.write(extractFromClasspath(name));
	}
	return file;
}