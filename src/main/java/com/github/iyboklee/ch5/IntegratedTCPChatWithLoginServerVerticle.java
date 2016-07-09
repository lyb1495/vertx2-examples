/**
 * @Author iyboklee (iyboklee@gmail.com)
 */
package com.github.iyboklee.ch5;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.Handler;
import org.vertx.java.core.VoidHandler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.impl.DefaultFutureResult;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.net.NetServer;
import org.vertx.java.core.net.NetSocket;
import org.vertx.java.core.parsetools.RecordParser;
import org.vertx.java.platform.Verticle;

public class IntegratedTCPChatWithLoginServerVerticle extends Verticle {

	public static final String INTERNAL_TCP_SERV_ADDR 	 = "com.github.iyboklee.chat.internal.tcp";
	public static final String INTERNAL_SOCKJS_SERV_ADDR = "com.github.iyboklee.chat.internal.sockjs";
	
	private final static String AUTH_MGR_ADDRESS_PREFIX = "vertx.basicauthmanager";
	private final static int LOCAL_AUTH_CACHE_TIMEOUT = 900000; //-- 15min
	
	private Logger logger;
	private EventBus eb;
	private NetServer server;
	private Set<String> sockets;
	
	private long authCacheTimeout;
	private final Map<String, Auth> authCache = new HashMap<>();
	private final Map<NetSocket, SocketInfo> sockInfos = new HashMap<>();
	  
	private class SocketInfo {
		private Set<String> sessions;
		private String writeHandlerID;
		private String username;
	}
	
	private class Auth {
		private final long timerID;

	    Auth(final String sessionID, final NetSocket socket) {
	    	timerID = vertx.setTimer(authCacheTimeout, new Handler<Long>() {
	    		public void handle(Long id) {
	    			uncacheAuthorisation(sessionID, socket);
	    		}
	    	});
	    }

	    void cancel() {
	    	vertx.cancelTimer(timerID);
	    }
	}
	
	private void cacheAuthorisation(String sessionID, NetSocket socket) {
		if (!authCache.containsKey(sessionID)) {
			logger.debug("cache: "+sessionID);
			authCache.put(sessionID, new Auth(sessionID, socket));
		}
		SocketInfo socketInfo = sockInfos.get(socket);
		Set<String> sess = socketInfo.sessions;
		if (sess == null) {
			sess = new HashSet<>();
			socketInfo.sessions = sess;
		}
		sess.add(sessionID);
	}
	
	private void uncacheAuthorisation(String sessionID, NetSocket socket) {
		logger.debug("uncache: "+sessionID);
		authCache.remove(sessionID);
	    SocketInfo socketInfo = sockInfos.get(socket);
	    Set<String> sess = socketInfo.sessions;
	    if (sess != null) {
	    	sess.remove(sessionID);
	    	if (sess.isEmpty()) {
	    		socketInfo.sessions = null;
	    	}
	    }
	}
	
	private void authorise(JsonObject message, String sessionID,
			final Handler<AsyncResult<Boolean>> handler) {
		final DefaultFutureResult<Boolean> res = new DefaultFutureResult<>();
		if (authCache.containsKey(sessionID)) {
			logger.debug("cache hit: "+sessionID);
			res.setResult(true).setHandler(handler);
		}
		else {
			logger.debug("cache miss: "+sessionID);
			eb.send(AUTH_MGR_ADDRESS_PREFIX+".authorise", message, new Handler<Message<JsonObject>>() {
				public void handle(Message<JsonObject> reply) {
					boolean authed = reply.body().getString("status").equals("ok");
					res.setResult(authed).setHandler(handler);
				}
			});
		}
	}
	
	@SuppressWarnings("unused")
	private String findWriteHanlderID(String username) {
		Iterator<SocketInfo> it = sockInfos.values().iterator();
		while (it.hasNext()) {
			SocketInfo socketInfo = it.next();
			if (socketInfo.username.equals(username)) {
				return socketInfo.writeHandlerID;
			}
		}
		return null;
	}
	
	private void setUsername(String username, NetSocket socket) {
		SocketInfo socketInfo = sockInfos.get(socket);
		socketInfo.username = username;
	}
	
	private void write(JsonObject message, NetSocket socket) {
		if (message == null) return;
		
		byte[] data;
		try {
			data = message.toString().getBytes("UTF-8");
			Buffer buffer = new Buffer(4+data.length);
			buffer.setInt(0, data.length);
			buffer.setBytes(4, data);
			socket.write(buffer);
		} catch (UnsupportedEncodingException e) {
		}	
	}
	
