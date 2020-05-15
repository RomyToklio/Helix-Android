package io.helix.android.service;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;

import org.helixj.core.Block;
import org.helixj.core.Coin;
import org.helixj.core.FilteredBlock;
import org.helixj.core.Peer;
import org.helixj.core.Transaction;
import org.helixj.core.TransactionConfidence;
import org.helixj.core.listeners.AbstractPeerDataEventListener;
import org.helixj.core.listeners.PeerConnectedEventListener;
import org.helixj.core.listeners.PeerDataEventListener;
import org.helixj.core.listeners.PeerDisconnectedEventListener;
import org.helixj.core.listeners.TransactionConfidenceEventListener;
import org.helixj.store.BlockStore;
import org.helixj.wallet.Wallet;
import org.helixj.wallet.listeners.WalletCoinsReceivedEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ScheduledExecutorService;

import chain.BlockchainManager;
import chain.BlockchainState;
import chain.Impediment;
import helixmore.listeners.AddressListener;
import io.helix.android.HelixApplication;
import io.helix.android.R;
import io.helix.android.module.HelixContext;
import io.helix.android.module.HelixModuleImp;
import io.helix.android.module.store.SnappyBlockchainStore;
import io.helix.android.rate.CoinMarketCapApiClient;
import io.helix.android.rate.CoinTypes;
import io.helix.android.rate.RequestHelixRateException;
import io.helix.android.rate.db.HelixRate;
import io.helix.android.ui.wallet_activity.WalletActivity;
import io.helix.android.utils.AppConf;
import io.helix.android.utils.CrashReporter;

import static io.helix.android.module.HelixContext.CONTEXT;
import static io.helix.android.service.IntentsConstants.ACTION_ADDRESS_BALANCE_CHANGE;
import static io.helix.android.service.IntentsConstants.ACTION_BROADCAST_TRANSACTION;
import static io.helix.android.service.IntentsConstants.ACTION_CANCEL_COINS_RECEIVED;
import static io.helix.android.service.IntentsConstants.ACTION_NOTIFICATION;
import static io.helix.android.service.IntentsConstants.ACTION_RESET_BLOCKCHAIN;
import static io.helix.android.service.IntentsConstants.ACTION_SCHEDULE_SERVICE;
import static io.helix.android.service.IntentsConstants.DATA_TRANSACTION_HASH;
import static io.helix.android.service.IntentsConstants.INTENT_BROADCAST_DATA_BLOCKCHAIN_STATE;
import static io.helix.android.service.IntentsConstants.INTENT_BROADCAST_DATA_ON_COIN_RECEIVED;
import static io.helix.android.service.IntentsConstants.INTENT_BROADCAST_DATA_PEER_CONNECTED;
import static io.helix.android.service.IntentsConstants.INTENT_BROADCAST_DATA_TYPE;
import static io.helix.android.service.IntentsConstants.INTENT_EXTRA_BLOCKCHAIN_STATE;
import static io.helix.android.service.IntentsConstants.NOT_BLOCKCHAIN_ALERT;
import static io.helix.android.service.IntentsConstants.NOT_COINS_RECEIVED;

/**
 * Created by furszy on 6/12/17.
 */

public class HelixWalletService extends Service{

    private Logger log = LoggerFactory.getLogger(HelixWalletService.class);

    private HelixApplication HelixApplication;
    private HelixModuleImp module;
    //private HelixPeergroup HelixPeergroup;
    private BlockchainManager blockchainManager;

    private PeerConnectivityListener peerConnectivityListener;

    private PowerManager.WakeLock wakeLock;
    private NotificationManager nm;
    private LocalBroadcastManager broadcastManager;

    private SnappyBlockchainStore blockchainStore;
    private boolean resetBlockchainOnShutdown = false;
    /** Created service time (just for checks) */
    private long serviceCreatedAt;
    /** Cached amount to notify balance */
    private Coin notificationAccumulatedAmount = Coin.ZERO;
    /**  */
    private final Set<Impediment> impediments = EnumSet.noneOf(Impediment.class);

    private BlockchainState blockchainState = BlockchainState.NOT_CONNECTION;

    private volatile long lastUpdateTime = System.currentTimeMillis();
    private volatile long lastMessageTime = System.currentTimeMillis();

    public class helixBinder extends Binder {
        public HelixWalletService getService() {
            return HelixWalletService.this;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return new helixBinder();
    }

    private AddressListener addressListener = new AddressListener() {
        @Override
        public void onBalanceChange(String address, long confirmed, long unconfirmed,int numConfirmations) {
            Intent intent = new Intent(ACTION_ADDRESS_BALANCE_CHANGE);
            broadcastManager.sendBroadcast(intent);
        }
    };

