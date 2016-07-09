/**
 * @Author iyboklee (iyboklee@gmail.com)
 */
package com.github.iyboklee.ch2;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;
import org.vertx.java.core.VoidHandler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.net.NetClient;
import org.vertx.java.core.net.NetSocket;
import org.vertx.java.platform.Verticle;

public class TCPChatClientVerticle extends Verticle {

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
					//-- this handler will be called every time data is received on the socket
					socket.dataHandler(new Handler<Buffer>() {
						@Override
						public void handle(Buffer buffer) {
							logger.info("received data: "+buffer.toString());
						}
					});
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
				if (writeHandlerID != null)
					eb.send(writeHandlerID, new Buffer(message.body()));
			}
		});
	}

	@Override
	public void stop() {
		if (client != null)
			client.close();
	}

}