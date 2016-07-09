/**
 * @Author iyboklee (iyboklee@gmail.com)
 */
package com.github.iyboklee.ch4.mods;

import java.util.Map;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;
import org.vertx.java.core.MultiMap;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.http.HttpServer;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.platform.Verticle;

public class HTTPMongoPersistorClientVerticle extends Verticle {

	private final static String MONGO_PERSISTOR_ADDRESS = "vertx.mongopersistor";
	
	private Logger logger;
	private EventBus eb;
	HttpServer httpServer;
	
	private void deployMongoPersistor() {
		JsonObject mongoPersistorConfig = new JsonObject();
		mongoPersistorConfig.putString("address", MONGO_PERSISTOR_ADDRESS);
		mongoPersistorConfig.putString("host", "localhost");
		mongoPersistorConfig.putNumber("port", 27017);
		mongoPersistorConfig.putString("db_name", "my-chat");
		mongoPersistorConfig.putNumber("pool_size", 10);
		
		container.deployModule("io.vertx~mod-mongo-persistor~2.1.0", mongoPersistorConfig);
	}
	
	private String getParamValue(MultiMap params, String name) {
		for (Map.Entry<String, String> entry : params.entries()) {
			if (entry.getKey().equals(name))
				return entry.getValue();
		}
		return null;
	}
	
	private JsonObject find(String username) {
		JsonObject q = new JsonObject();
		q.putString("action", "find");
		q.putString("collection", "users");
		q.putObject("matcher", new JsonObject().putString("username", username));
		return q;
	}
	
	private JsonObject save(String username, String password) {
		JsonObject data = new JsonObject();
		data.putString("action", "save");
		data.putString("collection", "users");
		data.putObject("document", new JsonObject()
			.putString("username", username).putString("password", password));
		return data;
	}
	
	private JsonObject update(String username, String password) {
		JsonObject data = new JsonObject();
		data.putString("action", "update");
		data.putString("collection", "users");
		data.putObject("criteria", new JsonObject().putString("username", username));
		data.putObject("objNew", new JsonObject()
			.putObject("$set", new JsonObject().putString("password", password)));
		data.putBoolean("upsert", false);
		data.putBoolean("upsert", false);
		return data;
	}
	
	private JsonObject delete(String username) {
		JsonObject q = new JsonObject();
		q.putString("action", "delete");
		q.putString("collection", "users");
		q.putObject("matcher", new JsonObject().putString("username", username));
		return q;
	}
	
	final Handler<HttpServerRequest> requestHandler = new Handler<HttpServerRequest>() {
		@Override
		public void handle(final HttpServerRequest request) {
			String path = request.path();
			String method = request.method();
			MultiMap params = request.params();
			
			if ( !"/users".equals(path) )
				request.response().setStatusCode(404).end("Not Found Page");
			
			JsonObject mongoOp = null;
			
			if ("GET".equals(method)) {
				String username = getParamValue(params, "username");
				if (username != null)
					mongoOp = find(username);
			}
			else if ("POST".equals(method)) {
				String username = getParamValue(params, "username");
				String password = getParamValue(params, "password");
				if (username!=null &&password!=null)
					mongoOp = save(username, password);
			}
			else if ("PUT".equals(method)) {
				String username = getParamValue(params, "username");
				String password = getParamValue(params, "password");
				if (username!=null &&password!=null)
					mongoOp = update(username, password);
			}
			else if ("DELETE".equals(method)) {
				String username = getParamValue(params, "username");
				if (username != null)
					mongoOp = delete(username);
			}
			else {
				request.response().setStatusCode(405).end("Method Not Allowed");
			}
			if (mongoOp !=null) {
				eb.send(MONGO_PERSISTOR_ADDRESS, mongoOp, new Handler<Message<JsonObject>>() {
					@Override
					public void handle(Message<JsonObject> reply) {
						request.response().setStatusCode(200).end(reply.body().toString());
					}
				});
			}
		}
	};
	
	@Override
	public void start() {
		logger = container.logger();
		eb = vertx.eventBus();
		httpServer = vertx.createHttpServer();
		
		deployMongoPersistor();
		httpServer.requestHandler(requestHandler);	
		
		httpServer.listen(80, new Handler<AsyncResult<HttpServer>>() {
			@Override
			public void handle(AsyncResult<HttpServer> asyncResult) {
				logger.info("bind result: "+asyncResult.succeeded());
			}
		});
	}
}