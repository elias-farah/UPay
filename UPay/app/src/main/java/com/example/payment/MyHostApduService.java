package com.example.payment;

import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationManager;
import android.nfc.cardemulation.HostApduService;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;

import androidx.annotation.NonNull;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.application.isradeleon.notify.Notify;
import com.example.upay.JavaMailAPI;
import com.example.upay.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.nio.ByteBuffer;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


//import static com.example.payment.ConstantCard.DEFAULT_SWIPE_DATA;
import static com.example.payment.ConstantCard.DEFAULT_SWIPE_DATA;
import static com.example.payment.ConstantCard.SWIPE_DATA_PREF_KEY;

public class MyHostApduService extends HostApduService implements SharedPreferences.OnSharedPreferenceChangeListener {

    private FirebaseUser user;
    private FirebaseStorage storage;
    private StorageReference storageReference;
    private DatabaseReference mDatabase;

    private static final String TAG = MyHostApduService.class.getSimpleName();

    private static final byte[] ISO7816_UNKNOWN_ERROR_RESPONSE = {
            (byte)0x6F, (byte)0x00
    };
    /*
     *  PPSE (Proximity Payment System Environment)
     *  This is the first select that a point of sale device will send to the payment device.
     */
    private static final byte[] PPSE_APDU_SELECT = {
            (byte)0x00, // CLA (class of command)
            (byte)0xA4, // INS (instruction); A4 = select
            (byte)0x04, // P1  (parameter 1)  (0x04: select by name)
            (byte)0x00, // P2  (parameter 2)
            (byte)0x0E, // LC  (length of data)  14 (0x0E) = length("2PAY.SYS.DDF01")
            // 2PAY.SYS.DDF01 (ASCII values of characters used):
            // This value requests the card or payment device to list the application
            // identifiers (AIDs) it supports in the response:
            '2', 'P', 'A', 'Y', '.', 'S', 'Y', 'S', '.', 'D', 'D', 'F', '0', '1',
            (byte)0x00 // LE   (max length of expected result, 0 implies 256)
    };
    private static final byte[] PPSE_APDU_SELECT_RESP = {
            (byte)0x6F,  // FCI Template
            (byte)0x23,  // length = 35
            (byte)0x84,  // DF Name
            (byte)0x0E,  // length("2PAY.SYS.DDF01")
            // Data (ASCII values of characters used):
            '2', 'P', 'A', 'Y', '.', 'S', 'Y', 'S', '.', 'D', 'D', 'F', '0', '1',
            (byte)0xA5, // FCI Proprietary Template
            (byte)0x11, // length = 17
            (byte)0xBF, // FCI Issuer Discretionary Data
            (byte)0x0C, // length = 12
            (byte)0x0E,
            (byte)0x61, // Directory Entry
            (byte)0x0C, // Entry length = 12
            (byte)0x4F, // ADF Name
            (byte)0x07, // ADF Length = 7
            (byte)0xA0, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x03, // VISA;

            (byte)0x10, (byte)0x10,
            (byte)0x87,  // Application Priority Indicator
            (byte)0x01,  // length = 1
            (byte)0x01,
            (byte) 0x90, // SW1  (90 00 = Success)
            (byte) 0x00  // SW2
    };
    /*
     *  MSD (Magnetic Stripe Data)
     */
    private static final byte[] VISA_MSD_SELECT = {
            (byte)0x00,  // CLA
            (byte)0xa4,  // INS
            (byte)0x04,  // P1
            (byte)0x00,  // P2
            (byte)0x07,  // LC (data length = 7)
            // POS is selecting the AID (Visa debit or credit) that we specified in the PPSE
            // response:
            (byte)0xA0, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x03, (byte)0x10, (byte)0x10,
            (byte)0x00   // LE
    };
    private static final byte[] VISA_MSD_SELECT_RESPONSE = {
            (byte) 0x6F,  // File Control Information (FCI) Template
            (byte) 0x1E,  // length = 30 (0x1E)
            (byte) 0x84,  // Dedicated File (DF) Name
            (byte) 0x07,  // DF length = 7
            // A0000000031010  (Visa debit or credit AID)
            (byte)0xA0, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x03, (byte)0x10, (byte)0x10,
            (byte) 0xA5,  // File Control Information (FCI) Proprietary Template
            (byte) 0x13,  // length = 19 (0x13)
            (byte) 0x50,  // Application Label
            (byte) 0x0B,  // length
            'V', 'I', 'S', 'A', ' ', 'C', 'R', 'E', 'D', 'I', 'T',
            (byte) 0x9F, (byte) 0x38,  // Processing Options Data Object List (PDOL)
            (byte) 0x03,  // length
            (byte) 0x9F, (byte) 0x66, (byte) 0x02, // PDOL value (Does this request terminal type?)
            (byte) 0x90,  // SW1
            (byte) 0x00   // SW2
    };
    /*
     *  GPO (Get Processing Options) command
     */
    private static final byte[] GPO_COMMAND = {
            (byte) 0x80,  // CLA
            (byte) 0xA8,  // INS
            (byte) 0x00,  // P1
            (byte) 0x00,  // P2
            (byte) 0x04,  // LC (length)
            // Data
            (byte) 0x83,  // tag
            (byte) 0x02,  // length
            (byte) 0x80,    //  { These 2 bytes can vary, so we'll only        }
            (byte) 0x00,    //  { compare the header of this GPO command below }
            (byte) 0x00
    };

