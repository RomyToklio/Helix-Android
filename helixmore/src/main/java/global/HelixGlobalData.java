package global;

import java.util.ArrayList;
import java.util.List;

import helixmore.helixPeerData;

/**
 * Created by furszy on 7/2/17.
 */

public class helixGlobalData {

    public static final String[] TRUSTED_NODES = new String[] {
        "helix.timhorton.ninja",
    };

    public static final List<helixPeerData> listTrustedHosts() {
        List<helixPeerData> list = new ArrayList<>();
        for (String trustedNode : TRUSTED_NODES) {
            list.add(new helixPeerData(trustedNode,11774,11774));
        }
        return list;
    }

}
