/**
 * @Author iyboklee (iyboklee@gmail.com)
 */
package com.github.iyboklee.ch3;

import java.util.List;
import java.util.Map;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpServer;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.platform.Verticle;

public class HTTPServerVerticle extends Verticle {

	private Logger logger;
	
	@Override
	public void start() {
		logger = container.logger();
		HttpServer httpServer = vertx.createHttpServer();
		
		//-- When a request arrives, the request handler is called passing in an instance of HttpServerRequest.
		//-- The handler is called when the headers of the request have been fully read.
		//-- This is because the body may be very large and we don't want to create problems with exceeding available memory.
		httpServer.requestHandler(new Handler<HttpServerRequest>() {
			@Override
			public void handle(HttpServerRequest request) {
				String method = request.method();
				String uri = request.uri();
				String path = request.path();
				String query = request.query();
				logger.info("received http request: {method="+method+", uri="+uri+", path="+path+", query="+query+"}");
				
				//-- Http request params
				List<Map.Entry<String, String>> params = request.params().entries();
				for (Map.Entry<String, String> param : params) {
					logger.info("param["+param.getKey()+"] = "+param.getValue());
				}
				
				//-- Http request headers
				List<Map.Entry<String, String>> headers = request.headers().entries();
				for (Map.Entry<String, String> header : headers) {
					logger.info("header["+header.getKey()+"] = "+header.getValue());
				}
				
				/*
				final Buffer body = new Buffer(0);
				
				//-- This will then get called every time a chunk of the request body arrives.
				//-- The dataHandler may be called more than once depending on the size of the body.
				request.dataHandler(new Handler<Buffer>() {
					@Override
					public void handle(Buffer buffer) {
						logger.info("received data: "+buffer.toString());
						body.appendBuffer(buffer);
					}
				});
				
				//-- The entire body has now been received
				request.endHandler(new VoidHandler() {
					@Override
					protected void handle() {
						logger.info("total received data: "+body.toString());
					}
				});
				*/
				
				//-- The body handler is called only once when the entire request body has been read.
				//-- Beware of doing this with very large requests since the entire request body will be stored in memory.
				request.bodyHandler(new Handler<Buffer>() {
					@Override
					public void handle(Buffer buffer) {
						logger.info("received data: "+buffer.toString());
					}
				});		
				
				request.response().setStatusCode(200).end("OK");
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