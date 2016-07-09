/**
 * @Author iyboklee (iyboklee@gmail.com)
 */
package com.github.iyboklee.ch3;

import java.io.File;
import java.net.URISyntaxException;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;
import org.vertx.java.core.http.HttpServer;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.sockjs.EventBusBridgeHook;
import org.vertx.java.core.sockjs.SockJSServer;
import org.vertx.java.core.sockjs.SockJSSocket;
import org.vertx.java.platform.Verticle;

public class SockJSEbbChatWithLoginServerVerticle extends Verticle {

	private Logger logger;
	private HttpServer httpServer;
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
		httpServer = vertx.createHttpServer();
		port = appConfig.getInteger("port", 80);
		origin = appConfig.getString("origin", "http://localhost");

		httpServer.requestHandler(new Handler<HttpServerRequest>() {
			final String indexHtml = getClasspathResourceFile("/com/github/iyboklee/ch3/index_login.html");
			final String vertxBusJs = getClasspathResourceFile("/com/github/iyboklee/ch3/vertxbus-2.1.js");

			@Override
			public void handle(HttpServerRequest request) {
				if (request.path().equals("/")) request.response().sendFile(indexHtml);
				else if (request.path().endsWith("vertxbus-2.1.js")) request.response().sendFile(vertxBusJs);
				else request.response().setStatusCode(404).end("Not Found Page");
			}
		});

		SockJSServer sockJSServer = vertx.createSockJSServer(httpServer);

		// -- bridge hook
		sockJSServer.setHook(new EventBusBridgeHook() {
			@Override
			public boolean handleSocketCreated(SockJSSocket sock) {
				if (origin != null) {
					String originHeader = sock.headers().get("origin");
					if (originHeader == null || !originHeader.equals(origin)) {
						return false; // -- reject the socket
					}
				}
				return true; // -- true to accept the socket, false to reject it
			}

			@Override
			public void handleSocketClosed(SockJSSocket sock) {
				logger.info("handleSocketClosed, sock = "+sock);
			}

			@Override
			public boolean handleSendOrPub(SockJSSocket sock, boolean send, JsonObject msg, String address) {
				logger.info("handleSendOrPub, sock = "+sock+", send = "+send+", address = "+address);
				return true; // -- true To allow the send/publish to occur, false otherwise
			}

			@Override
			public boolean handlePreRegister(SockJSSocket sock, String address) {
				logger.info("handlePreRegister, sock = "+sock+", address = "+address);
				return true; // -- true to let the registration occur, false otherwise
			}

			@Override
			public void handlePostRegister(SockJSSocket sock, String address) {
				logger.info("handlePostRegister, sock = "+sock+", address = "+address);
			}

			@Override
			public boolean handleUnregister(SockJSSocket sock, String address) {
				logger.info("handleUnregister, sock = "+sock+", address = "+address);
				return true;
			}

			@Override
			public boolean handleAuthorise(JsonObject message, String sessionID, Handler<AsyncResult<Boolean>> handler) {
				return false; // -- true if you wish to override authorisation
			}
		});

		JsonObject sockJSconfig = new JsonObject();
		sockJSconfig.putString("prefix", appConfig.getString("prefix", "/mySockJS"));

		JsonArray inbound = new JsonArray();
		inbound.add(new JsonObject().putString("address", "vertx.basicauthmanager.login"));
		inbound.add(new JsonObject().putBoolean("requires_auth", true));

		JsonArray outbound = new JsonArray();
		outbound.add(new JsonObject());
		
		JsonObject bridgeConfig = new JsonObject();
		bridgeConfig.putString("auth_address", "vertx.basicauthmanager.authorise");
		bridgeConfig.putNumber("auth_timeout", 5 * 60 * 1000);

		sockJSServer.bridge(sockJSconfig, inbound, outbound, bridgeConfig);

		httpServer.listen(port, new Handler<AsyncResult<HttpServer>>() {
			@Override
			public void handle(AsyncResult<HttpServer> asyncResult) {
				logger.info("bind result: "+asyncResult.succeeded());
			}
		});
	}

	@Override
	public void stop() {
		if (httpServer != null)
			httpServer.close();
	}

}