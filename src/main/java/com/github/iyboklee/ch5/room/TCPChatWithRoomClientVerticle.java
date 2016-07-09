/**
 * @Author iyboklee (iyboklee@gmail.com)
 */
package com.github.iyboklee.ch5.room;

import java.io.UnsupportedEncodingException;
import java.util.Date;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;
import org.vertx.java.core.VoidHandler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.net.NetClient;
import org.vertx.java.core.net.NetSocket;
import org.vertx.java.core.parsetools.RecordParser;
import org.vertx.java.platform.Verticle;

public class TCPChatWithRoomClientVerticle extends Verticle {

	private Logger logger;
	private EventBus eb;
	private NetClient client;
	private String writeHandlerID;
	private String username;
	private String sessionID;
	private String rmID;
	
	private void deployWorkerVerticle() {
		container.deployWorkerVerticle("TCPChatWithRoomClientWorkerVerticle.java");
	}
	
	@Override
	public void start() {
		logger = container.logger();
		eb = vertx.eventBus();
		client = vertx.createNetClient();

		deployWorkerVerticle();
		
		client.connect(8090, "localhost", new Handler<AsyncResult<NetSocket>>() {
			@Override
			public void handle(AsyncResult<NetSocket> asyncResult) {
				logger.info("connect result: "+asyncResult.succeeded());

				if (asyncResult.succeeded()) {
					final NetSocket socket = asyncResult.result();
					writeHandlerID = socket.writeHandlerID();
					//-- tcp stream framing
					final RecordParser framer = RecordParser.newFixed(4, null);
					framer.setOutput(new Handler<Buffer>() {
						int payloadLength = -1;
						@Override
						public void handle(Buffer buffer) {
							if (payloadLength == -1) {
								payloadLength = buffer.getInt(0);
								framer.fixedSizeMode(payloadLength);
							}
							else {
								MessageParser message = new MessageParser(buffer);
								if (sessionID != null) {
									if (message.getMessageType() == MessageParser.MessageType.CHAT) {
										String from = message.getUsername();
										String msg = message.getBodymsg();
										System.out.println(from+": "+msg);
									}
									else if (message.getMessageType() == MessageParser.MessageType.ENTER) {
										if (message.statusOK()) {
											rmID = message.getRmID();
											System.out.println("enter the room '"+rmID+"'");
										}
									}
									else if (message.getMessageType() == MessageParser.MessageType.LEAVE) {
										if (message.statusOK()) {
											System.out.println("leave the room '"+rmID+"'");
											rmID = null;
										}
									}	
								}
								else {
									boolean authed = message.statusOK();
									//-- login success
									if (authed) {
										sessionID = message.getSessionID();
										System.out.println("user '"+username+"' login success at "+new Date());
									}
									//-- login failure
									else {
										System.out.println("user '"+username+"' login failure. please enter exit.");
									}
								}
								
								framer.fixedSizeMode(4);
								payloadLength = -1;
							}
						}
					});
					//-- this handler will be called every time data is received on the socket
					socket.dataHandler(framer);
					//-- socket closed
					socket.closeHandler(new VoidHandler() {
						@Override
						protected void handle() {
							logger.info("connection closed: "+socket.remoteAddress());
						}
					});
					//-- something went wrong
					socket.exceptionHandler(new Handler<Throwable>() {
						@Override
						public void handle(Throwable throwable) {
							logger.error("unexpected exception: ", throwable);
						}
					});
				}
			}
		});
		
		eb.registerHandler("com.github.iyboklee.chat", new Handler<Message<JsonObject>>() {
			@Override
			public void handle(Message<JsonObject> input) {
				if (writeHandlerID != null) {
					JsonObject message = input.body();
					JsonObject body = message.getObject("body");
					if (message.getBoolean("exit", false)) {
						container.exit();
					}
					else {
						if (sessionID != null) {
							message.putString("sessionID", sessionID);
							body.putString("username", username);
							String line = body.getString("message");
							String[] tokens = line.split(" ");
							
							if ("/enter".startsWith(tokens[0]) && tokens.length == 2 && rmID == null) {
								message.putNumber("messageType", MessageParser.MessageType.ENTER.ordinal());
								message.putString("rmID", tokens[1]);
							}
							else if ("/leave".startsWith(tokens[0]) && tokens.length == 2 && rmID != null) {
								message.putNumber("messageType", MessageParser.MessageType.LEAVE.ordinal());
								message.putString("rmID", tokens[1]);
							}
							else if (rmID != null) {
								message.putNumber("messageType", MessageParser.MessageType.CHAT.ordinal());
								message.putString("rmID", rmID);
							}
							else {
								System.out.println("you must enter roomID");
								return;
							}
						}
						else {
							if (username == null)
							 username = message.getString("username");
						}
						
						byte[] data;
						try {
							data = message.toString().getBytes("UTF-8");
							Buffer buffer = new Buffer(4+data.length);
							buffer.setInt(0, data.length);
							buffer.setBytes(4, data);
							eb.send(writeHandlerID, buffer);
						} catch (UnsupportedEncodingException e) {
						}
					}
				}	
			}
		});
	}

	@Override
	public void stop() {
		if (client != null)
			client.close();
	}

}