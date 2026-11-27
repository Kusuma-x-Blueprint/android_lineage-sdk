/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.internal.statusbar;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.TrafficStats;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.UserHandle;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.Display;
import android.widget.TextView;

import lineageos.providers.LineageSettings;

import org.lineageos.platform.internal.R;

import java.util.HashMap;
import java.util.stream.Stream;

public class NetworkTraffic extends TextView {
    private static final String TAG = "NetworkTraffic";

    private static final boolean DEBUG = false;

    // This must match the interface prefix in Connectivity's clatd.c.
    private static final String CLAT_PREFIX = "v4-";

    private static final int MODE_DISABLED = 0;
    private static final int MODE_UPSTREAM_AND_DOWNSTREAM = 3;

    private static final int MESSAGE_TYPE_PERIODIC_REFRESH = 0;
    private static final int MESSAGE_TYPE_UPDATE_VIEW = 1;
    private static final int MESSAGE_TYPE_ADD_NETWORK = 2;
    private static final int MESSAGE_TYPE_REMOVE_NETWORK = 3;

    private static final int REFRESH_INTERVAL = 2000;

    // Threshold is always defined in kbps (8 kbps = 1 kB/s)
    private static final long AUTOHIDE_THRESHOLD = 8;

    private final int mTextSizeMulti;
    private final Handler mTrafficHandler;
    private final SettingsObserver mObserver;

    private int mMode = MODE_DISABLED;
    private boolean mNetworkTrafficIsVisible;
    private long mTxKbps;
    private long mRxKbps;
    private long mLastTxBytes;
    private long mLastRxBytes;
    private long mLastUpdateTime;
    private boolean mAutoHide;
    private int mIconTint = Color.WHITE;
    private boolean mAttached;
    private boolean mBlocked;
    private boolean mScreenOn;
    private boolean mScreenReceiverRegistered;
    private boolean mObserverRegistered;

    // Network tracking related variables
    private ConnectivityManager mConnectivityManager;
    private ConnectivityManager.NetworkCallback mNetworkCallback;
    private ConnectivityManager.NetworkCallback mDefaultNetworkCallback;

    private final HashMap<Network, LinkProperties> mLinkPropertiesMap = new HashMap<>();
    // Used to indicate that the set of sources contributing
    // to current stats have changed.
    private boolean mNetworksChanged = true;

    public interface OnVisibilityChangeListener {
        void onTrafficVisibilityChanged(boolean visible);
    }

    private OnVisibilityChangeListener mVisibilityListener;

    public void setOnTrafficVisibilityChangedListener(OnVisibilityChangeListener listener) {
        mVisibilityListener = listener;
    }

    public boolean isTrafficVisible() {
        return mNetworkTrafficIsVisible;
    }

    public void setBlocked(boolean blocked) {
        if (mBlocked == blocked) {
            return;
        }
        mBlocked = blocked;
        if (mBlocked) {
            mNetworkTrafficIsVisible = false;
            mTrafficHandler.removeCallbacksAndMessages(null);
            unregisterNetworkCallbacks();
            unregisterScreenReceiver();
            mObserver.unobserve();
            setText("");
            if (mVisibilityListener == null) {
                setVisibility(GONE);
            }
        } else if (mAttached) {
            mScreenOn = isDisplayOn();
            mObserver.observe();
            updateSettings();
        }
    }

    public void onIconTintChanged(int tint) {
        mIconTint = tint;
        setTextColor(mIconTint);
    }

    public NetworkTraffic(Context context) {
        this(context, null);
    }

