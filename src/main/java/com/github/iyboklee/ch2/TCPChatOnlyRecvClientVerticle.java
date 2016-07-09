/**
 * @Author iyboklee (iyboklee@gmail.com)
 */
package com.github.iyboklee.ch2;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;
import org.vertx.java.core.VoidHandler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.net.NetClient;
import org.vertx.java.core.net.NetSocket;
import org.vertx.java.platform.Verticle;

public class TCPChatOnlyRecvClientVerticle extends Verticle {
	
	private Logger logger;
	private NetClient client;

	@Override
	public void start() {
		logger = container.logger();
		client = vertx.createNetClient();

		client.connect(8090, "localhost", new Handler<AsyncResult<NetSocket>>() {
			@Override
			public void handle(AsyncResult<NetSocket> asyncResult) {
				logger.info("connect result: "+asyncResult.succeeded());

				if (asyncResult.succeeded()) {
					final NetSocket socket = asyncResult.result();
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
	}

	@Override
	public void stop() {
		if (client != null)
			client.close();
	}
	
}