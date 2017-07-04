# gwen
Tiny voice assistant using [Google Assistant SDK](https://developers.google.com/assistant/sdk/) and [Snowboy](http://docs.kitt.ai/snowboy/). Tries to replicate some of the functionality found in Google Home, mostly question answering.

Additionally, this is a bit more privacy concious, as only the audio data of your question will be send to Google. The hotword detection works on-device through Snowboy. Once the hotword is detected (currently "Alexa" as that's the only universal Snowboy model I could find), subsequent audio data is send to Google until the end of the question is detected.

## Prerequisits
1. A Mac, Linux PC or Raspberry Pi (only tested on Raspbian Jessie) with a (configured) microphone and speakers.
2. [JDK8](http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html), older JDKs will likely just work as well (pre-installed in Raspbian Jessie)
3. Git

Note: Gwen currently does not work on Windows, as there is no supported for Snowboy on Windows yet.

## Building
```
git clone https://github.com/badlogic/gwen
cd gwen
./gradlew --no-daemon dist
```

## Usage
Setup a developer project and generated a client id and client secret as described in the [Google Assistant SDK docs](https://developers.google.com/assistant/sdk/prototype/getting-started-other-platforms/config-dev-project-and-account).

Create a file called "gwen.json" in your home directory, e.g. `/Users/someguy/gwen.json` (macOS), `/home/pi/gwen.json` (Linux/Raspbian), with the following content

```
{
   clientId: "<your-id-here>",
   clientSecret: "<your-secret-here>"
}
```

Assuming you've already built Gwen, you can start it from the project's root directory:

```
java -jar build/libs/gwen-1.0.jar
```

The first time you run Gwen, you'll be asked to open a URL in your browser, authorize one of your Google accounts, then copy & paste a code back into the terminal. Gwen will remember your credentials in a file called `credentials.json` in the project's root directory for subsequent runs. This file is neither encrypted nor otherwise secured. It contains OAuth2 access and refresh tokens, which you should most likely not share.

If authorization succeeded, just say ["Alexa" followed by a question](https://www.youtube.com/watch?v=5LLdVjMYUVo).
