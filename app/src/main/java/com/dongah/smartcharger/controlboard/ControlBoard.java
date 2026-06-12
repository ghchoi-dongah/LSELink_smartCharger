package com.dongah.smartcharger.controlboard;

import com.dongah.smartcharger.utils.BitUtilities;
import com.dongah.smartcharger.utils.CRC16;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Objects;

import android_serialport_api.SerialPort;

public class ControlBoard implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(ControlBoard.class);

    public static final int HEAD_SIZE = 3;
    public static final int CRC_SIZE = 2;
    public static final int RX_SIZE = 31 * 2; //control board word type(x2)
    public static final int RX_DATA_SIZE = HEAD_SIZE + CRC_SIZE + RX_SIZE;
    private static final int maxVoltage = 253;
    private static final int minVoltage = 170;
    private static final int maxCurrent = 38;

    /**
     * serial
     **/
    SerialPort serialPort;
    private InputStream inputStream;
    private OutputStream outputStream;

    /**
     * buffer
     **/
    private RxData rxData;
    private TxData txData;
    private int noDataCount;
    private boolean disconnected = true;
    private  boolean bEndFlag = false;

    boolean isOpen = false;
    int maxCh = 1;
    String comPort;
    int tCount = 0;
    int curCh = 0;
    boolean chkTx = false;
    int availableCount;
    Thread receiveThread;

    /**
     * 통신 설계
     **/
    private final byte[] sendCh = {0x01, 0x02, 0x03, 0x04, 0x05, 0x06};
    short[] rxBuffer200 = new short[10];
    byte[] receiveData = new byte[128];
    byte[] realReceiveData = new byte[RX_DATA_SIZE];
    byte[] powerReceiveData = new byte[77];                             //header + data + checksum
    short command = 0;
    byte[] headParse = new byte[2];


    /**
     * Getter & Setter
     **/
    public RxData getRxData() {
        return rxData;
    }

    public TxData getTxData() {
        return txData;
    }

    public boolean isDisconnected() {
        return disconnected;
    }

    ControlBoardListener controlBoardListener;

    public void setControlBoardListener(ControlBoardListener controlBoardListener) {
        this.controlBoardListener = controlBoardListener;
    }

    public void setControlBoardListenerStop() {
        controlBoardListener = null;
    }

    /**
     * ControlBoard constructor
     *
     * @param maxCh   max channel
     * @param comPort use port name
     **/
    public ControlBoard(int maxCh, String comPort) {
        try {
            this.maxCh = maxCh;
            this.comPort = comPort;

            serialPort = new SerialPort(new File(comPort), 38400, 0);
            // multi channel
            rxData = new RxData();
            txData = new TxData();
            txData.setInit(); // 초기화
            isOpen = true;
            disconnected = false;
            inputStream = serialPort.getInputStream();
            outputStream = serialPort.getOutputStream();
            receiveThread = new Thread(this);
            receiveThread.start();
        } catch (Exception e) {
            logger.error("serial initial error : {}", e.getMessage());
        }
    }

    /**
     * crc check
     *
     * @param chkData source data
     * @return result = true
     **/
    private boolean CheckResponse(byte[] chkData) {
        byte[] crc;
        crc = CRC16.setCrc16ModBus(chkData, 0, chkData.length - 2);
        return Objects.equals(crc[0], chkData[chkData.length - 1]) &&
                Objects.equals(crc[1], chkData[chkData.length - 2]);
    }

    /**
     * receive data validation (checksum)
     *
     * @param srcData receive data
     **/
    private void responseReceive(byte[] srcData) {
        if (isOpen) {
            try {
                System.arraycopy(srcData,0, realReceiveData,0, RX_DATA_SIZE);
                if (Objects.equals(realReceiveData[1],(byte)0x04) && CheckResponse(realReceiveData)) {
                    byte currentCh = realReceiveData[0];
                    short dataLength = realReceiveData[2];
                    short data1, data2;
                    short[] values = new short[dataLength/2];
                    for (int i = 0; i < dataLength / 2; i++) {
                        data1 = (realReceiveData[3 + (i * 2)]);
                        values[i] = (short)((data1 << 8) & 0xff00);
                        data2 = (short)(realReceiveData[4 + (i * 2)] & 0xff);
                        values[i] = (short)(values[i] | data2);
                    }
                    rxData.Decode(values);
                    if (controlBoardListener != null) controlBoardListener.onControlBoardReceive(rxData);
                    Arrays.fill(realReceiveData,(byte)0x00);
                }
            } catch (Exception e) {
                logger.error("responseReceive error : {}", e.getMessage());
            }
        }
    }

    /**
     * txData request
     *
     * @param deviceId device id
     * @param command  0x04
     * @param start    start address
     * @param register request count
     * @return success : true  fail : false
     **/
    private boolean requestSend(byte deviceId, byte command, short start, short register) {
        boolean result = false;
        try {
            if (isOpen) {
                byte[] bytes = new byte[8];
                byte[] crc;
                bytes[0] = deviceId;
                bytes[1] = command;
                bytes[2] = (byte) ((start >> 8) & 0xff);
                bytes[3] = (byte) (start & 0xff);
                bytes[4] = (byte) ((register >> 8) * 0xff);
                bytes[5] = (byte) (register & 0xff);
                crc = CRC16.setCrc16ModBus(bytes, 0, bytes.length - 2);
                bytes[6] = crc[1];
                bytes[7] = crc[0];
                outputStream.write(bytes);
            }
            result = true;
        } catch (Exception e) {
            logger.error("requestSend error : {}", e.getMessage());
        }
        return result;
    }

    /**
     * txData request
     *
     * @param deviceId device id
     * @param command  0x04 command
     * @param start    start address
     * @param register request count
     * @param values   send buffer
     * @return success : true  fail : false
     **/
    private boolean requestSend(byte deviceId, byte command, short start, short register, short[] values) {
        boolean result = false;
        try {
            if (isOpen) {
                // header (2byte) + start address (2byte) + data length(2byte) + data + crc(2byte)
                byte[] bytes = new byte[HEAD_SIZE + 2 + 2 + (register * 2) + CRC_SIZE];
                byte[] crc;
                bytes[0] = deviceId;
                bytes[1] = command;
                bytes[2] = (byte) ((start >> 8) & 0xff);
                bytes[3] = (byte) (start & 0xff);
                bytes[4] = (byte) ((register >> 8) & 0xff);
                bytes[5] = (byte) (register & 0xff);
                bytes[6] = (byte) (register * 2);
                for (int i = 0; i < register; i++) {
                    bytes[7 + (2 * i)] = (byte) ((values[i] >> 8) & 0xff);
                    bytes[8 + (2 * i)] = (byte) (values[i] & 0xff);
                }
                crc = CRC16.setCrc16ModBus(bytes, 0, bytes.length - 2);
                bytes[bytes.length - 2] = crc[1];
                bytes[bytes.length - 1] = crc[0];
                outputStream.write(bytes);
            }
            result = true;
        } catch (Exception e) {
            logger.error("requestBufferSend error : {}", e.getMessage());
        }
        return result;
    }

    @Override
    public void run() {
        while (!bEndFlag && !Thread.currentThread().isInterrupted()) {
            try {

                if (tCount++ > 1200) tCount = 0;
                /**
                 * tCount % 2
                 * 0: 채널 변경하면서 0x04 명령 전송
                 * 1: 현재 채널의 txData를 Encode()해서 0x10 명령 전송
                 **/
                int sendType = tCount % 3;
                switch (sendType) {
                    case 0:
                        curCh = (curCh + 1) % maxCh;
                        chkTx = requestSend(sendCh[curCh], (byte) 0x04, (short) 400, (short) 14);
                        break;
                    case 1:
                        rxBuffer200 = txData.Encode();
                        chkTx = requestSend(sendCh[curCh],(byte)0x10, (short)200, (short)9, rxBuffer200);
                        if (controlBoardListener != null) controlBoardListener.onControlBoardSend(txData);
                        break;
                    case 2:
                        chkTx = requestSend();
                        break;
                }
                Thread.sleep(150);
                // data receive
                try {
                    Arrays.fill(receiveData, (byte) 0x00);  // 수신 버퍼 초기화
                    availableCount = inputStream.available();   // 수신 대기 중인 바이트 수 확인
                    if (availableCount >= HEAD_SIZE) {
                        int readCount = inputStream.read(receiveData, 0, availableCount);
                    } else {
                        Thread.sleep(10);
                        if (noDataCount < 6000) noDataCount++;
                        if (noDataCount > 50) {
                            disconnected = true;
                        }
                        continue;
                    }


                    System.arraycopy(receiveData,0,headParse,0,2);
//                Log.d("CLEAR", Arrays.toString(dataTransformation.bytesToHexArray(receiveData)));
                    command = (short) BitUtilities.makeInt(headParse[0],headParse[1]);
                    //parsing
                    switch (command) {
                        case 0x0110:
                            noDataCount = 0;
                            disconnected = false;
                            break;
                        case 0x0104:
                            responseReceive(receiveData);
                            noDataCount = 0;
                            disconnected = false;
                            break;
                        case 0x0204:
                            responseReceivePowerMeter(receiveData);
                            noDataCount = 0;
                            disconnected = false;
                            break;
                        default:
                            if (noDataCount < 6500) noDataCount++;
                            if (noDataCount > 50) {
                                disconnected = true;
                            }
                            break;
                    }
                    Thread.sleep(150);
                } catch (Exception e) {
                    logger.error("receive error : {}", e.getMessage());
                }
            } catch (Exception e) {
                logger.error("thread receive error : {} ", e.getMessage());
            }
        }
    }

    private void responseReceivePowerMeter(byte[] srcData){
        try {
            System.arraycopy(srcData,0, powerReceiveData,0,77);
            if (CheckResponse(powerReceiveData)){
                int voltage = BitUtilities.makeInt(srcData[3],srcData[4],srcData[5],srcData[6]);    //소수 3째자리 표시
                int current = BitUtilities.makeInt(srcData[7],srcData[8],srcData[9],srcData[10]);   //소수 3째자리 표시
                long activePower = BitUtilities.makeInt(srcData[11],srcData[12],srcData[13],srcData[14]);    //W를 표시
                short frequency = (short) BitUtilities.makeInt(srcData[23],srcData[24]);
                long activeEnergy = BitUtilities.makeInt(srcData[71],srcData[72],srcData[73],srcData[74]);  //W로 표시

                rxData.setVoltage((long) (voltage * 0.001));
                rxData.setCurrent((long) (current * 0.001));
                rxData.setActivePower((long) (activePower * 0.1));  //w
                rxData.setActiveEnergy(activeEnergy * 10);               // 전력량 : w
                rxData.setFrequency((short) (frequency * 0.01));

                rxData.csOVR = rxData.getVoltage() >= maxVoltage;
                rxData.csUVR = rxData.getVoltage() <= minVoltage;
                rxData.csOCR = rxData.getCurrent() >= maxCurrent;

//                rxData.csFault = rxData.isCsEmergency() || rxData.isCsOVR() || rxData.isCsUVR() || rxData.isCsOCR();

//                rxData.csFault = rxData.isCsEmergency();

                txData.setHighPowerMeter(BitUtilities.makeShort(srcData[71],srcData[72]));
                txData.setLowPowerMeter(BitUtilities.makeShort(srcData[73],srcData[74]));
                txData.setOutVoltage((short) voltage);
                txData.setOutCurrent((short) current);
            }
        } catch (Exception e) {
            logger.error("responseReceivePowerMeter error : {}", e.getMessage());
        }
    }

    /**
     * POWER METER
     */
    private static final byte POWER_METER_ID = 0x02;
    private static final byte POWER_METER_READ = 0x04;
    byte[] powerMeterRequest = new byte[8];
    private boolean requestSend(){
        try {
            byte[] crc;
            powerMeterRequest[0] = POWER_METER_ID;
            powerMeterRequest[1] = POWER_METER_READ;
            powerMeterRequest[2] = 0X01;
            powerMeterRequest[3] = 0X00;
            powerMeterRequest[4] = 0X00;
            powerMeterRequest[5] = 0X24;
            crc = CRC16.setCrcModBusReverse(powerMeterRequest,0,powerMeterRequest.length-2);
            powerMeterRequest[6] = crc[0];
            powerMeterRequest[7] = crc[1];
            outputStream.write(powerMeterRequest);
//            Log.d("CLEAR", Arrays.toString(dataTransformation.bytesToHexArray(powerMeterRequest)));
            return true;
        } catch (Exception e) {
            logger.error("power meter requestSend() error : {}" , e.getMessage());
        }
        return false;
    }
}
