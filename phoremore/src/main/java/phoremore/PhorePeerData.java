package helixmore;

/**
 * Created by furszy on 6/13/17.
 */

public class helixPeerData {

    private String host;
    private int tcpPort;
    private int sslPort;
    private long prunningLimit;

    public helixPeerData(String host, int tcpPort, int sslPort) {
        this.host = host;
        this.tcpPort = tcpPort;
        this.sslPort = sslPort;
    }

    public String getHost() {
        return host;
    }

    public int getTcpPort() {
        return tcpPort;
    }

    public int getSslPort() {
        return sslPort;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof helixPeerData)) return false;
        helixPeerData other = (helixPeerData) o;
        if (!this.host.equals(other.getHost())){
            return false;
        }
        if (this.tcpPort != other.tcpPort){
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "helixPeerData{" +
                "host='" + host + '\'' +
                ", tcpPort=" + tcpPort +
                ", sslPort=" + sslPort +
                ", prunningLimit=" + prunningLimit +
                '}';
    }
}
