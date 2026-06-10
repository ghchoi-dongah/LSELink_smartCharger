package com.dongah.smartcharger.websocket.socket.handler.handlerreceive;

import android.os.Build;

import androidx.annotation.RequiresApi;

import com.dongah.smartcharger.MainActivity;
import com.dongah.smartcharger.basefunction.ChargingCurrentData;
import com.dongah.smartcharger.websocket.ocpp.core.datatransfer.lselink.AppSetSocConfirm;
import com.dongah.smartcharger.websocket.socket.OcppHandler;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AppSetSocHandler implements OcppHandler {
    private static final Logger logger = LoggerFactory.getLogger(AppSetSocHandler.class);

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public void handle(JSONObject payload, int connectorId, String messageId) throws Exception {
        try {
            MainActivity activity = (MainActivity) MainActivity.mContext;
            ChargingCurrentData chargingCurrentData = activity.getChargingCurrentData();

            JSONObject dataJson = payload.getJSONObject("data");
            String idTag = dataJson.getString("idTag");
            int soc = dataJson.getInt("setSoc");

            chargingCurrentData.setIdTag(idTag);
            chargingCurrentData.setTargetSoc(soc);

            // response
            AppSetSocConfirm appSetSocConfirm = new AppSetSocConfirm();
            activity.getSocketReceiveMessage().onResultSend(
                    connectorId,
                    appSetSocConfirm.getActionName(),
                    messageId,
                    appSetSocConfirm
            );
        } catch (Exception e) {
            logger.error("AppSetSocHandler error : {}", e.getMessage(), e);
        }
    }
}
