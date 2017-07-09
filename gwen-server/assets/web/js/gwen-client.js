function GwenClient () {
}

GwenClient.QUESTION_MODEL = 0;
GwenClient.HOTWORD_MODEL = 1;

GwenClient.HOTWORD = 0;
GwenClient.COMMAND = 1;
GwenClient.QUESTION = 2;
GwenClient.QUESTION_ANSWER_AUDIO = 3;
GwenClient.QUESTION_END = 4;
GwenClient.AUDIO_INPUT = 5;

GwenClient.prototype.connect = function (host, port, config) {
	this.port = port;
	this.config = config;
	this.socket = new WebSocket("ws://" + host + ":" + port)
	this.socket.binaryType = "arraybuffer";
	decoder = new TextDecoder("utf-8");
	this.socket.onmessage = function(event) {
		// console.log(event);
		var data = event.data;
		var view = new DataView(data);
		var messageType = view.getUint8(0);
		if (messageType == GwenClient.HOTWORD) {
			if (config.onHotword) {
				var nameSize = view.getUint32(1);
				var name = decoder.decode(new DataView(data.slice(5, 5 + nameSize + 1)));
				var modelType = view.getUint32(5 + nameSize);
				config.onHotword(name, modelType);
			}
		}
		if (messageType == GwenClient.COMMAND) {
			if (config.onCommand) {
				var nameSize = view.getUint32(1);
				var name = decoder.decode(new DataView(data.slice(5, 5 + nameSize + 1)));
				var textSize = view.getUint32(5 + nameSize);
				var text = decoder.decode(new DataView(data.slice(5 + nameSize + 4, 5 + nameSize + 4 + textSize + 1)));
				config.onCommand(name, text);
			}
		}
		if (messageType == GwenClient.QUESTION) {
			if (config.onQuestionStart) {
				var nameSize = view.getUint32(1);
				var name = decoder.decode(new DataView(data.slice(5, 5 + nameSize + 1)));
				var textSize = view.getUint32(5 + nameSize);
				var text = decoder.decode(new DataView(data.slice(5 + nameSize + 4, 5 + nameSize + 4 + textSize + 1)));
				config.onQuestionStart(name, text);
			}
		}
		if (messageType == GwenClient.QUESTION_ANSWER_AUDIO) {
			if (config.onQuestionAnswerAudio) {
				var nameSize = view.getUint32(1);
				var name = decoder.decode(new DataView(data.slice(5, 5 + nameSize + 1)));
				var audioSize = view.getUint32(5 + nameSize);
				var audio = data.slice(5 + nameSize + 4, 5 + nameSize + 4 + audioSize + 1);
				config.onQuestionAnswerAudio(name, audio);
			}
		}
		if (messageType == GwenClient.QUESTION_END) {
			if (config.onQuestionEnd) {
				var nameSize = view.getUint32(1);
				var name = decoder.decode(new DataView(data.slice(5, 5 + nameSize + 1)));
				config.onQuestionEnd(name);
			}
		}
		if (messageType == GwenClient.AUDIO_INPUT) {
			if (config.onAudioInput) {
				var audioSize = view.getUint32(1);
				var audio = data.slice(5, 5 + audioSize + 1);
				config.onAudioInput(name, audio);
			}
		}
	}
};

GwenClient.prototype.close = function () {
	this.socket.close();
};