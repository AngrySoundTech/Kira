package com.github.maxopoly.Kira.api;

import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.Logger;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import com.github.maxopoly.Kira.KiraMain;
import com.github.maxopoly.Kira.api.input.APISupplier;
import com.github.maxopoly.Kira.api.token.APIToken;
import com.github.maxopoly.Kira.api.token.APITokenManager;

public class KiraWebSocketServer extends WebSocketServer {

	private static final int API_VERSION = 1;

	private Map <WebSocket, APISession> connections;
	private Logger logger;

	public KiraWebSocketServer(Logger logger) {
		super(new InetSocketAddress("localhost/kira/api",14314));
		this.logger = logger;
		connections = new HashMap<>();
		start();
	}

	private APISession getAPISession(WebSocket socket) {
		return connections.get(socket);
	}

	@Override
	public void onClose(WebSocket conn, int code, String reason, boolean remote) {
		KiraMain.getInstance().getLogger().warn("Closing connection with " + conn.getRemoteSocketAddress() + ", because of: " + reason);
	}

	@Override
	public void onError(WebSocket conn, Exception ex) {
		String remoteAddr = conn == null ? "(no connection)" : conn.getRemoteSocketAddress().toString();
		KiraMain.getInstance().getLogger().warn("Error occured in API handling of " + remoteAddr, ex);
	}

	@Override
	public void onMessage(WebSocket conn, String message) {
		KiraMain.getInstance().getAPISessionManager().getInputHandler().handle(message, new APISupplier(getAPISession(conn)));
	}

	@Override
	public void onOpen(WebSocket conn, ClientHandshake handshake) {
		APISession session = setupSession(conn, handshake);
		if (session != null) {
			KiraMain.getInstance().getAPISessionManager().registerSession(session);
			connections.put(conn, session);
		}
	}

	@Override
	public void onStart() {
		//already did everything in constructor, but still have to override this
	}

	private APISession setupSession(WebSocket conn, ClientHandshake handshake) {
		Map<String, String> queryParams = getQueryParams(handshake.getResourceDescriptor());
		String tokenString = queryParams.get("apiToken");
		if (tokenString == null || tokenString.equals("")) {
			logger.info("Closing connection with " + conn.getRemoteSocketAddress() + ", because no api token was given");
			conn.close(400, "No token supplied");
			return null;
		}
		String appId = queryParams.get("applicationId");
		if (appId == null || appId.equals("")) {
			logger.info("Closing connection with " + conn.getRemoteSocketAddress() + ", because no application id was given");
			conn.close(400, "No app id supplied");
			return null;
		}
		String versionString = queryParams.get("apiVersion");
		if (versionString == null || versionString.equals("")) {
			logger.info("Closing connection with " + conn.getRemoteSocketAddress() + ", because no api version was given");
			conn.close(400, "No api version supplied");
			return null;
		}
		int version;
		try {
			version = Integer.parseInt(versionString);
		}
		catch (NumberFormatException e) {
			logger.info("Closing connection with " + conn.getRemoteSocketAddress() + ", because illegal version " + versionString + " was supplied");
			conn.close(400, "Illegal version, not a number");
			return null;
		}
		if (version != API_VERSION) {
			logger.info("Closing connection with " + conn.getRemoteSocketAddress() + ", because they are using an outdated api version");
			conn.close(400, "Outdated version, newest one is " + API_VERSION);
			return null;
		}
		APITokenManager tokenMan = KiraMain.getInstance().getAPISessionManager().getTokenManager();
		APIToken token = tokenMan.getToken(tokenString);
		if (token == null) {
			logger.info("Closing connection with " + conn.getRemoteSocketAddress() + ", because they supplied an invalid token");
			conn.close(400, "Invalid token");
			return null;
		}
		if (token.isOutdated()) {
			logger.info("Closing connection with " + conn.getRemoteSocketAddress() + ", because their token had timed out");
			conn.close(400, "Outdated token");
		}
		return token.generateSession(conn);
	}

	/**
	 * Takes an URI and returns the last key-value mapping for each query parameter pair.
	 * If the URI contains no query parameters, returns an empty map.
	 * Ignores the fragment part of the URI.
	 * Malformed keys/values (unsupported encoding) are ignored.
	 * Note that keys are case sensitive as per RFC 3986. https://tools.ietf.org/html/rfc3986#page-11
	 */
	public static Map<String, String> getQueryParams(String uri) {
		Map<String, String> queryPairs = new HashMap<String, String>();

		int paramsSepIdx = uri.indexOf("?");
		if (paramsSepIdx < 0) return queryPairs; // no query params in URI
		String query = uri.substring(paramsSepIdx + 1);

		int fragmentSepIdx = query.indexOf("#");
		if (fragmentSepIdx >= 0) {
			query = query.substring(0, fragmentSepIdx);
		}

		String[] pairs = query.split("&");
		for (String pair : pairs) {
			int kvSep = pair.indexOf("=");
			try {
			String key = URLDecoder.decode(pair.substring(0, kvSep), "UTF-8");
			String value = URLDecoder.decode(pair.substring(kvSep + 1), "UTF-8");
			queryPairs.put(key, value);
			} catch (UnsupportedEncodingException ignored) {
			}
		}
		return queryPairs;
	}
}
