package com.example.upay;

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NfcAdapter;
import android.nfc.cardemulation.CardEmulation;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.logging.Logger;

public class BottomSheetNFC extends BottomSheetDialogFragment {
    public NfcAdapter mNfcAdapter;
    public CardEmulation cardEmulation;


    public static final int REQUEST_CODE_DEFAULT_PAYMENT_APP = 1;
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.activity_bottom_sheet_nfc, container, false);
        mNfcAdapter = NfcAdapter.getDefaultAdapter(getContext());
        if (mNfcAdapter != null) {
            cardEmulation = CardEmulation.getInstance(mNfcAdapter);
        }
        Payment();
        return  view;
    }

    public void Payment(){
        ComponentName componentName;
        if (mNfcAdapter == null) {
            Toast.makeText(getContext(), "NFC is not available", Toast.LENGTH_LONG).show();
        }
        else {
            Toast.makeText(getContext(), "NFC availability : "+mNfcAdapter.isEnabled(), Toast.LENGTH_LONG).show();
            //
            componentName = new ComponentName(getContext(), MyHostApduService.class);
            boolean isDefault = cardEmulation.isDefaultServiceForCategory(componentName, CardEmulation.CATEGORY_PAYMENT);
            if (!isDefault) {
                Intent intent = new Intent(CardEmulation.ACTION_CHANGE_DEFAULT);
                intent.putExtra(CardEmulation.EXTRA_CATEGORY, CardEmulation.CATEGORY_PAYMENT);
                intent.putExtra(CardEmulation.EXTRA_SERVICE_COMPONENT, componentName);
                getActivity().startActivityForResult(intent, REQUEST_CODE_DEFAULT_PAYMENT_APP);
            }
        }
    }

//    @Override
//    public void onResume() {
//        super.onResume();
//        //
//       // setAsPreferredHceService();
//        IntentFilter paymentSentIntentFilter = new IntentFilter(MyHostApduService.PAYMENT_SENT);
//        LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(getContext());
//        lbm.registerReceiver(animationBroadcastReceiver, paymentSentIntentFilter);
//      //  creditCardView.updateValues();
//
//    }
//
//    @Override
//    public void onPause() {
//        super.onPause();
//        //
//        //unsetAsPreferredHceService();
//        IntentFilter paymentSentIntentFilter = new IntentFilter(MyHostApduService.PAYMENT_SENT);
//        LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(getActivity());
//        lbm.unregisterReceiver(animationBroadcastReceiver);
//
//    }

//    BroadcastReceiver animationBroadcastReceiver = new BroadcastReceiver() {
//        @Override
//        public void onReceive(Context context, Intent intent) {
//            String action = intent.getAction();
//            if (action.equals(MyHostApduService.PAYMENT_SENT)) {
//                Toast.makeText(getContext(), "Payment Sent Broadcast Received", Toast.LENGTH_LONG).show();
//                // payView.start();
//            }
//        }
//    };

//    public void setAsPreferredHceService() {
//        if (mNfcAdapter != null) {
//            boolean allowsForeground = cardEmulation.categoryAllowsForegroundPreference(CardEmulation.CATEGORY_PAYMENT);
//            if (allowsForeground) {
//                ComponentName hceComponentName = new ComponentName(getContext(), MyHostApduService.class);
//                cardEmulation.setPreferredService(getActivity(), hceComponentName);
//            }
//        }
//    }
//    public void unsetAsPreferredHceService() {
//        if (mNfcAdapter != null) {
//            boolean allowsForeground = cardEmulation.categoryAllowsForegroundPreference(CardEmulation.CATEGORY_PAYMENT);
//            if (allowsForeground) {
//                ComponentName hceComponentName = new ComponentName(getContext(), MyHostApduService.class);
//                cardEmulation.unsetPreferredService(getActivity());
//            }
//        }
//    }

//    private void startEditMagstripeActivity() {
//        Intent dialogIntent = new Intent(getContext(), EditMagstripeActivity.class);
//        dialogIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//        startActivity(dialogIntent);
//    }
}