/**
 * @Author iyboklee (iyboklee@gmail.com)
 */
package com.github.iyboklee.ch2;

import java.io.UnsupportedEncodingException;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;
import org.vertx.java.core.VoidHandler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.net.NetClient;
import org.vertx.java.core.net.NetSocket;
import org.vertx.java.core.parsetools.RecordParser;
import org.vertx.java.platform.Verticle;

public class TCPChatWithFramingClientVerticle extends Verticle {

	private Logger logger;
	private EventBus eb;
	private NetClient client;
	private String writeHandlerID;
	
	@Override
	public void start() {
		logger = container.logger();
		eb = vertx.eventBus();
		client = vertx.createNetClient();

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
								logger.info("received data: "+buffer.toString());
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
		
		eb.registerHandler("com.github.iyboklee.chat", new Handler<Message<String>>() {
			@Override
			public void handle(Message<String> message) {
				if (writeHandlerID != null) {
					byte[] data;
					try {
						data = message.body().getBytes("UTF-8");
						Buffer buffer = new Buffer(4+data.length);
						buffer.setInt(0, data.length);
						buffer.setBytes(4, data);
						eb.send(writeHandlerID, buffer);
					} catch (UnsupportedEncodingException e) {
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