    private final class PeerConnectivityListener implements PeerConnectedEventListener, PeerDisconnectedEventListener{

        @Override
        public void onPeerConnected(Peer peer, int i) {
            //todo: notify peer connected
            log.info("Peer connected: "+peer.getAddress());
            broadcastPeerConnected();
        }

        @Override
        public void onPeerDisconnected(Peer peer, int i) {
            //todo: notify peer disconnected
            log.info("Peer disconnected: "+peer.getAddress());
        }
    }

    private final PeerDataEventListener blockchainDownloadListener = new AbstractPeerDataEventListener() {
        @Override
        public void onBlocksDownloaded(final Peer peer, final Block block, final FilteredBlock filteredBlock, final int blocksLeft) {
            try {
                final long now = System.currentTimeMillis();
                if (now - lastMessageTime > TimeUnit.SECONDS.toMillis(6)) {
                    if (blocksLeft < 6) {
                        blockchainState = BlockchainState.SYNC;
                    } else {
                        blockchainState = BlockchainState.SYNCING;
                    }
                    HelixApplication.getAppConf().setLastBestChainBlockTime(block.getTime().getTime());
                    broadcastBlockchainState(true);
                }
            }catch (Exception e){
                e.printStackTrace();
                CrashReporter.saveBackgroundTrace(e,HelixApplication.getPackageInfo());
            }
        }
    };

    private class RunnableBlockChecker implements Runnable{

        private Block block;

        public RunnableBlockChecker(Block block) {
            this.block = block;
        }

        @Override
        public void run() {
            org.helixj.core.Context.propagate(HelixContext.CONTEXT);
            lastMessageTime = System.currentTimeMillis();
            broadcastBlockchainState(false);
        }
    }


    private final Handler mHandler = new Handler();
    private final ScheduledExecutorService mRateScheduler = Executors.newScheduledThreadPool(1);
    private ScheduledFuture<?> mRateFuture = null;

    final Runnable mRateRunnable = new Runnable() {
        public void run() {
            try {
                CoinMarketCapApiClient c = new CoinMarketCapApiClient();
                CoinMarketCapApiClient.helixMarket helixMarket = c.gethelixPrice();
                HelixRate HelixRate = new HelixRate("USD",helixMarket.priceUsd,System.currentTimeMillis());
                module.saveRate(HelixRate);

                final HelixRate helixBtcRate = new HelixRate("BTC", helixMarket.priceBtc, System.currentTimeMillis());
                module.saveRate(helixBtcRate);

                // Get the rest of the rates:
                List<HelixRate> rates = new CoinMarketCapApiClient.BitPayApi().getRates(new CoinMarketCapApiClient.BitPayApi.RatesConvertor<HelixRate>() {
                    @Override
                    public HelixRate convertRate(String code, String name, BigDecimal bitcoinRate) {
                        BigDecimal rate = bitcoinRate.multiply(helixBtcRate.getRate());
                        return new HelixRate(code,rate,System.currentTimeMillis());
                    }
                });
                for (HelixRate rate : rates) {
                    module.saveRate(rate);
                }
            } catch (RequestHelixRateException e) {
                e.printStackTrace();
            } catch (Exception e){
                e.printStackTrace();
            }
        }
     };

    private void startRateTimer() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (mRateFuture == null && cm.getActiveNetworkInfo() != null) {
            mRateFuture = mRateScheduler.scheduleAtFixedRate(mRateRunnable, 0, 60, TimeUnit.SECONDS);
        }
    }

    private void stopRateTimer() {
        if (mRateFuture != null) {
            mRateFuture.cancel(true);
            mRateFuture = null;
        }
    }

