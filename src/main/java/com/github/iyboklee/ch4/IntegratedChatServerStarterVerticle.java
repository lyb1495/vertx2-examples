/**
 * @Author iyboklee (iyboklee@gmail.com)
 */
package com.github.iyboklee.ch4;

import org.vertx.java.platform.Verticle;

public class IntegratedChatServerStarterVerticle extends Verticle {

	@Override
	public void start() {
		container.deployVerticle("com.github.iyboklee.ch4.IntegratedSockJSEbbChatServerVerticle");
		container.deployVerticle("com.github.iyboklee.ch4.IntegratedTCPChatServerVerticle");
	}
	
}