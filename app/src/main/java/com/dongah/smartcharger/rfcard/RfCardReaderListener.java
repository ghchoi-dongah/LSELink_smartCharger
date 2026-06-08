package com.dongah.smartcharger.rfcard;

public interface RfCardReaderListener {
    void onRfCardDataReceive(int ch, String cardNum, boolean value);
}
