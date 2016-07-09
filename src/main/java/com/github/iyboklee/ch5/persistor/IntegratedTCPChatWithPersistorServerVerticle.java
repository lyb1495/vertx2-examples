/**
 * @Author iyboklee (iyboklee@gmail.com)
 */
package com.github.iyboklee.ch5.persistor;

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

public class IntegratedTCPChatWithPersistorServerVerticle extends Verticle {

	public static final String INTERNAL_TCP_SERV_ADDR 	 = "com.github.iyboklee.chat.internal.tcp";
	public static final String INTERNAL_SOCKJS_SERV_ADDR = "com.github.iyboklee.chat.internal.sockjs";

	private final static String AUTH_MGR_ADDRESS_PREFIX = "vertx.basicauthmanager";
	private final static int LOCAL_AUTH_CACHE_TIMEOUT = 900000; //-- 15min
	
	private Logger logger;
	private EventBus eb;
	private NetServer server;
	private Set<String> sockets;
	
	private final Map<String, Set<NetSocket>> rooms = new HashMap<>();
	
	private long authCacheTimeout;
	private final Map<String, Auth> authCache = new HashMap<>();
	private final Map<NetSocket, SocketInfo> sockInfos = new HashMap<>();
	
	private class SocketInfo {
		private Set<String> sessions;
		private String writeHandlerID;
		private String username;
		private String rmID;
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
	
	private boolean enter(String rmID, NetSocket socket) {
		SocketInfo socketInfo = sockInfos.get(socket);
		if (socketInfo != null && socketInfo.rmID == null) {
			Set<NetSocket> users = rooms.get(rmID);
			if (users == null) {
				users = new HashSet<>();
				rooms.put(rmID, users);
			}
			users.add(socket);
			socketInfo.rmID = rmID;
			return true;
		}
		return false;
	}
		
	private boolean leave(String rmID, NetSocket socket) {
		SocketInfo socketInfo = sockInfos.get(socket);
		if (socketInfo != null && socketInfo.rmID != null && socketInfo.rmID.equals(rmID)) {
			Set<NetSocket> users = rooms.get(rmID);
			if (users != null) {
				users.remove(socket);
			}
			socketInfo.rmID = null;
			return true;
		}
		return false;
	}
	
	private void setUsername(String username, NetSocket socket) {
		SocketInfo socketInfo = sockInfos.get(socket);
		if (socketInfo != null)
			socketInfo.username = username;
	}
	
	private SocketInfo findByUsername(String username, String rmID) {
		Set<NetSocket> sockets = rooms.get(rmID);
		if (sockets != null) {
			for (NetSocket socket : sockets) {
				SocketInfo socketInfo = sockInfos.get(socket);
				if (username.equals(socketInfo.username) && rmID.equals(socketInfo.rmID)) {
					return socketInfo;
				}
			}
		}
		return null;
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
	
	private void writeAll(JsonObject message, String rmID, String whisperTarget, String exclude) {
		if (message == null || rmID == null) return;
				
		Set<NetSocket> users = rooms.get(rmID);
		if (users != null) {
			byte[] data;
			try {
				boolean isWhisper = (whisperTarget != null);
				data = message.toString().getBytes("UTF-8");
				Buffer buffer = new Buffer(4+data.length);
				buffer.setInt(0, data.length);
				buffer.setBytes(4, data);
				if (!isWhisper) {
					for (NetSocket socket : users) {
						if (exclude == null || !exclude.equals(socket.writeHandlerID())) {
							socket.write(buffer);
						}			
					}
				}
				else {
					SocketInfo socketInfo = findByUsername(whisperTarget, rmID);
					if (socketInfo != null) {
						eb.send(socketInfo.writeHandlerID, buffer);
					}
				}
			} catch (UnsupportedEncodingException e) {
			}	
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
						final MessageParser message = new MessageParser(buffer);
						//-- session id check
						if (message.getSessionID() != null) {
							authorise(message.origin(), message.getSessionID(), new AsyncResultHandler<Boolean>() {
								@Override
								public void handle(AsyncResult<Boolean> result) {
									if (result.succeeded()) {
										if (result.result()) {
											cacheAuthorisation(message.getSessionID(), socket);
											if (message.getMessageType() == MessageParser.MessageType.CHAT || message.getMessageType() == MessageParser.MessageType.WHISPER) {
												if (message.getBody() != null && message.getRmID() != null) {
													SocketInfo socketInfo = sockInfos.get(socket);
													if (socketInfo.rmID != null && socketInfo.rmID.equals(message.getRmID())) {
														if (message.getMessageType() == MessageParser.MessageType.CHAT) {
															eb.publish(INTERNAL_TCP_SERV_ADDR, message.getInteralChatBroadcastData(socket));
															//-- send to sockjs server
															eb.send(INTERNAL_SOCKJS_SERV_ADDR, message.getInteralChatBroadcastData(null));
															//-- save chat message
															eb.send("com.github.iyboklee.chat.internal.persistor", message.origin());
														}
														else if (message.getWhisperTarget() != null) {
															eb.publish(INTERNAL_TCP_SERV_ADDR, message.getInteralChatBroadcastData(socket));
															//-- publish to sockjs server
															eb.publish(INTERNAL_SOCKJS_SERV_ADDR, message.getInteralChatBroadcastData(null));
														}
														else {
															logger.warn("message rejected because whisperTarget is missing");
														}
													}
													else {
														logger.warn("message rejected because invalid rmID");
													}
												}
												else {
													logger.warn("message rejected because body or rmID is missing");
												}	
											}
											else if (message.getMessageType() == MessageParser.MessageType.ENTER) {
												if (message.getBody() != null && message.getRmID() != null) {
													boolean isOK = enter(message.getRmID(), socket);
													JsonObject response = message.origin();
													response.putString("status", (isOK)?"ok":"denied");
													write(response, socket);
												}
												else {
													logger.warn("message rejected because body or rmID is missing");
												}
											}
											else if (message.getMessageType() == MessageParser.MessageType.LEAVE) {
												if (message.getBody() != null && message.getRmID() != null) {
													boolean isOK = leave(message.getRmID(), socket);
													JsonObject response = message.origin();
													response.putString("status", (isOK)?"ok":"denied");
													write(response, socket);
												}
												else {
													logger.warn("message rejected because body or rmID is missing");
												}
											}
											else {
												logger.warn("message rejected because not supported messageType");
											}
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
							eb.send(AUTH_MGR_ADDRESS_PREFIX+".login", message.origin(), new Handler<Message<JsonObject>>() {
								@Override
								public void handle(Message<JsonObject> reply) {
									boolean authed = reply.body().getString("status").equals("ok");
									if (authed) {
										sockets.add(socket.writeHandlerID());
										setUsername(message.origin().getString("username"), socket);
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
				    SocketInfo socketInfo = sockInfos.remove(socket);
				    if (socketInfo != null) {
				    	//-- leave a room
				    	if (socketInfo.rmID != null) {
				    		Set<NetSocket> users = rooms.get(socketInfo.rmID);
				    		if (users != null) {
				    			users.remove(socket);
				    		}
				    	}
				    	//-- close any cached authorisations for this connection
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
				JsonObject internalBroadcastData = message.body();
				String rmID = (String) internalBroadcastData.removeField("rmID");
				String whisperTarget = (String) internalBroadcastData.removeField("whisperTarget");
				String writerHandlerID = (String) internalBroadcastData.removeField("writerHandlerID");
				writeAll(internalBroadcastData, rmID, whisperTarget, writerHandlerID);
			}
		});
	}
}