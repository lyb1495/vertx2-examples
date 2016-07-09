/**
 * @Author iyboklee (iyboklee@gmail.com)
 */
package com.github.iyboklee.ch2;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.Handler;
import org.vertx.java.core.VoidHandler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.net.NetServer;
import org.vertx.java.core.net.NetSocket;
import org.vertx.java.platform.Verticle;

public class TCPChatServerVerticle extends Verticle {
	
	private Logger logger;
	private NetServer server;
	private List<NetSocket> sockets;

	@Override
	public void start() {
		logger = container.logger();
		server = vertx.createNetServer();
		sockets = new ArrayList<NetSocket>();

		server.connectHandler(new Handler<NetSocket>() {
			@Override
			public void handle(final NetSocket socket) {
				logger.info("Thread ["+Thread.currentThread().getId()+"] client connected: "+socket.remoteAddress());
				sockets.add(socket);
				//-- this handler will be called every time data is received on the socket
				socket.dataHandler(new Handler<Buffer>() {
					@Override
					public void handle(Buffer buffer) {
						logger.info("Thread ["+Thread.currentThread().getId()+"] received data: "+buffer.toString());
						for (NetSocket s : sockets) {
							if (!socket.equals(s)) {
								logger.info("Thread ["+Thread.currentThread().getId()+"] send data: "+buffer.toString());
								s.write(buffer);
							}
						}
					}
				});
				//-- socket closed
				socket.closeHandler(new VoidHandler() {
					@Override
					protected void handle() {
						logger.info("connection closed: "+socket.remoteAddress());
						Iterator<NetSocket> it = sockets.iterator();
						while (it.hasNext()) {
							if (socket.equals(it.next())) {
								it.remove();
								break;
							}
						}
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
		});

		server.listen(8090, "localhost", new AsyncResultHandler<NetServer>() {
			@Override
			public void handle(AsyncResult<NetServer> asyncResult) {
				logger.info("bind result: "+asyncResult.succeeded());
			}
		});
	}

	@Override
	public void stop() {
		if (server != null)
			server.close();
	}
	
}