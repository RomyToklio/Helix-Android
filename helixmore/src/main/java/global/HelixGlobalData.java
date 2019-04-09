package global;

import java.util.ArrayList;
import java.util.List;

import helixmore.HelixPeerData;

/**
 * Created by furszy on 7/2/17.
 */

public class HelixGlobalData {

    public static final String[] TRUSTED_NODES = new String[] {
        "helix.timhorton.ninja",
    };

    public static final List<HelixPeerData> listTrustedHosts() {
        List<HelixPeerData> list = new ArrayList<>();
        for (String trustedNode : TRUSTED_NODES) {
            list.add(new HelixPeerData(trustedNode,11774,11774));
        }
        return list;
    }

}
