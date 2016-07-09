/**
 * @Author iyboklee (iyboklee@gmail.com)
 */
package com.github.iyboklee.ch2;

import java.io.Console;

import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.platform.Verticle;

public class TCPChatClientWorkerVerticle extends Verticle {

	private EventBus eb;
	private boolean readline;
	
	@Override
	public void start() {
		eb = vertx.eventBus();
		readline = true;
		
		Console console = System.console();
		if (console != null) {
			while (readline) {
				String line = console.readLine();
				if (line.equals("exit"))
					readline = false;
				else
					eb.send("com.github.iyboklee.chat", line);
			}
		}
	}
	
	@Override
	public void stop() {
		readline = false;
	}
	
}