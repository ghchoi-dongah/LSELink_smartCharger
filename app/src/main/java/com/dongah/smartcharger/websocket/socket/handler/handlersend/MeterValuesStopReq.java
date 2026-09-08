package com.dongah.smartcharger.websocket.socket.handler.handlersend;

import android.os.Build;

import androidx.annotation.RequiresApi;

import com.dongah.smartcharger.MainActivity;
import com.dongah.smartcharger.basefunction.ChargerConfiguration;
import com.dongah.smartcharger.basefunction.ChargingCurrentData;
import com.dongah.smartcharger.basefunction.GlobalVariables;
import com.dongah.smartcharger.utils.LogDataSave;
import com.dongah.smartcharger.websocket.ocpp.core.datatransfer.lselink.MeterValuesData;
import com.dongah.smartcharger.websocket.ocpp.core.datatransfer.lselink.MeterValuesRequest;
import com.dongah.smartcharger.websocket.ocpp.utilities.ZonedDateTimeConvert;
import com.dongah.smartcharger.websocket.socket.SocketState;
import com.google.gson.Gson;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class MeterValuesStopReq {
    private static final Logger logger = LoggerFactory.getLogger(MeterValuesStopReq.class);

    private final int connectorId;

    public int getConnectorId() {
        return connectorId;
    }

    public MeterValuesStopReq(int connectorId) {
        this.connectorId = connectorId;
    }

    /**
     * 충전 종료 시 최종 DT(MeterValues)를 전송한다.
     * accTickWh = (현재 전력량 - 충전 시작 전력량) - 이미 전송한 누적량
     * → meterStop - meterStart 와 accTickWh 합산의 오차를 보정.
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    public void sendMeterValuesStop(MeterValuesReq meterValuesReq) {
        try {
            MainActivity activity = (MainActivity) MainActivity.mContext;
            if (activity == null) return;

            ChargingCurrentData chargingCurrentData = activity.getChargingCurrentData();
            ChargerConfiguration chargerConfiguration = activity.getChargerConfiguration();
            ZonedDateTimeConvert zonedDateTimeConvert = new ZonedDateTimeConvert();

            long currentMeter = chargingCurrentData.getPowerMeter();
            long powerMeterStart = chargingCurrentData.getPowerMeterStart();
            long totalSentDiff = (meterValuesReq != null) ? meterValuesReq.getTotalSentPowerMeterDiff() : 0;
            long prevPowerMeter = (meterValuesReq != null) ? meterValuesReq.getPrevPowerMeter() : currentMeter;
            long remainingWh = (currentMeter - powerMeterStart) - totalSentDiff;

            MeterValuesData meterValuesData = new MeterValuesData();
            meterValuesData.chargeBoxSerialNumber = chargerConfiguration.getChargeBoxSerialNumber();
            meterValuesData.chargePointSerialNumber = chargerConfiguration.getChargerId();
            meterValuesData.connectorId = connectorId;
            meterValuesData.transactionId = chargingCurrentData.getTransactionId();
            meterValuesData.idTag = chargingCurrentData.getIdTag();
            meterValuesData.timestamp = zonedDateTimeConvert.doGetKstDatetimeAsString();
            meterValuesData.power = (float) ((chargingCurrentData.getOutPutVoltage() * 10) * (chargingCurrentData.getOutPutCurrent() * 0.001));
            meterValuesData.eps = (int) (chargingCurrentData.getOutPutVoltage() * 10);
            meterValuesData.ecu = (int) (chargingCurrentData.getOutPutCurrent() * 0.001);
            meterValuesData.accTickWh = (float) (remainingWh * 0.001);
            meterValuesData.accWh = (float) ((prevPowerMeter + remainingWh) * 0.001);
            meterValuesData.accTickTime = GlobalVariables.getMeterValueSampleInterval();
            meterValuesData.rechgHr = (int) chargingCurrentData.getChargingTime();
            meterValuesData.remnHr = chargingCurrentData.getRemaintime() / 60;
            meterValuesData.btrRm = chargingCurrentData.getSoc();
            meterValuesData.slprcUpc = (float) chargingCurrentData.getPowerUnitPrice();
            meterValuesData.crtrUpc = 0.0f;

            Gson gson = new Gson();
            String jsonData = gson.toJson(meterValuesData);

            MeterValuesRequest meterValuesRequest = new MeterValuesRequest();
            meterValuesRequest.setVendorId(chargerConfiguration.getChargePointVendor());
            meterValuesRequest.setMessageId("MeterValues");
            meterValuesRequest.setData(jsonData);

            SocketState socketState = activity.getSocketReceiveMessage().getSocket().getState();
            if (socketState.equals(SocketState.OPEN)) {
                activity.getSocketReceiveMessage().onSend(
                        connectorId,
                        meterValuesRequest.getActionName(),
                        meterValuesRequest);
            } else {
                String uuid = UUID.randomUUID().toString();
                saveFullMeterValues(connectorId, uuid, meterValuesRequest);
            }
        } catch (Exception e) {
            logger.error("sendMeterValuesStop error : {}", e.getMessage());
        }
    }

    private void saveFullMeterValues(int connectorId, String uniqueId, MeterValuesRequest req) {
        try {
            JSONArray frame = new JSONArray();
            frame.put(2);
            frame.put(uniqueId);
            frame.put(req.getActionName());

            JSONObject payload = new JSONObject();
            payload.put("vendorId", req.getVendorId());
            payload.put("messageId", req.getMessageId());
            payload.put("data", req.getData());
            frame.put(payload);

            LogDataSave logDataSave = new LogDataSave();
            logDataSave.makeDump(connectorId, frame.toString());
        } catch (Exception e) {
            logger.error("saveFullMeterValues error : {}", e.getMessage());
        }
    }
}
