package com.dongah.smartcharger.websocket.socket.handler.handlerreceive;

import android.os.Build;

import androidx.annotation.RequiresApi;

import com.dongah.smartcharger.MainActivity;
import com.dongah.smartcharger.basefunction.GlobalVariables;
import com.dongah.smartcharger.websocket.ocpp.core.DataTransferStatus;
import com.dongah.smartcharger.websocket.ocpp.core.datatransfer.lselink.BatteryEncryptKeyConfirm;
import com.dongah.smartcharger.websocket.socket.OcppHandler;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BatteryEncryptKeyHandler implements OcppHandler {
    private static final Logger logger = LoggerFactory.getLogger(BatteryEncryptKeyHandler.class);

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public void handle(JSONObject payload, int connectorId, String messageId) throws Exception {
        try {
            MainActivity activity = (MainActivity) MainActivity.mContext;

            JSONObject dataJson = payload.getJSONObject("data");
            String keyId = dataJson.getString("keyId");
            GlobalVariables.setBatteryEncryptKeyId(keyId);
            logger.info("BatteryEncryptKey received keyId: {}", keyId);

            // response
            BatteryEncryptKeyConfirm batteryEncryptKeyConfirm = new BatteryEncryptKeyConfirm(DataTransferStatus.Accepted);
            activity.getSocketReceiveMessage().onResultSend(
                    connectorId,
                    batteryEncryptKeyConfirm.getActionName(),
                    messageId,
                    batteryEncryptKeyConfirm
            );
        } catch (Exception e) {
            logger.error("BatteryEncryptKeyHandler error : {}", e.getMessage(), e);
        }
    }
}
