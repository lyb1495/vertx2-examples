/**
 * @Author iyboklee (iyboklee@gmail.com)
 */
package com.github.iyboklee.ch3;

import org.vertx.java.core.json.JsonObject;
import org.vertx.java.platform.Verticle;

public class SockJSEbbChatWithLoginServerStarterVerticle extends Verticle {
	
	@Override
	public void start() {
		container.deployVerticle("com.github.iyboklee.ch3.SockJSEbbChatWithLoginServerVerticle");
		
		JsonObject authMgrConfig = new JsonObject();
		authMgrConfig.putString("address", "vertx.basicauthmanager");
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
	
}