/**
 * @Author iyboklee (iyboklee@gmail.com)
 */
package com.github.iyboklee.ch2;

import java.util.Iterator;
import java.util.Set;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.Handler;
import org.vertx.java.core.VoidHandler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.net.NetServer;
import org.vertx.java.core.net.NetSocket;
import org.vertx.java.core.parsetools.RecordParser;
import org.vertx.java.platform.Verticle;

public class TCPChatWithFramingServerVerticle extends Verticle {
	
	private Logger logger;
	private EventBus eb;
	private NetServer server;
	private Set<String> sockets;

	@Override
	public void start() {
		logger = container.logger();
		eb = vertx.eventBus();
		server = vertx.createNetServer();
		sockets = vertx.sharedData().getSet("sockets");

		server.connectHandler(new Handler<NetSocket>() {
			@Override
			public void handle(final NetSocket socket) {
				sockets.add(socket.writeHandlerID());
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
							Buffer newbuf = new Buffer(4+payloadLength);
							newbuf.setInt(0, payloadLength);
							newbuf.setBuffer(4, buffer);
							for (String s : sockets) {
								if (!socket.writeHandlerID().equals(s))
									eb.send(s, newbuf);
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
						Iterator<String> it = sockets.iterator();
						while (it.hasNext()) {
							if (socket.writeHandlerID().equals(it.next())) {
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