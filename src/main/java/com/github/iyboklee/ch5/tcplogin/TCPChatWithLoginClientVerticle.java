/**
 * @Author iyboklee (iyboklee@gmail.com)
 */
package com.github.iyboklee.ch5.tcplogin;

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

public class TCPChatWithLoginClientVerticle extends Verticle {

	private Logger logger;
	private EventBus eb;
	private NetClient client;
	private String writeHandlerID;
	private String username;
	private String sessionID;
	
	private void deployWorkerVerticle() {
		container.deployWorkerVerticle("TCPChatWithLoginClientWorkerVerticle.java");
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
								JsonObject message = new JsonObject(buffer.toString());
								if (sessionID != null) {
									String from = message.getString("username");
									String msg = message.getString("message");
									System.out.println(from+": "+msg);
								}
								else {
									boolean authed = message.getString("status").equals("ok");
									//-- login success
									if (authed) {
										sessionID = message.getString("sessionID");
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
					JsonObject body = input.body();
					if (body.getBoolean("exit", false)) {
						container.exit();
					}
					else {
						if (sessionID != null) {
							body.getObject("body").putString("username", username);
							body.putString("sessionID", sessionID);
						}
						else {
							if (username == null)
							 username = body.getString("username");
						}
						
						byte[] data;
						try {
							data = body.toString().getBytes("UTF-8");
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