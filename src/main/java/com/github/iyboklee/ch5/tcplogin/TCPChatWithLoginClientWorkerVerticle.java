/**
 * @Author iyboklee (iyboklee@gmail.com)
 */
package com.github.iyboklee.ch5.tcplogin;

import java.io.Console;

import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.platform.Verticle;

public class TCPChatWithLoginClientWorkerVerticle extends Verticle {

	private EventBus eb;
	private boolean readline;
	
	private boolean isLogin = false;
	private String username;
	private String password;
	
	private void inputDelay(long dely) {
		try {
			Thread.sleep(dely);
		} catch (InterruptedException e) {
		}
	}
	
	@Override
	public void start() {
		eb = vertx.eventBus();
		readline = true;
		
		Console console = System.console();
		if (console != null) {
			inputDelay(500);
			while (readline) {
				JsonObject input = null;
				if (isLogin) {
					String line = console.readLine();
					if (line.equals("exit")) {
						readline = false;
						input = new JsonObject();
						input.putBoolean("exit", true);
					}
					else if (line.length() > 0) {
						input = new JsonObject();
						input.putObject("body", new JsonObject().putString("message", line));
					}	
				}
				else {
					isLogin = true;
					System.out.print("input username: ");
					username = console.readLine();
					System.out.print("input password: ");
					password = console.readLine();
					
					input = new JsonObject();
					input.putString("username", username);
					input.putString("password", password);
				}
				
				if (input != null)
					eb.send("com.github.iyboklee.chat", input);
			}
		}
	}
	
	@Override
	public void stop() {
		readline = false;
	}
	
}