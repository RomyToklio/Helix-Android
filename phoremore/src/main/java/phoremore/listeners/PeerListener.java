package helixmore.listeners;

import helixmore.helixPeer;

/**
 * Created by furszy on 6/17/17.
 */

public interface PeerListener {

    void onConnected(helixPeer helixmorePeer);

    void onDisconnected(helixPeer helixmorePeer);

    void onExceptionCaught(helixPeer helixmorePeer, Exception e);
}
