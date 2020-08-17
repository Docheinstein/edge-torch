package org.docheinstein.torch;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraManager.TorchCallback;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;

import androidx.annotation.NonNull;

import com.samsung.android.sdk.look.cocktailbar.SlookCocktailManager;
import com.samsung.android.sdk.look.cocktailbar.SlookCocktailProvider;


public class EdgeSinglePlusReceiver extends SlookCocktailProvider {

    private static final String TAG = EdgeSinglePlusReceiver.class.getSimpleName();

    private static final String ACTION_ENABLE_TORCH = "org.docheinstein.torch.ACTION_ENABLE_TORCH";
    private static final String ACTION_DISABLE_TORCH = "org.docheinstein.torch.ACTION_DISABLE_TORCH";
    private static final String ACTION_TOGGLE_TORCH = "org.docheinstein.torch.ACTION_TOGGLE_TORCH";
    private static final String EXTRA_COCKTAIL_ID = "cocktailId";

    private enum TorchState {
        Unavailable,
        Enabled,
        Disabled
        ;

        public TorchState opposite() {
            if (this == Unavailable)
                throw new RuntimeException("Cannot call opposite() on Unavailable");
            return this == Enabled ? Disabled : Enabled;
        }

        public static TorchState fromBool(boolean enabled) {
            return enabled ? Enabled : Disabled;
        }

        public boolean value() {
            if (this == Unavailable)
                throw new RuntimeException("Cannot call value() on Unavailable");
            return this == Enabled;
        }
    }

    private class TorchCallbackReceiver extends TorchCallback {

        private Context mContext;
        private int mCocktailId;

        private TorchCallbackReceiver(Context context, int cocktailId) {
            mContext = context;
            mCocktailId = cocktailId;
        }

        @Override
        public void onTorchModeChanged(@NonNull String cameraId, boolean enabled) {
            if (!cameraId.equals(sCameraId)) {
                Log.w(TAG, "Ignoring onTorchModeChanged for camera [" + cameraId + "]");
                return;
            }

            Log.i(TAG, "Torch [" + cameraId + "] mode changed: " + enabled);
            sTorchState = TorchState.fromBool(enabled);
            renderCocktail(mContext, mCocktailId);
        }

        @Override
        public void onTorchModeUnavailable(@NonNull String cameraId) {
            if (!cameraId.equals(sCameraId)) {
                Log.w(TAG, "Ignoring onTorchModeUnavailable for camera [" + cameraId + "]");
                return;
            }

            Log.i(TAG, "Torch [" + cameraId + "] unavailable");
            sTorchState = TorchState.Unavailable;
            renderCocktail(mContext, mCocktailId);
        }
    }


    private static final Object sLock = new Object();
    private static String sCameraId = null;

    private static TorchState sTorchState = TorchState.Unavailable;
    private static TorchCallbackReceiver sTorchCallback = null;



    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        if (action == null) {
            Log.w(TAG, "Invalid action onReceive");
            return;
        }

        Log.i(TAG, "[" + hashCode() + "] onReceive: " + action);

        int cocktailId = -1;
        Bundle extras = intent.getExtras();
        if (extras != null && extras.containsKey(EXTRA_COCKTAIL_ID)) {
            cocktailId = extras.getInt(EXTRA_COCKTAIL_ID);
        }

