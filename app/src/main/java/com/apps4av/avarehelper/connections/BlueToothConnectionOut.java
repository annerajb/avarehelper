/*
Copyright (c) 2012, Apps4Av Inc. (apps4av.com) 
All rights reserved.

Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:

    * Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
    *     * Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
    *
    *     THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package com.apps4av.avarehelper.connections;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.json.JSONObject;

import com.apps4av.avarehelper.nmea.GGAPacket;
import com.apps4av.avarehelper.nmea.RMBPacket;
import com.apps4av.avarehelper.nmea.RMCPacket;
import com.apps4av.avarehelper.utils.Logger;
import com.ds.avare.IHelper;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;

/**
 * 
 * @author zkhan
 *
 */
public class BlueToothConnectionOut {

    private static BluetoothAdapter mBtAdapter = null;
    private static BluetoothSocket mBtSocket = null;
    private static OutputStream mStream = null;
    private static boolean mRunning = false;
    private boolean mSecure = true;
    
    private static BlueToothConnectionOut mConnection;
    
    private static ConnectionStatus mConnectionStatus;
    private static IHelper mHelper;
    
    private Thread mThread;
    private String mDevName;

    /*
     *  Well known SPP UUID
     */
    private static final UUID MY_UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    /**
     * 
     */
    private BlueToothConnectionOut() {
    }

    
    /**
     * 
     * @return
     */
    public static BlueToothConnectionOut getInstance() {

        if(null == mConnection) {
            mConnection = new BlueToothConnectionOut();
            mBtAdapter = BluetoothAdapter.getDefaultAdapter();
            mConnectionStatus = new ConnectionStatus();
            mConnectionStatus.setState(ConnectionStatus.DISCONNECTED);
        }
        return mConnection;
    }

    /**
     * 
     */
    public void stop() {
        Logger.Logit("Stopping BT");
        if(mConnectionStatus.getState() != ConnectionStatus.CONNECTED) {
            Logger.Logit("Stopping BT failed because already stopped");
            return;
        }
        mRunning = false;
        if(null != mThread) {
            mThread.interrupt();
        }
    }

    /**
     * 
     */
    public void start() {
        
        Logger.Logit("Starting BT");
        if(mConnectionStatus.getState() != ConnectionStatus.CONNECTED) {
            Logger.Logit("Starting BT failed because already started");
            return;
        }
        
        mRunning = true;
        
        /*
         * Thread that reads BT
         */
        mThread = new Thread() {
            @Override
            public void run() {
                
                Logger.Logit("BT writing data");

                /*
                 * This state machine will keep trying to connect to 
                 * ADBS/GPS receiver
                 */
                while(mRunning) {
                    
                    /*
                     * Read data from Avare
                     */
                    String recvd = null;
                    try {
                        recvd = mHelper.recvDataText();
                    } catch (Exception e1) {
                        try {
                            Thread.sleep(1000);
                        } catch (Exception e) {
                            
                        }
                        continue;
                    }
                    
                    if(null == recvd) {
                        continue;
                    }

                    /*
                     * Send to BT
                     */
                    byte buffer[] = null;
                    byte buffer2[] = null;
                    byte buffer3[] = null;
                    try {
                        JSONObject object;
                        object = new JSONObject(recvd);
                        Logger.Logit("sending message to BT " + mDevName + " " + 
                                object.toString());
                        String type = object.getString("type");
                        if(type == null) {
                            continue;
                        }
                        if(type.equals("ownship")) {

                            RMCPacket pkt = new RMCPacket(object.getLong("time"),
                                    object.getDouble("latitude"),
                                    object.getDouble("longitude"),
                                    object.getDouble("speed"),
                                    object.getDouble("bearing"));
                            buffer = pkt.getPacket().getBytes();
                            GGAPacket pkt2 = new GGAPacket(object.getLong("time"),
                                    object.getDouble("latitude"),
                                    object.getDouble("longitude"),
                                    object.getDouble("altitude"));
                            buffer2 = pkt2.getPacket().getBytes();
                            RMBPacket pkt3 = new RMBPacket(object.getLong("time"),
                                    object.getDouble("destDistance"),
                                    object.getDouble("destBearing"),
                                    object.getDouble("destLongitude"),
                                    object.getDouble("destLatitude"),
                                    object.getDouble("destId"),
                                    object.getDouble("destOriginId"),
                                    object.getDouble("destDeviation"),
                                    object.getDouble("speed"));
                            buffer3 = pkt3.getPacket().getBytes();
                        }
                    } catch (Exception e) {
                        continue;
                    }
                    
                    if(null == buffer || null == buffer2) {
                        continue;
                    }
                                        
                    /*
                     * Make NMEA messages.
                     */
                    
                    /*
                     * Write.
                     */
                    int wrote = write(buffer);
                    if(wrote <= 0) {
                        if(!mRunning) {
                            break;
                        }
                        try {
                            Thread.sleep(1000);
                        } catch (Exception e) {
                            
                        }
                        
                        /*
                         * Try to reconnect
                         */
                        Logger.Logit("Disconnected from BT device, retrying to connect");

                        disconnect();
                        connect(mDevName, mSecure);
                        continue;
                    }

                    wrote = write(buffer2);
                    if(wrote <= 0) {
                        if(!mRunning) {
                            break;
                        }
                        try {
                            Thread.sleep(1000);
                        } catch (Exception e) {
                            
                        }
                        
                        /*
                         * Try to reconnect
                         */
                        Logger.Logit("Disconnected from BT device, retrying to connect");

                        disconnect();
                        connect(mDevName, mSecure);
                        continue;
                    }

                    // RMB
                    if(buffer3 == null) {
                        continue;
                    }
                    if(buffer3.length <= 0) {
                        continue;
                    }
                    wrote = write(buffer3);
                    if(wrote <= 0) {
                        if(!mRunning) {
                            break;
                        }
                        try {
                            Thread.sleep(1000);
                        } catch (Exception e) {
                            
                        }
                        
                        /*
                         * Try to reconnect
                         */
                        Logger.Logit("Disconnected from BT device, retrying to connect");

                        disconnect();
                        connect(mDevName, mSecure);
                        continue;
                    }

                }
            }
        };
        mThread.start();
    }
    
