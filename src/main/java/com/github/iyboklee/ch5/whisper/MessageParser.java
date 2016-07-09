/**
 * @Author iyboklee (iyboklee@gmail.com)
 */
package com.github.iyboklee.ch5.whisper;

import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.net.NetSocket;

public class MessageParser {

	public enum MessageType {
		CHAT, ENTER, LEAVE, NOTICE, WHISPER;
		
		public static MessageType getMessageType(int code) {
			if (code == 0) return CHAT;
			if (code == 1) return ENTER;
			if (code == 2) return LEAVE;
			if (code == 3) return NOTICE;
			if (code == 4) return WHISPER;
			return null;
		}
	}
	
	private JsonObject origin;
	private MessageType messageType;
	private String status;
	private String sessionID;
	private String rmID;
	private String whisperTarget;
	private JsonObject body;
	private String username;
	private String bodymsg;
	
	public MessageParser(Buffer buffer) {
		this(buffer.toString());
	}
	
	public MessageParser(String jsonString) {
		this(new JsonObject(jsonString));
	}

	public MessageParser(JsonObject message) {
		this.origin = message;
		messageType = MessageType.getMessageType(message.getInteger("messageType", -1));
		status = message.getString("status");
		sessionID = message.getString("sessionID");
		rmID = message.getString("rmID");
		whisperTarget = message.getString("whisperTarget");
		body = message.getObject("body");
		if (body != null) {
			username = body.getString("username");
			bodymsg = body.getString("message");
		}
	}

public static String arrayToString(String[] array, int offset, int length) {
	StringBuffer sb = new StringBuffer();
	for (int i=offset; i<length; i++) {
		sb.append(array[i]);
		if (i < length-1) 
			sb.append(" ");
	}
	return sb.toString();
}
	
	public JsonObject getInteralChatBroadcastData(NetSocket writer) {
		JsonObject internalBroadcastData = new JsonObject();
		internalBroadcastData.putNumber("messageType", messageType.ordinal());
		internalBroadcastData.putObject("body", body);
		internalBroadcastData.putString("rmID", rmID);
		body.putBoolean("whisper", (whisperTarget != null));
		if (whisperTarget != null)
			internalBroadcastData.putString("whisperTarget", whisperTarget);
		
		if (writer != null)
			internalBroadcastData.putString("writerHandlerID", writer.writeHandlerID());
		return internalBroadcastData;
	}
	
	public boolean statusOK() {
		return (status!=null && "ok".equalsIgnoreCase(status));
	}
	
	public JsonObject origin() {
		return origin;
	}

	public MessageType getMessageType() {
		return messageType;
	}

	public String getStatus() {
		return status;
	}

	public String getSessionID() {
		return sessionID;
	}

	public String getRmID() {
		return rmID;
	}

	public String getWhisperTarget() {
		return whisperTarget;
	}

	public JsonObject getBody() {
		return body;
	}

	public String getUsername() {
		return username;
	}

	public String getBodymsg() {
		return bodymsg;
	}
	
}