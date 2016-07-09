/**
 * @Author iyboklee (iyboklee@gmail.com)
 */
package com.github.iyboklee.ch4;

import java.io.File;
import java.net.URISyntaxException;
import java.util.Iterator;
import java.util.Map;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.http.HttpServer;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.sockjs.EventBusBridgeHook;
import org.vertx.java.core.sockjs.SockJSServer;
import org.vertx.java.core.sockjs.SockJSSocket;
import org.vertx.java.platform.Verticle;

public class IntegratedSockJSEbbChatServerVerticle extends Verticle {

	public static final String INTERNAL_TCP_SERV_ADDR 	 = "com.github.iyboklee.chat.internal.tcp";
	public static final String INTERNAL_SOCKJS_SERV_ADDR = "com.github.iyboklee.chat.internal.sockjs";
	
	private Logger logger;
	private EventBus eb;
	private HttpServer httpServer;
	private Map<String, Integer> addresses;
	private int port;
	private String origin;
	
	private String getClasspathResourceFile(String filepath) {
		try {
			File file = new File(getClass().getResource(filepath).toURI());
			return file.toString();
		} catch (URISyntaxException e) {
		}
		return null;
	}
	
	@Override
	public void start() {
		JsonObject appConfig = container.config();
		
		logger = container.logger();
		eb = vertx.eventBus();
		httpServer = vertx.createHttpServer();
		addresses = vertx.sharedData().getMap("addresses");
		port = appConfig.getInteger("port", 80);
		origin = appConfig.getString("origin", "http://localhost");
		
		httpServer.requestHandler(new Handler<HttpServerRequest>() {
			final String indexHtml = getClasspathResourceFile("/com/github/iyboklee/ch4/index.html");
			final String vertxBusJs = getClasspathResourceFile("/com/github/iyboklee/ch4/vertxbus-2.1.js");
			
			@Override
			public void handle(HttpServerRequest request) {
				if (request.path().equals("/")) request.response().sendFile(indexHtml);
				else if (request.path().endsWith("vertxbus-2.1.js")) request.response().sendFile(vertxBusJs);
				else request.response().setStatusCode(404).end("Not Found Page");
			}
		});
		
		SockJSServer sockJSServer = vertx.createSockJSServer(httpServer);
		
		JsonObject sockJSconfig = new JsonObject();
		sockJSconfig.putString("prefix", appConfig.getString("prefix", "/mySockJS"));
		
		//-- bridge hook
		sockJSServer.setHook(new EventBusBridgeHook() {
			@Override
			public boolean handleSocketCreated(SockJSSocket sock) {
				if (origin!=null) {
					String originHeader = sock.headers().get("origin");
					if (originHeader == null || !originHeader.equals(origin)) {
						return false; //-- reject the socket
					}
				}
				return true; //-- true to accept the socket, false to reject it
			}
			
			@Override
			public void handleSocketClosed(SockJSSocket sock) {
				logger.info("handleSocketClosed, sock = "+sock);
			}
			
			@Override
			public boolean handleSendOrPub(SockJSSocket sock, boolean send, JsonObject msg, String address) {
				logger.info("handleSendOrPub, sock = "+sock+", send = "+send+", address = "+address);
				//-- send to tcp server
				eb.send(INTERNAL_TCP_SERV_ADDR, msg.getString("body"));
				return true; //-- true To allow the send/publish to occur, false otherwise
			}
			
			@Override
			public boolean handlePreRegister(SockJSSocket sock, String address) {
				logger.info("handlePreRegister, sock = "+sock+", address = "+address);
				return true; //-- true to let the registration occur, false otherwise
			}

			@Override
			public void handlePostRegister(SockJSSocket sock, String address) {
				logger.info("handlePostRegister, sock = "+sock+", address = "+address);
				Integer counter = addresses.get(address);
				if (counter == null) {
					counter = 1;
				}
				else {
					++counter;
				}
				logger.info("address: "+address+", counter: "+counter);
				addresses.put(address, counter);
			}

			@Override
			public boolean handleUnregister(SockJSSocket sock, String address) {
				logger.info("handleUnregister, sock = "+sock+", address = "+address);
				Integer counter = addresses.get(address);
				if (counter != null) {
					if (--counter == 0) {
						addresses.remove(address);
					}
					else {
						addresses.put(address, counter);
					}
				}
				logger.info("address: "+address+", counter: "+counter);
				return true;
			}

			@Override
			public boolean handleAuthorise(JsonObject message, String sessionID, Handler<AsyncResult<Boolean>> handler) {
				return false; //-- true if you wish to override authorisation
			}
		});
		
		//-- bridge perms(allow everytings)
		JsonArray permitted = new JsonArray();
		permitted.add(new JsonObject());
		
		sockJSServer.bridge(sockJSconfig, permitted, permitted);
		
		httpServer.listen(port, new Handler<AsyncResult<HttpServer>>() {
			@Override
			public void handle(AsyncResult<HttpServer> asyncResult) {
				logger.info("bind result: "+asyncResult.succeeded());
			}
		});
		
		//-- event bus subscribe
		eb.registerHandler(INTERNAL_SOCKJS_SERV_ADDR, new Handler<Message<Buffer>>() {
			@Override
			public void handle(final Message<Buffer> message) {
				Iterator<String> keys = addresses.keySet().iterator();
				while (keys.hasNext()) {
					eb.publish(keys.next(), message.body().toString());
				}
			}
		});
	}
	
	@Override
	public void stop() {
		if (httpServer != null)
			httpServer.close();
	}
	
}