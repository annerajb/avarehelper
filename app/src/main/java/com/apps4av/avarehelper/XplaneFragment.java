/*
Copyright (c) 2012, Apps4Av Inc. (apps4av.com) 
All rights reserved.

Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:

    * Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
    *     * Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
    *
    *     THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/
package com.apps4av.avarehelper;


import com.apps4av.avarehelper.connections.XplaneConnection;
import com.apps4av.avarehelper.storage.SavedEditText;
import com.apps4av.avarehelper.utils.Logger;
import com.apps4av.avarehelper.utils.Util;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;
import android.support.v4.app.*;

/**
 * 
 * @author zkhan
 *
 */
public class XplaneFragment extends Fragment {
    
    private XplaneConnection mXp;
    private SavedEditText mTextXplanePort;
    private TextView mTextXplaneIp;
    private CheckBox mXplaneCb;

    private Context mContext;

    @Override  
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {  
        
        mContext = container.getContext();

        View view = inflater.inflate(R.layout.layout_xplane, container, false);
        
        mTextXplaneIp = (TextView)view.findViewById(R.id.main_xplane_ip);
        mTextXplanePort = (SavedEditText)view.findViewById(R.id.main_xplane_port);
        mXplaneCb = (CheckBox)view.findViewById(R.id.main_button_xplane_connect);
        mTextXplaneIp.setText(mTextXplaneIp.getText() + "(" + Util.getIpAddr(mContext) + ")");
        mXplaneCb.setOnClickListener(new OnClickListener() {
            
            
            @Override
            public void onClick(View v) {
                if (((CheckBox) v).isChecked()) {
                    try {
                        mXp.connect(Integer.parseInt(mTextXplanePort.getText().toString()));
                    }
                    catch (Exception e) {
                        /*
                         * Number parse
                         */
                        Logger.Logit("Invalid port");
                    }
                    mXp.start();
                }
                else {
                    mXp.stop();
                    mXp.disconnect();
                }
            }
        });


        /*
         * List of BT devices is same
         */
        mXp = XplaneConnection.getInstance();
        
        setStates();
        
        return view;  
        
    }

    /**
     * 
     */
    private void setStates() {
        mXplaneCb.setChecked(mXp.isConnected());

        if(mXp.getPort() != 0) {
            mTextXplanePort.setText("" + mXp.getPort());
        }

    }

    @Override  
    public void onDestroyView() {  
        super.onDestroyView();
    }

} 