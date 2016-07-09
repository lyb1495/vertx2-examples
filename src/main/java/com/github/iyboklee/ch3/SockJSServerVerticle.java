/**
 * @Author iyboklee (iyboklee@gmail.com)
 */
package com.github.iyboklee.ch3;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpServer;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.sockjs.SockJSServer;
import org.vertx.java.core.sockjs.SockJSSocket;
import org.vertx.java.platform.Verticle;

public class SockJSServerVerticle extends Verticle {

	private Logger logger;
	
	@Override
	public void start() {
		logger = container.logger();
		HttpServer httpServer = vertx.createHttpServer();
		
		httpServer.requestHandler(new Handler<HttpServerRequest>() {
			@Override
			public void handle(HttpServerRequest request) {
				String method = request.method();
				String uri = request.uri();
				String path = request.path();
				String query = request.query();
				logger.info("received http request: {method="+method+", uri="+uri+", path="+path+", query="+query+"}");
				
				request.bodyHandler(new Handler<Buffer>() {
					@Override
					public void handle(Buffer buffer) {
						logger.info("received data: "+buffer.toString());
					}
				});		
				
				request.response().setStatusCode(200).end("OK");
			}
		});
		
		SockJSServer sockJSServer = vertx.createSockJSServer(httpServer);
		
		JsonObject config = new JsonObject();
		config.putString("prefix", "/mySockJS");
		
		//-- this handler will be called when new SockJS sockets are created.
		sockJSServer.installApp(config, new Handler<SockJSSocket>() {
			@Override
			public void handle(final SockJSSocket sockJSSocket) {
				//-- this handler will be called every time data is received on the sockJSSocket
				sockJSSocket.dataHandler(new Handler<Buffer>() {
					@Override
					public void handle(Buffer buffer) {
						logger.info("received data: "+buffer.toString());
						sockJSSocket.write(buffer);
					}
				});
				//-- something went wrong
				sockJSSocket.exceptionHandler(new Handler<Throwable>() {
					@Override
					public void handle(Throwable throwable) {
						logger.error("unexpected exception: ", throwable);
					}
				});
			}
		});
		
		httpServer.listen(8080, new Handler<AsyncResult<HttpServer>>() {
			@Override
			public void handle(AsyncResult<HttpServer> asyncResult) {
				logger.info("bind result: "+asyncResult.succeeded());
			}
		});
	}
}