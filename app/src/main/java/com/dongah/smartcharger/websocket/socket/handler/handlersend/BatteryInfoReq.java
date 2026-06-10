package com.dongah.smartcharger.websocket.socket.handler.handlersend;

import android.os.Build;

import androidx.annotation.RequiresApi;

import com.dongah.smartcharger.MainActivity;
import com.dongah.smartcharger.basefunction.ChargingCurrentData;
import com.dongah.smartcharger.basefunction.GlobalVariables;
import com.dongah.smartcharger.websocket.ocpp.core.datatransfer.lselink.BatteryData;
import com.dongah.smartcharger.websocket.ocpp.core.datatransfer.lselink.BatteryInfoData;
import com.dongah.smartcharger.websocket.ocpp.core.datatransfer.lselink.BatteryInfoRequest;
import com.dongah.smartcharger.websocket.ocpp.core.datatransfer.vas.BatteryInfoDataSend;
import com.dongah.smartcharger.websocket.ocpp.utilities.ZonedDateTimeConvert;
import com.google.gson.Gson;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

public class BatteryInfoReq {
    private static final Logger logger = LoggerFactory.getLogger(BatteryInfoReq.class);

    @RequiresApi(api = Build.VERSION_CODES.O)
    public void sendBatteryInfo() {
        try {
            MainActivity activity = (MainActivity) MainActivity.mContext;
            ChargingCurrentData chargingCurrentData = activity.getChargingCurrentData();

            ZonedDateTimeConvert zonedDateTimeConvert = new ZonedDateTimeConvert();
            String time = zonedDateTimeConvert.getStringTimeZone(chargingCurrentData.getChargingStartTime());

            Gson gson = new Gson();
            List<BatteryData> batteryDataList = new ArrayList<>();

            File file = new File(GlobalVariables.getRootPath() + File.separator + "batteryInfo.dongah");
            if (file.exists()) {
                BufferedReader reader = new BufferedReader(new FileReader(file));
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty()) {
                        BatteryInfoDataSend send = gson.fromJson(line, BatteryInfoDataSend.class);
                        BatteryData batteryData = new BatteryData();
                        batteryData.setTimestamp(send.getTimeStamp());
                        batteryData.setBattery(send.getBattery());
                        batteryDataList.add(batteryData);
                    }
                }
                reader.close();
            }

            BatteryInfoData batteryInfoData = new BatteryInfoData();
            batteryInfoData.setInfoCnt(String.valueOf(batteryDataList.size()));
            batteryInfoData.setConnectorId(String.valueOf(chargingCurrentData.getConnectorId()));
            batteryInfoData.setTsdt(time);
            batteryInfoData.setKeyId(GlobalVariables.getBatteryEncryptKeyId());
            batteryInfoData.setBatteryData(batteryDataList);

            BatteryInfoRequest batteryInfoRequest = new BatteryInfoRequest();
            batteryInfoRequest.setVendorId(activity.getChargerConfiguration().getChargePointVendor());
            batteryInfoRequest.setMessageId("Battery Info");
            batteryInfoRequest.setData(gson.toJson(batteryInfoData));

            activity.getSocketReceiveMessage().onSend(
                    chargingCurrentData.getConnectorId(),
                    batteryInfoRequest.getActionName(),
                    batteryInfoRequest
            );
        } catch (Exception e) {
            logger.error("sendBatteryInfo error : {}", e.getMessage(), e);
        }
    }
}
