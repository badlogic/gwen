import com.badlogicgames.gwen.*

class HelloWorldClient(host: String, port: Int) : GwenTCPPubSubClient(host, port) {
	override fun hotword(modelName: String, type: GwenModelType) {
		println("Hotword: $modelName, $type");
	}

	override fun command(modelName: String, text: String) {
		println("Command: $modelName, $text");
	}

	override fun questionStart(modelName: String, text: String) {
		println("Question start: $modelName, $text");
	}

	override fun questionAnswerAudio(modelName: String, audio: ByteArray) {
		// No-op
	}

	override fun questionEnd(modelName: String) {
		println("Question end: $modelName");
	}

	override fun audioInput(audio: ByteArray) {
		// No-op
	}

	override fun getConfig(): GwenPubSubClientConfig {
		println("Getting options");
		return GwenPubSubClientConfig(
			"Hello World Client",
			"This is a <strong>simple</strong> hello world client that prints events to stdout",
			mutableListOf(
					 GwenPubSubClientConfigOption("Option #1", GwenPubSubClientConfigOptionType.BOOLEAN, false),
					 GwenPubSubClientConfigOption("Option #2", GwenPubSubClientConfigOptionType.NUMBER, 123f),
					 GwenPubSubClientConfigOption("Option #3", GwenPubSubClientConfigOptionType.STRING, "Hello world")
			)
		);
	}

	override fun setOptions(options: List<GwenPubSubClientConfigOption>) {
		println("Setting options");
		for (option in options) {
			println(option);
		}
	}
}

fun main(args: Array<String>) {
	HelloWorldClient("localhost", 8778);
	while(true) Thread.sleep(1000);
}