        //noinspection SwitchStatementWithTooFewBranches
        switch (action) {
            case ACTION_TOGGLE_TORCH:
                if (sTorchState != TorchState.Unavailable)
                    onSetTorchState(context, cocktailId, sTorchState.opposite());
                break;
            case ACTION_ENABLE_TORCH:
                onSetTorchState(context, cocktailId, TorchState.Enabled);
                break;
            case ACTION_DISABLE_TORCH:
                onSetTorchState(context, cocktailId, TorchState.Disabled);
                break;
            default:
                super.onReceive(context, intent);
                break;
        }
    }

    @Override
    public void onEnabled(Context context) {
        Log.i(TAG, "onEnabled");
    }

    @Override
    public void onDisabled(Context context) {
        Log.i(TAG, "onDisabled");
    }

    @Override
    public void onVisibilityChanged(Context context, int cocktailId, int visibility) {
        boolean visible = visibility == SlookCocktailManager.COCKTAIL_VISIBILITY_SHOW;
        Log.i(TAG, "onVisibilityChanged, visible = " + visible);
        if (visible)
            synchronized (sLock) {
                renderCocktail(context, cocktailId);
            }
    }

    @Override
    public void onUpdate(final Context context, SlookCocktailManager cocktailManager, int[] cocktailIds) {
        if (cocktailIds == null || cocktailIds.length != 1) {
            Log.w(TAG, "Unexpected cocktails array");
            return;
        }

        final int cocktailId = cocktailIds[0];

        Log.i(TAG, "onUpdate {" + cocktailId + "}");

        synchronized (sLock) {

            CameraManager cameraManager = context.getSystemService(CameraManager.class);

            if (cameraManager == null) {
                Log.e(TAG, "No camera available!");
                return;
            }


            if (sCameraId == null) {
                try {
                    Log.d(TAG, "Trying to retrieve camera list");
                    String[] cameraIds = cameraManager.getCameraIdList();

                    if (cameraIds.length == 0) {
                        Log.e(TAG, "No camera available!");
                        return;
                    }

                    for (String cameraId : cameraIds) {
                        Log.i(TAG, "Found camera: " + cameraId);
                    }

                    sCameraId = cameraIds[0];
                    Log.i(TAG, "Using the camera: " + sCameraId);

                } catch (CameraAccessException e) {
                    Log.e(TAG, "Failed to retrieve camera ids", e);
                    return;
                }
            }

            if (sTorchCallback == null) {
                Log.d(TAG, "Initializing torch callback");
                sTorchCallback = new TorchCallbackReceiver(context, cocktailId);
            }

            cameraManager.registerTorchCallback(sTorchCallback, null);

            renderCocktail(context, cocktailId);
        }
    }

    private RemoteViews createPanelView(Context context, int cocktailId) {
        Log.v(TAG, "Creating panel view");

        RemoteViews panelView = new RemoteViews(
                BuildConfig.APPLICATION_ID, R.layout.single_plus_panel_layout);

        setViewAction(context, panelView, cocktailId, R.id.flashlightOn,
                EdgeSinglePlusReceiver.ACTION_DISABLE_TORCH);
        setViewAction(context, panelView, cocktailId, R.id.flashlightOff,
                EdgeSinglePlusReceiver.ACTION_ENABLE_TORCH);
        return panelView;
    }

    private void setViewAction(Context context, RemoteViews remoteView, int cocktailId, int viewId, String action) {
        Intent clickIntent = new Intent(context, EdgeSinglePlusReceiver.class);
        clickIntent.setAction(action);
        clickIntent.putExtra(EdgeSinglePlusReceiver.EXTRA_COCKTAIL_ID, cocktailId);

        PendingIntent clickPendingIntent = PendingIntent.getBroadcast(
                context, 0, clickIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        remoteView.setOnClickPendingIntent(viewId, clickPendingIntent);
    }


    private void onSetTorchState(final Context context, final int cocktailId, TorchState newTorchState) {
        synchronized (sLock) {
            CameraManager cameraManager = context.getSystemService(CameraManager.class);

            if (cameraManager == null) {
                Log.e(TAG, "No camera available!");
                return;
            }

            try {
                Log.i(TAG, "Changing torch state: " + newTorchState);
                cameraManager.setTorchMode(sCameraId, newTorchState.value());
            } catch (CameraAccessException e) {
                Log.e(TAG, "Failed to set torch state", e);
                return;
            } catch (RuntimeException re) {
                Log.w(TAG, "Runtime exception", re);
            }

            renderCocktail(context, cocktailId);
        }
    }



    private void renderCocktail(Context context, int cocktailId) {
        RemoteViews panelView = createPanelView(context, cocktailId);

        if (!hasFlashlight(context)) {
            Log.e(TAG, "No flashlight available!");
            panelView.setViewVisibility(R.id.flashlightUnavailableText, View.VISIBLE);
            panelView.setViewVisibility(R.id.flashlightOn, View.GONE);
            panelView.setViewVisibility(R.id.flashlightOff, View.GONE);
            return;
        }

        Log.d(TAG, "Updating panel view for state: " + sTorchState);

        panelView.setViewVisibility(R.id.flashlightUnavailableText, View.GONE);

        panelView.setViewVisibility(R.id.flashlightOn,
                sTorchState == TorchState.Enabled ? View.VISIBLE : View.GONE);
        panelView.setViewVisibility(R.id.flashlightOff,
                sTorchState == TorchState.Disabled ? View.VISIBLE : View.GONE);

        SlookCocktailManager.getInstance(context).updateCocktail(cocktailId, panelView);
    }

    private static boolean hasFlashlight(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH);
    }
}