    private boolean isGpoCommand(byte[] apdu) {
        return (apdu.length > 4 &&
                apdu[0] == GPO_COMMAND[0] &&
                apdu[1] == GPO_COMMAND[1] &&
                apdu[2] == GPO_COMMAND[2] &&
                apdu[3] == GPO_COMMAND[3]
        );
    }
    private static final byte[] GPO_COMMAND_RESPONSE = {
            (byte) 0x80,
            (byte) 0x06,  // length
            (byte) 0x00,
            (byte) 0x80,
            (byte) 0x08,
            (byte) 0x01,
            (byte) 0x01,
            (byte) 0x00,
            (byte) 0x90,  // SW1
            (byte) 0x00   // SW2
    };
    private static final byte[] READ_REC_COMMAND = {
            (byte) 0x00,  // CLA
            (byte) 0xB2,  // INS
            (byte) 0x01,  // P1
            (byte) 0x0C,  // P2
            (byte) 0x00   // length
    };
    private static final Pattern TRACK_2_PATTERN = Pattern.compile(".*;(\\d{12,19}=\\d{1,128})\\?.*");

    private static byte[] readRecResponse = {};

    private static void configureReadRecResponse(String swipeData) {
        Matcher matcher = TRACK_2_PATTERN.matcher(swipeData);
        if (matcher.matches()) {
            String track2EquivData = matcher.group(1);
            // convert the track 2 data into the required byte representation
            track2EquivData = track2EquivData.replace('=', 'D');
            if (track2EquivData.length() % 2 != 0) {
                // add an 'F' to make the hex string a whole number of bytes wide
                track2EquivData += "F";
            }
            // Each binary byte is represented by 2 4-bit hex characters
            int track2EquivByteLen = track2EquivData.length()/2;

            readRecResponse = new byte[6 + track2EquivByteLen];

            ByteBuffer bb = ByteBuffer.wrap(readRecResponse);
            bb.put((byte) 0x70);                            // EMV Record Template tag
            bb.put((byte) (track2EquivByteLen + 2));        // Length with track 2 tag
            bb.put((byte) 0x57);                                // Track 2 Equivalent Data tag
            bb.put((byte)track2EquivByteLen);                   // Track 2 data length
            bb.put(Util.hexToByteArray(track2EquivData));           // Track 2 equivalent data
            bb.put((byte) 0x90);                            // SW1
            bb.put((byte) 0x00);                            // SW2
        } else {
        }

    }
    @Override
    public byte[] processCommandApdu(byte[] commandApdu, Bundle bundle) {
        String inboundApduDescription;
        byte[] responseApdu;

        if (Arrays.equals(PPSE_APDU_SELECT, commandApdu)) {
            inboundApduDescription = "Received PPSE select: ";
            responseApdu = PPSE_APDU_SELECT_RESP;
        } else if (Arrays.equals(VISA_MSD_SELECT, commandApdu)) {
            inboundApduDescription =  "Received Visa-MSD select: ";
            responseApdu =  VISA_MSD_SELECT_RESPONSE;
        } else if (isGpoCommand(commandApdu)) {
            inboundApduDescription =  "Received GPO (get processing options): ";
            responseApdu =  GPO_COMMAND_RESPONSE;
        } else if (Arrays.equals(READ_REC_COMMAND, commandApdu)) {
            inboundApduDescription = "Received READ REC: ";
            responseApdu = readRecResponse;
        } else {
            inboundApduDescription = "Received Unhandled APDU: ";
            responseApdu = ISO7816_UNKNOWN_ERROR_RESPONSE;
        }
        return responseApdu;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if (SWIPE_DATA_PREF_KEY.equals(key)) {
//            user = FirebaseAuth.getInstance().getCurrentUser();
//            FirebaseDatabase.getInstance().getReference("Users").child(user.getUid()).addValueEventListener(new ValueEventListener() {
//                @Override
//                public void onDataChange(DataSnapshot dataSnapshot) {
            String swipeData = prefs.getString(SWIPE_DATA_PREF_KEY, DEFAULT_SWIPE_DATA);
            configureReadRecResponse(swipeData);
//                }
//                @Override
//                public void onCancelled(@NonNull DatabaseError error) {
//
//                }
//            });
//        }
        }
    }
    public void onCreate() {
        super.onCreate();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        //
//        user = FirebaseAuth.getInstance().getCurrentUser();
//        FirebaseDatabase.getInstance().getReference("Users").child(user.getUid()).addValueEventListener(new ValueEventListener() {
//            @Override
//            public void onDataChange(DataSnapshot dataSnapshot) {
                String swipeData = prefs.getString(SWIPE_DATA_PREF_KEY, DEFAULT_SWIPE_DATA);
                configureReadRecResponse(swipeData);
                prefs.registerOnSharedPreferenceChangeListener(this);
//            }
//            @Override
//            public void onCancelled(@NonNull DatabaseError error) {
//
//            }
//        });
        int x = 1;
        if (x == 1) {
            sendData();
            //
            Intent intent = new Intent("my-message");
            // Adding some data
            intent.putExtra("my-integer", 1);
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
            x++;
        }
        else{

        }
    }