	private void writeAll(JsonObject message, NetSocket exclude) {
		if (message == null) return;
			
		byte[] data;
		try {
			data = message.toString().getBytes("UTF-8");
			Buffer buffer = new Buffer(4+data.length);
			buffer.setInt(0, data.length);
			buffer.setBytes(4, data);
			for (String s : sockets) {
				if (exclude == null || !exclude.writeHandlerID().equals(s)) {
					eb.send(s, buffer);
				}			
			}
		} catch (UnsupportedEncodingException e) {
		}	
	}
	
	final Handler<NetSocket> connectHandler = new Handler<NetSocket>() {
		@Override
		public void handle(final NetSocket socket) {
			SocketInfo socketInfo = new SocketInfo();
			socketInfo.writeHandlerID = socket.writeHandlerID();
			sockInfos.put(socket, socketInfo);
			
			//-- tcp stream framing
			final RecordParser framer = RecordParser.newFixed(4, null);
			framer.setOutput(new Handler<Buffer>() {
				int payloadLength = -1;
				@Override
				public void handle(Buffer buffer) {
					if (payloadLength == -1) {
						payloadLength = buffer.getInt(0);
						framer.fixedSizeMode(payloadLength);
					}
					else {
						final JsonObject message = new JsonObject(buffer.toString());
						final String sessionID = message.getString("sessionID");
						//-- sessin id check
						if (sessionID != null) {
							authorise(message, sessionID, new AsyncResultHandler<Boolean>() {
								@Override
								public void handle(AsyncResult<Boolean> result) {
									if (result.succeeded()) {
										if (result.result()) {
											cacheAuthorisation(sessionID, socket);
											writeAll(message.getObject("body"), socket);
											
											//-- send to sockjs server
											eb.send(INTERNAL_SOCKJS_SERV_ADDR, message.getObject("body"));
										}
										else {
											logger.warn("message rejected because sessionID is not authorised");
										}
									}
									else {
										logger.error("error in performing authorisation", result.cause());
									}
								}
							});	
						}
						//-- session id null
						else {
							eb.send(AUTH_MGR_ADDRESS_PREFIX+".login", message, new Handler<Message<JsonObject>>() {
								@Override
								public void handle(Message<JsonObject> reply) {
									boolean authed = reply.body().getString("status").equals("ok");
									if (authed) {
										sockets.add(socket.writeHandlerID());
										setUsername(message.getString("username"), socket);
									}
									write(reply.body(), socket);
								}
							});
						}
						
						framer.fixedSizeMode(4);
						payloadLength = -1;
					}
				}
			});
			//-- this handler will be called every time data is received on the socket
			socket.dataHandler(framer);
			//-- socket closed
			socket.closeHandler(new VoidHandler() {
				@Override
				protected void handle() {
					Iterator<String> it = sockets.iterator();
					while (it.hasNext()) {
						if (socket.writeHandlerID().equals(it.next())) {
							it.remove();
							break;
						}
					}
					//-- close any cached authorisations for this connection
				    SocketInfo socketInfo = sockInfos.remove(socket);
				    if (socketInfo != null) {
				    	Set<String> sses = socketInfo.sessions;
				    	if (sses != null) {
				    		for (String sessionID: sses) {
				    			Auth auth = authCache.remove(sessionID);
				    			if (auth != null)
				    				auth.cancel();
				    		}
				    	}
				    }
				}
			});
			//-- something went wrong
			socket.exceptionHandler(new Handler<Throwable>() {
				@Override
				public void handle(Throwable throwable) {
					logger.error("unexpected exception: ", throwable);
				}
			});
		}
	};
	
	@Override
	public void start() {
		JsonObject appConfig = container.config();
		
		logger = container.logger();
		eb = vertx.eventBus();
		server = vertx.createNetServer();
		sockets = vertx.sharedData().getSet("sockets");
		
		authCacheTimeout = appConfig.getInteger("authCacheTimeout", LOCAL_AUTH_CACHE_TIMEOUT);
		
		server.connectHandler(connectHandler);
		
		server.listen(8090, "localhost", new AsyncResultHandler<NetServer>() {
			@Override
			public void handle(AsyncResult<NetServer> asyncResult) {
				logger.info("bind result: "+asyncResult.succeeded());
			}
		});
		
		//-- event bus subscribe
		eb.registerHandler(INTERNAL_TCP_SERV_ADDR, new Handler<Message<JsonObject>>() {
			@Override
			public void handle(final Message<JsonObject> message) {
				writeAll(message.body(), null);
			}
		});
	}
}