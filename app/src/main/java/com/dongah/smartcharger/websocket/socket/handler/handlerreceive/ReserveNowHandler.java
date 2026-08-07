package com.dongah.smartcharger.websocket.socket.handler.handlerreceive;

import android.os.Build;

import androidx.annotation.RequiresApi;

import com.dongah.smartcharger.MainActivity;
import com.dongah.smartcharger.basefunction.ChargingCurrentData;
import com.dongah.smartcharger.basefunction.GlobalVariables;
import com.dongah.smartcharger.controlboard.RxData;
import com.dongah.smartcharger.utils.SupportFunction;
import com.dongah.smartcharger.websocket.ocpp.core.ChargePointStatus;
import com.dongah.smartcharger.websocket.ocpp.reservation.ReservationStatus;
import com.dongah.smartcharger.websocket.ocpp.reservation.ReserveNowConfirmation;
import com.dongah.smartcharger.websocket.socket.OcppHandler;
import com.dongah.smartcharger.websocket.socket.handler.handlersend.StatusNotificationReq;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

public class ReserveNowHandler implements OcppHandler {
    private static final Logger logger = LoggerFactory.getLogger(ReserveNowHandler.class);

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public void handle(JSONObject payload, int connectorId, String messageId) throws Exception {
        try {
            int resConnectorId = payload.has("connectorId") ? payload.getInt("connectorId") : 0;
            String resExpiryDate = payload.has("expiryDat") ? payload.getString("expiryDate") : "";
            String resIdTag = payload.has("idTag") ? payload.getString("idTag") : "";
            String resParentIdTag = payload.has("parentIdTag") ? payload.getString("parentIdTag") : "";
            String resReservationId = payload.has("reservationId") ? payload.getString("reservationId") : "";

            MainActivity activity = (MainActivity) MainActivity.mContext;
            ChargingCurrentData chargingCurrentData = activity.getChargingCurrentData();
            RxData rxData = activity.getControlBoard().getRxData();
            boolean faultedCase = rxData.isCsFault(), occupiedCase = false, unavailableCase = false;

            if (GlobalVariables.isReserveConnectorZeroSupported() &&  resConnectorId == 0) {
                occupiedCase = activity.getChargingCurrentData().getChargePointStatus() == ChargePointStatus.Available;
                unavailableCase = GlobalVariables.ChargerOperation[1];
            } else if (resConnectorId > 0) {
                occupiedCase = chargingCurrentData.getChargePointStatus() == ChargePointStatus.Available;
                unavailableCase = GlobalVariables.ChargerOperation[resConnectorId];
            }

            //configuration key SupportedFeatureProfiles check
            SupportFunction supportFunction = new SupportFunction();
            boolean reserveSupported = supportFunction.onSupportedFeatureProfiles("Reservation") ;

            ReservationStatus reservationStatus;
            reservationStatus = (!reserveSupported || resConnectorId == 0 ? ReservationStatus.Rejected : faultedCase ? ReservationStatus.Faulted :
                    !unavailableCase ? ReservationStatus.Unavailable : occupiedCase ? ReservationStatus.Occupied :
                            ReservationStatus.Accepted);

            ReserveNowConfirmation reserveNowConfirmation = new ReserveNowConfirmation(reservationStatus);
            activity.getSocketReceiveMessage().onResultSend(
                    resConnectorId,
                    reserveNowConfirmation.getActionName(),
                    messageId,
                    reserveNowConfirmation
            );

            if (Objects.equals(reservationStatus, ReservationStatus.Accepted)) {
                chargingCurrentData.setResConnectorId(resConnectorId);
                chargingCurrentData.setResExpiryDate(resExpiryDate);
                chargingCurrentData.setResIdTag(resIdTag);
                chargingCurrentData.setResParentIdTag(resParentIdTag);
                chargingCurrentData.setResReservationId(resReservationId);
                chargingCurrentData.setChargePointStatus(ChargePointStatus.Reserved);
                chargingCurrentData.setReservedStatus(ChargePointStatus.Reserved);
            }

            // StatusNotification(Reserved)
            StatusNotificationReq statusNotificationReq = new StatusNotificationReq(connectorId);
            statusNotificationReq.sendStatusNotification();
        } catch (Exception e) {
            logger.error("ReserveNowHandler error : {}", e.getMessage(), e);
        }
    }
}
