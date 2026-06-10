package com.dongah.smartcharger.websocket.socket.handler.handlerreceive;

import android.os.Build;
import android.os.Environment;

import androidx.annotation.RequiresApi;

import com.dongah.smartcharger.MainActivity;
import com.dongah.smartcharger.basefunction.ChargingCurrentData;
import com.dongah.smartcharger.basefunction.GlobalVariables;
import com.dongah.smartcharger.basefunction.UiSeq;
import com.dongah.smartcharger.utils.FileManagement;
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
import java.util.Arrays;
import java.util.Objects;

public class ChangeAvailabilityHandler implements OcppHandler {
    private static final Logger logger = LoggerFactory.getLogger(ChangeAvailabilityHandler.class);

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public void handle(JSONObject payload, int connectorId, String messageId) throws Exception {
        try {
            MainActivity activity = (MainActivity) MainActivity.mContext;
            AvailabilityType type = AvailabilityType.valueOf(payload.getString("type"));

            // Operative → 충전기 사용 가능
            boolean checkType = type == AvailabilityType.Operative;

            ChargePointStatus status = (type.equals(AvailabilityType.Operative) || type.equals(AvailabilityType.Managecomplete))
                    ? ChargePointStatus.Available : type.equals(AvailabilityType.Inoperative)
                    ? ChargePointStatus.Unavailable : ChargePointStatus.Maintenance;


            // ChargerOperate
            // connectorId == 0 → 전체 업데이트
            boolean isCharging = Objects.equals(activity.getClassUiProcess().getUiSeq(), UiSeq.CHARGING);
            if (connectorId == 0) {


                AvailabilityStatus result =
                        ((type == AvailabilityType.Inoperative) || (type == AvailabilityType.Maintenance) && isCharging)
                                ? AvailabilityStatus.Scheduled
                                : AvailabilityStatus.Accepted;

                // change availability response
                ChangeAvailabilityConfirmation changeAvailabilityConfirmation = new ChangeAvailabilityConfirmation(result);
                activity.getSocketReceiveMessage().onResultSend(
                        connectorId,
                        changeAvailabilityConfirmation.getActionName(),
                        messageId,
                        changeAvailabilityConfirmation);

                Arrays.fill(GlobalVariables.ChargerOperation, checkType);

                for (int i = 0; i < GlobalVariables.maxChannel; i++) {
                    ChargingCurrentData chargingCurrentData = activity.getChargingCurrentData();
                    chargingCurrentData.setChargePointStatus(status);

                    // StatusNotification send
                    StatusNotificationReq statusNotificationReq = new StatusNotificationReq(i+1);
                    statusNotificationReq.sendStatusNotification(i+1, chargingCurrentData.getChargePointStatus());
                }

            } else {
                AvailabilityStatus result =
                        ((type == AvailabilityType.Inoperative) || (type == AvailabilityType.Maintenance)) && isCharging
                                ? AvailabilityStatus.Scheduled
                                : AvailabilityStatus.Accepted;

                // change availability response
                ChangeAvailabilityConfirmation changeAvailabilityConfirmation = new ChangeAvailabilityConfirmation(result);
                activity.getSocketReceiveMessage().onResultSend(
                        connectorId,
                        changeAvailabilityConfirmation.getActionName(),
                        messageId,
                        changeAvailabilityConfirmation);

                GlobalVariables.ChargerOperation[connectorId] = checkType;

                ChargingCurrentData chargingCurrentData = activity.getChargingCurrentData();
                chargingCurrentData.setChargePointStatus(status);

                // StatusNotification send
                StatusNotificationReq statusNotificationReq = new StatusNotificationReq(connectorId);
                statusNotificationReq.sendStatusNotification(connectorId, chargingCurrentData.getChargePointStatus());
            }

            onChargerOperateSave(checkType);
        } catch (Exception e) {
            logger.error("ChangeAvailabilityHandler error : {}", e.getMessage(), e);
        }
    }


    private void onChargerOperateSave(boolean checkType) {
        try {
            boolean chk;
            FileManagement fileManagement = new FileManagement();
            String rootPath = Environment.getExternalStorageDirectory().toString() + File.separator + "Download";
            String fileName = "ChargerOperate";
            File file = new File(rootPath + File.separator + fileName);
            if (file.exists()) chk = file.delete();

            for (int i = 0; i < GlobalVariables.maxPlugCount; i++) {
                String statusContent = String.valueOf(GlobalVariables.ChargerOperation[i]);
                fileManagement.stringToFileSave(rootPath, fileName, statusContent, true);
            }
        } catch (Exception e) {
            logger.error("onChargerOperateSave {}", e.getMessage());
        }
    }
}
