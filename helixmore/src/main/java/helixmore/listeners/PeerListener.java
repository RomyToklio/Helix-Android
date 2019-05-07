package helixmore.listeners;

import helixmore.HelixPeer;

/**
 * Created by furszy on 6/17/17.
 */

public interface PeerListener {

    void onConnected(HelixPeer helixmorePeer);

    void onDisconnected(HelixPeer helixmorePeer);

    void onExceptionCaught(HelixPeer helixmorePeer, Exception e);
}