    public NetworkTraffic(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public NetworkTraffic(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        final Resources resources = getResources();
        mTextSizeMulti = resources.getDimensionPixelSize(R.dimen.net_traffic_multi_text_size);

        mNetworkTrafficIsVisible = false;

        mConnectivityManager = mContext.getSystemService(ConnectivityManager.class);
        mTrafficHandler = new Handler(mContext.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case MESSAGE_TYPE_PERIODIC_REFRESH:
                        if (!mScreenOn || mBlocked) {
                            return;
                        }
                        recalculateStats();
                        displayStatsAndReschedule();
                        break;

                    case MESSAGE_TYPE_UPDATE_VIEW:
                        if (!mScreenOn || mBlocked) {
                            return;
                        }
                        displayStatsAndReschedule();
                        break;

                    case MESSAGE_TYPE_ADD_NETWORK:
                        final LinkPropertiesHolder lph = (LinkPropertiesHolder) msg.obj;
                        mLinkPropertiesMap.put(lph.getNetwork(), lph.getLinkProperties());
                        mNetworksChanged = true;
                        break;

                    case MESSAGE_TYPE_REMOVE_NETWORK:
                        mLinkPropertiesMap.remove((Network) msg.obj);
                        mNetworksChanged = true;
                        break;
                }
            }

            private void recalculateStats() {
                final long now = SystemClock.elapsedRealtime();
                final long timeDelta = now - mLastUpdateTime; /* ms */
                if (timeDelta < REFRESH_INTERVAL * 0.95f) {
                    return;
                }
                // Sum tx and rx bytes from all sources of interest
                long txBytes = 0;
                long rxBytes = 0;
                // Add interface stats, including stats from Clat's IPv4 interface
                // (for applicable IPv6 networks). Stats are 0 if it doesn't exist.
                final String[] ifaces = mLinkPropertiesMap.values().stream()
                        .map(link -> link.getInterfaceName()).filter(iface -> iface != null)
                        .flatMap(iface -> Stream.of(iface, CLAT_PREFIX + iface))
                        .toArray(String[]::new);
                for (String iface : ifaces) {
                    final long ifaceTxBytes = TrafficStats.getTxBytes(iface);
                    final long ifaceRxBytes = TrafficStats.getRxBytes(iface);
                    if (DEBUG) {
                        Log.d(TAG, "adding stats from interface " + iface
                                + " txbytes " + ifaceTxBytes + " rxbytes " + ifaceRxBytes);
                    }
                    txBytes += ifaceTxBytes;
                    rxBytes += ifaceRxBytes;
                }

                final long txBytesDelta = txBytes - mLastTxBytes;
                final long rxBytesDelta = rxBytes - mLastRxBytes;

                if (!mNetworksChanged && timeDelta > 0 && txBytesDelta >= 0 && rxBytesDelta >= 0) {
                    mTxKbps = (long) (txBytesDelta * 8f / 1000f / (timeDelta / 1000f));
                    mRxKbps = (long) (rxBytesDelta * 8f / 1000f / (timeDelta / 1000f));
                } else if (mNetworksChanged) {
                    mTxKbps = 0;
                    mRxKbps = 0;
                    mNetworksChanged = false;
                }
                mLastTxBytes = txBytes;
                mLastRxBytes = rxBytes;
                mLastUpdateTime = now;
            }

            private void displayStatsAndReschedule() {
                final boolean enabled = mMode != MODE_DISABLED && isConnectionAvailable();
                final long totalKbps = mTxKbps + mRxKbps;
                final boolean shouldHide = mAutoHide && totalKbps < AUTOHIDE_THRESHOLD;

                final boolean isVisible = enabled && !shouldHide;
                if (mNetworkTrafficIsVisible != isVisible) {
                    mNetworkTrafficIsVisible = isVisible;
                    if (mVisibilityListener != null) {
                        mVisibilityListener.onTrafficVisibilityChanged(isVisible);
                    }
                }

                if (!isVisible) {
                    setText("");
                    if (mVisibilityListener == null) {
                        setVisibility(GONE);
                    }
                } else {
                    final String output = formatOutput(totalKbps);

                    // Update view if there's anything new to show
                    if (!output.contentEquals(getText())) {
                        setTextSize(TypedValue.COMPLEX_UNIT_PX, (float) mTextSizeMulti);
                        setText(output);
                    }
                    if (mVisibilityListener == null) {
                        setVisibility(VISIBLE);
                    }
                }

                // Schedule periodic refresh
                mTrafficHandler.removeMessages(MESSAGE_TYPE_PERIODIC_REFRESH);
                if (enabled && mScreenOn && !mBlocked
                        && (mNetworkTrafficIsVisible || mAutoHide)) {
                    mTrafficHandler.sendEmptyMessageDelayed(MESSAGE_TYPE_PERIODIC_REFRESH,
                            REFRESH_INTERVAL);
                }
            }

            private String formatOutput(long kbps) {
                final String value;
                final String unit;
                if (kbps < 8000) {
                    value = String.format("%.0f", (float) kbps / 8);
                    unit = mContext.getString(R.string.kilobytespersecond_short);
                } else {
                    final String format;
                    if (kbps < 80000) {
                        format = "%.2f";
                    } else if (kbps < 800000) {
                        format = "%.1f";
                    } else {
                        format = "%.0f";
                    }
                    value = String.format(format, (float) kbps / 8000);
                    unit = mContext.getString(R.string.megabytespersecond_short);
                }
                return value + "\n" + unit;
            }
        };
        mObserver = new SettingsObserver(mTrafficHandler);
    }

    private void registerScreenReceiver() {
        if (!mScreenReceiverRegistered && mAttached && !mBlocked && mMode != MODE_DISABLED) {
            IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
            filter.addAction(Intent.ACTION_SCREEN_ON);
            mContext.registerReceiver(mScreenReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            mScreenReceiverRegistered = true;
        }
    }

    private void unregisterScreenReceiver() {
        if (mScreenReceiverRegistered) {
            mContext.unregisterReceiver(mScreenReceiver);
            mScreenReceiverRegistered = false;
        }
    }

    private boolean isDisplayOn() {
        final Display display = getDisplay();
        if (display != null) {
            return display.getState() == Display.STATE_ON;
        }
        final PowerManager pm = mContext.getSystemService(PowerManager.class);
        return pm != null && pm.isInteractive();
    }

    private void unregisterNetworkCallbacks() {
        if (mNetworkCallback != null) {
            mConnectivityManager.unregisterNetworkCallback(mNetworkCallback);
            mNetworkCallback = null;
        }
        if (mDefaultNetworkCallback != null) {
            mConnectivityManager.unregisterNetworkCallback(mDefaultNetworkCallback);
            mDefaultNetworkCallback = null;
        }
    }

    private void manageNetworkCallbacks() {
        if (mMode == MODE_DISABLED || mBlocked) {
            // Unregister callbacks if disabling
            unregisterNetworkCallbacks();
            unregisterScreenReceiver();
            return;
        }

        registerScreenReceiver();

        // Register callbacks if enabling
        if (mNetworkCallback == null) {
            mNetworkCallback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onLinkPropertiesChanged(Network network,
                        LinkProperties linkProperties) {
                    Message msg = new Message();
                    msg.what = MESSAGE_TYPE_ADD_NETWORK;
                    msg.obj = new LinkPropertiesHolder(network, linkProperties);
                    mTrafficHandler.sendMessage(msg);
                }

                @Override
                public void onLost(Network network) {
                    Message msg = new Message();
                    msg.what = MESSAGE_TYPE_REMOVE_NETWORK;
                    msg.obj = network;
                    mTrafficHandler.sendMessage(msg);
                }
            };

            NetworkRequest request = new NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                    .build();

            mConnectivityManager.registerNetworkCallback(request, mNetworkCallback);
        }

        if (mDefaultNetworkCallback == null) {
            mDefaultNetworkCallback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    updateViewState();
                }

                @Override
                public void onLost(Network network) {
                    updateViewState();
                }
            };

            mConnectivityManager.registerDefaultNetworkCallback(mDefaultNetworkCallback);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        mAttached = true;
        if (mBlocked) {
            return;
        }
        mScreenOn = isDisplayOn();
        mObserver.observe();
        updateSettings();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        mAttached = false;
        mObserver.unobserve();
        mTrafficHandler.removeCallbacksAndMessages(null);
        unregisterNetworkCallbacks();
        unregisterScreenReceiver();
    }

    private final BroadcastReceiver mScreenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                mScreenOn = false;
                mTrafficHandler.removeMessages(MESSAGE_TYPE_PERIODIC_REFRESH);
                mTrafficHandler.removeMessages(MESSAGE_TYPE_UPDATE_VIEW);
            } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                mScreenOn = true;
                if (mMode != MODE_DISABLED && !mBlocked) {
                    mNetworksChanged = true;
                    mLastUpdateTime = 0;
                    mTrafficHandler.removeMessages(MESSAGE_TYPE_PERIODIC_REFRESH);
                    mTrafficHandler.sendEmptyMessage(MESSAGE_TYPE_PERIODIC_REFRESH);
                }
            }
        }
    };

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            if (mObserverRegistered) {
                return;
            }
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(LineageSettings.Secure.getUriFor(
                    LineageSettings.Secure.NETWORK_TRAFFIC_MODE),
                    false, this, UserHandle.USER_ALL);
            resolver.registerContentObserver(LineageSettings.Secure.getUriFor(
                    LineageSettings.Secure.NETWORK_TRAFFIC_AUTOHIDE),
                    false, this, UserHandle.USER_ALL);
            mObserverRegistered = true;
        }

        void unobserve() {
            if (mObserverRegistered) {
                mContext.getContentResolver().unregisterContentObserver(this);
                mObserverRegistered = false;
            }
        }

        @Override
        public void onChange(boolean selfChange) {
            updateSettings();
        }
    }

    private boolean isConnectionAvailable() {
        return mConnectivityManager.getActiveNetwork() != null;
    }

    private void updateSettings() {
        if (mBlocked) {
            return;
        }
        ContentResolver resolver = mContext.getContentResolver();

        mMode = LineageSettings.Secure.getInt(resolver,
                LineageSettings.Secure.NETWORK_TRAFFIC_MODE, 0);
        mAutoHide = LineageSettings.Secure.getInt(resolver,
                LineageSettings.Secure.NETWORK_TRAFFIC_AUTOHIDE, 0) == 1;

        manageNetworkCallbacks();
        updateViewState();
    }

    private void updateViewState() {
        if (mBlocked || !mScreenOn) {
            return;
        }
        mTrafficHandler.sendEmptyMessage(MESSAGE_TYPE_UPDATE_VIEW);
    }

    private static class LinkPropertiesHolder {
        private final Network mNetwork;
        private final LinkProperties mLinkProperties;

        public LinkPropertiesHolder(Network network, LinkProperties linkProperties) {
            mNetwork = network;
            mLinkProperties = linkProperties;
        }

        public Network getNetwork() {
            return mNetwork;
        }

        public LinkProperties getLinkProperties() {
            return mLinkProperties;
        }
    }
}
