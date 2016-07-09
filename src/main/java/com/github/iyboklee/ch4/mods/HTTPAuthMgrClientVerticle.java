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

public class HTTPAuthMgrClientVerticle extends Verticle {

	private final static String AUTH_MGR_ADDRESS_PREFIX = "vertx.basicauthmanager";
	
	private Logger logger;
	private EventBus eb;
	HttpServer httpServer;
	
	private void deployAuthMgr() {
		JsonObject authMgrConfig = new JsonObject();
		authMgrConfig.putString("address", AUTH_MGR_ADDRESS_PREFIX);
		authMgrConfig.putString("user_collection", "users");
		authMgrConfig.putString("persistor_address", "vertx.mongopersistor");
		authMgrConfig.putNumber("session_timeout", 1800000); // --30min			
		container.deployModule("io.vertx~mod-auth-mgr~2.0.0-final", authMgrConfig);
				
		JsonObject mongoPersistorConfig = new JsonObject();
		mongoPersistorConfig.putString("address", "vertx.mongopersistor");
		mongoPersistorConfig.putString("host", "localhost");
		mongoPersistorConfig.putNumber("port", 27017);
		mongoPersistorConfig.putString("db_name", "my-chat");
		mongoPersistorConfig.putNumber("pool_size", 10);
		container.deployModule("io.vertx~mod-mongo-persistor~2.1.0", mongoPersistorConfig);
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
					String address = null;
					//-- login request
					//-- {"username": "myid","password": "1234"}
					if ("/login".equals(request.path())) {
						address = AUTH_MGR_ADDRESS_PREFIX+".login";
					}
					//-- logout request
					//-- {"sessionID":"28488cf5-c909-4e99-a134c-7a25d3362986"}
					else if ("/logout".equals(request.path())) {
						address = AUTH_MGR_ADDRESS_PREFIX+".logout";
					}
					//-- authorise request
					//-- {"sessionID":"28488cf5-c909-4e99-a134c-7a25d3362986"}
					else if ("/authorise".equals(request.path())) {
						address = AUTH_MGR_ADDRESS_PREFIX+".authorise";
					}
					else {
						request.response().setStatusCode(404).end("Not Found Page");
					}
					if (address != null) {
						eb.send(address, new JsonObject(buffer.toString()), new Handler<Message<JsonObject>>() {
							@Override
							public void handle(Message<JsonObject> reply) {
								request.response().setStatusCode(200).end(reply.body().toString());
							}
						});
					}
				}
			});
		}
	};
	
	@Override
	public void start() {
		logger = container.logger();
		eb = vertx.eventBus();
		httpServer = vertx.createHttpServer();
		
		deployAuthMgr();
		httpServer.requestHandler(requestHandler);	
		
		httpServer.listen(80, new Handler<AsyncResult<HttpServer>>() {
			@Override
			public void handle(AsyncResult<HttpServer> asyncResult) {
				logger.info("bind result: "+asyncResult.succeeded());
			}
		});
	}
	
}