    private final BroadcastReceiver connectivityReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                final String action = intent.getAction();
                if (ConnectivityManager.CONNECTIVITY_ACTION.equals(action)) {
                    final NetworkInfo networkInfo = (NetworkInfo) intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);
                    final boolean hasConnectivity = networkInfo.isConnected();
                    log.info("network is {}, state {}/{}", hasConnectivity ? "up" : "down", networkInfo.getState(), networkInfo.getDetailedState());
                    if (hasConnectivity) {
                        impediments.remove(Impediment.NETWORK);
                        startRateTimer();
                    } else {
                        impediments.add(Impediment.NETWORK);
                        stopRateTimer();
                    }
                    check();
                } else if (Intent.ACTION_DEVICE_STORAGE_LOW.equals(action)) {
                    log.info("device storage low");

                    impediments.add(Impediment.STORAGE);
                    check();
                } else if (Intent.ACTION_DEVICE_STORAGE_OK.equals(action)) {
                    log.info("device storage ok");
                    impediments.remove(Impediment.STORAGE);
                    check();
                }
            }catch (Exception e){
                e.printStackTrace();
            }
        }
    };

    private WalletCoinsReceivedEventListener coinReceiverListener = new WalletCoinsReceivedEventListener() {

        android.support.v4.app.NotificationCompat.Builder mBuilder;
        PendingIntent deleteIntent;
        PendingIntent openPendingIntent;

        @Override
        public void onCoinsReceived(Wallet wallet, Transaction transaction, Coin coin, Coin coin1) {
            //todo: acá falta una validación para saber si la transaccion es mia.
            org.helixj.core.Context.propagate(CONTEXT);

            try {

                int depthInBlocks = transaction.getConfidence().getDepthInBlocks();

                long now = System.currentTimeMillis();
                if (lastUpdateTime + 5000 < now) {
                    lastUpdateTime = now;
                    Intent intent = new Intent(ACTION_NOTIFICATION);
                    intent.putExtra(INTENT_BROADCAST_DATA_TYPE, INTENT_BROADCAST_DATA_ON_COIN_RECEIVED);
                    broadcastManager.sendBroadcast(intent);
                }

                //final Address address = WalletUtils.getWalletAddressOfReceived(WalletConstants.NETWORK_PARAMETERS,transaction, wallet);
                final Coin amount = transaction.getValue(wallet);
                final TransactionConfidence.ConfidenceType confidenceType = transaction.getConfidence().getConfidenceType();

                if (amount.isGreaterThan(Coin.ZERO)) {
                    //notificationCount++;
                    notificationAccumulatedAmount = notificationAccumulatedAmount.add(amount);
                    Intent openIntent = new Intent(getApplicationContext(), WalletActivity.class);
                    openPendingIntent = PendingIntent.getActivity(getApplicationContext(), 0, openIntent, 0);
                    Intent resultIntent = new Intent(getApplicationContext(), HelixWalletService.this.getClass());
                    resultIntent.setAction(ACTION_CANCEL_COINS_RECEIVED);
                    deleteIntent = PendingIntent.getService(HelixWalletService.this, 0, resultIntent, PendingIntent.FLAG_CANCEL_CURRENT);
                    mBuilder = new NotificationCompat.Builder(getApplicationContext())
                            .setContentTitle(
                                HelixWalletService.this.getString(R.string.notification_receive_title))
                            .setContentText(String.format(
                                HelixWalletService.this.getString(R.string.notification_receive_message),
                                notificationAccumulatedAmount.toFriendlyString()))
                            .setAutoCancel(true)
                            .setSmallIcon(R.mipmap.ic_notification)
                            .setColor(
                                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                                ? getResources().getColor(R.color.bgGreen, null)
                                : ContextCompat.getColor(HelixWalletService.this, R.color.bgGreen))
                            .setDeleteIntent(deleteIntent)
                            .setContentIntent(openPendingIntent);
                    nm.notify(NOT_COINS_RECEIVED, mBuilder.build());
                } else {
                    log.error("transaction with a value lesser than zero arrives..");
                }

            }catch (Exception e){
                e.printStackTrace();
            }

        }
    };

    private TransactionConfidenceEventListener transactionConfidenceEventListener = new TransactionConfidenceEventListener() {
        @Override
        public void onTransactionConfidenceChanged(Wallet wallet, Transaction transaction) {
            org.helixj.core.Context.propagate(CONTEXT);
            try {
                if (transaction != null) {
                    if (transaction.getConfidence().getDepthInBlocks() > 1) {
                        long now = System.currentTimeMillis();
                        if (lastUpdateTime + 5000 < now) {
                            lastUpdateTime = now;
                            // update balance state
                            Intent intent = new Intent(ACTION_NOTIFICATION);
                            intent.putExtra(INTENT_BROADCAST_DATA_TYPE, INTENT_BROADCAST_DATA_ON_COIN_RECEIVED);
                            broadcastManager.sendBroadcast(intent);
                        }
                    }
                }
            }catch (Exception e){
                e.printStackTrace();
            }
        }
    };

    @Override
    public void onCreate() {
        serviceCreatedAt = System.currentTimeMillis();
        super.onCreate();
        try {
            log.info("helix service started");
            // Android stuff
            final String lockName = getPackageName() + " blockchain sync";
            final PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, lockName);
            nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            broadcastManager = LocalBroadcastManager.getInstance(this);
            HelixApplication = HelixApplication.getInstance();
            module = (HelixModuleImp) HelixApplication.getModule();
            blockchainManager = module.getBlockchainManager();
            // Schedule service
            tryScheduleService();
            // Request rate
            startRateTimer();

            peerConnectivityListener = new PeerConnectivityListener();

            File file = getDir("blockstore_v2",MODE_PRIVATE);
            String filename = HelixContext.Files.BLOCKCHAIN_FILENAME;
            boolean fileExists = new File(file,filename).exists();

            org.helixj.core.Context.propagate(HelixContext.CONTEXT);
           /* log.info("helixj starting");

            HelixContext.CONTEXT.initHelix(true,false);
            log.info("helixj started");*/

            blockchainStore = new SnappyBlockchainStore(HelixContext.CONTEXT,file,filename);
            blockchainManager.init(
                    blockchainStore,
                    file,
                    filename,
                    fileExists
            );

            module.addCoinsReceivedEventListener(coinReceiverListener);
            module.addOnTransactionConfidenceChange(transactionConfidenceEventListener);

            final IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
            intentFilter.addAction(Intent.ACTION_DEVICE_STORAGE_LOW);
            intentFilter.addAction(Intent.ACTION_DEVICE_STORAGE_OK);
            registerReceiver(connectivityReceiver, intentFilter); // implicitly init PeerGroup
        } catch (Error e){
            e.printStackTrace();
            CrashReporter.appendSavedBackgroundTraces(e);
            Intent intent = new Intent(IntentsConstants.ACTION_STORED_BLOCKCHAIN_ERROR);
            broadcastManager.sendBroadcast(intent);
            throw e;
        } catch (Exception e){
            // todo: I have to handle the connection refused..
            e.printStackTrace();
            CrashReporter.appendSavedBackgroundTraces(e);
            // for now i just launch a notification
            Intent intent = new Intent(IntentsConstants.ACTION_TRUSTED_PEER_CONNECTION_FAIL);
            broadcastManager.sendBroadcast(intent);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        log.info("helix service onStartCommand");
        try {
            if (intent != null) {
                try {
                    log.info("service init command: " + intent
                            + (intent.hasExtra(Intent.EXTRA_ALARM_COUNT) ? " (alarm count: " + intent.getIntExtra(Intent.EXTRA_ALARM_COUNT, 0) + ")" : ""));
                } catch (Exception e) {
                    e.printStackTrace();
                    log.info("service init command: " + intent
                            + (intent.hasExtra(Intent.EXTRA_ALARM_COUNT) ? " (alarm count: " + intent.getLongArrayExtra(Intent.EXTRA_ALARM_COUNT) + ")" : ""));
                }
                final String action = intent.getAction();
                if (ACTION_SCHEDULE_SERVICE.equals(action)) {
                    check();
                } else if (ACTION_CANCEL_COINS_RECEIVED.equals(action)) {
                    notificationAccumulatedAmount = Coin.ZERO;
                    nm.cancel(NOT_COINS_RECEIVED);
                } else if (ACTION_RESET_BLOCKCHAIN.equals(action)) {
                    log.info("will remove blockchain on service shutdown");
                    resetBlockchainOnShutdown = true;
                    stopSelf();
                } else if (ACTION_BROADCAST_TRANSACTION.equals(action)) {
                    blockchainManager.broadcastTransaction(intent.getByteArrayExtra(DATA_TRANSACTION_HASH));
                }
            } else {
                log.warn("service restart, although it was started as non-sticky");
            }
        }catch (Exception e){
            e.printStackTrace();
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        log.info(".onDestroy()");
        try {
            // todo: notify module about this shutdown...
            unregisterReceiver(connectivityReceiver);

            // remove listeners
            module.removeCoinsReceivedEventListener(coinReceiverListener);
            module.removeTransactionsConfidenceChange(transactionConfidenceEventListener);
            blockchainManager.removeBlockchainDownloadListener(blockchainDownloadListener);
            // destroy the blockchain
            /*if (resetBlockchainOnShutdown){
                try {
                    blockchainStore.truncate();
                }catch (Exception e){
                    e.printStackTrace();
                }
            }*/
            blockchainManager.destroy(resetBlockchainOnShutdown);

            /*if (HelixPeergroup.isRunning()) {
                HelixPeergroup.shutdown();
            }*/

            if (wakeLock.isHeld()) {
                log.debug("wakelock still held, releasing");
                wakeLock.release();
            }

            log.info("service was up for " + ((System.currentTimeMillis() - serviceCreatedAt) / 1000 / 60) + " minutes");
            // schedule service it is not scheduled yet
            tryScheduleService();
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    /**
     * Schedule service for later
     */
    private void tryScheduleService() {
        boolean isSchedule = System.currentTimeMillis()<module.getConf().getScheduledBLockchainService();

        if (!isSchedule){
            log.info("scheduling service");
            AlarmManager alarm = (AlarmManager)getSystemService(ALARM_SERVICE);
            // long scheduleTime = System.currentTimeMillis() + 1000 * 15; //(1000 * 60 * 60);
            long scheduleTime = System.currentTimeMillis();

            Intent intent = new Intent(this, HelixWalletService.class);
            intent.setAction(ACTION_SCHEDULE_SERVICE);
            alarm.set(
                    // This alarm will wake up the device when System.currentTimeMillis()
                    // equals the second argument value
                    alarm.RTC_WAKEUP,
                    scheduleTime,
                    // PendingIntent.getService creates an Intent that will start a service
                    // when it is called. The first argument is the Context that will be used
                    // when delivering this intent. Using this has worked for me. The second
                    // argument is a request code. You can use this code to cancel the
                    // pending intent if you need to. Third is the intent you want to
                    // trigger. In this case I want to create an intent that will start my
                    // service. Lastly you can optionally pass flags.
                    PendingIntent.getService(this, 0,intent , 0)
            );
            // save
            module.getConf().saveScheduleBlockchainService(scheduleTime);
        }
    }

    private AtomicBoolean isChecking = new AtomicBoolean(false);

    /**
     * Check and download the blockchain if it needed
     */
    private void check() {
        log.info("check");
        try {
            if (!isChecking.getAndSet(true)) {
                blockchainManager.check(
                        impediments,
                        peerConnectivityListener,
                        peerConnectivityListener,
                        blockchainDownloadListener);
                //todo: ver si conviene esto..
                broadcastBlockchainState(true);
                isChecking.set(false);
            }
        }catch (Exception e){
            e.printStackTrace();
            isChecking.set(false);
            broadcastBlockchainState(false);
        }
    }

    private void broadcastBlockchainState(boolean isCheckOk) {
        boolean showNotif = false;
        if (!impediments.isEmpty()) {

            StringBuilder stringBuilder = new StringBuilder();
            for (Impediment impediment : impediments) {
                if (stringBuilder.length()!=0){
                    stringBuilder.append("\n");
                }
                if (impediment == Impediment.NETWORK){
                    blockchainState = BlockchainState.NOT_CONNECTION;
                    stringBuilder.append("No peer connection");
                }else if(impediment == Impediment.STORAGE){
                    stringBuilder.append("No available storage");
                    showNotif = true;
                }
            }

            if(showNotif) {
                android.support.v4.app.NotificationCompat.Builder mBuilder =
                        new NotificationCompat.Builder(getApplicationContext())
                                .setSmallIcon(R.mipmap.ic_launcher)
                                .setContentTitle("Alert")
                                .setContentText(stringBuilder.toString())
                                .setAutoCancel(true)
                                .setColor(
                                        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) ?
                                                getResources().getColor(R.color.bgGreen,null)
                                                :
                                                ContextCompat.getColor(HelixWalletService.this,R.color.bgGreen))
                        ;

                nm.notify(NOT_BLOCKCHAIN_ALERT, mBuilder.build());
            }
        }

        if (isCheckOk){
            broadcastBlockchainStateIntent();
        }
    }

    private static int num = 0;

    private void broadcastBlockchainStateIntent(){
        final long now = System.currentTimeMillis();
        if (now-lastMessageTime> TimeUnit.SECONDS.toMillis(6)) {
            lastMessageTime = System.currentTimeMillis();
            num++;
            log.warn("broadcasting blockchain state change.. "+num);
            Intent intent = new Intent(ACTION_NOTIFICATION);
            intent.putExtra(INTENT_BROADCAST_DATA_TYPE, INTENT_BROADCAST_DATA_BLOCKCHAIN_STATE);
            intent.putExtra(INTENT_EXTRA_BLOCKCHAIN_STATE,blockchainState);
            broadcastManager.sendBroadcast(intent);
        }
    }

    private void broadcastPeerConnected() {
        Intent intent = new Intent(ACTION_NOTIFICATION);
        intent.putExtra(INTENT_BROADCAST_DATA_TYPE, INTENT_BROADCAST_DATA_PEER_CONNECTED);
        broadcastManager.sendBroadcast(intent);
    }

}
