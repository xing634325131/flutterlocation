package com.lyokone.location;

import android.os.Build;
import android.util.Log;
import androidx.annotation.Nullable;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;

final class MethodCallHandlerImpl implements MethodCallHandler {
    private static final String TAG = "MethodCallHandlerImpl";

    private final FlutterLocation location;
    @Nullable
    private MethodChannel channel;

    private static final String METHOD_CHANNEL_NAME = "lyokone/location";

    MethodCallHandlerImpl(FlutterLocation location) {
        this.location = location;
    }

    @Override
    public void onMethodCall(MethodCall call, Result result) {
        switch (call.method) {
        case "changeSettings":
            result.error("-1", "not support", "Method not support");
            //onChangeSettings(call, result);
            break;
        case "getLocation":
            onGetLocation(result);
            break;
        case "hasPermission":
            onHasPermission(result);
            break;
        case "requestPermission":
            onRequestPermission(result);
            break;
        case "serviceEnabled":
            location.checkServiceEnabled(result);
            break;
        case "requestService":
            location.requestService(result);
            break;
        default:
            result.notImplemented();
            break;
        }
    }

    /**
     * Registers this instance as a method call handler on the given
     * {@code messenger}.
     */
    void startListening(BinaryMessenger messenger) {
        if (channel != null) {
            Log.wtf(TAG, "Setting a method call handler before the last was disposed.");
            stopListening();
        }

        channel = new MethodChannel(messenger, METHOD_CHANNEL_NAME);
        channel.setMethodCallHandler(this);
    }

    /**
     * Clears this instance from listening to method calls.
     */
    void stopListening() {
        if (channel == null) {
            Log.d(TAG, "Tried to stop listening when no MethodChannel had been initialized.");
            return;
        }

        channel.setMethodCallHandler(null);
        channel = null;
    }

    private void onGetLocation(Result result) {
        location.getLocationResult = result;
        if (!location.checkPermissions()) {
            location.requestPermissions();
        } else {
            location.startRequestingLocation();
        }
    }

    private void onHasPermission(Result result) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            result.success(1);
            return;
        }

        if (location.checkPermissions()) {
            result.success(1);
        } else {
            result.success(0);
        }
    }

    private void onRequestPermission(Result result) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            result.success(1);
            return;
        }

        location.result = result;
        location.requestPermissions();
    }

}
