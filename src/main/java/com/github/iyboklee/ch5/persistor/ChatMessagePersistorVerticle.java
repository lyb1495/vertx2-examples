/**
 * @Author iyboklee (iyboklee@gmail.com)
 */
package com.github.iyboklee.ch5.persistor;

import java.text.SimpleDateFormat;
import java.util.Date;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.eventbus.ReplyException;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.platform.Verticle;

public class ChatMessagePersistorVerticle extends Verticle {

	public static final String INTERNAL_CHAT_MESSAGE_PERSISTOR_ADDR = "com.github.iyboklee.chat.internal.persistor";
	
	public static final String MONGODB_REPOSITORY_TYPE = "mongodb";
	public static final String MONGODB_REPOSITORY_ADDR = "vertx.mongopersistor";
	public static final String MYSQL_REPOSITORY_TYPE = "mysql";
	public static final String MYSQL_REPOSITORY_ADDR = "vertx.mysqlpersistor";
	
	public static final int REPOSITORY_OP_TIMEOUT = 1000;
	
	private Logger logger;
	private EventBus eb;
	
	private String address;
	private String repositoryType;
	private String repositoryAddress;
	private int repositoryOpTimeout;
	
	private Handler<Message<JsonObject>> persistorHandler;
	
	SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	
	private JsonObject saveOp(JsonObject param) {
		JsonObject body = param.getObject("body");
		String rmID = param.getString("rmID");
		String username = body.getString("username");
		String message = body.getString("message");
		if (rmID != null && username != null && message != null) {
			JsonObject op = new JsonObject();
			if (MONGODB_REPOSITORY_TYPE.equalsIgnoreCase(repositoryType)) {
				op.putString("action", "save");
				op.putString("collection", "chat_messages");
				op.putObject("document", new JsonObject()
					.putString("username", username)
					.putString("message", message)
					.putString("rmID", rmID)
					.putString("regdate", sdf.format(new Date())));
				return op;
			}
			else if (MYSQL_REPOSITORY_TYPE.equalsIgnoreCase(repositoryType)) {
				op.putString("action", "prepared");
				op.putString("statement", "insert into chat_messages (username,rmID,message,regdate) values(?,?,?,CURRENT_TIMESTAMP())");
				JsonArray values = new JsonArray();
				values.addString(username);
				values.addString(rmID);
				values.addString(message);
				op.putArray("values", values);
				return op;
			}
		}
		return null;
	}
	
	private void persist(Message<JsonObject> message) {
		JsonObject op = saveOp(message.body());
		if (op != null) {
			eb.sendWithTimeout(repositoryAddress, op, repositoryOpTimeout, new Handler<AsyncResult<Message<JsonObject>>>() {
				@Override
				public void handle(AsyncResult<Message<JsonObject>> result) {
					if (result.succeeded()) {
						JsonObject reply = result.result().body();
						if (reply.getString("status").equals("error"))
							logger.error(reply.toString());
					}
					else {
						ReplyException cause = (ReplyException) result.cause();
						JsonObject error = new JsonObject().putString("address", address)
								.putNumber("failureCode", cause.failureCode())
								.putString("failureType", cause.failureType().name())
								.putString("message", cause.getMessage());
						logger.error(error);
					}
				}
			});
		}
	}
	
	@Override
	public void start() {
		JsonObject appConfig = container.config();
		
		logger = container.logger();
		eb = vertx.eventBus();
		
		address = appConfig.getString("address", INTERNAL_CHAT_MESSAGE_PERSISTOR_ADDR);
		repositoryType = appConfig.getString("repository_type", MONGODB_REPOSITORY_TYPE);
		repositoryAddress = appConfig.getString("repository_address", MONGODB_REPOSITORY_ADDR);
		repositoryOpTimeout = appConfig.getInteger("repository_op_timeout", REPOSITORY_OP_TIMEOUT);
		
		persistorHandler = new Handler<Message<JsonObject>>() {
			@Override
			public void handle(Message<JsonObject> message) {
				persist(message);
			}
		};
		eb.registerHandler(address, persistorHandler);
	}
}