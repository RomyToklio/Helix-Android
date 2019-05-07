package helixmore.listeners;

import java.util.List;

import helixmore.HelixPeer;
import helixmore.messages.responses.StatusHistory;
import helixmore.messages.responses.Unspent;
import helixmore.utility.TxHashHeightWrapper;

/**
 * Created by furszy on 6/17/17.
 */

public interface PeerDataListener {

    void onSubscribedAddressChange(HelixPeer helixmorePeer, String address, String status);

    void onListUnpent(HelixPeer helixmorePeer,String address, List<Unspent> unspent);

    void onBalanceReceive(HelixPeer helixmorePeer, String address, long confirmed, long unconfirmed);

    void onGetHistory(HelixPeer helixmorePeer, StatusHistory statusHistory);
}
