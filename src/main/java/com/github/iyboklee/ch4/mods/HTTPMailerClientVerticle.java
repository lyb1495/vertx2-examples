/**
 * @Author iyboklee (iyboklee@gmail.com)
 */
package com.github.iyboklee.ch4.mods;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.http.HttpServer;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.platform.Verticle;

public class HTTPMailerClientVerticle extends Verticle {

	private final static String MAILER_ADDRESS = "vertx.mailer";
	
	private Logger logger;
	private EventBus eb;
	HttpServer httpServer;
	
	private void deployMailer() {
		JsonObject mailerConf = new JsonObject();
		mailerConf.putString("address", MAILER_ADDRESS);
		mailerConf.putString("host", "smtp.googlemail.com");
		mailerConf.putNumber("port", 465);
		mailerConf.putBoolean("ssl", true);
		mailerConf.putBoolean("auth", true);
		mailerConf.putString("username", "yourmail@gmail.com");
		mailerConf.putString("password", "yourpassword");
		mailerConf.putString("content_type", "text/plain");
				
		container.deployModule("io.vertx~mod-mailer~2.0.0-final", mailerConf);
	}
	
	final Handler<HttpServerRequest> requestHandler = new Handler<HttpServerRequest>() {
		@Override
		public void handle(final HttpServerRequest request) {
			if (!"POST".equals(request.method())) {
				request.response().setStatusCode(405).end("Method Not Allowed");
				return;
			}
			request.bodyHandler(new Handler<Buffer>() {
				@Override
				public void handle(Buffer buffer) {
					logger.info("received data: "+buffer.toString());
					JsonObject params = new JsonObject(buffer.toString());
					
					JsonObject mail = new JsonObject();
					mail.putString("from", "devop84@gmail.com");
					mail.putString("to", params.getString("to"));
					mail.putString("subject", params.getString("subject"));
					mail.putString("body", params.getString("body"));
				
					eb.send(MAILER_ADDRESS, mail, new Handler<Message<JsonObject>>() {
						@Override
						public void handle(Message<JsonObject> reply) {
							request.response().setStatusCode(200).end(reply.body().toString());
						}
					});
				}
			});
		}
	};
	
	@Override
	public void start() {
		logger = container.logger();
		eb = vertx.eventBus();
		httpServer = vertx.createHttpServer();
		
		deployMailer();
		httpServer.requestHandler(requestHandler);	
		
		httpServer.listen(80, new Handler<AsyncResult<HttpServer>>() {
			@Override
			public void handle(AsyncResult<HttpServer> asyncResult) {
				logger.info("bind result: "+asyncResult.succeeded());
			}
		});
	}
}