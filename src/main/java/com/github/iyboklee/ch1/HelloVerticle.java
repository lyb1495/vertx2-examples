/**
 * @Author iyboklee (iyboklee@gmail.com)
 */
package com.github.iyboklee.ch1;

import org.vertx.java.core.logging.Logger;
import org.vertx.java.platform.Verticle;

public class HelloVerticle extends Verticle {
	
	private Logger logger;

	@Override
	public void start() {
		logger = container.logger();
		logger.info("hello, world");
	}
	
}