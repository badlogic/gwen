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
GwenClient.GET_CONFIG = 6;
GwenClient.SET_CONFIG = 7;

GwenClient.OPTION_TYPE_BOOLEAN = 0;
GwenClient.OPTION_TYPE_NUMBER = 1;
GwenClient.OPTION_TYPE_STRING = 2

GwenClient.prototype.connect = function (host, port, config) {
	this.port = port;
	this.config = config;
	this.socket = new WebSocket("ws://" + host + ":" + port)
	this.socket.binaryType = "arraybuffer";
	socket = this.socket;
	decoder = new TextDecoder("utf-8");
	encoder = new TextEncoder("utf-8");
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
		if (messageType == GwenClient.GET_CONFIG) {
			if (config.onGetConfig) {
				var conf = config.onGetConfig();
				var name = encoder.encode(conf.name);
				var desc = encoder.encode(conf.description);
				var numOptions = conf.options.length;
				var numBytes = 4 + name.byteLength + 4 + desc.byteLength + 4;
				var options = [];
				for (var i = 0; i < numOptions; i++) {
					var option = conf.options[i];
					var optionName = encoder.encode(option.name);
					var optionValue = null;
					var optionType = null;
					numBytes += 4 + optionName.byteLength + 4;
					if (typeof(option.value) === "boolean") {
						optionValue = option.value == true? 1 : 0;
						optionType = GwenClient.OPTION_TYPE_BOOLEAN;
						numBytes += 4;
					} else if (typeof(option.value) === "number") {
						optionValue = option.value;
						optionType = GwenClient.OPTION_TYPE_NUMBER;
						numBytes += 4;
					} else if (typeof(option.value) === "string") {
						optionValue = encoder.encode(option.value);
						optionType = GwenClient.OPTION_TYPE_STRING;
						numBytes += 4 + optionValue.byteLength;
					} else {
						throw Exception("Option value must be boolean, number or string");
					}
					options.push({name: optionName, type: optionType, value: optionValue});
				}

				var bytes = new Uint8Array(numBytes);
				var bytesView = new DataView(bytes.buffer);
				var idx = 0;
				bytesView.setUint32(idx, name.byteLength);
				idx += 4;
				for (var i = 0, n = name.byteLength; i < n; i++, idx++)
					bytesView.setUInt8(idx, name[i]);
				bytesView.setUint32(idx, desc.byteLength);
				idx += 4;
				for (var i = 0, n = desc.byteLength; i < n; i++, idx++)
                	bytesView.setUInt8(idx, desc[i]);
                bytesView.setUint32(idx, numOptions);
                idx += 4;
                for (var i = 0; i < numOptions; i++) {
                	var option = options[i];
                	bytesView.setUint32(idx, option.name.byteLength);
                	id += 4;
                	for (var i = 0, n = option.name.byteLength; i < n; i++, idx++)
                		bytesView.setUint8(idx, option.name[i]);
                	bytesView.setUint32(idx, option.type);
                	idx += 4;
                	if (option.type == GwenClient.OPTION_TYPE_BOOLEAN) {
                		bytesView.setUint32(idx, option.value);
                		idx += 4;
                	} else if (option.type == GwenClient.OPTION_TYPE_NUMBER) {
                		bytesView.setFloat32(idx, option.value);
                		idx += 4;
                	} else if (option.type == GwenClient.OPTION_TYPE_STRING) {
                		bytesView.setUint32(option.value.byteLength);
                		idx += 4;
                		for (var i = 0, n = option.value.byteLength; i < n; i++, idx++)
                			bytesView.setUint8(idx, option.value[i]);
                	}
                }
                socket.send(bytes.buffer);
			} else {
				var bytes = new Uint8Array(3 * 4);
				var bytesView = new DataView(bytes.buffer);
				bytesView.setUint32(0, 0);
				bytesView.setUint32(4, 0);
				bytesView.setUint32(8, 0);
				socket.send(bytes.buffer);
			}
		}
		if (messageType == GwenClient.SET_CONFIG) {
			if (config.onSetConfig) {
				var idx = 0;
				var numOptions = view.getUint32(0);
				idx += 4;
				var options = [];
				for (var i = 0; i < numOptions; i++) {
					var nameSize = view.getUint32(idx);
					idx += 4;
                    var name = decoder.decode(new DataView(data.slice(idx, idx + nameSize + 1)));
                    idx += nameSize;
                    var type = view.getUint32(idx);
                    idx += 4;
                    var value = null;
                    var type = null;
                    if (type == GwenClient.OPTION_TYPE_BOOLEAN) {
                    	type = GwenClient.OPTION_TYPE_BOOLEAN;
                    	value = view.getUint32(idx) != 0 ? true : false;
                    	idx += 4;
                    } else if (type == GwenClient.OPTION_TYPE_NUMBER) {
                    	type = GwenClient.OPTION_TYPE_NUMBER;
                    	value = view.getFloat32(idx);
                    	idx += 4;
                    } else if (type == GwenClient.OPTION_TYPE_STRING) {
                    	var stringSize = view.getUin32(idx);
                    	idx += 4;
                    	var value = decoder.decode(new DataView(data.slice(idx, idx + stringSize + 1)));
                    	idx += stringSize;
                    }
                    options.push({name: name, type: type, value: value});
				}
				config.onSetConfig(options);
			}
		}
	}
};

GwenClient.prototype.close = function () {
	this.socket.close();
};