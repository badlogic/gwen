package com.badlogicgames.gwen;

import com.esotericsoftware.minlog.Log
import com.google.assistant.embedded.v1alpha1.*
import com.google.auth.oauth2.AccessToken
import com.google.auth.oauth2.OAuth2Credentials
import com.google.protobuf.ByteString
import io.grpc.ManagedChannelBuilder
import io.grpc.auth.MoreCallCredentials
import io.grpc.stub.StreamObserver
import java.util.*
import java.util.concurrent.CountDownLatch

class GoogleAssistant: StreamObserver<ConverseResponse> {
    private val oauth: OAuth;
    private val audioRecorder: AudioRecorder;
    private val audioPlayer: AudioPlayer;
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

    constructor(oauth: OAuth, audioRecorder: AudioRecorder, audioPlayer: AudioPlayer) {
        this.oauth = oauth;
        this.audioRecorder = audioRecorder;
        this.audioPlayer = audioPlayer;
        val channel = ManagedChannelBuilder.forAddress("embeddedassistant.googleapis.com", 443).build();
        this.client = EmbeddedAssistantGrpc.newStub(channel);
        setCredentials(oauth.getCredentials());
    }

    fun setCredentials(credentials: OAuthCredentials) {
        val token = AccessToken(credentials.accessToken, Date(credentials.expirationTime));
        val callCredentials = MoreCallCredentials.from(OAuth2Credentials(token));
        client = client.withCallCredentials(callCredentials);
    }

    fun speechToText(): String {
        setCredentials(oauth.getCredentials());

        println("Starting text to speech");
        val requester = client.converse(this);
        finished = CountDownLatch(1);
        stopRecording = false;
        continueConversation = false;
        isCompleted = false;
        speechToTextResult = "";
        stopOnRequestText = true;

        requester.onNext(createConfigRequest());
        val timeOutTime = System.currentTimeMillis() + TIME_OUT;
        while (finished.count != 0.toLong() && System.currentTimeMillis() < timeOutTime) {
            audioRecorder.read();
            if (!stopRecording) {
                val audioIn = ByteString.copyFrom(audioRecorder.getByteData())
                val request = ConverseRequest.newBuilder().setAudioIn(audioIn).build();
                requester.onNext(request);
            } else {
                Thread.sleep(20);
            }
        }
        return speechToTextResult;
    }

    fun converse(callback: GoogleAssistantCallback): Boolean {
        setCredentials(oauth.getCredentials());
        this.callback = callback;

        val requester = client.converse(this);
        finished = CountDownLatch(1);
        stopRecording = false;
        continueConversation = false;
        isCompleted = false;
        stopOnRequestText = false;
        var sentCompleted = false;

        requester.onNext(createConfigRequest());
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
                Thread.sleep(20);
            }
        }
        if (System.currentTimeMillis() < timeOutTime) currentState = null;
        return continueConversation;
    }

    private fun createConfigRequest(): ConverseRequest {
        val audioIn = AudioInConfig.newBuilder()
                .setEncoding(AudioInConfig.Encoding.LINEAR16)
                .setSampleRateHertz(audioRecorder.getSamplingRate()).build();
        val audioOut = AudioOutConfig.newBuilder()
                .setEncoding(AudioOutConfig.Encoding.LINEAR16)
                .setSampleRateHertz(audioPlayer.getSamplingRate()).build();

        val configBuilder = ConverseConfig.newBuilder().setAudioInConfig(audioIn).setAudioOutConfig(audioOut);
        if (currentState != null) {
            configBuilder.setConverseState(ConverseState.newBuilder().setConversationState(currentState).build());
        }
        return ConverseRequest.newBuilder().setConfig(configBuilder.build()).build();
    }

    override fun onNext(value: ConverseResponse?) {
        if (value == null) return;

        if (value.eventType != ConverseResponse.EventType.EVENT_TYPE_UNSPECIFIED) {
            Log.trace("Asssitant - Event, type : ${value.eventType.name}");
        }

        if (value.eventType == ConverseResponse.EventType.END_OF_UTTERANCE) {
            Log.trace("Assistant - End of utterance");
            stopRecording = true;
        }

        if (value.error != null && value.error.code != 0) {
            Log.trace("Assistant - Error: ${value.error.message}");
        }

        if (value.audioOut != null) {
            if (!stopOnRequestText) {
                val audioData = value.audioOut.audioData.toByteArray();
                audioPlayer.play(audioData, 0, audioData.size);
                callback.answerAudio(audioData);
            }
        }

        if (value.result != null) {
            this.currentState = value.result.conversationState

            if (value.result.spokenRequestText != null && !value.result.spokenRequestText.isEmpty()) {
                Log.trace("Assistant - Question Text : ${value.result.spokenRequestText}");
                speechToTextResult = value.result.spokenRequestText
                if (stopOnRequestText) finished.countDown();
                else callback.questionComplete(speechToTextResult);
            }

            if (value.result.spokenResponseText != null && !value.result.spokenResponseText.isEmpty()) {
                Log.trace("Assistant - Answer Text : ${value.result.spokenResponseText}");
            }

            if (value.result.microphoneMode == ConverseResult.MicrophoneMode.DIALOG_FOLLOW_ON) {
                Log.trace("Assistant - Dialog follow on");
                continueConversation = true;
                isCompleted = true;
            }
            if (value.result.microphoneMode == ConverseResult.MicrophoneMode.CLOSE_MICROPHONE) {
                Log.trace("Assistant - Close microphone");
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
