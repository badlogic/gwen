package com.badlogicgames.gwen;

import com.esotericsoftware.minlog.Log
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
							 var websocketPubSubPort: Int = 8779) {

	@Synchronized fun save() {
		FileWriter(File(appPath, "config.json")).use {
			val credentials = this.credentials;
			if (credentials != null) {
				credentials.expirationTime = System.currentTimeMillis() + credentials.expiresIn * 1000;
			}
			GsonBuilder().setPrettyPrinting().create().toJson(this, it);
		}
	}
}

fun loadConfig(): GwenConfig {
	try {
		val configFile = File(appPath, "config.json");
		if (!configFile.exists()) {
			Log.debug("No config file found");
			return GwenConfig();
		} else {
			Log.debug("Loading config")
			return Gson().fromJson<GwenConfig>(JsonReader(FileReader(File(appPath, "config.json"))), GwenConfig::class.java);
		}
	} catch (e: Throwable) {
		Log.error("Error loading config", e);
		throw Exception("Error loading config", e);
	}
}