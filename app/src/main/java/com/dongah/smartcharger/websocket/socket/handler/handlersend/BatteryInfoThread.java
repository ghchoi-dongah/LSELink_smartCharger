package com.dongah.smartcharger.websocket.socket.handler.handlersend;

import android.os.Build;

import androidx.annotation.RequiresApi;

import com.dongah.smartcharger.MainActivity;
import com.dongah.smartcharger.basefunction.ChargerConfiguration;
import com.dongah.smartcharger.basefunction.ChargingCurrentData;
import com.dongah.smartcharger.basefunction.GlobalVariables;
import com.dongah.smartcharger.websocket.ocpp.common.OccurenceConstraintException;
import com.dongah.smartcharger.websocket.ocpp.core.datatransfer.lselink.BatteryInfoData;
import com.dongah.smartcharger.websocket.ocpp.core.datatransfer.lselink.BatteryInfoRequest;
import com.dongah.smartcharger.websocket.ocpp.core.datatransfer.vas.BatteryData;
import com.dongah.smartcharger.websocket.ocpp.core.datatransfer.vas.VasData;
import com.dongah.smartcharger.websocket.ocpp.utilities.ZonedDateTimeConvert;
import com.dongah.smartcharger.websocket.socket.SocketReceiveMessage;
import com.dongah.smartcharger.websocket.socket.SocketState;
import com.google.gson.Gson;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BatteryInfoThread extends Thread {
    private static final Logger logger = LoggerFactory.getLogger(BatteryInfoThread.class);
    private static final String FILE_NAME = "batteryInfo.dongah";
    private volatile boolean stopped = false;
    private final int delayTime;
    private int count = 0;

    public BatteryInfoThread(int delayTime) {
        this.delayTime = delayTime;
    }

    public void stopThread() {
        stopped = true;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public void run() {
        logger.info("BatteryInfoThread started");
        while (!stopped && !isInterrupted()) {
            try {
                Thread.sleep(1000);
                count++;
                if (count >= delayTime) {
                    count = 0;
                    processBatteryInfo();
                }
            } catch (InterruptedException e) {
                logger.info("BatteryInfoThread interrupted");
                break;
            } catch (Exception e) {
                logger.error("BatteryInfoThread error : {}", e.getMessage());
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void processBatteryInfo() throws OccurenceConstraintException {
        MainActivity activity = (MainActivity) MainActivity.mContext;
        if (activity == null) return;

        try {
            String filePath = GlobalVariables.getRootPath() + File.separator + FILE_NAME;
            File file = new File(filePath);
            if (file.exists()) {
                SocketReceiveMessage socketReceiveMessage = activity.getSocketReceiveMessage();
                SocketState socketState = socketReceiveMessage.getSocket().getState();

                List<String> lines = new ArrayList<>();
                try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        lines.add(line.trim());
                    }
                } catch (Exception ee) {
                    logger.error("processBatteryInfo file read :  {}", ee.getMessage());
                    return;
                }

                List<String> toSend = lines.subList(0, Math.min(GlobalVariables.infoCnt, lines.size()));
                // remain data
                List<String> toKeep = lines.subList(Math.min(GlobalVariables.infoCnt, lines.size()), lines.size());

                if (!toSend.isEmpty()) {
                    List<Map<String, String>> cleanList = new ArrayList<>();
                    for (String line : toSend) {
                        try {
                            JSONObject lineObj = new JSONObject(line);
                            Map<String, String> batteryMap = new HashMap<>();
                            batteryMap.put("battery", lineObj.getString("battery"));
                            batteryMap.put("timestamp", lineObj.getString("timeStamp"));
                            cleanList.add(batteryMap);
                        } catch (JSONException e) {
                            throw new RuntimeException(e);
                        }
                    }

                    int sendCnt = Math.min(GlobalVariables.infoCnt, cleanList.size());

                    try {
                        ChargingCurrentData chargingCurrentData = activity.getChargingCurrentData();
                        ChargerConfiguration chargerConfiguration = activity.getChargerConfiguration();
                        ZonedDateTimeConvert zonedDateTimeConvert = new ZonedDateTimeConvert();
                        String time = zonedDateTimeConvert.getStringTimeZone(chargingCurrentData.getChargingStartTime());

                        BatteryInfoData batteryInfoData = new BatteryInfoData();
                        batteryInfoData.setInfoCnt(String.valueOf(sendCnt));
                        batteryInfoData.setConnectorId(String.valueOf(chargingCurrentData.getConnectorId()));
                        batteryInfoData.setTsdt(time);
                        batteryInfoData.setKeyId(GlobalVariables.getBatteryEncryptKeyId());
                        batteryInfoData.setBatteryData(cleanList);

                        BatteryInfoRequest batteryInfoRequest = new BatteryInfoRequest();
                        batteryInfoRequest.setVendorId(chargerConfiguration.getChargePointVendor());
                        batteryInfoRequest.setMessageId("batteryInfo");

                        Gson gson = new Gson();
                        batteryInfoRequest.setData(gson.toJson(batteryInfoData));

                        if (socketState == SocketState.OPEN) {
                            try {
                                socketReceiveMessage.onSend(
                                        1,
                                        batteryInfoRequest.getActionName(),
                                        batteryInfoRequest
                                );
                            } catch (OccurenceConstraintException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    } catch (Exception e){
                        throw new RuntimeException(e);
                    }
                }

                // 남은 데이터를 다시 파일에 저장
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath, false))) {
                    for (String line : toKeep) {
                        writer.write(line);
                        writer.newLine();
                    }
                    System.out.println("남은 JSON 데이터 파일에 저장 완료");
                } catch (IOException ez) {
                    logger.error("remain data save : {}", ez.getMessage());
                }
            }
        } catch (Exception e) {
            logger.error("processBatteryInfo error : {}", e.getMessage(), e);
        }
    }
}
