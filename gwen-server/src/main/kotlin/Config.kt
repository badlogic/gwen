package com.badlogicgames.gwen;

import com.esotericsoftware.minlog.Log.*
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import com.google.gson.stream.JsonReader
import java.io.File
import java.io.FileReader
import java.io.FileWriter

data class GoogleAssistantConfig (
	val clientId: String,
	val clientSecret: String
);

data class OAuthCredentials(@SerializedName("access_token") var accessToken: String,
									 @SerializedName("expires_in") var expiresIn: Int,
									 @SerializedName("token_type") var tokenType: String,
									 @SerializedName("refresh_token") var refreshToken: String,
									 @SerializedName("id_token") var idToken: String,
									 @SerializedName("expiration_time") var expirationTime: Long);

data class GwenConfig(var assistantConfig: GoogleAssistantConfig? = null,
							 var credentials: OAuthCredentials? = null,
							 var playAudioLocally: Boolean = true,
							 var sendLocalAudioInput: Boolean = false,
							 var recordStereo: Boolean = false,
							 var pubSubPort: Int = 8778,
							 var websocketPubSubPort: Int = 8779,
							 @Transient var file: File) {

	@Synchronized fun save() {
		FileWriter(file).use {
			val credentials = this.credentials;
			if (credentials != null) {
				credentials.expirationTime = System.currentTimeMillis() + credentials.expiresIn * 1000;
			}
			GsonBuilder().setPrettyPrinting().create().toJson(this, it);
		}
	}
}

fun loadConfig(configFile: File = File(appPath, "config.json")): GwenConfig {
	try {
		if (!configFile.exists()) {
			debug("No config file found");
			return GwenConfig(file = configFile);
		} else {
			debug("Loading config")
			var config = Gson().fromJson<GwenConfig>(JsonReader(FileReader(configFile)), GwenConfig::class.java);
			config.file = configFile;
			return config;
		}
	} catch (e: Throwable) {
		error("Error loading config", e);
		throw Exception("Error loading config", e);
	}
}