package com.dongah.smartcharger.websocket.socket.handler.handlerreceive;

import android.os.Build;

import androidx.annotation.RequiresApi;

import com.dongah.smartcharger.MainActivity;
import com.dongah.smartcharger.basefunction.ChargingCurrentData;
import com.dongah.smartcharger.websocket.ocpp.core.ChargePointStatus;
import com.dongah.smartcharger.websocket.ocpp.reservation.CancelReservationConfirmation;
import com.dongah.smartcharger.websocket.ocpp.reservation.CancelReservationStatus;
import com.dongah.smartcharger.websocket.socket.OcppHandler;
import com.dongah.smartcharger.websocket.socket.handler.handlersend.StatusNotificationReq;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

public class CancelReservationHandler implements OcppHandler {
    private static final Logger logger = LoggerFactory.getLogger(CancelReservationHandler.class);

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public void handle(JSONObject payload, int connectorId, String messageId) throws Exception {
        try {
            String resReservationId = payload.has("reservationId") ? payload.getString("reservationId") : "-1";
            MainActivity activity = (MainActivity) MainActivity.mContext;

            int cancelConnectorId = onFindConnectorId(resReservationId);
            CancelReservationStatus cancelReservationStatus = cancelConnectorId > 0 ?
                    CancelReservationStatus.Accepted : CancelReservationStatus.Rejected;

            // response
            CancelReservationConfirmation cancelReservationConfirmation = new CancelReservationConfirmation(cancelReservationStatus);
            activity.getSocketReceiveMessage().onResultSend(
                    100,
                    cancelReservationConfirmation.getActionName(),
                    messageId,
                    cancelReservationConfirmation
            );

            if (cancelReservationStatus == CancelReservationStatus.Accepted) {
                // StatusNotification(Accepted) send
                StatusNotificationReq statusNotificationReq = new StatusNotificationReq(connectorId);
                statusNotificationReq.sendStatusNotification();
            }
        } catch (Exception e) {
            logger.error("CancelReservationHandler error : {}", e.getMessage(), e);
        }
    }

    private int onFindConnectorId(String reservationId) {
        int result = 0;
        try {
            ChargingCurrentData chargingCurrentData = ((MainActivity) MainActivity.mContext).getChargingCurrentData();
            if (Objects.equals(reservationId, chargingCurrentData.getResReservationId())) {
                result = chargingCurrentData.getResConnectorId();
                chargingCurrentData.setResConnectorId(0);
                chargingCurrentData.setResIdTag("");
                chargingCurrentData.setResExpiryDate("");
                chargingCurrentData.setResReservationId("");
                chargingCurrentData.setResParentIdTag("");
                chargingCurrentData.setChargePointStatus(ChargePointStatus.Available);
                chargingCurrentData.setReservedStatus(ChargePointStatus.Available);
            }
        } catch (Exception e) {
            logger.error("onFindConnectorId error : {}", e.getMessage(), e);
        }
        return result;
    }
}
