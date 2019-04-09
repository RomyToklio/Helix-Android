package helixmore.listeners;

import java.util.List;

import helixmore.helixPeer;
import helixmore.messages.responses.StatusHistory;
import helixmore.messages.responses.Unspent;
import helixmore.utility.TxHashHeightWrapper;

/**
 * Created by furszy on 6/17/17.
 */

public interface PeerDataListener {

    void onSubscribedAddressChange(helixPeer helixmorePeer, String address, String status);

    void onListUnpent(helixPeer helixmorePeer,String address, List<Unspent> unspent);

    void onBalanceReceive(helixPeer helixmorePeer, String address, long confirmed, long unconfirmed);

    void onGetHistory(helixPeer helixmorePeer, StatusHistory statusHistory);
}
