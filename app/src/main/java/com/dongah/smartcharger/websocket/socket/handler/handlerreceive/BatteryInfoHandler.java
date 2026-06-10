package com.dongah.smartcharger.websocket.socket.handler.handlerreceive;

import com.dongah.smartcharger.websocket.socket.OcppHandler;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BatteryInfoHandler implements OcppHandler {
    private static final Logger logger = LoggerFactory.getLogger(BatteryInfoHandler.class);

    @Override
    public void handle(JSONObject payload, int connectorId, String messageId) throws Exception {

    }
}
