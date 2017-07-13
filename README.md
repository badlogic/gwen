# Gwen
Gwen is an extensible voice assistant framework. It offers the following functionality:

 * Detect hotwords on-device via [Snowboy](https://snowboy.kitt.ai/)
 * Send natural language queries to [Google Assistant](https://developers.google.com/assistant/sdk/) after hotword 
   detection to:
     * Receive your spoken words as text (speech-to-text)
     * Receive an audio response from Google Assistant to answer pressing questions such as "What time is it?"
        or "Where do unicorns live?"
 * Extensible via a simple TCP or Websocket pub/sub protocol, so you can 
    * React to commands, e.g. turn on your Phillips Hue bulbs by voice, ask to play some music, etc.
    * Playback any audio clip to signal events such as "hotword detected", "command text received", error etc.
    * Playback Google Assistant responses on whatever device you want
    
Gwen is intended to be used as an easily deployable speech-to-text and question answering service for which you write
a client that then handles interpretation of commands as well as audio output of answers to questions given by Google Assistant. 
    
Gwen has been tested on macOS as well as Raspberian on a Raspberry PI. Support for Linux x86_64 is being worked on.
Gwen does currently not work on Windows as Snowboy is not supported there. Gwen currently only supports English.
   
## Why?
Gwen was created as a means to control what data is being send to Google. Depending on your reading of the
[Google Home data security and privacy policy](https://support.google.com/googlehome/answer/7072285?hl=en), Google
 may gather any data the Google Home device can record *"to make [their] services faster, smarter, more relevant, and 
 more useful to you"*. By placing a Google Home device in your home and associating it with your Google account,
 Google may get to know you more up-close than you may want.
 
 Another motivation for Gwen is it to enable creating our own, custom command processing for home automation,
 making the entire setup trivial for programmers.

## How?
Gwen tries to give you more control over your privacy by only sending the audio data to Google Assistant that is necessary. After detecting
  a hotword such as "Snowboy", which happens on-device without sending anything to the outside world, Gwen will 
  send the subsequent audio stream from your microphone to the Google Assistant API. Google Assistant signals when
 it detects then end of your utterance, at which point no more audio is send to Google's servers.
 
 **Note:** You need to authorize a Google account to be used with Gwen. This means that Google will know from whom the 
 audio Gwen sends stems.
 
## Installation
Gwen requires the following software to be installed on your Mac or Raspberry PI:

 * Oracle JDK 8 or later
 	* On macOS, download and install the [JDK from Oracle](http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html)
 	* On Linux/Raspbian, `sudo apt-get install oracle-java8-jdk`
 * libatlas
 	* On macOS, nothing needs to be installed
 	* On Linux/Raspbian, `sudo apt-get install libatlas-base-dev`
 	
Gwen requires a microphone as well as a set of speakers. For Linux/Raspberry PI, ensure that your ALSA configuration
is setup correctly. Gwen will use the default microsphone and audio output.

With the pre-requisits installed, you can [download the latest build](http://libgdx.badlogicgames.com/gwen/), then in the directory you 
 downloaded the `.jar` file to, execute `java -jar gwen-1.0.jar`. Note that there is a separate build for
 Raspbian! As a rule of thumb, always start Gwen from the same directory as the `.jar` file.
 
 You can stop Gwen at any time via `CTRL+c` on the command line.

Gwen has an integrated web interface to let you configure its various bits and pieces. The interface is exposed 
on port `8777`. You can access the web interface via `http://localhost:8777` on the same machine you started Gwen on. 
Alternatively, e.g. if you run Gwen on a Raspberry PI, simply use the IP address of the device in your LAN as the
host name.

On the first run, Gwen requires you to setup a Google Developer project. Follow the [Google Assistant instructions](https://developers.google.com/assistant/sdk/prototype/getting-started-other-platforms/config-dev-project-and-account)
on how to setup the project and retrieve the project's `clientId` and `clientSecret`. Enter these credentials in
the Gwen web interface.

![https://libgdx.badlogicgames.com/uploads/Screen%20Shot%202017-07-13%20at%202.13.46%20AM-2OHEqrBP7x.png](https://libgdx.badlogicgames.com/uploads/Screen%20Shot%202017-07-13%20at%202.13.46%20AM-2OHEqrBP7x.png)

Next, authorize Gwen to use your Google account. Click the link in the web interface, select your account, then paste
 the code you receive into the web interface.
 
 ![https://libgdx.badlogicgames.com/uploads/Screen%20Shot%202017-07-13%20at%202.15.49%20AM-kR0YKqEuIG.png](https://libgdx.badlogicgames.com/uploads/Screen%20Shot%202017-07-13%20at%202.15.49%20AM-kR0YKqEuIG.png)
 ![https://libgdx.badlogicgames.com/uploads/Screen%20Shot%202017-07-13%20at%202.16.12%20AM-pqW3968y2Q.png](https://libgdx.badlogicgames.com/uploads/Screen%20Shot%202017-07-13%20at%202.16.12%20AM-pqW3968y2Q.png)

If everything went right, Gwen will now start up and present you with a simple status page.

![https://libgdx.badlogicgames.com/uploads/Screen%20Shot%202017-07-13%20at%202.20.13%20AM-YMViLnATeF.png](https://libgdx.badlogicgames.com/uploads/Screen%20Shot%202017-07-13%20at%202.20.13%20AM-YMViLnATeF.png)
  
## Configuration
Upon successful installation, you can further customize Gwen.

### Models
A Gwen model consists of a unique name, a [Snowboy](https://snowboy.kitt.ai/) hotword detector model and a type. 

You can use any of the detector models available on the Snowboy website. Gwen supports both universal models (`.umdl`) 
that are speaker independent, as well as personal models, which are trained based on samples of your own voice. A 
detector model is then used to recognize when you speak a hotword.

The type specifices how your speech after the hotword should be interpreted. There are currently two types:

* `Command`: Once the model's hotword was detected, subsequent audio input is send to Google Assistant and a speech-to-text 
result is send back to be processed as a command, e.g. "Turn on the lights" or "Play a Bowie album".
* `Question` Once the model's hotword was detected, subsequent audio input is send to Google Assistant and an audio
answer by Google is returned to be played back locally.

Gwen comes with two universal models, `Alexa` for questions, and `Snowboy` for commands. You can setup any number
of models under the `Models` tab of the web interface.

All changes to the model configuration are saved to `models.json`. Model files you updload will be saved to the `usermodels/`
directory.

### Misc.
The `Config` tab in Gwen's web interface let's you modify various settings:

* `Play audio locally`: Gwen can playback the answers by Google Assistant locally. If you disable this setting, you'll have to 
playback the audio answers yourself in your Gwen client.
* `Record stereo`: Your voice will be recorded in stereo should your mirophone support it. This option is a fallback solutation
for microphone drivers that pretend to support mono recording, but really only support stereo recording (looking at you, Respeaker).
* `Send audio input`: **DANGER ZONE**. This is only meant for debugging purposes, so your Gwen client is send all the audio
 input coming from the mic.
* `TCP pub/sub port`: the port to which a TCP Gwen client can connect.
* `Websocket pub/sub port`: the port to which a Websocket client can connect.

Changing any of these settings will trigger an in-process restart of Gwen. Additionally, all configuration changes are 
saved to `config.json`

## Writing a Gwen client
Once you setup and started the Gwen server as described above, you can write a client to react to various events. We
provide a simple [Kotlin implementation](https://github.com/badlogic/gwen/blob/master/gwen-client/src/main/kotlin/GwenClient.kt) 
for a TCP based [Gwen client](https://github.com/badlogic/gwen/blob/master/gwen-server/src/main/resources/assets/web/js/gwen-client.js), 
as well as a Javascript implementation for a Websocket based Gwen client. 

The general flow of a user interaction is as follows:

1. The user speaks a hotword
2. One of the models detects the hotword (on-device)
3. The user speaks a question/command that is send to Google Assistant
	1. if the model is a command model, once Google Assistant detects the end of the utterance, the speech is returned as text
	2. if the model is a question model, once Google Assistant detects the end of the utterance, the speech is returned as text, 
	and the answer by Google Assistant is returned as PCM data in multiple packets, to be played back through speakers.

A client can react to the following events:
 
 * `HOTWORD`: send when a model's hotword was detected. You'll receive the model's name and type. This is the ideal
 event to playback a "I'm now listening to you" sound.
 * `COMMAND`: send when a command model'S hotword was detected, and the user spoke the command. You'll receive the
  model's name and the text the user spoke. This is the ideal event to implement things like turning on the light, playing 
  back music and so on. You should also play a sound that indicates that the command was (un-)sucessfully processed.
 * `QUESTION`: send when a question model's hotword was detected, and the user spoke the question. You'll receive the
 model's name and the text the user spoke. Mildly useful.
 * `QUESTION_ANSWER_AUDIO`: send by Google Assistant once a user question was received. Will usually result in multiple of
 these events being received by your client for playback through the audio card. The received payload is 16-bit little endian
 mono PCM data. You can directly pass this data to an instance of `LocalAudioPlayer#play()`
 * `QUESTION_END`: send when both the question and the answer have been processed.
 * `AUDIO_INPUT`: send when the `Send audio input` flag was enabled in the configuration. Gwen will continuously stream the recorded
 audio to the client in form of these events. The received payload is 16-bit little endian mono PCM data. Useful for debugging.
 
To implement a client in Kotlin, inherit from `GwenClient` e.g.:

```Kotlin
val client = object : GwenPubSubClient("localhost", 8778) {
	override fun hotword(modelName: String, type: GwenModelType) {
	}

	override fun command(modelName: String, text: String) {
		if (text.toLowerCase() == "procrastinate")
			Desktop.getDesktop().browse(URI("https://reddit.com"));
	}

	override fun questionStart(modelName: String, text: String) {
	}

	override fun questionAnswerAudio(modelName: String, audio: ByteArray) {
	}

	override fun questionEnd(modelName: String) {
	}

	override fun audioInput(audio: ByteArray) {
	}
};
```

This Gwen client will help you procrastinate! And here it is in JavaScript, using Websockets:

```Javascript
var client = new GwenClient();
client.connect("localhost", 8779, {
	onHotword: function(modelName, modelType) {		
	},
	onCommand: function(modelName, command) {	
		if (command == "procrastinate") window.open("https://reddit.com");
	},
	onQuestionStart: function(modelName, question) {	
	},
	onQuestionAnswerAudio: function(modelName, audio) {
	},
	onQuestionEnd: function(modelName) {		
	},
	onAudioInput: function(audio) {
	}
});
```

If you don't want to use Kotlin (or anyother JVM language) or JavaScript (or a compile to JavaScript language), you can 
easily implement the simple TCP protocol for the pub/sub server. Following the [`GwenClient` implementation](https://github.com/badlogic/gwen/blob/master/gwen-client/src/main/kotlin/GwenClient.kt#L33)
is the best protocol documentation at the moment, as the protocol might slightly change in upcoming releases.

## Security
Gwen takes a few short cuts that may result in security concerns if not handled properly.

* Your Google Developer project id/secret and OAuth token are stored as plain-text in `config.json`. Re-use of these by 
malicious entities is unlikely to happen due to the nature of OAuth, but you should still know.
* The web interface is not secured and will listen on all network interfaces. Make sure you don't expose it
to the outside world, especially if you have the `Send audio input` flag enabled.
* The pub/sub server is not secured and will listen on all network interfaces. Make sure you don't expose it
to the outside world, especially if you have the `Send audio input` flag enabled.
 
All communication with Google is secured via TLS.

There be dragons, use at your own risk, read the sources.

## Contributing, License, Contact
Please refer to [https://github.com/badlogic/gwen/blob/master/CONTRIBUTING.md](https://github.com/badlogic/gwen/blob/master/CONTRIBUTING.md) on how to contribute to Gwen.

Please refer to [https://github.com/badlogic/gwen/blob/master/LICENSE](https://github.com/badlogic/gwen/blob/master/LICENSE) for the full license text (MIT).

Feel free to ping me on Twitter ([@badlogicgames](https://twitter.com/badlogicgames)).