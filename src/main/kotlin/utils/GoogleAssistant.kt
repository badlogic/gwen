package com.badlogicgames.gwen;

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
    @Volatile private var stopRecording: Boolean = false;
    @Volatile private var continueConversation: Boolean = false;
    private val TIME_OUT = 60 * 1000;

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

    fun converse() {
        setCredentials(oauth.getCredentials());
        finished = CountDownLatch(1);
        stopRecording = false;
        continueConversation = false;

        val requester = client.converse(this);

        do {
            requester.onNext(createConfigRequest());
            val timeOutTime = System.currentTimeMillis() + TIME_OUT;
            var isCompleted = false;
            while (finished.count != 0.toLong() && System.currentTimeMillis() < timeOutTime) {
                if (!stopRecording) {
                    audioRecorder.read();
                    val audioIn = ByteString.copyFrom(audioRecorder.getByteData())
                    val request = ConverseRequest.newBuilder().setAudioIn(audioIn).build();
                    requester.onNext(request);
                } else {
                    if (!isCompleted) {
                        requester.onCompleted()
                        isCompleted = true;
                    };
                    Thread.sleep(20);
                }
            }
            if (System.currentTimeMillis() < timeOutTime) currentState = null;
        } while (continueConversation);
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
            println("Event type : ${value.eventType.name}");
        }

        if (value.eventType == ConverseResponse.EventType.END_OF_UTTERANCE) {
            println("response: end of utterance");
            stopRecording = true;
        }

        if (value.error != null && value.error.code != 0) {
            println("Error in response : ${value.error.message}");
        }

        if (value.audioOut != null) {
            val audioData = value.audioOut.audioData.toByteArray();
            audioPlayer.play(audioData, 0, audioData.size);
        }

        if (value.result != null) {
            this.currentState = value.result.conversationState

            if (value.result.spokenRequestText != null && !value.result.spokenRequestText.isEmpty()) {
                println("Request Text : ${value.result.spokenRequestText}");
            }

            if (value.result.spokenResponseText != null && !value.result.spokenResponseText.isEmpty()) {
                println("Response Text : ${value.result.spokenResponseText}");
            }
        }

        if (value.eventType == ConverseResponse.EventType.EVENT_TYPE_UNSPECIFIED) {
            // println("response: unspecified type");
            return;
        }

        if (value.result.microphoneMode == ConverseResult.MicrophoneMode.DIALOG_FOLLOW_ON) {
            println("response: dialog follow on");
            continueConversation = true;
        }
        if (value.result.microphoneMode == ConverseResult.MicrophoneMode.CLOSE_MICROPHONE) {
            println("response: close microphone");
            continueConversation = false;
        }
    }

    override fun onError(t: Throwable?) {
        finished.countDown();
    }

    override fun onCompleted() {
        finished.countDown();
    }
}
