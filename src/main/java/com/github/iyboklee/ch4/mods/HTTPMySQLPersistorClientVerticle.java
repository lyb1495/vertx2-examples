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
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.platform.Verticle;

public class HTTPMySQLPersistorClientVerticle extends Verticle {

	private final static String MYSQL_PERSISTOR_ADDRESS = "vertx.mysql.persistor";
	
	private Logger logger;
	private EventBus eb;
	HttpServer httpServer;
	
	private void deployMySQLPersistor() {
		JsonObject mysqlConf = new JsonObject();
		mysqlConf.putString("address", MYSQL_PERSISTOR_ADDRESS);
	    mysqlConf.putString("connection", "MySQL");
	    mysqlConf.putString("host", "localhost");
	    mysqlConf.putNumber("port", 3306);
	    mysqlConf.putString("username", "root");
	    mysqlConf.putString("password", "mypassword");
	    mysqlConf.putString("database", "mydb");
				
		container.deployModule("io.vertx~mod-mysql-postgresql_2.10~0.3.1", mysqlConf);
	}
	
	private String getParamValue(MultiMap params, String name) {
		for (Map.Entry<String, String> entry : params.entries()) {
			if (entry.getKey().equals(name))
				return entry.getValue();
		}
		return null;
	}
	
	private JsonObject select(String username) {
		JsonObject sql = new JsonObject();
		sql.putString("action", "prepared");
		sql.putString("statement", "select * from users where username=?");
		JsonArray values = new JsonArray();
		values.addString(username);
		sql.putArray("values", values);
		return sql;
	}
	
	private JsonObject insert(String username, String password) {
		JsonObject sql = new JsonObject();
		sql.putString("action", "prepared");
		sql.putString("statement", "insert into users values(?,?)");
		JsonArray values = new JsonArray();
		values.addString(username);
		values.addString(password);
		sql.putArray("values", values);
		return sql;
	}
	
	private JsonObject update(String username, String password) {
		JsonObject sql = new JsonObject();
		sql.putString("action", "raw");
		sql.putString("command", "update users set password='"+password+"' where username='"+username+"'");
		return sql;
	}
	
	private JsonObject delete(String username) {
		JsonObject sql = new JsonObject();
		sql.putString("action", "raw");
		sql.putString("command", "delete from users where username='"+username+"'");
		return sql;
	}
	
	final Handler<HttpServerRequest> requestHandler = new Handler<HttpServerRequest>() {
		@Override
		public void handle(final HttpServerRequest request) {
			String path = request.path();
			String method = request.method();
			MultiMap params = request.params();
			
			if ( !"/users".equals(path) )
				request.response().setStatusCode(404).end("Not Found Page");
			
			JsonObject mysqlOp = null;
			
			if ("GET".equals(method)) {
				String username = getParamValue(params, "username");
				if (username != null)
					mysqlOp = select(username);
			}
			else if ("POST".equals(method)) {
				String username = getParamValue(params, "username");
				String password = getParamValue(params, "password");
				if (username!=null &&password!=null)
					mysqlOp = insert(username, password);
			}
			else if ("PUT".equals(method)) {
				String username = getParamValue(params, "username");
				String password = getParamValue(params, "password");
				if (username!=null &&password!=null)
					mysqlOp = update(username, password);
			}
			else if ("DELETE".equals(method)) {
				String username = getParamValue(params, "username");
				if (username != null)
					mysqlOp = delete(username);
			}
			else {
				request.response().setStatusCode(405).end("Method Not Allowed");
			}
			if (mysqlOp !=null) {
				eb.send(MYSQL_PERSISTOR_ADDRESS, mysqlOp, new Handler<Message<JsonObject>>() {
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
		
		deployMySQLPersistor();
		httpServer.requestHandler(requestHandler);	
		
		httpServer.listen(80, new Handler<AsyncResult<HttpServer>>() {
			@Override
			public void handle(AsyncResult<HttpServer> asyncResult) {
				logger.info("bind result: "+asyncResult.succeeded());
			}
		});
	}
}