    /**
     * 
     * @param state
     */
    private void setState(int state) {
        mConnectionStatus.setState(state);
    }
    
    /**
     * 
     * @return
     */
    public List<String> getDevices() {
        List<String> list = new ArrayList<String>();
        if(null == mBtAdapter) {
            return list;
        }

        Set<BluetoothDevice> pairedDevices = mBtAdapter.getBondedDevices();
        
        /*
         * Find devices
         */
        if(null == pairedDevices) {            
            return list;
        }
        for(BluetoothDevice bt : pairedDevices) {
            list.add((String)bt.getName());
        }
        
        return list;
    }
    
    /**
     * 
     * A device name devNameMatch, will connect to first device whose
     * name matched this string.
     * @return
     */
    public boolean connect(String devNameMatch, boolean secure) {
        
        Logger.Logit("Connecting to device " + devNameMatch);

        if(devNameMatch == null) {
            return false;
        }
        
        mDevName = devNameMatch;
        mSecure = secure;
        
        /*
         * Only when not connected, connect
         */
        if(mConnectionStatus.getState() != ConnectionStatus.DISCONNECTED) {
            Logger.Logit("Failed! Already connected?");

            return false;
        }
        setState(ConnectionStatus.CONNECTING);
        if(null == mBtAdapter) {
            Logger.Logit("Failed! BT adapter not found");

            setState(ConnectionStatus.DISCONNECTED);
            return false;
        }
        Set<BluetoothDevice> pairedDevices = mBtAdapter.getBondedDevices();

        /*
         * Find device
         */
        if(null == pairedDevices) {            
            Logger.Logit("Failed! No paired devices");
            setState(ConnectionStatus.DISCONNECTED);
            return false;
        }
        
        Logger.Logit("BT finding devices");

        BluetoothDevice device = null;
        for(BluetoothDevice bt : pairedDevices) {
           if(bt.getName().equals(devNameMatch)) {
               device = bt;
           }
        }
   
        /*
         * Stop discovery
         */
        mBtAdapter.cancelDiscovery();
 
        if(null == device) {
            Logger.Logit("Failed! No such device");

            setState(ConnectionStatus.DISCONNECTED);
            return false;
        }
        
        /*
         * Make socket
         */
        Logger.Logit("Finding socket for SPP secure = " + mSecure);

        if(secure) {
            try {
                mBtSocket = device.createRfcommSocketToServiceRecord(MY_UUID);
            } 
            catch(Exception e) {
                Logger.Logit("Failed! SPP secure socket failed");
    
                setState(ConnectionStatus.DISCONNECTED);
                return false;
            }
        }
        else {
            try {
                mBtSocket = device.createInsecureRfcommSocketToServiceRecord(MY_UUID);
            } 
            catch(Exception e1) {
                Logger.Logit("Failed! SPP insecure socket failed");
                
                setState(ConnectionStatus.DISCONNECTED);
                return false;
            }            
        }
    
        /*
         * Establish the connection.  This will block until it connects.
         */
        Logger.Logit("Connecting socket");

        try {
            mBtSocket.connect();
        } 
        catch(Exception e) {
            try {
                mBtSocket.close();
            } 
            catch(Exception e2) {
            }
            Logger.Logit("Failed! Socket connection error");

            setState(ConnectionStatus.DISCONNECTED);
            return false;
        } 

        Logger.Logit("Getting input stream");

        try {
            mStream = mBtSocket.getOutputStream();
        } 
        catch (Exception e) {
            try {
                mBtSocket.close();
            } 
            catch(Exception e2) {
            }
            Logger.Logit("Failed! Input stream error");

            setState(ConnectionStatus.DISCONNECTED);
        } 

        setState(ConnectionStatus.CONNECTED);

        Logger.Logit("Success!");

        return true;
    }
    
    /**
     * 
     */
    public void disconnect() {
        
        Logger.Logit("Disconnecting from device");

        /*
         * Exit
         */
        try {
            mStream.close();
        } 
        catch(Exception e2) {
            Logger.Logit("Error stream close");
        }
        
        try {
            mBtSocket.close();
        } 
        catch(Exception e2) {
            Logger.Logit("Error socket close");
        }    
        setState(ConnectionStatus.DISCONNECTED);
        Logger.Logit("Disconnected");
    }
    
    /**
     * 
     * @return
     */
    private int write(byte[] buffer) {
        int wrote = buffer.length;
        try {
            mStream.write(buffer, 0, buffer.length);
        } 
        catch(Exception e) {
            wrote = -1;
        }
        return wrote;
    }

    /**
     * 
     * @return
     */
    public boolean isConnected() {
        return mConnectionStatus.getState() == ConnectionStatus.CONNECTED;
    }

    /**
     * 
     * @return
     */
    public boolean isConnectedOrConnecting() {
        return mConnectionStatus.getState() == ConnectionStatus.CONNECTED ||
                mConnectionStatus.getState() == ConnectionStatus.CONNECTING;
    }

    /**
     * 
     * @param helper
     */
    public void setHelper(IHelper helper) {
        mHelper = helper;
    }
    
    /**
     * 
     * @return
     */
    public boolean isSecure() {
        return mSecure;
    }

    /**
     * 
     * @return
     */
    public String getConnDevice() {
        return mDevName;
    }

}
