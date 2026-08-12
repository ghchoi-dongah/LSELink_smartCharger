package com.dongah.smartcharger.websocket.socket.handler.handlerreceive;

import android.os.Build;

import androidx.annotation.RequiresApi;

import com.dongah.smartcharger.MainActivity;
import com.dongah.smartcharger.basefunction.ChargingCurrentData;
import com.dongah.smartcharger.basefunction.GlobalVariables;
import com.dongah.smartcharger.basefunction.UiSeq;
import com.dongah.smartcharger.controlboard.TxData;
import com.dongah.smartcharger.utils.FileManagement;
import com.dongah.smartcharger.websocket.ocpp.common.OccurenceConstraintException;
import com.dongah.smartcharger.websocket.ocpp.core.AvailabilityStatus;
import com.dongah.smartcharger.websocket.ocpp.core.AvailabilityType;
import com.dongah.smartcharger.websocket.ocpp.core.ChangeAvailabilityConfirmation;
import com.dongah.smartcharger.websocket.ocpp.core.ChargePointStatus;
import com.dongah.smartcharger.websocket.socket.OcppHandler;
import com.dongah.smartcharger.websocket.socket.handler.handlersend.StatusNotificationReq;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Objects;

public class ChangeAvailabilityHandler implements OcppHandler {
    private static final Logger logger = LoggerFactory.getLogger(ChangeAvailabilityHandler.class);

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public void handle(JSONObject payload, int connectorId, String messageId) throws Exception {
        try {
            AvailabilityType type = AvailabilityType.valueOf(payload.getString("type"));

            MainActivity activity = (MainActivity) MainActivity.mContext;
            ChargingCurrentData chargingCurrentData = activity.getChargingCurrentData();
            UiSeq uiSeq = activity.getClassUiProcess().getUiSeq();
            TxData txData = activity.getControlBoard().getTxData();

            int rConnectorId = chargingCurrentData.getConnectorId();
            boolean isCharging = Objects.equals(uiSeq, UiSeq.CHARGING);

            // 충전 중 일시중지/재시작
            if (type == AvailabilityType.Pause || type == AvailabilityType.Restart) {
                AvailabilityStatus status = isCharging ? AvailabilityStatus.Accepted : AvailabilityStatus.Rejected;
                sendChangeAvailabilityResponse(activity, rConnectorId, messageId, status);  // response
                if (Objects.equals(status, AvailabilityStatus.Accepted)) {
                    ChargePointStatus chargePointStatus = type == AvailabilityType.Pause ?
                            ChargePointStatus.Pause : ChargePointStatus.Charging;
                    chargingCurrentData.setChargePointStatus(chargePointStatus);

                    // send StatusNotification
                    StatusNotificationReq statusNotificationReq = new StatusNotificationReq(rConnectorId);
                    statusNotificationReq.sendStatusNotification(rConnectorId, chargePointStatus);

                    // 전력제어
                    if (type == AvailabilityType.Pause) {
                        txData.setPwmDuty((short) 0);
                    } else {
                        txData.setPwmDuty((short) chargingCurrentData.getLimitPower() >= 7 ?(short) 50 :(short) 25);
                    }
                }
                return;
            }

            // Operative → 충전기 사용 가능
            boolean checkType = type == AvailabilityType.Operative;
            // cp status
            ChargePointStatus status = (type.equals(AvailabilityType.Operative) || type.equals(AvailabilityType.Managecomplete))
                    ? ChargePointStatus.Available : type.equals(AvailabilityType.Inoperative)
                    ? ChargePointStatus.Unavailable : ChargePointStatus.Maintenance;

            // status
            AvailabilityStatus result =
                    (type == AvailabilityType.Inoperative || type == AvailabilityType.Maintenance) && isCharging
                            ? AvailabilityStatus.Scheduled
                            : AvailabilityStatus.Accepted;

            // change availability response
            sendChangeAvailabilityResponse(activity, rConnectorId, messageId, result);
            GlobalVariables.ChargerOperation[rConnectorId] = checkType;

            // StatusNotification send
            chargingCurrentData.setChargePointStatus(status);
            StatusNotificationReq statusNotificationReq = new StatusNotificationReq(rConnectorId);
            statusNotificationReq.sendStatusNotification(rConnectorId, chargingCurrentData.getChargePointStatus());

            onChargerOperateSave(checkType);
        } catch (Exception e) {
            logger.error("ChangeAvailabilityHandler error : {}", e.getMessage(), e);
        }
    }


    @RequiresApi(api = Build.VERSION_CODES.O)
    private void sendChangeAvailabilityResponse(MainActivity activity, int connectorId, String messageId, AvailabilityStatus status) throws OccurenceConstraintException {
        ChangeAvailabilityConfirmation confirmation = new ChangeAvailabilityConfirmation(status);
        activity.getSocketReceiveMessage().onResultSend(
                connectorId,
                confirmation.getActionName(),
                messageId,
                confirmation
        );
    }

    private void onChargerOperateSave(boolean checkType) {
        try {
            boolean chk;
            FileManagement fileManagement = new FileManagement();
            String rootPath = GlobalVariables.getRootPath();
            String fileName = "ChargerOperate";
            File file = new File(rootPath + File.separator + fileName);
            if (file.exists()) chk = file.delete();

            for (int i = 0; i < GlobalVariables.maxPlugCount; i++) {
                String statusContent = String.valueOf(GlobalVariables.ChargerOperation[i]);
                fileManagement.stringToFileSave(rootPath, fileName, statusContent, true);
            }
        } catch (Exception e) {
            logger.error("onChargerOperateSave error : {}", e.getMessage());
        }
    }
}
