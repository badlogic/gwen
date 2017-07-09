// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: google/assistant/embedded/v1alpha1/embedded_assistant.proto

package com.google.assistant.embedded.v1alpha1;

public interface ConverseResponseOrBuilder extends
// @@protoc_insertion_point(interface_extends:google.assistant.embedded.v1alpha1.ConverseResponse)
	com.google.protobuf.MessageOrBuilder {

	/**
	 * <pre>
	 * *Output-only* If set, returns a [google.rpc.Status][google.rpc.Status] message that
	 * specifies the error for the operation.
	 * If an error occurs during processing, this message will be set and there
	 * will be no further messages sent.
	 * </pre>
	 *
	 * <code>.google.rpc.Status error = 1;</code> */
	com.google.rpc.Status getError ();

	/**
	 * <pre>
	 * *Output-only* If set, returns a [google.rpc.Status][google.rpc.Status] message that
	 * specifies the error for the operation.
	 * If an error occurs during processing, this message will be set and there
	 * will be no further messages sent.
	 * </pre>
	 *
	 * <code>.google.rpc.Status error = 1;</code> */
	com.google.rpc.StatusOrBuilder getErrorOrBuilder ();

	/**
	 * <pre>
	 * *Output-only* Indicates the type of event.
	 * </pre>
	 *
	 * <code>.google.assistant.embedded.v1alpha1.ConverseResponse.EventType event_type = 2;</code> */
	int getEventTypeValue ();

	/**
	 * <pre>
	 * *Output-only* Indicates the type of event.
	 * </pre>
	 *
	 * <code>.google.assistant.embedded.v1alpha1.ConverseResponse.EventType event_type = 2;</code> */
	com.google.assistant.embedded.v1alpha1.ConverseResponse.EventType getEventType ();

	/**
	 * <pre>
	 * *Output-only* The audio containing the assistant's response to the query.
	 * </pre>
	 *
	 * <code>.google.assistant.embedded.v1alpha1.AudioOut audio_out = 3;</code> */
	com.google.assistant.embedded.v1alpha1.AudioOut getAudioOut ();

	/**
	 * <pre>
	 * *Output-only* The audio containing the assistant's response to the query.
	 * </pre>
	 *
	 * <code>.google.assistant.embedded.v1alpha1.AudioOut audio_out = 3;</code> */
	com.google.assistant.embedded.v1alpha1.AudioOutOrBuilder getAudioOutOrBuilder ();

	/**
	 * <pre>
	 * *Output-only* The semantic result for the user's spoken query.
	 * </pre>
	 *
	 * <code>.google.assistant.embedded.v1alpha1.ConverseResult result = 5;</code> */
	com.google.assistant.embedded.v1alpha1.ConverseResult getResult ();

	/**
	 * <pre>
	 * *Output-only* The semantic result for the user's spoken query.
	 * </pre>
	 *
	 * <code>.google.assistant.embedded.v1alpha1.ConverseResult result = 5;</code> */
	com.google.assistant.embedded.v1alpha1.ConverseResultOrBuilder getResultOrBuilder ();

	public com.google.assistant.embedded.v1alpha1.ConverseResponse.ConverseResponseCase getConverseResponseCase ();
}