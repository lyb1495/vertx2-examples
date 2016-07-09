/**
 * @Author iyboklee (iyboklee@gmail.com)
 */
package com.github.iyboklee.ch2;

import org.vertx.java.platform.Verticle;

public class TCPChatClientStarterVerticle extends Verticle {

	@Override
	public void start() {
		//container.deployVerticle("com.github.iyboklee.ch2.TCPChatClientVerticle");
		container.deployVerticle("com.github.iyboklee.ch2.TCPChatWithFramingServerVerticle");
		container.deployWorkerVerticle("com.github.iyboklee.ch2.TCPChatClientWorkerVerticle");
	}
}