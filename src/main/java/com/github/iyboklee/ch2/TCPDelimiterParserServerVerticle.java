/**
 * @Author iyboklee (iyboklee@gmail.com)
 */
package com.github.iyboklee.ch2;

import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.net.NetServer;
import org.vertx.java.core.net.NetSocket;
import org.vertx.java.core.parsetools.RecordParser;
import org.vertx.java.platform.Verticle;

public class TCPDelimiterParserServerVerticle extends Verticle {
   
	private Logger logger;
	
	@Override
	public void start() {
		logger = container.logger();
		NetServer netServer = vertx.createNetServer();
		
		netServer.connectHandler(new Handler<NetSocket>() {
			@Override
			public void handle(NetSocket socket) {
				socket.dataHandler(RecordParser.newDelimited("\n", new Handler<Buffer>() {
					@Override
					public void handle(Buffer buffer) {
						logger.info("received data: "+buffer.toString());
					}
				}));
			}
		});
		
		netServer.listen(8090, "localhost");
	}
	
}