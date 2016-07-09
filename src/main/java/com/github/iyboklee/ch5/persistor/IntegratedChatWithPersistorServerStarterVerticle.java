/**
 * @Author iyboklee (iyboklee@gmail.com)
 */
package com.github.iyboklee.ch5.persistor;

import org.vertx.java.core.json.JsonObject;
import org.vertx.java.platform.Verticle;

public class IntegratedChatWithPersistorServerStarterVerticle extends Verticle {

	@Override
	public void start() {
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
		
		/*JsonObject mysqlConf = new JsonObject();
		mysqlConf.putString("address", "vertx.mysqlpersistor");
	    mysqlConf.putString("connection", "MySQL");
	    mysqlConf.putString("host", "localhost");
	    mysqlConf.putNumber("port", 3306);
	    mysqlConf.putString("username", "root");
	    mysqlConf.putString("password", "mypassword");
	    mysqlConf.putString("database", "mydb");
		container.deployModule("io.vertx~mod-mysql-postgresql_2.10~0.3.1", mysqlConf);*/
		
		JsonObject chatMsgPersistor = new JsonObject();
		chatMsgPersistor.putString("address", "com.github.iyboklee.chat.internal.persistor");
		chatMsgPersistor.putString("repository_type", "mongodb");
		//chatMsgPersistor.putString("repository_type", "mysql");
		chatMsgPersistor.putString("repository_address", "vertx.mongopersistor");
		//chatMsgPersistor.putString("repository_address", "vertx.mysqlpersistor");
	    chatMsgPersistor.putNumber("repository_op_timeout", 1000);
		container.deployVerticle("com.github.iyboklee.ch5.persistor.ChatMessagePersistorVerticle", chatMsgPersistor);
		
		container.deployVerticle("com.github.iyboklee.ch5.persistor.IntegratedSockJSEbbChatWithPersistorServerVerticle");
		container.deployVerticle("com.github.iyboklee.ch5.persistor.IntegratedTCPChatWithPersistorServerVerticle");
	}
	
}