    @Override
    public void onDeactivated(int reason) {

    }


    public void sendData() {
        // Get location if available
        //
        user = FirebaseAuth.getInstance().getCurrentUser();
        mDatabase = FirebaseDatabase.getInstance().getReference();
        FirebaseDatabase.getInstance().getReference("Users").child(user.getUid()).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                //
                String Location = dataSnapshot.child("Location").child("Current Country Location").getValue().toString();
                String Place = dataSnapshot.child("Location").child("Current Place Location").getValue().toString();
                String lon = dataSnapshot.child("Location").child("Longitude").getValue().toString();
                String lat = dataSnapshot.child("Location").child("Latitude").getValue().toString();
                String count = dataSnapshot.child("User Data").child("Transaction count").getValue().toString();
                String Switch3 = dataSnapshot.child("Switches").child("Switch3").getValue().toString();
                String Switch4 = dataSnapshot.child("Switches").child("Switch4").getValue().toString();
                String Currency = dataSnapshot.child("Currency").child("Currency").getValue().toString();
                //
                String AmountFINAL = "21";
                //
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    LocalDate date = LocalDate.now();
                    DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd-MM-yyyy");
                    String text = date.format(dtf);
                    //
                    LocalDate parsedDate = LocalDate.parse(text, dtf);
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/YYYY");
                    //
                    user = FirebaseAuth.getInstance().getCurrentUser();
                    mDatabase = FirebaseDatabase.getInstance().getReference();
                    //
                    if (Currency.equals("$")){
                    }
                    else if (Currency.equals("???")){
                        Double new_val = Double.parseDouble(AmountFINAL);
                        AmountFINAL = new DecimalFormat("##.##").format(new_val*1.20);
                    }
                    else if (Currency.equals("$CA")){
                        Double new_val = Double.parseDouble(AmountFINAL);
                        AmountFINAL = new DecimalFormat("##.##").format(new_val*0.81);
                    }

                    //
                    HashMap<String, Object> values = new HashMap<>();
                    //TODO() Certain Values are For Testing;
                    values.put("Name", Place);
                    values.put("Location", Location);
                    values.put("Amount", AmountFINAL);
                    values.put("Date", formatter.format(parsedDate));
                    values.put("Longitude", lon);
                    values.put("Latitude", lat);
                    //
                    mDatabase.child("Users").child(user.getUid()).child("Transactions").child(count).setValue(values);
                    int x = Integer.parseInt(count) + 1;
                    HashMap<String, Object> values2 = new HashMap<>();
                    values2.put("Transaction count", x);
                    mDatabase.child("Users").child(user.getUid()).child("User Data").setValue(values2);
                    //
                    if (Switch3.equals("true")) {
                        BottomSheetNFC m = new BottomSheetNFC();
                        PurchaseNotification(Place, Location, formatter.format(parsedDate), AmountFINAL,Currency);
                    }
                    if (Switch4.equals("true")) {
                        //TODO() Certain Values are For Testing;
                        BottomSheetNFC m = new BottomSheetNFC();
                        sendEmail(Place, Location, formatter.format(parsedDate), AmountFINAL,Currency);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
            //
        });
    }
    public void sendEmail(String Name,String Location,String Date,String Amount,String Currency) {
        user = FirebaseAuth.getInstance().getCurrentUser();
        String mEmail = user.getEmail();
        String mSubject = "UPay- "+Name+" Transaction";
        String mMessage = "Your Recent Transaction at "+Name+":" +
                "\nLocation: "+Location
                +"\nThe amount of "+Currency+Amount+" has been spent on " +Date;

        JavaMailAPI javaMailAPI = new JavaMailAPI(getApplicationContext(), mEmail, mSubject, mMessage);

        javaMailAPI.execute();
    }

    public void PurchaseNotification(String Name,String Location,String Date,String Amount,String Currency){
        // String mSubject = "UPay- "+Name+" Transaction";
        String mMessage = "Your Recent Transaction at "+Name+":" +
                "\nLocation: "+Location
                +"\nThe amount of "+Currency+Amount+" has been spent on " +Date;
        Notify.build(getApplicationContext())
                .setTitle("UPay")
                .setContent(mMessage)
                .setSmallIcon(R.drawable.ic_payment)
                .setColor(R.color.color4)
                .largeCircularIcon()
                .show(); // Show notification
    }
    }