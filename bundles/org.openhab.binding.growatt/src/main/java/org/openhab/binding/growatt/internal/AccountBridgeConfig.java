package org.openhab.binding.growatt.internal;

import org.eclipse.jdt.annotation.NonNullByDefault;

@NonNullByDefault
public class AccountBridgeConfig {

    private String username = "user";
    private String password = "password";
    private String server = "server.growatt.com";

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getServer() {
        return server;
    }

    public void setServer(String server) {
        this.server = server;
    }

}
