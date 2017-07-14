package com.badlogicgames.gwen;

import com.esotericsoftware.minlog.Log.*
import com.google.assistant.embedded.v1alpha1.*
import com.google.auth.oauth2.AccessToken
import com.google.auth.oauth2.OAuth2Credentials
import com.google.protobuf.ByteString
import io.grpc.ManagedChannelBuilder
import io.grpc.auth.MoreCallCredentials
import io.grpc.stub.StreamObserver
import java.util.*
import java.util.concurrent.CountDownLatch

class GoogleAssistant : StreamObserver<ConverseResponse> {
	private var client: EmbeddedAssistantGrpc.EmbeddedAssistantStub;
	private var currentState: ByteString? = null;
	private var finished = CountDownLatch(1);
	@Volatile private var speechToTextResult: String = "";
	@Volatile private var stopRecording: Boolean = false;
	@Volatile private var continueConversation: Boolean = false;
	@Volatile private var isCompleted: Boolean = false;
	@Volatile private var stopOnRequestText = false;
	private val TIME_OUT = 60 * 1000;
	private var callback: GoogleAssistantCallback = object : GoogleAssistantCallback {
		override fun questionComplete(question: String) {
		}

		override fun answerAudio(audio: ByteArray) {
		}
	};

	constructor() {
		val channel = ManagedChannelBuilder.forAddress("embeddedassistant.googleapis.com", 443).build();
		this.client = EmbeddedAssistantGrpc.newStub(channel);
	}

	fun setCredentials(credentials: OAuthCredentials) {
		val token = AccessToken(credentials.accessToken, Date(credentials.expirationTime));
		val callCredentials = MoreCallCredentials.from(OAuth2Credentials(token));
		client = client.withCallCredentials(callCredentials);
	}

	fun speechToText(oauth: OAuth, audioRecorder: AudioRecorder, audioPlayer: AudioPlayer): String {
		setCredentials(oauth.getCredentials());

		val requester = client.converse(this);
		finished = CountDownLatch(1);
		stopRecording = false;
		continueConversation = false;
		isCompleted = false;
		speechToTextResult = "";
		stopOnRequestText = true;

		requester.onNext(createConfigRequest(audioRecorder, audioPlayer));
		val timeOutTime = System.currentTimeMillis() + TIME_OUT;
		while (finished.count != 0.toLong() && System.currentTimeMillis() < timeOutTime) {
			audioRecorder.read();
			if (!stopRecording) {
				val audioIn = ByteString.copyFrom(audioRecorder.getByteData())
				val request = ConverseRequest.newBuilder().setAudioIn(audioIn).build();
				requester.onNext(request);
			}
		}
		return speechToTextResult;
	}

	fun converse(oauth: OAuth, audioRecorder: AudioRecorder, audioPlayer: AudioPlayer, callback: GoogleAssistantCallback): Boolean {
		this.callback = callback;
		setCredentials(oauth.getCredentials());

		val requester = client.converse(this);
		finished = CountDownLatch(1);
		stopRecording = false;
		continueConversation = false;
		isCompleted = false;
		stopOnRequestText = false;
		var sentCompleted = false;

		requester.onNext(createConfigRequest(audioRecorder, audioPlayer));
		val timeOutTime = System.currentTimeMillis() + TIME_OUT;
		while (finished.count != 0.toLong() && System.currentTimeMillis() < timeOutTime) {
			audioRecorder.read();
			if (!stopRecording) {
				val audioIn = ByteString.copyFrom(audioRecorder.getByteData())
				val request = ConverseRequest.newBuilder().setAudioIn(audioIn).build();
				requester.onNext(request);
			} else {
				if (isCompleted && !sentCompleted) {
					requester.onCompleted();
					sentCompleted = true;
				}
			}
		}
		if (System.currentTimeMillis() < timeOutTime) currentState = null;
		return continueConversation;
	}

	private fun createConfigRequest(audioRecorder: AudioRecorder, audioPlayer: AudioPlayer): ConverseRequest {
		val audioIn = AudioInConfig.newBuilder()
				  .setEncoding(AudioInConfig.Encoding.LINEAR16)
				  .setSampleRateHertz(audioRecorder.getSamplingRate()).build();
		val audioOut = AudioOutConfig.newBuilder()
				  .setEncoding(AudioOutConfig.Encoding.LINEAR16)
				  .setSampleRateHertz(audioPlayer.getSamplingRate()).build();

		val configBuilder = ConverseConfig.newBuilder().setAudioInConfig(audioIn).setAudioOutConfig(audioOut);
		if (currentState != null) {
			configBuilder.converseState = ConverseState.newBuilder().setConversationState(currentState).build();
		}
		return ConverseRequest.newBuilder().setConfig(configBuilder.build()).build();
	}

	override fun onNext(value: ConverseResponse?) {
		if (value == null) return;

		if (value.eventType != ConverseResponse.EventType.EVENT_TYPE_UNSPECIFIED) {
			trace("Asssitant - Event, type: ${value.eventType.name}");
		}

		if (value.eventType == ConverseResponse.EventType.END_OF_UTTERANCE) {
			trace("Assistant - End of utterance");
			stopRecording = true;
		}

		if (value.error != null && value.error.code != 0) {
			trace("Assistant - Error: ${value.error.message}");
		}

		if (value.audioOut != null) {
			if (!stopOnRequestText) {
				val audioData = value.audioOut.audioData.toByteArray();
				if (audioData.isNotEmpty()) {
					callback.answerAudio(audioData);
				}
			}
		}

		if (value.result != null) {
			this.currentState = value.result.conversationState

			if (value.result.spokenRequestText != null && !value.result.spokenRequestText.isEmpty()) {
				trace("Assistant - Question text: ${value.result.spokenRequestText}");
				speechToTextResult = value.result.spokenRequestText
				if (stopOnRequestText) finished.countDown();
				else callback.questionComplete(speechToTextResult);
			}

			if (value.result.spokenResponseText != null && !value.result.spokenResponseText.isEmpty()) {
				trace("Assistant - Answer text: ${value.result.spokenResponseText}");
			}

			if (value.result.microphoneMode == ConverseResult.MicrophoneMode.DIALOG_FOLLOW_ON) {
				trace("Assistant - Dialog follow on");
				continueConversation = true;
				isCompleted = true;
			}
			if (value.result.microphoneMode == ConverseResult.MicrophoneMode.CLOSE_MICROPHONE) {
				trace("Assistant - Close microphone");
				continueConversation = false;
				isCompleted = true;
				if (stopOnRequestText) finished.countDown();
			}
		}
	}

	override fun onError(t: Throwable?) {
		finished.countDown();
	}

	override fun onCompleted() {
		finished.countDown();
	}

	interface GoogleAssistantCallback {
		fun questionComplete(question: String);
		fun answerAudio(audio: ByteArray);
	}
}
