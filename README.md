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
    
Gwen has been tested on macOS as well as Raspberian on a Raspberry PI. Support for Linux x86_64 is being worked on.
Gwen does currently not work on Windows as Snowboy is not supported there. Gwen currently only supports English.
   
## Why?
Gwen was created as a means to control what data is being send to Google. Depending on your reading of the
[Google Home data security and privacy policy](https://support.google.com/googlehome/answer/7072285?hl=en), Google
 may gather any data the Google Home device can record *"to make [their] services faster, smarter, more relevant, and 
 more useful to you"*. By placing a Google Home device in your home and associating it with your Google account,
 Google may get to know you more up-close than you may want.
 
 Another motivation to create Gwen was to be able to create our own, custom command processing for home automation,
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
 	* On Raspbian, `sudo apt-get install oracle-`

